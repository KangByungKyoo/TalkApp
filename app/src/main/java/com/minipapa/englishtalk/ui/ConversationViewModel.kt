package com.minipapa.englishtalk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import androidx.lifecycle.ViewModelProvider
import com.minipapa.englishtalk.data.FirebaseSessionRepository
import com.minipapa.englishtalk.data.SessionRepository
import com.minipapa.englishtalk.data.SessionCreationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import com.minipapa.englishtalk.voice.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import com.minipapa.englishtalk.settings.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect

class ConversationViewModel(
    private val sessionRepository: SessionRepository = FirebaseSessionRepository(),
    private val voiceClient: VoiceClient? = null,
    private val settingsRepository: ConversationSettingsRepository? = null
) : ViewModel() {
    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState = _uiState.asStateFlow()
    private var connectionJob: Job? = null
    private var connectionGeneration = 0

    init {
        if (settingsRepository != null) {
            _uiState.value = _uiState.value.copy(settingsLoaded = false)
            viewModelScope.launch {
                settingsRepository.settings.catch {
                    _uiState.value = _uiState.value.copy(settingsNotice = "설정을 불러올 수 없습니다. 앱을 다시 실행해 주세요.")
                }.collect { settings ->
                    val old = _uiState.value
                    if (old.isActive && settings.character != old.settings.character) endConversation()
                    val active = _uiState.value.isActive
                    val changed = old.settings != settings
                    _uiState.value = _uiState.value.copy(settings = settings, settingsLoaded = true,
                        settingsApplying = if (active && changed) true else _uiState.value.settingsApplying)
                    if (active && changed && voiceClient?.updateSettings(settings) == false) {
                        endConversation()
                        _uiState.value = _uiState.value.copy(settingsNotice = "새 목소리를 적용하려면 대화를 다시 시작해 주세요.")
                    }
                }
            }
        }
    }

    fun selectCharacter(character: ConversationCharacter) {
        val state = _uiState.value
        if (!state.settingsLoaded || state.settingsSaving || state.settingsApplying || state.settings.character == character) return
        val repository = settingsRepository ?: return
        val wasActive = state.isActive
        if (wasActive) endConversation()
        _uiState.value = _uiState.value.copy(settingsSaving = true,
            settingsNotice = if (wasActive) "캐릭터와 목소리를 변경하여 대화가 종료됐습니다. 새 친구와 대화를 시작해 주세요." else null)
        viewModelScope.launch {
            try { repository.selectCharacter(character) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _uiState.value = _uiState.value.copy(settingsNotice = "캐릭터 설정을 저장하지 못했습니다. 다시 선택해 주세요.") }
            finally { _uiState.value = _uiState.value.copy(settingsSaving = false) }
        }
    }

    fun selectResponseLength(length: ResponseLength) {
        val state = _uiState.value
        if (!state.settingsLoaded || state.settingsSaving || state.settingsApplying || state.settings.responseLength == length) return
        val repository = settingsRepository ?: return
        _uiState.value = state.copy(settingsSaving = true, settingsNotice = null)
        viewModelScope.launch {
            try { repository.selectResponseLength(length) }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { _uiState.value = _uiState.value.copy(settingsNotice = "답변 길이를 저장하지 못했습니다. 다시 선택해 주세요.") }
            finally { _uiState.value = _uiState.value.copy(settingsSaving = false) }
        }
    }

    fun testServerConnection() {
        if (!_uiState.value.settingsLoaded || _uiState.value.settingsSaving || _uiState.value.isActive || _uiState.value.serverTestStatus == ServerTestStatus.LOADING) return
        _uiState.value = _uiState.value.copy(
            serverTestStatus = ServerTestStatus.LOADING,
            serverTestMessage = "Firebase 로그인 및 OpenAI 임시 인증 요청 중…"
        )
        viewModelScope.launch {
            try {
                val credentials = sessionRepository.createSession()
                // Stage 2 test only: discard the token; never put it in UI state or storage.
                _uiState.value = _uiState.value.copy(
                    serverTestStatus = ServerTestStatus.SUCCESS,
                    serverTestMessage = "인증 성공 · ${credentials.model}\n발급 당시 남은 유효 시간: ${credentials.remainingSecondsAtReceipt}초\n실제 음성 대화는 대화 시작 버튼을 눌러 주세요."
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionCreationException) {
                _uiState.value = _uiState.value.copy(serverTestStatus = ServerTestStatus.ERROR, serverTestMessage = "인증 실패 · ${e.userMessage}")
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(serverTestStatus = ServerTestStatus.ERROR, serverTestMessage = "인증 실패 · 서버에 연결할 수 없습니다.")
            }
        }
    }

    fun startConversation() {
        if (!_uiState.value.settingsLoaded || _uiState.value.settingsSaving || _uiState.value.isActive || _uiState.value.serverTestStatus == ServerTestStatus.LOADING) return
        val id = ++connectionGeneration
        _uiState.value = _uiState.value.copy(status = ConversationStatus.CONNECTING, errorMessage = null,
            settingsApplying = true, settingsNotice = null, appliedSettings = null, inputTokens = 0, outputTokens = 0)
        connectionJob = viewModelScope.launch {
            try {
                val client = voiceClient ?: throw VoiceConnectionException("음성 연결이 초기화되지 않았습니다.")
                withTimeout(50_000) {
                    val credentials = sessionRepository.createSession()
                    if (id != connectionGeneration) return@withTimeout
                    client.connect(credentials, _uiState.value.settings) { event ->
                        viewModelScope.launch { if (id == connectionGeneration) onVoiceEvent(event) }
                    }
                }
            } catch (_: TimeoutCancellationException) {
                if (id == connectionGeneration) failConversation("음성 연결 시간이 초과되었습니다. 다시 시작해 주세요.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: SessionCreationException) {
                if (id == connectionGeneration) failConversation(e.userMessage)
            } catch (e: VoiceConnectionException) {
                if (id == connectionGeneration) failConversation(e.userMessage)
            } catch (_: Exception) {
                if (id == connectionGeneration) failConversation("음성 연결에 실패했습니다. 네트워크와 마이크 권한을 확인해 주세요.")
            }
        }
    }

    fun endConversation() {
        if (!_uiState.value.isActive) return
        connectionGeneration++
        connectionJob?.cancel()
        voiceClient?.close()
        _uiState.value = _uiState.value.copy(status = ConversationStatus.ENDED, errorMessage = null, settingsApplying = false, appliedSettings = null)
    }

    fun onMicrophonePermissionDenied() {
        failConversation("마이크 권한이 필요합니다. 다시 시작하거나 앱 설정에서 마이크 권한을 허용해 주세요.")
    }

    private fun onVoiceEvent(event: VoiceEvent) {
        when (event) {
            VoiceEvent.Connected -> _uiState.value = _uiState.value.copy(status = ConversationStatus.TALKING, voiceActivity = VoiceActivity.THINKING)
            VoiceEvent.Listening -> _uiState.value = _uiState.value.copy(voiceActivity = VoiceActivity.LISTENING)
            VoiceEvent.UserSpeaking -> _uiState.value = _uiState.value.copy(voiceActivity = VoiceActivity.USER_SPEAKING)
            VoiceEvent.Thinking -> _uiState.value = _uiState.value.copy(voiceActivity = VoiceActivity.THINKING)
            VoiceEvent.AiSpeaking -> _uiState.value = _uiState.value.copy(voiceActivity = VoiceActivity.AI_SPEAKING)
            is VoiceEvent.SettingsApplied -> if (event.settings == _uiState.value.settings) {
                _uiState.value = _uiState.value.copy(settingsApplying = false, appliedSettings = event.settings)
            }
            is VoiceEvent.SessionConfiguration -> Unit // Consumed by the transport before the public callback.
            is VoiceEvent.Usage -> _uiState.value = _uiState.value.copy(
                inputTokens = _uiState.value.inputTokens + event.inputTokens,
                outputTokens = _uiState.value.outputTokens + event.outputTokens)
            is VoiceEvent.Error -> failConversation(event.message)
        }
    }

    private fun failConversation(message: String) {
        connectionGeneration++
        connectionJob?.cancel()
        voiceClient?.close()
        _uiState.value = _uiState.value.copy(status = ConversationStatus.ERROR, errorMessage = message, settingsApplying = false, appliedSettings = null)
    }

    override fun onCleared() {
        connectionGeneration++
        voiceClient?.close()
        super.onCleared()
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass.isAssignableFrom(ConversationViewModel::class.java))
                return ConversationViewModel(voiceClient = WebRtcVoiceClient(context.applicationContext),
                    settingsRepository = DataStoreConversationSettingsRepository(context.applicationContext)) as T
            }
        }
    }
}
