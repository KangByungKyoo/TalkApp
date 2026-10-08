package com.minipapa.englishtalk.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ConversationViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ConversationUiState())
    val uiState = _uiState.asStateFlow()
    private var connectionJob: Job? = null

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
