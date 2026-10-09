package com.minipapa.englishtalk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.minipapa.englishtalk.data.FirebaseSessionRepository
import com.minipapa.englishtalk.data.SessionRepository
import com.minipapa.englishtalk.data.SessionCreationException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConversationViewModel(private val sessionRepository: SessionRepository = FirebaseSessionRepository()) : ViewModel() {
    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState = _uiState.asStateFlow()
    private var connectionJob: Job? = null

    fun testServerConnection() {
        if (_uiState.value.serverTestStatus == ServerTestStatus.LOADING) return
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
                    serverTestMessage = "인증 성공 · ${credentials.model}\n발급 당시 남은 유효 시간: ${credentials.remainingSecondsAtReceipt}초\n연결 테스트를 완료했습니다. 음성 연결은 다음 단계에서 제공됩니다."
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
        if (_uiState.value.isActive) return
        _uiState.value = _uiState.value.copy(status = ConversationStatus.CONNECTING, errorMessage = null)
        connectionJob = viewModelScope.launch {
            // Stage 1: UI simulation only. Replace with session events in a later stage.
            delay(1000)
            _uiState.value = _uiState.value.copy(status = ConversationStatus.TALKING)
        }
    }

    fun endConversation() {
        if (!_uiState.value.isActive) return
        connectionJob?.cancel()
        _uiState.value = _uiState.value.copy(status = ConversationStatus.ENDED, errorMessage = null)
    }

    fun onMicrophonePermissionDenied() {
        connectionJob?.cancel()
        _uiState.value = _uiState.value.copy(
            status = ConversationStatus.ERROR,
            errorMessage = "마이크 권한이 필요합니다. 다시 시작하거나 앱 설정에서 마이크 권한을 허용해 주세요."
        )
    }
}
