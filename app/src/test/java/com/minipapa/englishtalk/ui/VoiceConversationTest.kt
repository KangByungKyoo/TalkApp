package com.minipapa.englishtalk.ui

import com.minipapa.englishtalk.data.*
import com.minipapa.englishtalk.voice.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceConversationTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class FakeVoice : VoiceClient {
        var callback: ((VoiceEvent) -> Unit)? = null
        var closes = 0
        var waitForever = false
        override suspend fun connect(credentials: SessionCredentials, settings: com.minipapa.englishtalk.settings.ConversationSettings, onEvent: (VoiceEvent) -> Unit) {
            callback = onEvent
            if (waitForever) awaitCancellation()
        }
        override fun close() { closes++ }
        override fun updateSettings(settings: com.minipapa.englishtalk.settings.ConversationSettings) = true
    }
    private fun repository() = object : SessionRepository {
        override suspend fun createSession() = SessionCredentials("test-only", 1060, 58, "gpt-realtime")
    }

    @Test fun accumulatesCostOncePerResponseAndResetsForNewConversation() = runTest(dispatcher) {
        val voice = FakeVoice()
        val repo = object : SessionRepository {
            override suspend fun createSession() = SessionCredentials("test-only", 1060, 58, "gpt-realtime-2.1-mini")
        }
        val model = ConversationViewModel(repo, voice)
        model.startConversation()
        advanceUntilIdle()
        val usage = VoiceEvent.Usage(3000, 400, TokenBreakdown(1000, 2000, 500, 1000, 100, 300), "response-1")
        voice.callback!!(usage)
        voice.callback!!(usage)
        voice.callback!!(usage.copy(responseId = "response-2"))
        advanceUntilIdle()
        assertEquals(6000L, model.uiState.value.inputTokens)
        assertEquals(800L, model.uiState.value.outputTokens)
        assertEquals(0.03374, model.uiState.value.estimatedCostUsd, 1e-10)
        model.endConversation()
        assertEquals(0.03374, model.uiState.value.estimatedCostUsd, 1e-10)
        model.startConversation()
        advanceUntilIdle()
        assertEquals(0L, model.uiState.value.inputTokens)
        assertEquals(0.0, model.uiState.value.estimatedCostUsd, 0.0)
        voice.callback!!(VoiceEvent.Usage(10, 5))
        advanceUntilIdle()
        assertFalse(model.uiState.value.costDetailsComplete)
        model.endConversation()
    }

    @Test fun staysConnectingUntilNativeConnectionAndDataChannelReady() = runTest(dispatcher) {
        val voice = FakeVoice()
        val model = ConversationViewModel(repository(), voice)
        model.startConversation()
        advanceUntilIdle()
        assertEquals(ConversationStatus.CONNECTING, model.uiState.value.status)
        voice.callback!!(VoiceEvent.Connected)
        advanceUntilIdle()
        assertEquals(ConversationStatus.TALKING, model.uiState.value.status)
        voice.callback!!(VoiceEvent.UserSpeaking)
        advanceUntilIdle()
        assertEquals(VoiceActivity.USER_SPEAKING, model.uiState.value.voiceActivity)
        voice.callback!!(VoiceEvent.AiSpeaking)
        advanceUntilIdle()
        assertEquals(VoiceActivity.AI_SPEAKING, model.uiState.value.voiceActivity)
        model.endConversation()
        assertEquals(1, voice.closes)
    }

    @Test fun ignoresCallbacksFromEndedConversationAfterRestart() = runTest(dispatcher) {
        val voice = FakeVoice()
        val model = ConversationViewModel(repository(), voice)
        model.startConversation()
        advanceUntilIdle()
        val old = voice.callback!!
        model.endConversation()
        model.startConversation()
        advanceUntilIdle()
        old(VoiceEvent.Connected)
        old(VoiceEvent.Error("late failure"))
        advanceUntilIdle()
        assertEquals(ConversationStatus.CONNECTING, model.uiState.value.status)
        voice.callback!!(VoiceEvent.Connected)
        advanceUntilIdle()
        assertEquals(ConversationStatus.TALKING, model.uiState.value.status)
        model.endConversation()
    }

    @Test fun disconnectReleasesResourcesAndDisplaysSafeError() = runTest(dispatcher) {
        val voice = FakeVoice()
        val model = ConversationViewModel(repository(), voice)
        model.startConversation()
        advanceUntilIdle()
        voice.callback!!(VoiceEvent.Error("연결이 끊어졌습니다."))
        advanceUntilIdle()
        assertEquals(ConversationStatus.ERROR, model.uiState.value.status)
        assertEquals(1, voice.closes)
    }

    @Test fun cancelsHangingConnectionOnTimeout() = runTest(dispatcher) {
        val voice = FakeVoice().apply { waitForever = true }
        val model = ConversationViewModel(repository(), voice)
        model.startConversation()
        advanceUntilIdle()
        assertEquals(ConversationStatus.ERROR, model.uiState.value.status)
        assertEquals(1, voice.closes)
    }

    @Test fun failedAuthenticationDoesNotStartNativeAudio() = runTest(dispatcher) {
        val voice = FakeVoice()
        val repo = object : SessionRepository {
            override suspend fun createSession(): SessionCredentials = throw SessionCreationException("요청 한도 초과")
        }
        val model = ConversationViewModel(repo, voice)
        model.startConversation()
        advanceUntilIdle()
        assertNull(voice.callback)
        assertEquals(ConversationStatus.ERROR, model.uiState.value.status)
    }
}
