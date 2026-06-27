package com.ekam.baton.core.network.tunnel

import android.content.Context
import kotlinx.coroutines.*
import org.webrtc.*

class A2AWebRtcTransport constructor(
    private val context: Context,
    private val poolManager: ConnectionPoolManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var peerConnectionFactory: PeerConnectionFactory? = null

    init {
        initializeWebRtc()
    }

    private fun initializeWebRtc() {
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(true)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()
    }
    
    suspend fun createSession(sessionId: String): A2ASession {
        if (peerConnectionFactory == null) {
            initializeWebRtc()
        }
        val session = A2ASession(sessionId, peerConnectionFactory!!, scope)
        poolManager.registerConnection(session)
        return session
    }

    fun disposeFactory() {
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
    }
    
    data class A2AMessage(val isBinary: Boolean, val payload: ByteArray)
}
