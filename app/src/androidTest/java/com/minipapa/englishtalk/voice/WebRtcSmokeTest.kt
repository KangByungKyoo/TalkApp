package com.minipapa.englishtalk.voice

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class WebRtcSmokeTest {
    @Test fun nativeLibraryCreatesAudioAndEventChannelOfferWithoutOpeningMicrophone() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(context).createInitializationOptions())
        val module = JavaAudioDeviceModule.builder(context).createAudioDeviceModule()
        val factory = PeerConnectionFactory.builder().setAudioDeviceModule(module).createPeerConnectionFactory()
        val observer = object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
            override fun onIceCandidate(candidate: IceCandidate) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(channel: DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
        }
        val config = PeerConnection.RTCConfiguration(emptyList()).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
        val peer = factory.createPeerConnection(config, observer)!!
        val source = factory.createAudioSource(MediaConstraints())
        val track = factory.createAudioTrack("test-microphone-disabled", source).apply { setEnabled(false) }
        peer.addTrack(track, listOf("test"))
        val channel = peer.createDataChannel("oai-events", DataChannel.Init())
        try {
            val done = CountDownLatch(1)
            var sdp: String? = null
            peer.createOffer(object : SdpObserver {
                override fun onCreateSuccess(description: SessionDescription) { sdp = description.description; done.countDown() }
                override fun onCreateFailure(error: String) { done.countDown() }
                override fun onSetSuccess() = Unit
                override fun onSetFailure(error: String) = Unit
            }, MediaConstraints())
            assertTrue(done.await(10, TimeUnit.SECONDS))
            assertTrue(sdp?.contains("m=audio") == true)
            assertTrue(sdp?.contains("m=application") == true)
            // No local/remote description is applied: no recording, network or OpenAI request.
        } finally {
            channel.close(); channel.dispose()
            peer.close(); peer.dispose()
            track.dispose(); source.dispose(); factory.dispose(); module.release()
        }
    }

    @Test fun parsesVoiceEventsAndNeverShowsRawServerErrors() {
        assertEquals(VoiceEvent.UserSpeaking, RealtimeEvents.parse("""{"type":"input_audio_buffer.speech_started"}"""))
        assertEquals(VoiceEvent.AiSpeaking, RealtimeEvents.parse("""{"type":"output_audio_buffer.started"}"""))
        assertEquals(VoiceEvent.Listening, RealtimeEvents.parse("""{"type":"output_audio_buffer.stopped"}"""))
        assertNull(RealtimeEvents.parse("""{"type":"response.done","response":{"status":"completed"}}"""))
        val error = RealtimeEvents.parse("""{"type":"error","error":{"message":"PRIVATE_VALUE"}}""")
        assertTrue(error is VoiceEvent.Error)
        assertFalse(error.toString().contains("PRIVATE_VALUE"))
        assertNull(RealtimeEvents.parse("invalid-json"))
    }

    @Test fun sendsGaAudioConfigurationAndGreeting() {
        val config = org.json.JSONObject(RealtimeEvents.configuration())
        assertEquals("session.update", config.getString("type"))
        val vad = config.getJSONObject("session").getJSONObject("audio").getJSONObject("input").getJSONObject("turn_detection")
        assertEquals("server_vad", vad.getString("type"))
        assertTrue(vad.getBoolean("create_response"))
        assertTrue(vad.getBoolean("interrupt_response"))
        val greeting = org.json.JSONObject(RealtimeEvents.greeting())
        assertEquals("audio", greeting.getJSONObject("response").getJSONArray("output_modalities").getString(0))
    }

    @Test fun distinguishesBillingFromRateLimitsWithoutExposingRawErrors() {
        val quota = OpenAiFailure.parse(429, """{"error":{"code":"insufficient_quota","message":"PRIVATE_VALUE"}}""", null)
        assertEquals("insufficient_quota", quota.code)
        assertTrue(quota.userMessage.contains("잔액"))
        assertFalse(quota.toString().contains("PRIVATE_VALUE"))
        val rate = OpenAiFailure.parse(429, """{"error":{"code":"rate_limit_exceeded"}}""", "23")
        assertTrue(rate.userMessage.contains("23초"))
        val unrecognized = OpenAiFailure.parse(429, """{"error":{"code":"PRIVATE_VALUE"}}""", null)
        assertEquals("unknown", unrecognized.code)
        assertFalse(unrecognized.toString().contains("PRIVATE_VALUE"))
        assertEquals("unknown", OpenAiFailure.parse(429, "not-json", null).code)
        val spend = OpenAiFailure.parse(429, """{"error":{"code":"project_spend_limit_exceeded","type":"insufficient_quota"}}""", null)
        assertEquals("project_spend_limit_exceeded", spend.code)
        assertTrue(spend.userMessage.contains("지출"))
    }
}
