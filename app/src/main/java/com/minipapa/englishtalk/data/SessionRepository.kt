package com.minipapa.englishtalk.data

interface SessionRepository {
    suspend fun createSession(): SessionCredentials
}

// Memory only. Never persist or display the bearer token.
class SessionCredentials(
    val clientSecret: String,
    val expiresAt: Long,
    val remainingSecondsAtReceipt: Long,
    val model: String,
    private val receivedAtNanos: Long = System.nanoTime()
) {
    fun remainingValiditySeconds(nowNanos: Long = System.nanoTime()): Long =
        remainingSecondsAtReceipt - ((nowNanos - receivedAtNanos).coerceAtLeast(0) / 1_000_000_000)

    override fun toString(): String = "SessionCredentials([REDACTED])"
}

class SessionCreationException(val userMessage: String, val retryAfterSeconds: Long? = null) : Exception(userMessage)

fun sessionLimitFailure(details: Any?): SessionCreationException {
    val values = details as? Map<*, *>
    if (values?.get("source") != "session-limit") {
        return SessionCreationException("서버 또는 OpenAI의 요청 한도에 도달했습니다. 사용량과 잠시 후 재시도를 확인해 주세요.")
    }
    val wait = (values["retryAfterSeconds"] as? Number)?.toLong()?.coerceIn(1, 86_400) ?: 20
    val message = when (values["limitPeriod"]) {
        "day" -> "오늘의 세션 발급 한도에 도달했습니다. 약 ${(wait + 3599) / 3600}시간 후 다시 시도해 주세요."
        "minute" -> "분당 세션 발급 한도에 도달했습니다. ${wait}초 후 다시 시도해 주세요."
        else -> "세션은 20초 간격으로 시작할 수 있습니다. ${wait}초 후 다시 시도해 주세요."
    }
    return SessionCreationException(message, wait)
}

fun parseSessionResponse(data: Any?, elapsedSeconds: Long): SessionCredentials {
    val result = data as? Map<*, *> ?: throw SessionCreationException("서버 응답 형식이 올바르지 않습니다.")
    val secret = result["clientSecret"] as? String
    val expires = (result["expiresAt"] as? Number)?.toLong()
    val serverTime = (result["serverTime"] as? Number)?.toLong()
    val model = result["model"] as? String
    if (secret.isNullOrBlank() || expires == null || serverTime == null || model.isNullOrBlank()) {
        throw SessionCreationException("서버 인증 응답에 필요한 정보가 없습니다.")
    }
    // Use server time and conservatively subtract the whole round trip, avoiding device clock skew.
    val remaining = expires - serverTime - elapsedSeconds.coerceAtLeast(0)
    if (remaining <= 5) throw SessionCreationException("임시 인증 정보가 만료되었거나 곧 만료됩니다. 다시 테스트해 주세요.")
    return SessionCredentials(secret, expires, remaining, model)
}
