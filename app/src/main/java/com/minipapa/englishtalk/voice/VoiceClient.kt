package com.minipapa.englishtalk.voice

import com.minipapa.englishtalk.data.SessionCredentials
import com.minipapa.englishtalk.settings.ConversationSettings

interface VoiceClient {
    suspend fun connect(credentials: SessionCredentials, settings: ConversationSettings = ConversationSettings(), onEvent: (VoiceEvent) -> Unit)
    fun updateSettings(settings: ConversationSettings): Boolean
    fun close()
}

sealed interface VoiceEvent {
    data object Connected : VoiceEvent
    data object Listening : VoiceEvent
    data object UserSpeaking : VoiceEvent
    data object Thinking : VoiceEvent
    data object AiSpeaking : VoiceEvent
    data class SessionConfiguration(val instructions: String, val voiceId: String) : VoiceEvent
    data class SettingsApplied(val settings: ConversationSettings) : VoiceEvent
    data class Usage(val inputTokens: Int, val outputTokens: Int) : VoiceEvent
    data class Error(val message: String) : VoiceEvent
}

class VoiceConnectionException(val userMessage: String) : Exception(userMessage)
