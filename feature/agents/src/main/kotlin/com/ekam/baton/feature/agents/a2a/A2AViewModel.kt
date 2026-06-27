package com.ekam.baton.feature.agents.a2a

import androidx.lifecycle.ViewModel
import com.ekam.baton.core.network.security.ConnectionSecurityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Base64

import com.ekam.baton.core.network.tunnel.A2AWebRtcTransport
import com.ekam.baton.core.network.tunnel.A2ASession
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.data.model.Agent
import java.util.UUID
import org.webrtc.PeerConnection

class A2AViewModel(
    private val securityManager: ConnectionSecurityManager,
    private val webRtcTransport: A2AWebRtcTransport,
    private val agentRepository: AgentRepository
) : ViewModel() {
    private val _ephemeralPublicKey = MutableStateFlow("")
    val ephemeralPublicKey: StateFlow<String> = _ephemeralPublicKey.asStateFlow()
    
    private var _privateKeyEnc: String = ""
    private var _privateKeyIv: String = ""

    private val _activeTunnels = MutableStateFlow<List<String>>(emptyList())
    val activeTunnels: StateFlow<List<String>> = _activeTunnels.asStateFlow()

    private val _sdpOffer = MutableStateFlow<String?>(null)
    val sdpOffer: StateFlow<String?> = _sdpOffer.asStateFlow()

    private val _sdpAnswer = MutableStateFlow<String?>(null)
    val sdpAnswer: StateFlow<String?> = _sdpAnswer.asStateFlow()

    private var activeSession: A2ASession? = null
    private val _connectionState = MutableStateFlow<PeerConnection.PeerConnectionState>(PeerConnection.PeerConnectionState.NEW)
    val connectionState: StateFlow<PeerConnection.PeerConnectionState> = _connectionState.asStateFlow()

    init {
        generateNewIdentity()
        viewModelScope.launch {
            initNewSession()
        }
    }
    
    private suspend fun initNewSession() {
        activeSession = webRtcTransport.createSession(UUID.randomUUID().toString())
        viewModelScope.launch {
            activeSession?.connectionState?.collect { state ->
                _connectionState.value = state
            }
        }
    }

    private fun generateNewIdentity() {
        val keys = securityManager.generateClientKeys()
        _ephemeralPublicKey.value = keys.publicKeyHex
        _privateKeyEnc = keys.encryptedPrivateKeyBase64
        _privateKeyIv = keys.privateKeyIvBase64
    }

    fun rotateIdentity() {
        viewModelScope.launch {
            activeSession?.disconnect()
            generateNewIdentity()
            initNewSession()
            _sdpOffer.value = null
            _sdpAnswer.value = null
            _activeTunnels.value = emptyList() // Revoke active UI status for current session
        }
    }

    fun generateOffer() {
        viewModelScope.launch {
            val offer = activeSession?.createOffer()
            if (offer != null) {
                // Attach our X25519 public key to the SDP offer string so the peer gets both
                // the WebRTC ICE/DTLS info AND our Baton crypto identity at the same time.
                val combinedOffer = "${_ephemeralPublicKey.value}|||$offer"
                _sdpOffer.value = combinedOffer
            }
        }
    }

    fun receiveOfferAndGenerateAnswer(combinedOffer: String) {
        viewModelScope.launch {
            try {
                val parts = combinedOffer.split("|||")
                if (parts.size == 2) {
                    val remoteCryptoKey = parts[0]
                    val sdpOffer = parts[1]
                    
                    // 1. Establish Crypto Identity
                    pairAgent(remoteCryptoKey)
                    
                    // 2. Establish WebRTC Tunnel
                    val answer = activeSession?.handleOfferAndCreateAnswer(sdpOffer)
                    if (answer != null) {
                        val combinedAnswer = "${_ephemeralPublicKey.value}|||$answer"
                        _sdpAnswer.value = combinedAnswer
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun completeHandshake(combinedAnswer: String) {
        viewModelScope.launch {
            try {
                val parts = combinedAnswer.split("|||")
                if (parts.size == 2) {
                    val remoteCryptoKey = parts[0]
                    val sdpAnswer = parts[1]
                    
                    // 1. Establish Crypto Identity
                    pairAgent(remoteCryptoKey)
                    
                    // 2. Complete WebRTC Tunnel
                    activeSession?.handleAnswer(sdpAnswer)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun pairAgent(remoteKey: String) {
        try {
            val privateKey = securityManager.decryptPrivateKey(_privateKeyEnc, _privateKeyIv)
            val sharedSecret = securityManager.deriveSharedSecret(privateKey, remoteKey)
            val sharedSecretHex = securityManager.toHex(sharedSecret)
            
            // Persistent pairing
            viewModelScope.launch {
                val newAgent = Agent(
                    id = "a2a-${remoteKey.take(12)}",
                    name = "P2P Agent (${remoteKey.take(6)})",
                    description = "Secure A2A Peer established via WebRTC",
                    mcpEndpointUrl = "a2a://tunnel", // Protocol handled by ChatRepository
                    authType = "sovereign",
                    authConfig = "{}",
                    colorAccent = "#34A853", // Green for secure
                    securityMode = "sovereign",
                    securityConfig = "{\"shared_secret\": \"$sharedSecretHex\", \"peer_public_key\": \"$remoteKey\"}"
                )
                agentRepository.upsertAgent(newAgent)
            }

            val current = _activeTunnels.value.toMutableList()
            current.add("Connection Secure: ${remoteKey.take(8)}...")
            _activeTunnels.value = current
        } catch (e: Exception) {
            val current = _activeTunnels.value.toMutableList()
            current.add("Handshake failed: ${remoteKey.take(8)}...")
            _activeTunnels.value = current
        }
    }
}
