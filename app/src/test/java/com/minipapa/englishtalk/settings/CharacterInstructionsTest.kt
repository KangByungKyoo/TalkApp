package com.minipapa.englishtalk.settings

import com.minipapa.englishtalk.voice.SessionSettingsCoordinator
import org.junit.Assert.*
import org.junit.Test

class CharacterInstructionsTest {
    @Test fun allNineCombinationsHaveExactRecommendedTargetsAndCharacterLevel() {
        val expected = listOf(5..15, 15..25, 25..40, 10..20, 20..35, 35..55, 15..25, 25..45, 45..70)
        var index = 0
        val prompts = mutableSetOf<String>()
        for (character in ConversationCharacter.entries) for (length in ResponseLength.entries) {
            val range = length.targetWords(character)
            assertEquals(expected[index++], range)
            val prompt = RealtimeInstructionsBuilder.build(ConversationSettings(character, length))
            assertTrue(prompt.contains(character.config.name))
            assertTrue(prompt.contains("age of ${character.config.age}"))
            assertTrue(prompt.contains(character.config.vocabulary))
            assertTrue(prompt.contains("${range.first}-${range.last}"))
            assertTrue(prompt.contains("at most one question"))
            assertTrue(prompt.contains("Korean explanation"))
            assertTrue(prompt.contains("not a real person"))
            assertTrue(prompt.contains("not a hard cutoff"))
            assertTrue(prompt.contains("privacy"))
            prompts += prompt
        }
        assertEquals(9, prompts.size)
        assertEquals(3, ConversationCharacter.entries.map { it.config.voiceId }.toSet().size)
        assertTrue(ConversationCharacter.entries.all { it.config.voiceId in setOf("shimmer", "coral", "sage") })
    }

    @Test fun defaultsAndUnknownStoredIdsRecoverSafely() {
        assertEquals(ConversationCharacter.EMMA, ConversationSettings().character)
        assertEquals(ResponseLength.SHORT, ConversationSettings().responseLength)
        assertEquals(ConversationCharacter.EMMA, ConversationCharacter.fromId("removed-character"))
        assertEquals(ResponseLength.SHORT, ResponseLength.fromId("invalid"))
    }

    @Test fun noDuplicateUpdatesAndOnlyMatchingServerEchoIsAccepted() {
        val initial = ConversationSettings()
        val coordinator = SessionSettingsCoordinator(initial)
        assertEquals(initial, coordinator.nextUpdate())
        assertNull(coordinator.nextUpdate())
        assertNull(coordinator.acknowledge("old prompt", "coral"))
        assertNull(coordinator.acknowledge(RealtimeInstructionsBuilder.build(initial), "marin"))
        assertEquals(initial, coordinator.acknowledge(RealtimeInstructionsBuilder.build(initial), "coral"))
        assertNull(coordinator.acknowledge(RealtimeInstructionsBuilder.build(initial), "coral"))
        val normal = initial.copy(responseLength = ResponseLength.NORMAL)
        coordinator.select(normal)
        assertEquals(normal, coordinator.nextUpdate())
        val detailed = initial.copy(responseLength = ResponseLength.DETAILED)
        coordinator.select(detailed)
        assertEquals(detailed, coordinator.nextUpdate())
        assertNull(coordinator.acknowledge(RealtimeInstructionsBuilder.build(normal), "coral"))
        assertEquals(detailed, coordinator.acknowledge(RealtimeInstructionsBuilder.build(detailed), "coral"))
        assertNull(coordinator.nextUpdate())
    }

    @Test(expected = IllegalArgumentException::class)
    fun preventsChangingVoiceWithinAnExistingSession() {
        SessionSettingsCoordinator(ConversationSettings()).select(ConversationSettings(ConversationCharacter.LILY))
    }
}
