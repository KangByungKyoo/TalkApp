package com.minipapa.englishtalk.ui

enum class ConversationStatus(val label: String) {
    DISCONNECTED("연결 전"), CONNECTING("연결 중"), TALKING("대화 중"),
    ENDED("연결 종료"), ERROR("오류")
}

enum class ServerTestStatus { NOT_TESTED, LOADING, SUCCESS, ERROR }

data class ConversationUiState(
    val teacherName: String = "Emma",
    val status: ConversationStatus = ConversationStatus.DISCONNECTED,
    val errorMessage: String? = null,
    val serverTestStatus: ServerTestStatus = ServerTestStatus.NOT_TESTED,
    val serverTestMessage: String = "Firebase 서버 인증을 테스트할 수 있습니다."
) {
    val isActive: Boolean
        get() = status == ConversationStatus.CONNECTING || status == ConversationStatus.TALKING
}
