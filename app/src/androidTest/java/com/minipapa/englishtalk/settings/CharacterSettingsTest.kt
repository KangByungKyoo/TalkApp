package com.minipapa.englishtalk.settings

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.minipapa.englishtalk.voice.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import java.io.File

@RunWith(AndroidJUnit4::class)
class CharacterSettingsTest {
    @Test fun allNineSettingsProduceMatchingVoicePromptAndInitialGreeting() {
        for (character in ConversationCharacter.entries) for (length in ResponseLength.entries) {
            val settings = ConversationSettings(character, length)
            val payload = JSONObject(RealtimeEvents.configuration(settings)).getJSONObject("session")
            assertEquals(RealtimeInstructionsBuilder.build(settings), payload.getString("instructions"))
            assertEquals(character.config.voiceId, payload.getJSONObject("audio").getJSONObject("output").getString("voice"))
            val greeting = JSONObject(RealtimeEvents.greeting(settings)).getJSONObject("response").getString("instructions")
            assertTrue(greeting.startsWith(RealtimeInstructionsBuilder.build(settings)))
            assertTrue(greeting.contains("as ${character.config.name}"))
            val live = JSONObject(RealtimeEvents.configuration(settings, includeVoice = false)).getJSONObject("session")
            assertFalse(live.getJSONObject("audio").has("output"))
            val echo = JSONObject().put("type", "session.updated").put("session", payload).toString()
            val event = RealtimeEvents.parse(echo) as VoiceEvent.SessionConfiguration
            val coordinator = SessionSettingsCoordinator(settings)
            coordinator.nextUpdate()
            assertEquals(settings, coordinator.acknowledge(event.instructions, event.voiceId))
        }
    }

    @Test fun dataStorePersistsIndependentSelectionsAcrossStoreRecreation() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "settings-test-${System.nanoTime()}.preferences_pb")
        var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            var store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            var repository = DataStoreConversationSettingsRepository(store)
            assertEquals(ConversationSettings(), withTimeout(5000) { repository.settings.first() })
            repository.selectResponseLength(ResponseLength.DETAILED)
            repository.selectCharacter(ConversationCharacter.LILY)
            assertEquals(ConversationSettings(ConversationCharacter.LILY, ResponseLength.DETAILED), repository.settings.first())
            repository.selectResponseLength(ResponseLength.NORMAL)
            scope.coroutineContext[Job]!!.cancelAndJoin()
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
            repository = DataStoreConversationSettingsRepository(store)
            assertEquals(ConversationSettings(ConversationCharacter.LILY, ResponseLength.NORMAL), repository.settings.first())
            store.edit { it[stringPreferencesKey("character_id")] = "unknown"; it[stringPreferencesKey("response_length")] = "unknown" }
            assertEquals(ConversationSettings(), repository.settings.first())
        } finally {
            scope.coroutineContext[Job]!!.cancelAndJoin()
            file.delete()
        }
    }

    @Test fun responseUsageAndHarmlessCancellationAreParsedSafely() {
        assertEquals(VoiceEvent.Usage(12, 7), RealtimeEvents.parse("""{"type":"response.done","response":{"status":"completed","usage":{"input_tokens":12,"output_tokens":7}}}"""))
        assertNull(RealtimeEvents.parse("""{"type":"error","error":{"event_id":"settings-cancel-2","message":"PRIVATE_VALUE"}}"""))
    }
}
