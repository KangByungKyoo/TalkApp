package com.minipapa.englishtalk.ui

import com.minipapa.englishtalk.data.*
import com.minipapa.englishtalk.settings.*
import com.minipapa.englishtalk.voice.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*

@OptIn(ExperimentalCoroutinesApi::class)
class CharacterSettingsViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @Before fun setUp() { Dispatchers.setMain(dispatcher) }
    @After fun tearDown() { Dispatchers.resetMain() }

    private class SettingsRepo : ConversationSettingsRepository {
        override val settings = MutableStateFlow(ConversationSettings())
        var writes = 0
        var fail = false
        override suspend fun selectCharacter(character: ConversationCharacter) {
            if (fail) throw java.io.IOException()
            writes++
            settings.value = settings.value.copy(character = character)
        }
        override suspend fun selectResponseLength(length: ResponseLength) {
            if (fail) throw java.io.IOException()
            writes++
            settings.value = settings.value.copy(responseLength = length)
        }
    }
    private class Voice : VoiceClient {
        var connections = 0
        var closes = 0
        var callback: ((VoiceEvent) -> Unit)? = null
        val updates = mutableListOf<ConversationSettings>()
        var startedWith: ConversationSettings? = null
        override suspend fun connect(credentials: SessionCredentials, settings: ConversationSettings, onEvent: (VoiceEvent) -> Unit) {
            connections++
            startedWith = settings
            callback = onEvent
            onEvent(VoiceEvent.SettingsApplied(settings))
            onEvent(VoiceEvent.Connected)
        }
        override fun updateSettings(settings: ConversationSettings): Boolean { updates += settings; return true }
        override fun close() { closes++ }
    }
    private fun model(settings: SettingsRepo, voice: Voice) = ConversationViewModel(object : SessionRepository {
        override suspend fun createSession() = SessionCredentials("test-only", 1060, 58, "gpt-realtime")
    }, voice, settings)

    @Test fun preservesIndependentSettingsAndRestoresSelectionInNewViewModel() = runTest(dispatcher) {
        val repo = SettingsRepo()
        val voice = Voice()
        val model = model(repo, voice)
        advanceUntilIdle()
        model.selectResponseLength(ResponseLength.DETAILED)
        advanceUntilIdle()
        model.selectCharacter(ConversationCharacter.LILY)
        advanceUntilIdle()
        assertEquals(ConversationSettings(ConversationCharacter.LILY, ResponseLength.DETAILED), model.uiState.value.settings)
        model.selectResponseLength(ResponseLength.NORMAL)
        advanceUntilIdle()
        assertEquals(ConversationCharacter.LILY, model.uiState.value.settings.character)
        model.selectResponseLength(ResponseLength.NORMAL)
        advanceUntilIdle()
        assertEquals(3, repo.writes)
        val reloaded = model(repo, Voice())
        advanceUntilIdle()
        assertEquals(model.uiState.value.settings, reloaded.uiState.value.settings)
    }

    @Test fun lengthUpdateUsesSameSessionAndWaitsForMatchingAcknowledgement() = runTest(dispatcher) {
        val repo = SettingsRepo()
        val voice = Voice()
        val model = model(repo, voice)
        advanceUntilIdle()
        model.startConversation()
        advanceUntilIdle()
        model.selectResponseLength(ResponseLength.NORMAL)
        advanceUntilIdle()
        assertEquals(1, voice.connections)
        assertEquals(0, voice.closes)
        assertEquals(1, voice.updates.size)
        assertTrue(model.uiState.value.settingsApplying)
        voice.callback!!(VoiceEvent.SettingsApplied(ConversationSettings()))
        advanceUntilIdle()
        assertTrue(model.uiState.value.settingsApplying)
        voice.callback!!(VoiceEvent.SettingsApplied(repo.settings.value))
        advanceUntilIdle()
        assertFalse(model.uiState.value.settingsApplying)
        model.selectResponseLength(ResponseLength.NORMAL)
        advanceUntilIdle()
        assertEquals(1, voice.updates.size)
        model.endConversation()
    }

    @Test fun characterChangeEndsSessionAndRetainsReplyLengthWithoutAutomaticReconnect() = runTest(dispatcher) {
        val repo = SettingsRepo()
        val voice = Voice()
        val model = model(repo, voice)
        advanceUntilIdle()
        model.selectResponseLength(ResponseLength.DETAILED)
        advanceUntilIdle()
        model.startConversation()
        advanceUntilIdle()
        val old = voice.callback!!
        model.selectCharacter(ConversationCharacter.SOPHIA)
        advanceUntilIdle()
        assertEquals(ConversationStatus.ENDED, model.uiState.value.status)
        assertEquals(1, voice.closes)
        assertEquals(1, voice.connections)
        assertEquals(ResponseLength.DETAILED, model.uiState.value.settings.responseLength)
        old(VoiceEvent.Connected)
        advanceUntilIdle()
        assertEquals(ConversationStatus.ENDED, model.uiState.value.status)
        model.startConversation()
        advanceUntilIdle()
        assertEquals(ConversationCharacter.SOPHIA, voice.startedWith?.character)
        model.endConversation()
    }

    @Test fun writeFailureRetainsPreviousSettingsAndShowsMessage() = runTest(dispatcher) {
        val repo = SettingsRepo().apply { fail = true }
        val model = model(repo, Voice())
        advanceUntilIdle()
        model.selectCharacter(ConversationCharacter.LILY)
        advanceUntilIdle()
        assertEquals(ConversationSettings(), model.uiState.value.settings)
        assertNotNull(model.uiState.value.settingsNotice)
        assertFalse(model.uiState.value.settingsSaving)
    }
}
