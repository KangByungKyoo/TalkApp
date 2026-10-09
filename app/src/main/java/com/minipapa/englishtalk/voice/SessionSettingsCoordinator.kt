package com.minipapa.englishtalk.voice

import com.minipapa.englishtalk.settings.*

/** Pure state machine: suppress duplicates and validate effective server acknowledgements. */
class SessionSettingsCoordinator(initial: ConversationSettings) {
    var desired: ConversationSettings = initial
        private set
    var sent: ConversationSettings? = null
        private set
    var applied: ConversationSettings? = null
        private set

    fun select(settings: ConversationSettings) {
        require(settings.character == desired.character) { "Voice changes require a new session" }
        desired = settings
    }

    fun nextUpdate(): ConversationSettings? {
        if (sent == desired) return null
        sent = desired
        return desired
    }

    fun acknowledge(instructions: String, voiceId: String): ConversationSettings? {
        val pending = sent ?: return null
        if (pending != desired || instructions != RealtimeInstructionsBuilder.build(pending) || voiceId != pending.character.config.voiceId) return null
        if (applied == pending) return null
        applied = pending
        return pending
    }
}
