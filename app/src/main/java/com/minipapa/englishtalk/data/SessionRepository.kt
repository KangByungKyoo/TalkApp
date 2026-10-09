package com.minipapa.englishtalk.data

interface SessionRepository {
    suspend fun createSession(): SessionCredentials
}

// Memory only. Never persist or display the bearer token.
class SessionCredentials(
    val clientSecret: String,
    val expiresAt: Long,
    val remainingSecondsAtReceipt: Long,
    val model: String
) {
    override fun toString(): String = "SessionCredentials([REDACTED])"
}

class SessionCreationException(val userMessage: String) : Exception(userMessage)

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
