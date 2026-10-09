package com.minipapa.englishtalk.data

import org.junit.Assert.*
import org.junit.Test

class SessionLimitTest {
    @Test fun intervalAndDailyLimitsProvideSafeWaitInformation() {
        val interval = sessionLimitFailure(mapOf("source" to "session-limit", "limitPeriod" to "interval", "retryAfterSeconds" to 7))
        assertEquals(7L, interval.retryAfterSeconds)
        assertTrue(interval.userMessage.contains("7초"))
        val daily = sessionLimitFailure(mapOf("source" to "session-limit", "limitPeriod" to "day", "retryAfterSeconds" to 7200))
        assertTrue(daily.userMessage.contains("2시간"))
        val unknown = sessionLimitFailure(mapOf("message" to "PRIVATE_VALUE"))
        assertNull(unknown.retryAfterSeconds)
        assertFalse(unknown.userMessage.contains("PRIVATE_VALUE"))
    }
}
