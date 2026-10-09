package com.minipapa.englishtalk.voice

data class TokenBreakdown(
    val inputText: Int, val inputAudio: Int,
    val cachedText: Int, val cachedAudio: Int,
    val outputText: Int, val outputAudio: Int
)

object RealtimeCost {
    // USD per million tokens, verified 2026-10-09:
    // https://developers.openai.com/api/docs/models/gpt-realtime-2.1-mini
    fun estimateUsd(model: String?, usage: TokenBreakdown?): Double? {
        if (model != "gpt-realtime-2.1-mini" || usage == null) return null
        val u = usage
        if (listOf(u.inputText, u.inputAudio, u.cachedText, u.cachedAudio,
                u.outputText, u.outputAudio).any { it < 0 } ||
            u.cachedText > u.inputText || u.cachedAudio > u.inputAudio) return null
        return ((u.inputText - u.cachedText) * 0.60 + u.cachedText * 0.06 +
            (u.inputAudio - u.cachedAudio) * 10.0 + u.cachedAudio * 0.30 +
            u.outputText * 2.40 + u.outputAudio * 20.0) / 1_000_000
    }
}
