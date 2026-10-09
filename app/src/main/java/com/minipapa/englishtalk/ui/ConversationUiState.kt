package com.minipapa.englishtalk.ui

import com.minipapa.englishtalk.settings.ConversationSettings

enum class ConversationStatus(val label: String) {
    DISCONNECTED("연결 전"), CONNECTING("연결 중"), TALKING("대화 중"),
    ENDED("연결 종료"), ERROR("오류")
}

enum class ServerTestStatus { NOT_TESTED, LOADING, SUCCESS, ERROR }
enum class VoiceActivity(val label: String) {
    LISTENING("듣고 있어요. 영어로 말해 보세요."),
    USER_SPEAKING("말씀을 듣고 있어요."),
    THINKING("답변을 준비하고 있어요."),
    AI_SPEAKING("말하고 있어요.")
}

data class ConversationUiState(
    val settings: ConversationSettings = ConversationSettings(),
    val settingsLoaded: Boolean = true,
    val settingsSaving: Boolean = false,
    val settingsApplying: Boolean = false,
    val settingsNotice: String? = null,
    val appliedSettings: ConversationSettings? = null,
    val inputTokens: Long = 0,
    val outputTokens: Long = 0,
    val sessionCooldownSeconds: Long = 0,
    val status: ConversationStatus = ConversationStatus.DISCONNECTED,
    val errorMessage: String? = null,
    val voiceActivity: VoiceActivity = VoiceActivity.LISTENING,
    val serverTestStatus: ServerTestStatus = ServerTestStatus.NOT_TESTED,
    val serverTestMessage: String = "Firebase 서버 인증을 테스트할 수 있습니다."
) {
    val teacherName: String get() = settings.character.config.name
    val isActive: Boolean
        get() = status == ConversationStatus.CONNECTING || status == ConversationStatus.TALKING
}
