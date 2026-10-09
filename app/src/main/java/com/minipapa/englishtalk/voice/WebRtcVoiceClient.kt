package com.minipapa.englishtalk.voice

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.minipapa.englishtalk.data.SessionCredentials
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.webrtc.*
import org.webrtc.audio.JavaAudioDeviceModule
import java.nio.ByteBuffer
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** All native resource ownership and callbacks are serialized on the main thread. */
class WebRtcVoiceClient(context: Context) : VoiceClient {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private var generation = 0
    private var factory: PeerConnectionFactory? = null
    private var audioModule: JavaAudioDeviceModule? = null
    private var source: AudioSource? = null
    private var microphone: AudioTrack? = null
    private var peer: PeerConnection? = null
    private var channel: DataChannel? = null
    private var route: VoiceAudioRoute? = null
    private var exchange: SdpExchange? = null
    private var ready: CompletableDeferred<Unit>? = null
    private var gathered: CompletableDeferred<Unit>? = null
    private var connected = false
    private var started = false
    private var listener: ((VoiceEvent) -> Unit)? = null

    override suspend fun connect(credentials: SessionCredentials, onEvent: (VoiceEvent) -> Unit) {
        close()
        val id = generation
        listener = onEvent
        ready = CompletableDeferred()
        gathered = CompletableDeferred()
        try {
            withTimeout(35_000) {
                initialize(id)
                val connection = peer ?: throw VoiceConnectionException("WebRTC 연결을 만들 수 없습니다.")
                val offer = createOffer(connection)
                ensureCurrent(id)
                setDescription(connection, offer, local = true)
                withTimeoutOrNull(3_000) { gathered?.await() }
                ensureCurrent(id)
                val signaling = SdpExchange().also { exchange = it }
                val answer = signaling.exchange(connection.localDescription?.description ?: offer.description, credentials)
                ensureCurrent(id)
                setDescription(connection, SessionDescription(SessionDescription.Type.ANSWER, answer), local = false)
                ready?.await()
            }
        } catch (error: Throwable) {
            if (id == generation) close()
            throw error
        }
    }

