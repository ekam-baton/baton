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
import com.ekam.baton.core.data.repository.WorldRoomRepository
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.model.AgentRole
import com.ekam.baton.core.data.model.WorldRoom
import android.content.Context
import com.ekam.baton.core.network.service.SwarmForegroundService
import java.util.UUID
import com.ekam.baton.core.data.db.dao.SwarmDao
import com.ekam.baton.core.data.db.dao.TokenLedgerDao
import com.ekam.baton.core.data.db.entity.SwarmEntity
import org.webrtc.PeerConnection
import kotlin.random.Random

class A2AViewModel(
    private val securityManager: ConnectionSecurityManager,
    private val webRtcTransport: A2AWebRtcTransport,
    private val agentRepository: AgentRepository,
    private val worldRoomRepository: WorldRoomRepository,
    private val swarmDao: SwarmDao,
    private val tokenLedgerDao: TokenLedgerDao,
    private val context: Context? = null
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
    
    // WebRTC Telemetry States
    private val _swarmTopology = MutableStateFlow("Mesh Network (SFU)")
    val swarmTopology: StateFlow<String> = _swarmTopology.asStateFlow()

    private val _dataChannelStatus = MutableStateFlow("Muted (Context Phase)")
    val dataChannelStatus: StateFlow<String> = _dataChannelStatus.asStateFlow()

    private val _burnRate = MutableStateFlow("0.0 tk/s")
    val burnRate: StateFlow<String> = _burnRate.asStateFlow()

    private var activeSession: A2ASession? = null
    private val _connectionState = MutableStateFlow<PeerConnection.PeerConnectionState>(PeerConnection.PeerConnectionState.NEW)
    val connectionState: StateFlow<PeerConnection.PeerConnectionState> = _connectionState.asStateFlow()

    private val _pendingRoleAssignment = MutableStateFlow<Agent?>(null)
    val pendingRoleAssignment: StateFlow<Agent?> = _pendingRoleAssignment.asStateFlow()

    private val _activeSwarmId = MutableStateFlow<String?>(null)
    val activeSwarmId: StateFlow<String?> = _activeSwarmId.asStateFlow()

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
            if (offer != null && !offer.startsWith("ERROR:")) {
                // Attach our X25519 public key to the SDP offer string so the peer gets both
                // the WebRTC ICE/DTLS info AND our Baton crypto identity at the same time.
                val mungedOffer = mungeSdp(offer)
                val combinedOffer = "${_ephemeralPublicKey.value}|||$mungedOffer"
                _sdpOffer.value = combinedOffer
            } else {
                if (offer != null && offer.startsWith("ERROR:")) {
                    android.util.Log.e("A2AViewModel", "Failed to generate offer: $offer")
                }
                _sdpOffer.value = null
            }
        }
    }

    private fun mungeSdp(sdp: String): String {
        // Strip verbose/unnecessary attributes to compress QR code size by up to 60%
        return sdp.lines().filter { line ->
            line.isNotBlank() &&
            !line.startsWith("a=extmap") && // Verbose header extensions
            !line.contains("typ relay") && // Drop TURN relays (not needed for local air-gap)
            !line.contains("::1") && // Drop local IPv6 loopback
            !line.contains("tcp") // Force UDP candidates for faster P2P
        }.joinToString("\n")
    }

    fun receiveOfferAndGenerateAnswer(combinedOffer: String) {
        viewModelScope.launch {
            try {
                val parts = combinedOffer.split("|||", limit = 2)
                if (parts.size == 2) {
                    val remoteCryptoKey = parts[0]
                    val sdpOffer = parts[1]
                    if (sdpOffer.startsWith("ERROR:")) {
                        android.util.Log.e("A2AViewModel", "Received error in offer SDP: $sdpOffer")
                        _sdpAnswer.value = null
                        return@launch
                    }
                    
                    // 1. Establish Crypto Identity
                    pairAgent(remoteCryptoKey)
                    
                    // 2. Establish WebRTC Tunnel
                    val answer = activeSession?.handleOfferAndCreateAnswer(sdpOffer)
                    if (answer != null && !answer.startsWith("ERROR:")) {
                        val mungedAnswer = mungeSdp(answer)
                        val combinedAnswer = "${_ephemeralPublicKey.value}|||$mungedAnswer"
                        _sdpAnswer.value = combinedAnswer
                    } else {
                        if (answer != null && answer.startsWith("ERROR:")) {
                            android.util.Log.e("A2AViewModel", "Failed to generate answer: $answer")
                        }
                        _sdpAnswer.value = null
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("A2AViewModel", "Error starting server mode", e)
                _sdpAnswer.value = null
            }
        }
    }

    fun completeHandshake(combinedAnswer: String) {
        viewModelScope.launch {
            try {
                val parts = combinedAnswer.split("|||", limit = 2)
                if (parts.size == 2) {
                    val remoteCryptoKey = parts[0]
                    val sdpAnswer = parts[1]
                    if (sdpAnswer.startsWith("ERROR:")) {
                        android.util.Log.e("A2AViewModel", "Received error in answer SDP: $sdpAnswer")
                        return@launch
                    }
                    
                    // 1. Establish Crypto Identity
                    pairAgent(remoteCryptoKey)
                    
                    // 2. Complete WebRTC Tunnel
                    activeSession?.handleAnswer(sdpAnswer)
                }
            } catch (e: Exception) {
                android.util.Log.e("A2AViewModel", "Error completing handshake", e)
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
                    securityConfig = "{\"shared_secret\": \"$sharedSecretHex\", \"peer_public_key\": \"$remoteKey\"}",
                    role = AgentRole.CODER.name // Default, will be updated during assignment
                )
                agentRepository.upsertAgent(newAgent)
                _pendingRoleAssignment.value = newAgent
            }

            val current = _activeTunnels.value.toMutableList()
            current.add("Connection Secure: ${remoteKey.take(8)}...")
            _activeTunnels.value = current
            
            // Mark topology as active
            _swarmTopology.value = "P2P Direct"
            _dataChannelStatus.value = "Open [Events: Active]"
        } catch (e: Exception) {
            val current = _activeTunnels.value.toMutableList()
            current.add("Handshake failed: ${remoteKey.take(8)}...")
            _activeTunnels.value = current
            _swarmTopology.value = "Detached"
            _dataChannelStatus.value = "Closed / Failed"
        }
    }

    fun assignRoleToPendingAgent(role: AgentRole) {
        val agent = _pendingRoleAssignment.value ?: return
        viewModelScope.launch {
            // Update agent role in DB
            val updatedAgent = agent.copy(role = role.name)
            agentRepository.upsertAgent(updatedAgent)
            
            // Auto-create world room
            val room = WorldRoom(
                id = UUID.randomUUID().toString(),
                agentId = agent.id,
                role = role,
                displayName = agent.name,
                colorHex = agent.colorAccent,
                worldX = Random.nextInt(1, 15), // Random placement for now
                worldY = Random.nextInt(1, 15)
            )
            worldRoomRepository.upsertRoom(room)
            worldRoomRepository.setDefaultPropsForRoom(room)
            
            _pendingRoleAssignment.value = null
            
            // Record Swarm Creation
            val swarm = SwarmEntity(
                name = "A2A P2P Cluster",
                leaderAgentId = "local_orchestrator",
                workerAgentIds = "[\"${agent.id}\"]",
                topologyType = "P2P",
                intentContext = "Live A2A WebRTC Setup"
            )
            swarmDao.insertSwarm(swarm)
            _activeSwarmId.value = swarm.id
            context?.let { ctx ->
                SwarmForegroundService.start(ctx, swarm.id, swarm.name, 2)
            }
        }
    }
    
    fun dismissRoleAssignment() {
        _pendingRoleAssignment.value = null
    }

    fun forceHaltSwarm() {
        viewModelScope.launch {
            activeSession?.disconnect()
            _swarmTopology.value = "Halted"
            _dataChannelStatus.value = "Force Closed"
            context?.let { ctx ->
                SwarmForegroundService.stop(ctx)
            }
        }
    }
}
