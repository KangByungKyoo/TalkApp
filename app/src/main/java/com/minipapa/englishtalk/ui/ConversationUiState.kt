package com.minipapa.englishtalk.ui

enum class ConversationStatus(val label: String) {
    DISCONNECTED("연결 전"), CONNECTING("연결 중"), TALKING("대화 중"),
    ENDED("연결 종료"), ERROR("오류")
}

enum class ServerTestStatus { NOT_TESTED, LOADING, SUCCESS, ERROR }
enum class VoiceActivity(val label: String) {
    LISTENING("Emma가 듣고 있어요. 영어로 말해 보세요."),
    USER_SPEAKING("말씀을 듣고 있어요."),
    THINKING("Emma가 답변을 준비하고 있어요."),
    AI_SPEAKING("Emma가 말하고 있어요.")
}

data class ConversationUiState(
    val teacherName: String = "Emma",
    val status: ConversationStatus = ConversationStatus.DISCONNECTED,
    val errorMessage: String? = null,
    val voiceActivity: VoiceActivity = VoiceActivity.LISTENING,
    val serverTestStatus: ServerTestStatus = ServerTestStatus.NOT_TESTED,
    val serverTestMessage: String = "Firebase 서버 인증을 테스트할 수 있습니다."
) {
    val isActive: Boolean
        get() = status == ConversationStatus.CONNECTING || status == ConversationStatus.TALKING
}
