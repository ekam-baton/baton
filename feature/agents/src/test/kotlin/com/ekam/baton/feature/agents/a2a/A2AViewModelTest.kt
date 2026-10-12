package com.ekam.baton.feature.agents.a2a

import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.dao.SwarmDao
import com.ekam.baton.core.data.db.dao.TokenLedgerDao
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.db.entity.SwarmEntity
import com.ekam.baton.core.data.db.entity.TokenLedgerEntity
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.model.AgentRole
import com.ekam.baton.core.data.model.WorldRoom
import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.data.repository.WorldRoomRepository
import com.ekam.baton.core.network.security.ClientKeyDetails
import com.ekam.baton.core.network.security.ConnectionSecurityManager
import com.ekam.baton.core.network.tunnel.A2ASession
import com.ekam.baton.core.network.tunnel.A2AWebRtcTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.webrtc.PeerConnection
import java.security.SecureRandom

@OptIn(ExperimentalCoroutinesApi::class)
class A2AViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    // Fakes
    private lateinit var fakeSecurityManager: FakeConnectionSecurityManager
    private lateinit var fakeWebRtcTransport: FakeA2AWebRtcTransport
    private lateinit var fakeAgentDao: FakeAgentDao
    private lateinit var agentRepository: AgentRepository
    private lateinit var fakeWorldRoomRepository: FakeWorldRoomRepository
    private lateinit var fakeSwarmDao: FakeSwarmDao
    private lateinit var fakeTokenLedgerDao: FakeTokenLedgerDao

    private lateinit var viewModel: A2AViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeSecurityManager = FakeConnectionSecurityManager()
        fakeWebRtcTransport = FakeA2AWebRtcTransport()
        fakeAgentDao = FakeAgentDao()
        agentRepository = AgentRepository(fakeAgentDao)
        fakeWorldRoomRepository = FakeWorldRoomRepository()
        fakeSwarmDao = FakeSwarmDao()
        fakeTokenLedgerDao = FakeTokenLedgerDao()

        viewModel = A2AViewModel(
            securityManager = fakeSecurityManager,
            webRtcTransport = fakeWebRtcTransport,
            agentRepository = agentRepository,
            worldRoomRepository = fakeWorldRoomRepository,
            swarmDao = fakeSwarmDao,
            tokenLedgerDao = fakeTokenLedgerDao
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialStateAndIdentityGeneration() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Ephemeral identity was generated
        val pubKey = viewModel.ephemeralPublicKey.value
        assertNotNull(pubKey)
        assertTrue(pubKey.isNotEmpty())
        assertEquals(64, pubKey.length)

        // WebRTC initial states
        assertNull(viewModel.sdpOffer.value)
        assertNull(viewModel.sdpAnswer.value)
        assertTrue(viewModel.activeTunnels.value.isEmpty())
        assertEquals("Mesh Network (SFU)", viewModel.swarmTopology.value)
        assertEquals("Muted (Context Phase)", viewModel.dataChannelStatus.value)
        assertEquals("0.0 tk/s", viewModel.burnRate.value)
        assertEquals(PeerConnection.PeerConnectionState.NEW, viewModel.connectionState.value)
        assertNull(viewModel.pendingRoleAssignment.value)
        assertNull(viewModel.activeSwarmId.value)
    }

    @Test
    fun testRotateIdentity() = runTest(testDispatcher) {
        advanceUntilIdle()

        val initialKey = viewModel.ephemeralPublicKey.value
        fakeSecurityManager.nextPublicKeyHex = "fedcba0987654321fedcba0987654321fedcba0987654321fedcba0987654321"

        viewModel.rotateIdentity()
        advanceUntilIdle()

        val rotatedKey = viewModel.ephemeralPublicKey.value
        assertEquals("fedcba0987654321fedcba0987654321fedcba0987654321fedcba0987654321", rotatedKey)
        assertNotEquals(initialKey, rotatedKey)
        assertNull(viewModel.sdpOffer.value)
        assertNull(viewModel.sdpAnswer.value)
        assertTrue(viewModel.activeTunnels.value.isEmpty())
    }

    @Test
    fun testGenerateOfferMungesSdpAndPrependsKey() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.generateOffer()
        advanceUntilIdle()

        val offer = viewModel.sdpOffer.value
        assertNotNull(offer)
        assertTrue(offer!!.contains("|||"))

        val parts = offer.split("|||")
        assertEquals(2, parts.size)
        assertEquals(viewModel.ephemeralPublicKey.value, parts[0])

        val mungedSdp = parts[1]
        // Sdp munging should filter out a=extmap, typ relay, and tcp candidates
        assertFalse(mungedSdp.contains("a=extmap"))
        assertFalse(mungedSdp.contains("typ relay"))
        assertFalse(mungedSdp.contains("tcp"))
        assertTrue(mungedSdp.contains("typ host"))
    }

    @Test
    fun testReceiveOfferAndGenerateAnswer() = runTest(testDispatcher) {
        advanceUntilIdle()

        val remotePublicKey = "aabbccddeeff00112233445566778899aabbccddeeff00112233445566778899"
        val remoteOfferSdp = "v=0\no=- 999 2 IN IP4 127.0.0.1\ns=-\nt=0 0\na=candidate:1 1 UDP 2130706431 10.0.0.1 50000 typ host\n"
        val combinedOffer = "$remotePublicKey|||$remoteOfferSdp"

        viewModel.receiveOfferAndGenerateAnswer(combinedOffer)
        advanceUntilIdle()

        // 1. remote session generated answer
        val answer = viewModel.sdpAnswer.value
        assertNotNull(answer)
        assertTrue(answer!!.startsWith(viewModel.ephemeralPublicKey.value + "|||"))

        // 2. agent was paired into AgentRepository
        val pendingAgent = viewModel.pendingRoleAssignment.value
        assertNotNull(pendingAgent)
        assertEquals("a2a-${remotePublicKey.take(12)}", pendingAgent!!.id)
        assertEquals("P2P Agent (${remotePublicKey.take(6)})", pendingAgent.name)
        assertEquals(AgentRole.CODER.name, pendingAgent.role)

        // 3. active tunnels updated
        assertTrue(viewModel.activeTunnels.value.any { it.contains("Connection Secure") })
        assertEquals("P2P Direct", viewModel.swarmTopology.value)
        assertEquals("Open [Events: Active]", viewModel.dataChannelStatus.value)
    }

    @Test
    fun testCompleteHandshake() = runTest(testDispatcher) {
        advanceUntilIdle()

        val remotePublicKey = "11223344556677889900aabbccddeeff11223344556677889900aabbccddeeff"
        val remoteAnswerSdp = "v=0\no=- 888 2 IN IP4 127.0.0.1\ns=-\nt=0 0\na=candidate:1 1 UDP 2130706431 10.0.0.2 50000 typ host\n"
        val combinedAnswer = "$remotePublicKey|||$remoteAnswerSdp"

        viewModel.completeHandshake(combinedAnswer)
        advanceUntilIdle()

        // Agent was paired
        val pairedAgent = viewModel.pendingRoleAssignment.value
        assertNotNull(pairedAgent)
        assertEquals("a2a-${remotePublicKey.take(12)}", pairedAgent!!.id)

        // Session handled answer
        val activeSession = fakeWebRtcTransport.currentSession
        assertEquals(remoteAnswerSdp, activeSession.lastHandledAnswer)
    }

    @Test
    fun testAssignRoleToPendingAgent() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Setup pending agent
        val remotePublicKey = "cafebabe11223344556677889900aabbcafebabe11223344556677889900aabb"
        viewModel.receiveOfferAndGenerateAnswer("$remotePublicKey|||dummy_sdp")
        advanceUntilIdle()

        assertNotNull(viewModel.pendingRoleAssignment.value)

        // Assign role
        viewModel.assignRoleToPendingAgent(AgentRole.COORDINATOR)
        advanceUntilIdle()

        // 1. pending cleared
        assertNull(viewModel.pendingRoleAssignment.value)

        // 2. Agent role updated in repo
        val savedAgent = fakeAgentDao.agents.firstOrNull { it.id == "a2a-${remotePublicKey.take(12)}" }
        assertNotNull(savedAgent)
        assertEquals(AgentRole.COORDINATOR.name, savedAgent!!.role)

        // 3. World room created
        assertEquals(1, fakeWorldRoomRepository.upsertedRooms.size)
        val room = fakeWorldRoomRepository.upsertedRooms.first()
        assertEquals(savedAgent.id, room.agentId)
        assertEquals(AgentRole.COORDINATOR, room.role)

        // 4. Swarm created
        assertEquals(1, fakeSwarmDao.swarms.size)
        val swarm = fakeSwarmDao.swarms.first()
        assertEquals("A2A P2P Cluster", swarm.name)
        assertEquals("P2P", swarm.topologyType)
        assertTrue(swarm.workerAgentIds.contains(savedAgent.id))
        assertEquals(swarm.id, viewModel.activeSwarmId.value)
    }

    @Test
    fun testDismissRoleAssignment() = runTest(testDispatcher) {
        advanceUntilIdle()

        val remotePublicKey = "1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        viewModel.receiveOfferAndGenerateAnswer("$remotePublicKey|||dummy_sdp")
        advanceUntilIdle()

        assertNotNull(viewModel.pendingRoleAssignment.value)

        viewModel.dismissRoleAssignment()
        assertNull(viewModel.pendingRoleAssignment.value)
    }

    @Test
    fun testForceHaltSwarm() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.forceHaltSwarm()
        advanceUntilIdle()

        assertTrue(fakeWebRtcTransport.currentSession.isDisconnected)
        assertEquals("Halted", viewModel.swarmTopology.value)
        assertEquals("Force Closed", viewModel.dataChannelStatus.value)
    }

    @Test
    fun testPeerConnectionStatePropagation() = runTest(testDispatcher) {
        advanceUntilIdle()

        fakeWebRtcTransport.currentSession.emitState(PeerConnection.PeerConnectionState.CONNECTED)
        advanceUntilIdle()

        assertEquals(PeerConnection.PeerConnectionState.CONNECTED, viewModel.connectionState.value)
    }

    // ==========================================
    // FAKES
    // ==========================================

    private class FakeConnectionSecurityManager : ConnectionSecurityManager(null) {
        var nextPublicKeyHex: String = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        override fun generateClientKeys(): ClientKeyDetails {
            return ClientKeyDetails(
                publicKeyHex = nextPublicKeyHex,
                encryptedPrivateKeyBase64 = "ZW5jcnlwdGVkX3ByaXZhdGVfa2V5",
                privateKeyIvBase64 = "aXZfYmFzZTY0"
            )
        }

        override fun decryptPrivateKey(ciphertextBase64: String, ivBase64: String): ByteArray {
            return ByteArray(32) { 0x01 }
        }

        override fun deriveSharedSecret(privateKey: ByteArray, peerPublicKeyHex: String): ByteArray {
            return ByteArray(32) { 0x42 }
        }

        override fun toHex(bytes: ByteArray): String {
            return bytes.joinToString("") { "%02x".format(it) }
        }

        override fun fromHex(hex: String): ByteArray {
            val result = ByteArray(hex.length / 2)
            for (i in result.indices) {
                result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
            return result
        }
    }

    private class FakeA2ASession(id: String = "test-session") : A2ASession(id = id) {
        var isDisconnected = false
        var lastHandledAnswer: String? = null

        override suspend fun createOffer(): String? {
            return """
                v=0
                o=- 12345 2 IN IP4 127.0.0.1
                s=-
                t=0 0
                a=candidate:1 1 UDP 2130706431 192.168.1.100 50000 typ host
                a=candidate:2 1 UDP 1694498815 203.0.113.1 50000 typ relay
                a=extmap:1 urn:ietf:params:rtp-hdrext:sdes:mid
                a=candidate:3 1 TCP 1518280447 192.168.1.100 9 typ host tcptype active
            """.trimIndent()
        }

        override suspend fun handleOfferAndCreateAnswer(offerSdp: String): String? {
            return """
                v=0
                o=- 54321 2 IN IP4 127.0.0.1
                s=-
                t=0 0
                a=candidate:1 1 UDP 2130706431 192.168.1.101 50000 typ host
            """.trimIndent()
        }

        override suspend fun handleAnswer(answerSdp: String): Boolean {
            lastHandledAnswer = answerSdp
            return true
        }

        override suspend fun disconnect() {
            isDisconnected = true
            _connectionState.value = PeerConnection.PeerConnectionState.CLOSED
        }

        fun emitState(state: PeerConnection.PeerConnectionState) {
            _connectionState.value = state
        }
    }

    private class FakeA2AWebRtcTransport : A2AWebRtcTransport(null, null) {
        var currentSession = FakeA2ASession()

        override suspend fun createSession(sessionId: String): A2ASession {
            currentSession = FakeA2ASession(sessionId)
            return currentSession
        }
    }

    private class FakeAgentDao : AgentDao {
        val agents = mutableListOf<AgentEntity>()
        private val flow = MutableStateFlow<List<AgentEntity>>(emptyList())

        override fun getAllAgents(): Flow<List<AgentEntity>> = flow

        override suspend fun getAgentById(id: String): AgentEntity? {
            return agents.find { it.id == id }
        }

        override suspend fun upsertAgent(agent: AgentEntity) {
            agents.removeAll { it.id == agent.id }
            agents.add(agent)
            flow.value = agents.toList()
        }

        override suspend fun deleteAgent(id: String) {
            agents.removeAll { it.id == id }
            flow.value = agents.toList()
        }

        override suspend fun updateLastUsed(id: String, timestamp: Long) {
            val existing = agents.find { it.id == id } ?: return
            val updated = existing.copy(lastUsedAt = timestamp)
            upsertAgent(updated)
        }

        override suspend fun updateBlockedStatus(id: String, isBlocked: Boolean) {
            val existing = agents.find { it.id == id } ?: return
            val updated = existing.copy(isBlocked = isBlocked)
            upsertAgent(updated)
        }

        override fun isAgentBlocked(id: String): Flow<Boolean?> =
            flow.map { agents -> agents.find { it.id == id }?.isBlocked }
    }

    private class FakeWorldRoomRepository : WorldRoomRepository(null, null) {
        val upsertedRooms = mutableListOf<WorldRoom>()

        override suspend fun upsertRoom(room: WorldRoom) {
            upsertedRooms.removeAll { it.id == room.id }
            upsertedRooms.add(room)
        }

        override suspend fun setDefaultPropsForRoom(room: WorldRoom) {
            // No-op for unit test
        }
    }

    private class FakeSwarmDao : SwarmDao {
        val swarms = mutableListOf<SwarmEntity>()
        private val flow = MutableStateFlow<List<SwarmEntity>>(emptyList())

        override fun getAllSwarms(): Flow<List<SwarmEntity>> = flow

        override fun getActiveSwarms(): Flow<List<SwarmEntity>> = flow

        override suspend fun getSwarmById(id: String): SwarmEntity? = swarms.find { it.id == id }

        override fun observeSwarmById(id: String): Flow<SwarmEntity?> = flowOf(swarms.find { it.id == id })

        override suspend fun insertSwarm(swarm: SwarmEntity) {
            swarms.removeAll { it.id == swarm.id }
            swarms.add(swarm)
            flow.value = swarms.toList()
        }

        override suspend fun updateSwarm(swarm: SwarmEntity) {
            insertSwarm(swarm)
        }

        override suspend fun disbandSwarm(id: String, timestamp: Long) {
            val existing = swarms.find { it.id == id } ?: return
            val updated = existing.copy(isActive = false, disbandedAt = timestamp)
            insertSwarm(updated)
        }
    }

    private class FakeTokenLedgerDao : TokenLedgerDao {
        private val entries = mutableListOf<TokenLedgerEntity>()
        private val flow = MutableStateFlow<List<TokenLedgerEntity>>(emptyList())

        override suspend fun insertLedgerEntry(entry: TokenLedgerEntity) {
            entries.add(entry)
            flow.value = entries.toList()
        }

        override fun getLedgerForAgent(agentId: String): Flow<List<TokenLedgerEntity>> = flowOf(entries.filter { it.agentId == agentId })

        override fun getLedgerForSwarm(swarmId: String): Flow<List<TokenLedgerEntity>> = flowOf(entries.filter { it.swarmId == swarmId })

        override fun getTotalCostSince(startTime: Long): Flow<Double> = flowOf(0.0)

        override fun getTotalTokensForSwarm(swarmId: String): Flow<Int> = flowOf(0)

        override fun getAllLedgerEntries(): Flow<List<TokenLedgerEntity>> = flow

        override fun getGrandTotalTokens(): Flow<Int?> = flowOf(0)

        override fun getGrandTotalCost(): Flow<Double?> = flowOf(0.0)

        override suspend fun pruneOldLedgerEntries(keepCount: Int) {}
    }
}
