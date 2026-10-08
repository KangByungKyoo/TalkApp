package com.minipapa.englishtalk.ui

enum class ConversationStatus(val label: String) {
    DISCONNECTED("연결 전"), CONNECTING("연결 중"), TALKING("대화 중"),
    ENDED("연결 종료"), ERROR("오류")
}

data class ConversationUiState(
    val teacherName: String = "Emma",
    val status: ConversationStatus = ConversationStatus.DISCONNECTED,
    val errorMessage: String? = null
) {
    val isActive: Boolean
        get() = status == ConversationStatus.CONNECTING || status == ConversationStatus.TALKING
}
