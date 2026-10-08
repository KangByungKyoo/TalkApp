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

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    @Test fun startThenConnect() = runTest(dispatcher) {
        val model = ConversationViewModel()
        assertEquals(ConversationStatus.DISCONNECTED, model.uiState.value.status)
        model.startConversation()
        assertEquals(ConversationStatus.CONNECTING, model.uiState.value.status)
        advanceUntilIdle()
        assertEquals(ConversationStatus.TALKING, model.uiState.value.status)
    }

    @Test fun endingDuringConnectionCancelsPendingTransition() = runTest(dispatcher) {
        val model = ConversationViewModel()
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
        val model = ConversationViewModel()
        model.onMicrophonePermissionDenied()
        assertEquals(ConversationStatus.ERROR, model.uiState.value.status)
        model.startConversation()
        advanceUntilIdle()
        assertEquals(ConversationStatus.TALKING, model.uiState.value.status)
        assertEquals(null, model.uiState.value.errorMessage)
    }
}
