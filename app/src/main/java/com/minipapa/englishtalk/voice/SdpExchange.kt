package com.minipapa.englishtalk.voice

import com.minipapa.englishtalk.data.SessionCredentials
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import android.util.Log

class SdpExchange {
    @Volatile private var connection: HttpURLConnection? = null
    private val cancelled = AtomicBoolean(false)

    suspend fun exchange(offer: String, credentials: SessionCredentials): String = withContext(Dispatchers.IO) {
        if (cancelled.get()) throw kotlinx.coroutines.CancellationException()
        if (credentials.remainingValiditySeconds() <= 5) {
            throw VoiceConnectionException("임시 인증 정보가 만료되었습니다. 다시 시작해 주세요.")
        }
        val request = URL("https://api.openai.com/v1/realtime/calls").openConnection() as HttpURLConnection
        connection = request
        try {
            if (cancelled.get()) throw kotlinx.coroutines.CancellationException()
            request.requestMethod = "POST"
            request.instanceFollowRedirects = false
            request.connectTimeout = 15_000
            request.readTimeout = 15_000
            request.doOutput = true
            request.setRequestProperty("Authorization", "Bearer ${credentials.clientSecret}")
            request.setRequestProperty("Content-Type", "application/sdp")
            val bytes = offer.toByteArray(Charsets.UTF_8)
            request.setFixedLengthStreamingMode(bytes.size)
            request.outputStream.use { it.write(bytes) }
            val status = request.responseCode
            if (status !in 200..299) {
                val body = try {
                    request.errorStream?.use { input ->
                        val output = ByteArrayOutputStream()
                        val chunk = ByteArray(2048)
                        while (output.size() < 16384) {
                            val count = input.read(chunk, 0, minOf(chunk.size, 16384 - output.size()))
                            if (count < 0) break
                            output.write(chunk, 0, count)
                        }
                        output.toString("UTF-8")
                    }
                } catch (_: Exception) { null }
                val failure = OpenAiFailure.parse(status, body, request.getHeaderField("Retry-After"))
                Log.w("RealtimeSignaling", "OpenAI HTTP $status, code=${failure.code}")
                throw VoiceConnectionException(failure.userMessage)
            }
            // Never log raw error bodies, tokens, SDP or audio data.
            val answer = request.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    if (output.size() + count > 262_144) throw VoiceConnectionException("음성 연결 응답이 너무 큽니다.")
                    output.write(chunk, 0, count)
                }
                output.toString("UTF-8")
            }
            if (!answer.startsWith("v=0") || !answer.contains("m=audio")) {
                throw VoiceConnectionException("음성 연결 응답 형식이 올바르지 않습니다.")
            }
            answer
        } finally {
            request.disconnect()
            if (connection === request) connection = null
        }
    }

    fun cancel() { cancelled.set(true); connection?.disconnect() }
}
