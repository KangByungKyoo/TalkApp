package com.minipapa.englishtalk.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SessionResponseTest {
    private fun response(expires: Long = 1060) = mapOf(
        "clientSecret" to "test-ephemeral", "expiresAt" to expires,
        "serverTime" to 1000L, "model" to "gpt-realtime"
    )

    @Test fun parsesExpiryWithoutUsingDeviceWallClock() {
        val result = parseSessionResponse(response(), 3)
        assertEquals(57, result.remainingSecondsAtReceipt)
        assertFalse(result.toString().contains("test-ephemeral"))
    }

    @Test(expected = SessionCreationException::class)
    fun rejectsAlmostExpiredResponse() { parseSessionResponse(response(1006), 2) }

    @Test(expected = SessionCreationException::class)
    fun rejectsMissingCredential() { parseSessionResponse(mapOf("expiresAt" to 1060), 0) }
}
