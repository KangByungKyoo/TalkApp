package com.minipapa.englishtalk.ui

import com.minipapa.englishtalk.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class SessionCooldownTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun serverWaitBlocksRepeatedRequestsAndExpires() = runTest(dispatcher) {
        var requests = 0
        val model = ConversationViewModel(object : SessionRepository {
            override suspend fun createSession(): SessionCredentials {
                requests++
                throw SessionCreationException("3초 후 재시도", 3)
            }
        })
        model.testServerConnection()
        runCurrent()
        assertEquals(3L, model.uiState.value.sessionCooldownSeconds)
        model.testServerConnection()
        model.startConversation()
        runCurrent()
        assertEquals(1, requests)
        advanceTimeBy(3000)
        runCurrent()
        assertEquals(0L, model.uiState.value.sessionCooldownSeconds)
        model.testServerConnection()
        runCurrent()
        assertEquals(2, requests)
        advanceUntilIdle()
    }
}
