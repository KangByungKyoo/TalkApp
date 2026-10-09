package com.minipapa.englishtalk.ui

import com.minipapa.englishtalk.data.SessionCreationException
import com.minipapa.englishtalk.data.SessionCredentials
import com.minipapa.englishtalk.data.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ServerConnectionTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun showsSuccessWithoutExposingTokenAndBlocksDoubleTap() = runTest(dispatcher) {
        var calls = 0
        val model = ConversationViewModel(object : SessionRepository {
            override suspend fun createSession(): SessionCredentials {
                calls++
                delay(10)
                return SessionCredentials("test-secret-never-display", 1060, 58, "gpt-realtime")
            }
        })
        model.testServerConnection()
        model.testServerConnection()
        assertEquals(ServerTestStatus.LOADING, model.uiState.value.serverTestStatus)
        advanceUntilIdle()
        assertEquals(1, calls)
        assertEquals(ServerTestStatus.SUCCESS, model.uiState.value.serverTestStatus)
        assertFalse(model.uiState.value.toString().contains("test-secret-never-display"))
        assertEquals(ConversationStatus.DISCONNECTED, model.uiState.value.status)
    }

    @Test fun showsFailureThenRecovers() = runTest(dispatcher) {
        var fail = true
        val model = ConversationViewModel(object : SessionRepository {
            override suspend fun createSession(): SessionCredentials {
                if (fail) throw SessionCreationException("요청 한도 초과")
                return SessionCredentials("test-only", 1060, 58, "gpt-realtime")
            }
        })
        model.testServerConnection()
        advanceUntilIdle()
        assertEquals(ServerTestStatus.ERROR, model.uiState.value.serverTestStatus)
        fail = false
        model.testServerConnection()
        advanceUntilIdle()
        assertEquals(ServerTestStatus.SUCCESS, model.uiState.value.serverTestStatus)
    }
}
