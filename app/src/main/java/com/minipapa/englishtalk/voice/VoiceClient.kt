package com.minipapa.englishtalk.voice

import com.minipapa.englishtalk.data.SessionCredentials

interface VoiceClient {
    suspend fun connect(credentials: SessionCredentials, onEvent: (VoiceEvent) -> Unit)
    fun close()
}

sealed interface VoiceEvent {
    data object Connected : VoiceEvent
    data object Listening : VoiceEvent
    data object UserSpeaking : VoiceEvent
    data object Thinking : VoiceEvent
    data object AiSpeaking : VoiceEvent
    data class Error(val message: String) : VoiceEvent
}

class VoiceConnectionException(val userMessage: String) : Exception(userMessage)
