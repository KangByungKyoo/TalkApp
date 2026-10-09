package com.minipapa.englishtalk.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

@Suppress("DEPRECATION")
class VoiceAudioRoute(context: Context, private val onFocusLost: () -> Unit) {
    private val manager = context.getSystemService(AudioManager::class.java)
    private var request: AudioFocusRequest? = null
    private var oldMode = AudioManager.MODE_NORMAL
    private var oldSpeaker = false
    private var oldDevice: AudioDeviceInfo? = null

    fun acquire() {
        val focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onFocusLost()
            }.build()
        if (manager.requestAudioFocus(focus) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            throw VoiceConnectionException("다른 앱이 오디오를 사용 중입니다. 잠시 후 다시 시작해 주세요.")
        }
        oldMode = manager.mode
        oldSpeaker = manager.isSpeakerphoneOn
        if (Build.VERSION.SDK_INT >= 31) oldDevice = manager.communicationDevice
        request = focus
        manager.mode = AudioManager.MODE_IN_COMMUNICATION
        if (Build.VERSION.SDK_INT >= 31) {
            val speaker = manager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
            if (speaker != null && !manager.setCommunicationDevice(speaker)) {
                throw VoiceConnectionException("스피커를 사용할 수 없습니다.")
            }
        } else manager.isSpeakerphoneOn = true
    }

    fun release() {
        val focus = request ?: return
        request = null
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                val device = oldDevice
                if (device != null && manager.availableCommunicationDevices.any { it.id == device.id }) {
                    manager.setCommunicationDevice(device)
                } else manager.clearCommunicationDevice()
            } else manager.isSpeakerphoneOn = oldSpeaker
            manager.mode = oldMode
        } finally { manager.abandonAudioFocusRequest(focus) }
    }
}
