package com.minipapa.englishtalk.voice

import org.json.JSONObject
import com.minipapa.englishtalk.settings.*

object RealtimeEvents {
    fun configuration(settings: ConversationSettings = ConversationSettings(), includeVoice: Boolean = true): String {
        val audio = JSONObject().put("input", JSONObject()
            .put("turn_detection", JSONObject()
                .put("type", "server_vad")
                .put("create_response", true)
                .put("interrupt_response", true)))
        if (includeVoice) audio.put("output", JSONObject().put("voice", settings.character.config.voiceId))
        return JSONObject()
        .put("type", "session.update")
        .put("session", JSONObject().put("type", "realtime")
            .put("instructions", RealtimeInstructionsBuilder.build(settings))
            .put("audio", audio))
        .toString()
    }

    fun greeting(settings: ConversationSettings = ConversationSettings()): String = JSONObject()
        .put("type", "response.create")
        .put("response", JSONObject()
            .put("output_modalities", org.json.JSONArray().put("audio"))
            .put("instructions", RealtimeInstructionsBuilder.build(settings) +
                "\nFor this first reply only, introduce yourself briefly as ${settings.character.config.name} and begin a friendly conversation. Respect the selected vocabulary and reply length. Do not greet again in later replies."))
        .toString()

    fun control(type: String, eventId: String): String = JSONObject().put("type", type).put("event_id", eventId).toString()

    fun parse(text: String): VoiceEvent? {
        val event = try { JSONObject(text) } catch (_: Exception) { return null }
        return when (event.optString("type")) {
            "session.updated" -> event.optJSONObject("session")?.let { session ->
                VoiceEvent.SessionConfiguration(session.optString("instructions"),
                    session.optJSONObject("audio")?.optJSONObject("output")?.optString("voice").orEmpty())
            }
            "input_audio_buffer.speech_started" -> VoiceEvent.UserSpeaking
            "input_audio_buffer.speech_stopped", "response.created" -> VoiceEvent.Thinking
            "output_audio_buffer.started" -> VoiceEvent.AiSpeaking
            "output_audio_buffer.stopped", "output_audio_buffer.cleared" -> VoiceEvent.Listening
            "response.done" -> if (event.optJSONObject("response")?.optString("status") == "failed") {
                VoiceEvent.Error("AI 음성 응답 생성에 실패했습니다. 연결을 다시 시작해 주세요.")
            } else {
                val usage = event.optJSONObject("response")?.optJSONObject("usage")
                usage?.let {
                    val input = it.optJSONObject("input_token_details")
                    val output = it.optJSONObject("output_token_details")
                    val cached = input?.optJSONObject("cached_tokens_details")
                    val inputCount = it.optInt("input_tokens").coerceAtLeast(0)
                    val outputCount = it.optInt("output_tokens").coerceAtLeast(0)
                    val details = if (input != null && output != null &&
                        input.has("text_tokens") && input.has("audio_tokens") &&
                        output.has("text_tokens") && output.has("audio_tokens") &&
                        (input.optInt("cached_tokens") == 0 || cached != null) &&
                        (cached?.optInt("text_tokens") ?: 0).toLong() + (cached?.optInt("audio_tokens") ?: 0) == input.optInt("cached_tokens").toLong() &&
                        input.optInt("text_tokens").toLong() + input.optInt("audio_tokens") == inputCount.toLong() &&
                        output.optInt("text_tokens").toLong() + output.optInt("audio_tokens") == outputCount.toLong()) {
                        TokenBreakdown(input.optInt("text_tokens"), input.optInt("audio_tokens"),
                            cached?.optInt("text_tokens") ?: 0, cached?.optInt("audio_tokens") ?: 0,
                            output.optInt("text_tokens"), output.optInt("audio_tokens"))
                    } else null
                    VoiceEvent.Usage(inputCount, outputCount, details,
                        event.optJSONObject("response")?.optString("id").orEmpty())
                }
            } // Generation can finish before buffered audio finishes playing.
            "error" -> if (event.optJSONObject("error")?.optString("event_id")?.startsWith("settings-cancel-") == true) {
                null // Cancelling a response that has already ended is harmless.
            } else VoiceEvent.Error("AI 대화 중 오류가 발생했습니다. 연결을 다시 시작해 주세요.")
            else -> null
        }
    }
}
