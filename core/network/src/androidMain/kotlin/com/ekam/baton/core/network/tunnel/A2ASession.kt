package com.ekam.baton.core.network.tunnel

import kotlin.coroutines.resume
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.webrtc.*
import java.nio.ByteBuffer
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.ceil
import kotlin.math.min

class A2ASession(
    override val id: String,
    private val peerConnectionFactory: PeerConnectionFactory,
    private val scope: CoroutineScope
) : PoolableConnection {

    private var peerConnection: PeerConnection? = null
    private var dataChannel: DataChannel? = null
    
    private val _connectionState = MutableStateFlow<PeerConnection.PeerConnectionState>(PeerConnection.PeerConnectionState.NEW)
    val connectionState: StateFlow<PeerConnection.PeerConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<A2AWebRtcTransport.A2AMessage>()
    val incomingMessages = _incomingMessages.asSharedFlow()

    private val incomingChunks = ConcurrentHashMap<String, Array<ByteArray?>>()
    private val MAX_PAYLOAD_SIZE = 16336
    private val MAX_BUFFER = 1024 * 1024

    private var _lastAccessedAt: Long = System.currentTimeMillis()
    override val lastAccessedAt: Long
        get() = _lastAccessedAt
        
    private val _iceGatheringComplete = MutableStateFlow(false)

    init {
        createPeerConnection()
    }
    
    fun markAccessed() {
        _lastAccessedAt = System.currentTimeMillis()
    }

    private fun createPeerConnection() {
        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer()
        )
        val rtcConfig = PeerConnection.RTCConfiguration(iceServers)
        
        peerConnection = peerConnectionFactory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {
                if (state == PeerConnection.IceGatheringState.COMPLETE) {
                    _iceGatheringComplete.value = true
                }
            }
            override fun onIceCandidate(candidate: IceCandidate?) {}
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {
                dataChannel = channel
                setupDataChannelObserver()
            }
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, mediaStreams: Array<out MediaStream>?) {}
            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                if (newState != null) {
                    _connectionState.value = newState
                }
            }
        })
    }

    private fun createDataChannel() {
        val init = DataChannel.Init()
        init.ordered = true
        dataChannel = peerConnection?.createDataChannel("A2A_TUNNEL", init)
        setupDataChannelObserver()
    }

    private fun setupDataChannelObserver() {
        dataChannel?.registerObserver(object : DataChannel.Observer {
            override fun onBufferedAmountChange(previousAmount: Long) {}
            override fun onStateChange() {}
            override fun onMessage(buffer: DataChannel.Buffer?) {
                markAccessed()
                if (buffer == null || !buffer.binary) return
                
                val data = ByteArray(buffer.data.remaining())
                buffer.data.get(data)
                
                if (data.size < 48) return

                val bb = ByteBuffer.wrap(data)
                val idBytes = ByteArray(36)
                bb.get(idBytes)
                val messageId = String(idBytes, Charsets.UTF_8).trimEnd('\u0000')
                val chunkIndex = bb.int
                val totalChunks = bb.int
                val typeInt = bb.int
                val isBinary = typeInt == 1

                val payloadLength = data.size - 48
                val payload = ByteArray(payloadLength)
                bb.get(payload)

                val chunks = incomingChunks.getOrPut(messageId) { arrayOfNulls(totalChunks) }
                chunks[chunkIndex] = payload

                if (chunks.all { it != null }) {
                    var totalSize = 0
                    chunks.forEach { totalSize += it!!.size }
                    val completePayload = ByteArray(totalSize)
                    var offset = 0
                    chunks.forEach { 
                        System.arraycopy(it!!, 0, completePayload, offset, it.size)
                        offset += it.size
                    }
                    
                    incomingChunks.remove(messageId)
                    scope.launch {
                        _incomingMessages.emit(A2AWebRtcTransport.A2AMessage(isBinary, completePayload))
                    }
                }
            }
        })
    }

    suspend fun sendData(payload: ByteArray, isBinary: Boolean) {
        markAccessed()
        if (dataChannel?.state() != DataChannel.State.OPEN) return

        val messageId = UUID.randomUUID().toString()
        val totalChunks = ceil(payload.size.toDouble() / MAX_PAYLOAD_SIZE).toInt()
        val typeInt = if (isBinary) 1 else 0

        for (i in 0 until totalChunks) {
            while ((dataChannel?.bufferedAmount() ?: 0L) > MAX_BUFFER) {
                delay(50)
            }

            val offset = i * MAX_PAYLOAD_SIZE
            val length = min(MAX_PAYLOAD_SIZE, payload.size - offset)
            val chunkPayload = payload.copyOfRange(offset, offset + length)

            val headerBuffer = ByteBuffer.allocate(48)
            val idBytes = messageId.toByteArray(Charsets.UTF_8).copyOf(36)
            headerBuffer.put(idBytes)
            headerBuffer.putInt(i)
            headerBuffer.putInt(totalChunks)
            headerBuffer.putInt(typeInt)
            
            val combined = ByteArray(48 + length)
            System.arraycopy(headerBuffer.array(), 0, combined, 0, 48)
            System.arraycopy(chunkPayload, 0, combined, 48, length)

            val buffer = DataChannel.Buffer(ByteBuffer.wrap(combined), true)
            dataChannel?.send(buffer)
        }
    }

    suspend fun createOffer(): String? = suspendCancellableCoroutine { cont ->
        if (peerConnection == null) {
            cont.resume("ERROR: PeerConnection is null. Did initialization fail?")
            return@suspendCancellableCoroutine
        }

        createDataChannel()
        _iceGatheringComplete.value = false

        val mediaConstraints = MediaConstraints()
        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription?) {
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        scope.launch {
                            withTimeoutOrNull(5000) {
                                _iceGatheringComplete.first { it }
                            }
                            cont.resume(peerConnection?.localDescription?.description)
                        }
                    }
                    override fun onCreateFailure(p0: String?) { cont.resume("ERROR: Failed to set local description: $p0") }
                    override fun onSetFailure(p0: String?) { cont.resume("ERROR: Failed to set local description: $p0") }
                }, sdp)
            }
            override fun onSetSuccess() {}
            override fun onCreateFailure(p0: String?) { cont.resume("ERROR: Failed to create offer: $p0") }
            override fun onSetFailure(p0: String?) {}
        }, mediaConstraints)
    }

    suspend fun handleOfferAndCreateAnswer(offerSdp: String): String? = suspendCancellableCoroutine { cont ->
        if (peerConnection == null) {
            cont.resume("ERROR: PeerConnection is null.")
            return@suspendCancellableCoroutine
        }

        _iceGatheringComplete.value = false
        val sessionDescription = SessionDescription(SessionDescription.Type.OFFER, offerSdp)
        
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() {
                val mediaConstraints = MediaConstraints()
                peerConnection?.createAnswer(object : SdpObserver {
                    override fun onCreateSuccess(sdp: SessionDescription?) {
                        peerConnection?.setLocalDescription(object : SdpObserver {
                            override fun onCreateSuccess(p0: SessionDescription?) {}
                            override fun onSetSuccess() {
                                scope.launch {
                                    withTimeoutOrNull(5000) {
                                        _iceGatheringComplete.first { it }
                                    }
                                    cont.resume(peerConnection?.localDescription?.description)
                                }
                            }
                            override fun onCreateFailure(p0: String?) { cont.resume("ERROR: Failed to set local desc for answer: $p0") }
                            override fun onSetFailure(p0: String?) { cont.resume("ERROR: Failed to set local desc for answer: $p0") }
                        }, sdp)
                    }
                    override fun onSetSuccess() {}
                    override fun onCreateFailure(p0: String?) { cont.resume("ERROR: Failed to create answer: $p0") }
                    override fun onSetFailure(p0: String?) {}
                }, mediaConstraints)
            }
            override fun onCreateFailure(p0: String?) { cont.resume("ERROR: Failed to set remote desc: $p0") }
            override fun onSetFailure(p0: String?) { cont.resume("ERROR: Failed to set remote desc: $p0") }
        }, sessionDescription)
    }

    suspend fun handleAnswer(answerSdp: String) = suspendCancellableCoroutine<Boolean> { cont ->
        if (peerConnection == null) {
            cont.resume(false)
            return@suspendCancellableCoroutine
        }
        val sessionDescription = SessionDescription(SessionDescription.Type.ANSWER, answerSdp)
        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onSetSuccess() { cont.resume(true) }
            override fun onCreateFailure(p0: String?) { cont.resume(false) }
            override fun onSetFailure(p0: String?) { cont.resume(false) }
        }, sessionDescription)
    }

    override suspend fun disconnect() {
        dataChannel?.unregisterObserver()
        dataChannel?.dispose()
        dataChannel = null
        
        peerConnection?.dispose()
        peerConnection = null
        
        _connectionState.value = PeerConnection.PeerConnectionState.CLOSED
    }
}
