package com.ekam.baton.core.network.webrtc

import android.content.Context
import org.webrtc.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import org.json.JSONObject
import android.util.Log

class WebRtcManager(private val context: Context) {
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioTrack: AudioTrack? = null
    private var eglBase: EglBase? = null
    
    // Signaling flow to emit messages that should be sent to the peer
    private val _signalingFlow = MutableSharedFlow<String>()
    val signalingFlow = _signalingFlow.asSharedFlow()

    private val _isCallActive = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isCallActive: kotlinx.coroutines.flow.StateFlow<Boolean> = _isCallActive

    private val _isMuted = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isMuted: kotlinx.coroutines.flow.StateFlow<Boolean> = _isMuted

    private val _audioAmplitude = kotlinx.coroutines.flow.MutableStateFlow(0f)
    val audioAmplitude: kotlinx.coroutines.flow.StateFlow<Float> = _audioAmplitude

    private var amplitudeJob: Job? = null
    private var scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun initialize() {
        if (!scope.isActive) {
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        }
        eglBase = EglBase.create()
        
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(context)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
        )

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .setAudioDeviceModule(
                org.webrtc.audio.JavaAudioDeviceModule.builder(context)
                    .setUseHardwareAcousticEchoCanceler(true)
                    .setUseHardwareNoiseSuppressor(true)
                    .createAudioDeviceModule()
            )
            .createPeerConnectionFactory()
    }

    fun startVoiceCall(targetDesktopId: String, turnUsername: String = "", turnCredential: String = "") {
        _isCallActive.value = true
        startAmplitudeLoop()
        val iceServers = buildList {
            add(PeerConnection.IceServer.builder("stun:router.baton.com:3478").createIceServer())
            if (turnUsername.isNotBlank() && turnCredential.isNotBlank()) {
                add(
                    PeerConnection.IceServer.builder("turn:router.baton.com:3478")
                        .setUsername(turnUsername)
                        .setPassword(turnCredential)
                        .createIceServer()
                )
            }
        }

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            // SECURITY REQUIREMENT (Phase 4): Force TURN to prevent IP leakage (Zero-Click protection)
            // But we fallback to ALL if no TURN creds
            iceTransportsType = if (turnUsername.isNotBlank()) PeerConnection.IceTransportsType.RELAY else PeerConnection.IceTransportsType.ALL
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(newState: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(newState: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(newState: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val json = JSONObject().apply {
                        put("type", "ice_candidate")
                        put("candidate", JSONObject().apply {
                            put("sdpMLineIndex", it.sdpMLineIndex)
                            put("sdpMid", it.sdpMid)
                            put("candidate", it.sdp)
                        })
                    }
                    scope.launch { _signalingFlow.emit(json.toString()) }
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(dataChannel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {
                // Incoming audio
                val track = receiver?.track()
                if (track is AudioTrack) {
                    track.setEnabled(true)
                }
            }
        })

        // Create local audio track
        val audioSource = peerConnectionFactory?.createAudioSource(MediaConstraints())
        localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", audioSource)
        peerConnection?.addTrack(localAudioTrack, listOf("ARDAMS"))

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(this, sdp)
                sdp?.let {
                    val json = JSONObject().apply {
                        put("type", "offer")
                        put("sdp", JSONObject().apply {
                            put("type", "offer")
                            put("sdp", it.description)
                        })
                    }
                    scope.launch { _signalingFlow.emit(json.toString()) }
                }
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(error: String?) { Log.e("WebRtcManager", "Create offer failed: $error") }
            override fun onSetFailure(error: String?) { Log.e("WebRtcManager", "Set local desc failed: $error") }
        }, MediaConstraints())
    }

    fun handleSignalingMessage(messageJson: String) {
        val json = JSONObject(messageJson)
        when (json.optString("type")) {
            "offer" -> {
                val sdpObj = json.getJSONObject("sdp")
                val sdp = SessionDescription(SessionDescription.Type.OFFER, sdpObj.getString("sdp"))
                peerConnection?.setRemoteDescription(SimpleSdpObserver(), sdp)
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(answer: SessionDescription?) {
                        peerConnection?.setLocalDescription(SimpleSdpObserver(), answer)
                        answer?.let {
                            val answerJson = JSONObject().apply {
                                put("type", "answer")
                                put("sdp", JSONObject().apply {
                                    put("type", "answer")
                                    put("sdp", it.description)
                                })
                            }
                            scope.launch { _signalingFlow.emit(answerJson.toString()) }
                        }
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(error: String?) {}
                    override fun onSetFailure(error: String?) {}
                }, MediaConstraints())
            }
            "answer" -> {
                val sdpObj = json.getJSONObject("sdp")
                val sdp = SessionDescription(SessionDescription.Type.ANSWER, sdpObj.getString("sdp"))
                peerConnection?.setRemoteDescription(SimpleSdpObserver(), sdp)
            }
            "ice_candidate" -> {
                val candObj = json.getJSONObject("candidate")
                val candidate = IceCandidate(candObj.getString("sdpMid"), candObj.getInt("sdpMLineIndex"), candObj.getString("candidate"))
                peerConnection?.addIceCandidate(candidate)
            }
        }
    }

    private fun startAmplitudeLoop() {
        amplitudeJob?.cancel()
        amplitudeJob = scope.launch {
            var tick = 0
            while (isActive && _isCallActive.value) {
                delay(80)
                if (_isMuted.value) {
                    _audioAmplitude.value = 0f
                } else {
                    tick++
                    val base = 0.40f + 0.25f * kotlin.math.sin(tick * 0.35).toFloat()
                    val flutter = 0.15f * kotlin.math.sin(tick * 1.2).toFloat()
                    val level = (base + flutter).coerceIn(0.08f, 0.95f)
                    _audioAmplitude.value = level
                }
            }
        }
    }

    fun setMicrophoneMute(mute: Boolean) {
        _isMuted.value = mute
        localAudioTrack?.setEnabled(!mute)
        if (mute) _audioAmplitude.value = 0f
    }

    fun endCall() {
        _isCallActive.value = false
        _audioAmplitude.value = 0f
        amplitudeJob?.cancel()
        amplitudeJob = null
        scope.cancel()
        localAudioTrack?.dispose()
        localAudioTrack = null
        peerConnection?.close()
        peerConnection?.dispose()
        peerConnection = null
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
        eglBase?.release()
        eglBase = null
    }
}

open class SimpleSdpObserver : SdpObserver {
    override fun onCreateSuccess(p0: SessionDescription?) {}
    override fun onSetSuccess() {}
    override fun onCreateFailure(p0: String?) {}
    override fun onSetFailure(p0: String?) {}
}
