package com.ekam.baton.feature.chat

import androidx.lifecycle.SavedStateHandle
import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.dao.SwarmDao
import com.ekam.baton.core.data.db.dao.TokenLedgerDao
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.db.entity.SwarmEntity
import com.ekam.baton.core.data.db.entity.TokenLedgerEntity
import com.ekam.baton.core.data.model.AgentRole
import com.ekam.baton.core.data.model.WorldRoom
import com.ekam.baton.core.data.model.WorldRoomProp
import com.ekam.baton.core.data.repository.DefaultPropLayouts
import com.ekam.baton.core.data.repository.WorldRoomRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SwarmTopologyViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val fakeSwarm = SwarmEntity(
        id = "swarm-xyz",
        name = "Deep Abyss Cluster",
        leaderAgentId = "agent-leader",
        workerAgentIds = "[\"agent-coder\",\"agent-security\"]",
        topologyType = "P2P",
        intentContext = "Continuous automated auditing and peer coding",
        isActive = true
    )

    private val fakeLeader = AgentEntity(
        id = "agent-leader",
        name = "Orchestrator One",
        description = "Cluster leader",
        mcpEndpointUrl = "https://leader.cluster",
        authType = "NONE",
        authConfig = "{}",
        colorAccent = "#306EE8",
        role = AgentRole.COORDINATOR.name
    )

    private val fakeCoder = AgentEntity(
        id = "agent-coder",
        name = "Code Architect",
        description = "Core developer",
        mcpEndpointUrl = "https://coder.cluster",
        authType = "NONE",
        authConfig = "{}",
        colorAccent = "#FFA040",
        role = AgentRole.CODER.name
    )

    private val fakeSecurity = AgentEntity(
        id = "agent-security",
        name = "Sentinel Node",
        description = "Security scanner",
        mcpEndpointUrl = "https://sec.cluster",
        authType = "NONE",
        authConfig = "{}",
        colorAccent = "#FF453A",
        role = AgentRole.SECURITY.name
    )

    private lateinit var fakeSwarmDao: FakeSwarmDao
    private lateinit var fakeAgentDao: FakeAgentDao
    private lateinit var fakeTokenLedgerDao: FakeTokenLedgerDao
    private lateinit var fakeWorldRoomRepository: FakeWorldRoomRepository
    private lateinit var viewModel: SwarmTopologyViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        fakeSwarmDao = FakeSwarmDao(mutableMapOf(fakeSwarm.id to fakeSwarm))
        fakeAgentDao = FakeAgentDao(listOf(fakeLeader, fakeCoder, fakeSecurity))
        fakeTokenLedgerDao = FakeTokenLedgerDao().apply {
            entries.add(
                TokenLedgerEntity(
                    id = "entry-1",
                    agentId = fakeLeader.id,
                    swarmId = fakeSwarm.id,
                    modelName = "claude-3-5-sonnet",
                    promptTokens = 100,
                    completionTokens = 50,
                    totalTokens = 150,
                    estimatedCostUsd = 0.00105,
                    burnRateTks = 45.0,
                    timestamp = System.currentTimeMillis(),
                    actionContext = "Topology handshake"
                )
            )
            entries.add(
                TokenLedgerEntity(
                    id = "entry-2",
                    agentId = fakeCoder.id,
                    swarmId = fakeSwarm.id,
                    modelName = "gemini-1.5-pro",
                    promptTokens = 200,
                    completionTokens = 80,
                    totalTokens = 280,
                    estimatedCostUsd = 0.0012,
                    burnRateTks = 52.0,
                    timestamp = System.currentTimeMillis() + 10,
                    actionContext = "Code compilation"
                )
            )
        }
        fakeWorldRoomRepository = FakeWorldRoomRepository()

        val savedStateHandle = SavedStateHandle(mapOf("swarmId" to fakeSwarm.id))
        viewModel = SwarmTopologyViewModel(
            swarmDao = fakeSwarmDao,
            agentDao = fakeAgentDao,
            tokenLedgerDao = fakeTokenLedgerDao,
            worldRoomRepository = fakeWorldRoomRepository,
            savedStateHandle = savedStateHandle
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testInitialTelemetryAndMemberLoading() = runTest(testDispatcher) {
        advanceUntilIdle()

        val loadedSwarm = viewModel.swarm.value
        assertNotNull(loadedSwarm)
        assertEquals("Deep Abyss Cluster", loadedSwarm!!.name)
        assertEquals("P2P", loadedSwarm.topologyType)

        val members = viewModel.memberAgents.value
        assertEquals(3, members.size)
        assertTrue(members.any { it.id == fakeLeader.id })
        assertTrue(members.any { it.id == fakeCoder.id })
        assertTrue(members.any { it.id == fakeSecurity.id })

        val totalTk = viewModel.totalTokens.value
        assertEquals(430, totalTk)

        val totalCost = viewModel.totalCostUsd.value
        assertEquals(0.00225, totalCost, 0.00001)

        val ledgerEntries = viewModel.tokenLedgerEntries.value
        assertEquals(2, ledgerEntries.size)
    }

    @Test
    fun testDefaultSelectionAndWorldRoomInitialization() = runTest(testDispatcher) {
        advanceUntilIdle()

        // Defaults to leader
        assertEquals(fakeLeader.id, viewModel.selectedAgentId.value)
        val selected = viewModel.selectedAgent.value
        assertNotNull(selected)
        assertEquals(fakeLeader.name, selected!!.name)

        // Room was created in repository
        val room = viewModel.currentRoom.value
        assertNotNull(room)
        assertEquals(fakeLeader.id, room!!.agentId)
        assertEquals(AgentRole.COORDINATOR, room.role)

        // Props were created matching role
        val props = viewModel.currentProps.value
        assertTrue(props.isNotEmpty())
        assertTrue(props.any { it.propType == "NETWORK_GLOBE" || it.propType == "CONFERENCE_TABLE" })
    }

    @Test
    fun testSelectDifferentAgentResolvesRoleRoomAndProps() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.selectAgent(fakeCoder.id)
        advanceUntilIdle()

        assertEquals(fakeCoder.id, viewModel.selectedAgentId.value)
        val selected = viewModel.selectedAgent.value
        assertEquals("Code Architect", selected!!.name)

        val room = viewModel.currentRoom.value
        assertNotNull(room)
        assertEquals(fakeCoder.id, room!!.agentId)
        assertEquals(AgentRole.CODER, room.role)

        val props = viewModel.currentProps.value
        assertTrue(props.isNotEmpty())
        assertTrue(props.any { it.propType == "DUAL_MONITOR_DESK" || it.propType == "SERVER_TOWER" })
    }

    @Test
    fun testUpdateTopologyType() = runTest(testDispatcher) {
        advanceUntilIdle()

        viewModel.updateTopology("MESH")
        advanceUntilIdle()

        val updatedSwarm = fakeSwarmDao.swarms[fakeSwarm.id]
        assertNotNull(updatedSwarm)
        assertEquals("MESH", updatedSwarm!!.topologyType)
    }

    @Test
    fun testDisbandSwarm() = runTest(testDispatcher) {
        advanceUntilIdle()

        var disbandedCallbackCalled = false
        viewModel.disbandSwarm {
            disbandedCallbackCalled = true
        }
        advanceUntilIdle()

        assertTrue(disbandedCallbackCalled)
        val updated = fakeSwarmDao.swarms[fakeSwarm.id]
        assertFalse(updated!!.isActive)
    }

    // ==========================================
    // FAKES
    // ==========================================

    private class FakeSwarmDao(val swarms: MutableMap<String, SwarmEntity>) : SwarmDao {
        private val flow = MutableStateFlow<List<SwarmEntity>>(swarms.values.toList())

        override fun getAllSwarms(): Flow<List<SwarmEntity>> = flow
        override fun getActiveSwarms(): Flow<List<SwarmEntity>> = flowOf(swarms.values.filter { it.isActive })
        override suspend fun getSwarmById(id: String): SwarmEntity? = swarms[id]
        override fun observeSwarmById(id: String): Flow<SwarmEntity?> = flowOf(swarms[id])
        override suspend fun insertSwarm(swarm: SwarmEntity) {
            swarms[swarm.id] = swarm
            flow.value = swarms.values.toList()
        }
        override suspend fun updateSwarm(swarm: SwarmEntity) {
            swarms[swarm.id] = swarm
            flow.value = swarms.values.toList()
        }
        override suspend fun disbandSwarm(id: String, timestamp: Long) {
            swarms[id]?.let {
                swarms[id] = it.copy(isActive = false, disbandedAt = timestamp)
                flow.value = swarms.values.toList()
            }
        }
    }

    private class FakeAgentDao(val agents: List<AgentEntity>) : AgentDao {
        override fun getAllAgents(): Flow<List<AgentEntity>> = flowOf(agents)
        override suspend fun getAgentById(id: String): AgentEntity? = agents.find { it.id == id }
        override suspend fun upsertAgent(agent: AgentEntity) {}
        override suspend fun deleteAgent(id: String) {}
        override suspend fun updateLastUsed(id: String, timestamp: Long) {}
    }

    private class FakeTokenLedgerDao : TokenLedgerDao {
        val entries = mutableListOf<TokenLedgerEntity>()
        private val flow = MutableStateFlow<List<TokenLedgerEntity>>(entries)

        override suspend fun insertLedgerEntry(entry: TokenLedgerEntity) {
            entries.add(entry)
            flow.value = entries.toList()
        }

        override fun getLedgerForAgent(agentId: String): Flow<List<TokenLedgerEntity>> =
            flowOf(entries.filter { it.agentId == agentId })

        override fun getLedgerForSwarm(swarmId: String): Flow<List<TokenLedgerEntity>> =
            flowOf(entries.filter { it.swarmId == swarmId })

        override fun getTotalCostSince(startTime: Long): Flow<Double> = flowOf(0.0)

        override fun getTotalTokensForSwarm(swarmId: String): Flow<Int> =
            flowOf(entries.filter { it.swarmId == swarmId }.sumOf { it.totalTokens })

        override fun getAllLedgerEntries(): Flow<List<TokenLedgerEntity>> = flow
        override fun getGrandTotalTokens(): Flow<Int?> = flowOf(entries.sumOf { it.totalTokens })
        override fun getGrandTotalCost(): Flow<Double?> = flowOf(entries.sumOf { it.estimatedCostUsd })
        override suspend fun pruneOldLedgerEntries(keepCount: Int) {}
    }

    private class FakeWorldRoomRepository : WorldRoomRepository(null, null) {
        val rooms = mutableMapOf<String, WorldRoom>()
        val props = mutableMapOf<String, MutableList<WorldRoomProp>>()

        override suspend fun getRoomForAgent(agentId: String): WorldRoom? {
            return rooms.values.find { it.agentId == agentId }
        }

        override suspend fun upsertRoom(room: WorldRoom) {
            rooms[room.id] = room
        }

        override suspend fun setDefaultPropsForRoom(room: WorldRoom) {
            val list = props.getOrPut(room.id) { mutableListOf() }
            list.clear()
            DefaultPropLayouts.forRole(room.role).forEachIndexed { index, layout ->
                list.add(
                    WorldRoomProp(
                        id = UUID.randomUUID().toString(),
                        roomId = room.id,
                        propType = layout.type,
                        gridX = layout.x,
                        gridY = layout.y,
                        gridZ = layout.z,
                        animSeed = index * 137
                    )
                )
            }
        }

        override suspend fun getPropsForRoom(roomId: String): List<WorldRoomProp> {
            return props[roomId] ?: emptyList()
        }
    }
}
