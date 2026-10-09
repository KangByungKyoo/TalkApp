package com.minipapa.englishtalk.voice

import org.json.JSONObject

/** Only recognized public error codes leave the parser; never expose the error message/body. */
data class OpenAiFailure(val code: String, val userMessage: String) {
    companion object {
        fun parse(status: Int, body: String?, retryAfter: String?): OpenAiFailure {
            val error = try { body?.let { JSONObject(it).optJSONObject("error") } } catch (_: Exception) { null }
            val knownCodes = setOf("insufficient_quota", "credit_balance_exhausted",
                "organization_spend_limit_exceeded", "project_spend_limit_exceeded",
                "organization_usage_limit_exceeded", "rate_limit_exceeded", "slow_down")
            val rawCode = error?.optString("code").orEmpty()
            val code = when {
                rawCode in knownCodes -> rawCode
                error?.optString("type") == "insufficient_quota" -> "insufficient_quota"
                error?.optString("type") == "rate_limit_error" -> "rate_limit_exceeded"
                else -> "unknown"
            }
            val wait = retryAfter?.toLongOrNull()?.coerceIn(1, 86_400) ?: 60
            val message = when (status) {
                401, 403 -> "음성 연결 인증이 거부되었습니다. 다시 시작해 주세요."
                429 -> when (code) {
                    "credit_balance_exhausted" -> "OpenAI API 크레딧 잔액이 부족합니다. OpenAI 결제 설정에서 잔액을 확인해 주세요."
                    "insufficient_quota" -> "OpenAI API 잔액 또는 사용 가능 한도가 부족합니다. OpenAI 결제 설정과 사용 한도를 확인해 주세요."
                    "organization_spend_limit_exceeded", "project_spend_limit_exceeded" -> "OpenAI API 지출 한도에 도달했습니다. OpenAI 결제·프로젝트 한도를 확인해 주세요."
                    "organization_usage_limit_exceeded" -> "OpenAI 계정의 API 사용 한도에 도달했습니다. OpenAI 사용 한도를 확인해 주세요."
                    "rate_limit_exceeded", "slow_down" -> "OpenAI의 일시적인 요청 제한입니다. ${wait}초 이상 기다린 뒤 다시 시작해 주세요."
                    else -> "OpenAI가 음성 연결을 제한했습니다(429). API 잔액과 사용 한도를 확인해 주세요."
                }
                else -> "AI 음성 연결에 실패했습니다. 네트워크와 모델 설정을 확인해 주세요."
            }
            return OpenAiFailure(code, message)
        }
    }
}