    private fun initialize(id: Int) {
        check(Looper.myLooper() == Looper.getMainLooper())
        PeerConnectionFactory.initialize(PeerConnectionFactory.InitializationOptions.builder(appContext).createInitializationOptions())
        route = VoiceAudioRoute(appContext) {
            post(id) { fail("다른 앱이 오디오를 사용하여 대화를 종료했습니다.") }
        }
        route?.acquire()
        audioModule = JavaAudioDeviceModule.builder(appContext)
            .setAudioRecordErrorCallback(object : JavaAudioDeviceModule.AudioRecordErrorCallback {
                override fun onWebRtcAudioRecordInitError(errorMessage: String) { audioError(id) }
                override fun onWebRtcAudioRecordStartError(errorCode: JavaAudioDeviceModule.AudioRecordStartErrorCode, errorMessage: String) { audioError(id) }
                override fun onWebRtcAudioRecordError(errorMessage: String) { audioError(id) }
            })
            .setAudioTrackErrorCallback(object : JavaAudioDeviceModule.AudioTrackErrorCallback {
                override fun onWebRtcAudioTrackInitError(errorMessage: String) { audioError(id) }
                override fun onWebRtcAudioTrackStartError(errorCode: JavaAudioDeviceModule.AudioTrackStartErrorCode, errorMessage: String) { audioError(id) }
                override fun onWebRtcAudioTrackError(errorMessage: String) { audioError(id) }
            }).createAudioDeviceModule()
        factory = PeerConnectionFactory.builder().setAudioDeviceModule(audioModule).createPeerConnectionFactory()
        val configuration = PeerConnection.RTCConfiguration(listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )).apply { sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN }
        peer = factory?.createPeerConnection(configuration, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) = Unit
            override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) post(id) { gathered?.complete(Unit) }
            }
            override fun onIceCandidate(candidate: IceCandidate) = Unit
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
            override fun onAddStream(stream: MediaStream) = Unit
            override fun onRemoveStream(stream: MediaStream) = Unit
            override fun onDataChannel(dataChannel: DataChannel) = Unit
            override fun onRenegotiationNeeded() = Unit
            override fun onAddTrack(receiver: RtpReceiver, streams: Array<out MediaStream>) = Unit
            override fun onTrack(transceiver: RtpTransceiver) {
                post(id) { (transceiver.receiver.track() as? AudioTrack)?.setEnabled(true) }
            }
            override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
                post(id) {
                    when (state) {
                        PeerConnection.PeerConnectionState.CONNECTED -> { connected = true; maybeReady() }
                        PeerConnection.PeerConnectionState.FAILED, PeerConnection.PeerConnectionState.DISCONNECTED,
                        PeerConnection.PeerConnectionState.CLOSED -> fail("음성 연결이 끊어졌습니다. 네트워크를 확인하고 다시 시작해 주세요.")
                        else -> Unit
                    }
                }
            }
        }) ?: throw VoiceConnectionException("WebRTC 연결 초기화에 실패했습니다.")
        source = factory?.createAudioSource(MediaConstraints())
        microphone = factory?.createAudioTrack("microphone", source).also { it?.setEnabled(false) }
        peer?.addTrack(microphone, listOf("englishtalk"))
        channel = peer?.createDataChannel("oai-events", DataChannel.Init())
            ?: throw VoiceConnectionException("AI 이벤트 채널을 만들 수 없습니다.")
        channel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) = Unit
            override fun onStateChange() {
                post(id) {
                    when (channel?.state()) {
                        DataChannel.State.OPEN -> maybeReady()
                        DataChannel.State.CLOSED -> fail("AI 이벤트 연결이 종료되었습니다.")
                        else -> Unit
                    }
                }
            }
            override fun onMessage(buffer: DataChannel.Buffer) {
                if (buffer.binary || buffer.data.remaining() > 262_144) return
                val bytes = ByteArray(buffer.data.remaining())
                buffer.data.duplicate().get(bytes)
                val event = RealtimeEvents.parse(bytes.toString(Charsets.UTF_8)) ?: return
                post(id) {
                    if (event is VoiceEvent.Error) fail(event.message) else if (started) listener?.invoke(event)
                }
            }
        })
    }

    private fun maybeReady() {
        if (started || !connected || channel?.state() != DataChannel.State.OPEN) return
        if (!send(RealtimeEvents.configuration()) || !send(RealtimeEvents.greeting())) {
            fail("AI 대화를 시작할 수 없습니다.")
            return
        }
        started = true
        microphone?.setEnabled(true)
        listener?.invoke(VoiceEvent.Connected)
        ready?.complete(Unit)
    }

    private fun send(event: String): Boolean = channel?.send(
        DataChannel.Buffer(ByteBuffer.wrap(event.toByteArray(Charsets.UTF_8)), false)
    ) == true

    private fun post(id: Int, action: () -> Unit) { main.post { if (id == generation) action() } }
    private fun audioError(id: Int) { post(id) { fail("마이크 또는 스피커를 사용할 수 없습니다. 권한과 오디오 장치를 확인해 주세요.") } }
    private fun ensureCurrent(id: Int) { if (id != generation) throw kotlinx.coroutines.CancellationException("Conversation ended") }
    private fun fail(message: String) {
        val callback = listener
        ready?.completeExceptionally(VoiceConnectionException(message))
        close()
        callback?.invoke(VoiceEvent.Error(message))
    }

    private suspend fun createOffer(connection: PeerConnection): SessionDescription = suspendCancellableCoroutine { continuation ->
        connection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) { if (continuation.isActive) continuation.resume(description) }
            override fun onCreateFailure(error: String) { if (continuation.isActive) continuation.resumeWithException(VoiceConnectionException("음성 연결 요청을 만들 수 없습니다.")) }
            override fun onSetSuccess() = Unit
            override fun onSetFailure(error: String) = Unit
        }, MediaConstraints())
    }

    private suspend fun setDescription(connection: PeerConnection, description: SessionDescription, local: Boolean): Unit = suspendCancellableCoroutine { continuation ->
        val observer = object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onCreateFailure(error: String) = Unit
            override fun onSetSuccess() { if (continuation.isActive) continuation.resume(Unit) }
            override fun onSetFailure(error: String) { if (continuation.isActive) continuation.resumeWithException(VoiceConnectionException("음성 연결 설정에 실패했습니다.")) }
        }
        if (local) connection.setLocalDescription(observer, description) else connection.setRemoteDescription(observer, description)
    }

    override fun close() {
        generation++ // Drop queued callbacks from the previous native session.
        listener = null
        started = false
        connected = false
        ready?.cancel(); ready = null
        gathered?.cancel(); gathered = null
        exchange?.cancel(); exchange = null
        // A failed native cleanup must not prevent releasing the other resources.
        val oldChannel = channel.also { channel = null }
        val oldPeer = peer.also { peer = null }
        val oldMicrophone = microphone.also { microphone = null }
        val oldSource = source.also { source = null }
        val oldFactory = factory.also { factory = null }
        val oldModule = audioModule.also { audioModule = null }
        val oldRoute = route.also { route = null }
        runCatching { oldMicrophone?.setEnabled(false) }
        runCatching { oldChannel?.unregisterObserver() }
        runCatching { oldChannel?.close() }
        runCatching { oldChannel?.dispose() }
        runCatching { oldPeer?.close() }
        runCatching { oldPeer?.dispose() }
        runCatching { oldMicrophone?.dispose() }
        runCatching { oldSource?.dispose() }
        runCatching { oldFactory?.dispose() }
        runCatching { oldModule?.release() }
        runCatching { oldRoute?.release() }
    }
}
