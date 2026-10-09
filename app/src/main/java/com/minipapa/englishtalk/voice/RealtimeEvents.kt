package com.minipapa.englishtalk.voice

import org.json.JSONObject

object RealtimeEvents {
    fun configuration(): String = JSONObject()
        .put("type", "session.update")
        .put("session", JSONObject().put("type", "realtime")
            .put("audio", JSONObject().put("input", JSONObject()
                .put("turn_detection", JSONObject()
                    .put("type", "server_vad")
                    .put("create_response", true)
                    .put("interrupt_response", true)))))
        .toString()

    fun greeting(): String = JSONObject()
        .put("type", "response.create")
        .put("response", JSONObject()
            .put("output_modalities", org.json.JSONArray().put("audio"))
            .put("instructions", "Greet the learner briefly in English as Emma and ask one simple question to begin the conversation."))
        .toString()

    fun parse(text: String): VoiceEvent? {
        val event = try { JSONObject(text) } catch (_: Exception) { return null }
        return when (event.optString("type")) {
            "input_audio_buffer.speech_started" -> VoiceEvent.UserSpeaking
            "input_audio_buffer.speech_stopped", "response.created" -> VoiceEvent.Thinking
            "output_audio_buffer.started" -> VoiceEvent.AiSpeaking
            "output_audio_buffer.stopped", "output_audio_buffer.cleared" -> VoiceEvent.Listening
            "response.done" -> if (event.optJSONObject("response")?.optString("status") == "failed") {
                VoiceEvent.Error("AI 음성 응답 생성에 실패했습니다. 연결을 다시 시작해 주세요.")
            } else null // Generation can finish before buffered audio finishes playing.
            "error" -> VoiceEvent.Error("AI 대화 중 오류가 발생했습니다. 연결을 다시 시작해 주세요.")
            else -> null
        }
    }
}
