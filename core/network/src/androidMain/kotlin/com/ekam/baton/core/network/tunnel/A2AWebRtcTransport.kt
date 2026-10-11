package com.ekam.baton.core.network.tunnel

import android.content.Context
import kotlinx.coroutines.*
import org.webrtc.*

open class A2AWebRtcTransport constructor(
    private val context: Context? = null,
    private val poolManager: ConnectionPoolManager? = null
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private var peerConnectionFactory: PeerConnectionFactory? = null

    init {
        if (context != null) {
            initializeWebRtc()
        }
    }

    private fun initializeWebRtc() {
        val ctx = context ?: return
        val initializationOptions = PeerConnectionFactory.InitializationOptions.builder(ctx)
            .setEnableInternalTracer(false) // SECURITY FIX (LOW-3): Disable internal tracer
            .createInitializationOptions()
        PeerConnectionFactory.initialize(initializationOptions)

        val options = PeerConnectionFactory.Options()
        peerConnectionFactory = PeerConnectionFactory.builder()
            .setOptions(options)
            .createPeerConnectionFactory()
    }
    
    open suspend fun createSession(sessionId: String): A2ASession {
        if (peerConnectionFactory == null && context != null) {
            initializeWebRtc()
        }
        val factory = peerConnectionFactory
        val session = if (factory != null) {
            A2ASession(sessionId, factory, scope)
        } else {
            A2ASession(sessionId, scope = scope)
        }
        poolManager?.registerConnection(session)
        return session
    }

    open fun disposeFactory() {
        peerConnectionFactory?.dispose()
        peerConnectionFactory = null
    }
    
    data class A2AMessage(val isBinary: Boolean, val payload: ByteArray)
}
