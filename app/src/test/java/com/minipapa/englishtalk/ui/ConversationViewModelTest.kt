package com.minipapa.englishtalk.ui

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import com.minipapa.englishtalk.data.SessionCredentials
import com.minipapa.englishtalk.data.SessionRepository
import com.minipapa.englishtalk.voice.VoiceClient
import com.minipapa.englishtalk.voice.VoiceEvent
import kotlinx.coroutines.delay

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private fun model(): ConversationViewModel = ConversationViewModel(
        object : SessionRepository {
            override suspend fun createSession() = SessionCredentials("test-only", 1060, 58, "gpt-realtime")
        },
        object : VoiceClient {
            override suspend fun connect(credentials: SessionCredentials, onEvent: (VoiceEvent) -> Unit) {
                delay(1000)
                onEvent(VoiceEvent.Connected)
            }
            override fun close() = Unit
        }
    )

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun startThenConnect() = runTest(dispatcher) {
        val model = model()
        assertEquals(ConversationStatus.DISCONNECTED, model.uiState.value.status)
        model.startConversation()
        assertEquals(ConversationStatus.CONNECTING, model.uiState.value.status)
        advanceUntilIdle()
        assertEquals(ConversationStatus.TALKING, model.uiState.value.status)
    }

    @Test fun endingDuringConnectionCancelsPendingTransition() = runTest(dispatcher) {
        val model = model()
        model.startConversation()
        model.endConversation()
        advanceUntilIdle()
        assertEquals(ConversationStatus.ENDED, model.uiState.value.status)
        model.startConversation()
        advanceUntilIdle()
        assertEquals(ConversationStatus.TALKING, model.uiState.value.status)
        model.endConversation()
        assertEquals(ConversationStatus.ENDED, model.uiState.value.status)
    }

    @Test fun permissionDenialCanRecover() = runTest(dispatcher) {
        val model = model()
        model.onMicrophonePermissionDenied()
        assertEquals(ConversationStatus.ERROR, model.uiState.value.status)
        model.startConversation()
        advanceUntilIdle()
        assertEquals(ConversationStatus.TALKING, model.uiState.value.status)
        assertEquals(null, model.uiState.value.errorMessage)
    }
}
