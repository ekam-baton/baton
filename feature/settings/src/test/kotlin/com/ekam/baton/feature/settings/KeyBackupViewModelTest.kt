package com.ekam.baton.feature.settings

import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.db.dao.AuditDao
import com.ekam.baton.core.data.db.dao.MemoryDao
import com.ekam.baton.core.data.db.entity.AgentEntity
import com.ekam.baton.core.data.db.entity.AuditLogEntity
import com.ekam.baton.core.data.db.entity.MemoryEntity
import com.ekam.baton.core.data.model.Agent
import com.ekam.baton.core.data.model.Memory
import com.ekam.baton.core.data.preferences.AppPreferences
import com.ekam.baton.core.data.repository.AgentRepository
import com.ekam.baton.core.data.repository.MemoryRepository
import com.ekam.baton.core.network.repository.VaultRepository
import com.ekam.baton.core.network.security.ConnectionSecurityManager
import com.ekam.baton.core.network.security.IdentityKeyManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class KeyBackupViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeAuditDao(val logs: MutableList<AuditLogEntity> = mutableListOf()) : AuditDao {
        override suspend fun insertAuditLog(log: AuditLogEntity) {
            logs.add(log)
        }
        override fun getAllAuditLogs(): Flow<List<AuditLogEntity>> = flowOf(logs.toList())
        override suspend fun getLastAuditLog(): AuditLogEntity? = logs.lastOrNull()
        override suspend fun getAllAuditLogsSync(): List<AuditLogEntity> = logs.toList()
        override suspend fun pruneOldLogs(keepLastN: Int) {
            if (logs.size > keepLastN) {
                val kept = logs.takeLast(keepLastN)
                logs.clear()
                logs.addAll(kept)
            }
        }
    }

    private class FakeAgentDao(val agents: MutableList<AgentEntity> = mutableListOf()) : AgentDao {
        override fun getAllAgents(): Flow<List<AgentEntity>> = flowOf(agents.toList())
        override suspend fun getAgentById(id: String): AgentEntity? = agents.find { it.id == id }
        override suspend fun upsertAgent(agent: AgentEntity) {
            agents.removeAll { it.id == agent.id }
            agents.add(agent)
        }
        override suspend fun deleteAgent(id: String) {
            agents.removeAll { it.id == id }
        }
        override suspend fun updateLastUsed(id: String, timestamp: Long) {}
        override suspend fun updateBlockedStatus(id: String, isBlocked: Boolean) {}
        override fun isAgentBlocked(id: String): Flow<Boolean?> = flowOf(false)
    }

    private class FakeMemoryDao(val memories: MutableList<MemoryEntity> = mutableListOf()) : MemoryDao {
        override fun getAllMemories(): Flow<List<MemoryEntity>> = flowOf(memories.toList())
        override fun getMemoriesByLayer(layer: String): Flow<List<MemoryEntity>> =
            flowOf(memories.filter { it.layer == layer })
        override fun getMemoriesForAgent(agentId: String): Flow<List<MemoryEntity>> =
            flowOf(memories.filter { it.agentId == agentId || it.agentId == null })
        override fun searchMemories(query: String): Flow<List<MemoryEntity>> =
            flowOf(memories.filter { it.title.contains(query) || it.content.contains(query) })
        override suspend fun upsertMemory(memory: MemoryEntity) {
            memories.removeAll { it.id == memory.id }
            memories.add(memory)
        }
        override suspend fun deleteMemory(id: String) {
            memories.removeAll { it.id == id }
        }
        override suspend fun toggleMemoryActive(id: String, isActive: Boolean) {
            val idx = memories.indexOfFirst { it.id == id }
            if (idx != -1) {
                memories[idx] = memories[idx].copy(isActive = isActive)
            }
        }
        override suspend fun getTopSemanticMemories(agentId: String): List<MemoryEntity> = emptyList()
        override suspend fun getRecentEpisodicMemories(agentId: String, cutoffTime: Long): List<MemoryEntity> = emptyList()
        override suspend fun getWorkingMemories(conversationId: String): List<MemoryEntity> = emptyList()
        override suspend fun updateLastAccessedTime(ids: List<String>, time: Long) {}
        override suspend fun clearWorkingMemoriesForConversation(conversationId: String) {}
        override suspend fun clearAllMemories() { memories.clear() }
        override suspend fun deleteMemoriesOlderThan(cutoffTime: Long) {
            memories.removeAll { it.createdAt < cutoffTime }
        }
        override suspend fun deleteMemoriesForAgent(agentId: String) {
            memories.removeAll { it.agentId == agentId }
        }
    }

    private class FakeVaultRepository : VaultRepository() {
        var uploadResult: Result<Unit> = Result.success(Unit)
        var downloadResult: Result<String> = Result.success("")

        var lastUploadedEndpointUrl: String? = null
        var lastUploadedAuthHeader: String? = null
        var lastUploadedClientId: String? = null
        var lastUploadedVaultData: String? = null

        override suspend fun uploadVault(
            endpointUrl: String,
            authHeader: String?,
            clientId: String,
            vaultData: String
        ): Result<Unit> {
            lastUploadedEndpointUrl = endpointUrl
            lastUploadedAuthHeader = authHeader
            lastUploadedClientId = clientId
            lastUploadedVaultData = vaultData
            return uploadResult
        }

        override suspend fun downloadVault(
            endpointUrl: String,
            authHeader: String?,
            clientId: String
        ): Result<String> {
            return downloadResult
        }
    }

    private class FakeAppPreferences : AppPreferences(_context = null) {
        val syncActiveFlow = MutableStateFlow(true)
        val syncMemoriesFlow = MutableStateFlow(true)
        val syncKeysFlow = MutableStateFlow(false)
        val lastSyncFlow = MutableStateFlow(0L)
        val backendUrlFlow = MutableStateFlow("https://api.baton.com/")
        val jwtSecretFlow = MutableStateFlow("test-jwt-secret")

        override val backendUrl: Flow<String> get() = backendUrlFlow
        override val jwtSecret: Flow<String> get() = jwtSecretFlow
        override val cloudSyncActive: Flow<Boolean> get() = syncActiveFlow
        override val cloudSyncMemories: Flow<Boolean> get() = syncMemoriesFlow
        override val cloudSyncKeys: Flow<Boolean> get() = syncKeysFlow
        override val lastSyncTimestamp: Flow<Long> get() = lastSyncFlow

        override suspend fun setCloudSyncActive(active: Boolean) {
            syncActiveFlow.value = active
        }

        override suspend fun setCloudSyncMemories(sync: Boolean) {
            syncMemoriesFlow.value = sync
        }

        override suspend fun setCloudSyncKeys(sync: Boolean) {
            syncKeysFlow.value = sync
        }

        override suspend fun setLastSyncTimestamp(timestamp: Long) {
            lastSyncFlow.value = timestamp
        }
    }

    private lateinit var agentDao: FakeAgentDao
    private lateinit var memoryDao: FakeMemoryDao
    private lateinit var auditDao: FakeAuditDao
    private lateinit var agentRepository: AgentRepository
    private lateinit var memoryRepository: MemoryRepository
    private lateinit var vaultRepository: FakeVaultRepository
    private lateinit var appPreferences: FakeAppPreferences
    private lateinit var connectionSecurityManager: ConnectionSecurityManager
    private lateinit var viewModel: KeyBackupViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        IdentityKeyManager.testPublicKeyHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"

        agentDao = FakeAgentDao()
        memoryDao = FakeMemoryDao()
        auditDao = FakeAuditDao()
        agentRepository = AgentRepository(agentDao)
        memoryRepository = MemoryRepository(memoryDao, auditDao)
        vaultRepository = FakeVaultRepository()
        appPreferences = FakeAppPreferences()
        connectionSecurityManager = ConnectionSecurityManager(null)

        viewModel = KeyBackupViewModel(
            agentRepository = agentRepository,
            vaultRepository = vaultRepository,
            connectionSecurityManager = connectionSecurityManager,
            appPreferences = appPreferences,
            memoryRepository = memoryRepository,
            auditDao = auditDao,
            defaultDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        IdentityKeyManager.testPublicKeyHex = null
        Dispatchers.resetMain()
    }

    @Test
    fun `test initial state reflects preferences and Idle`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(BackupUiState.Idle, viewModel.uiState.value)
        assertTrue(viewModel.cloudSyncActive.value)
        assertTrue(viewModel.cloudSyncMemories.value)
        assertFalse(viewModel.cloudSyncKeys.value)
        assertEquals(0L, viewModel.lastSyncTimestamp.value)
    }

    @Test
    fun `test updating sync preferences propagates to StateFlows`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.setCloudSyncActive(false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.cloudSyncActive.value)

        viewModel.setCloudSyncMemories(false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.cloudSyncMemories.value)

        viewModel.setCloudSyncKeys(true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.cloudSyncKeys.value)
    }

    @Test
    fun `test exportVault packages agents and memories into encrypted envelope and uploads`() = runTest {
        agentDao.upsertAgent(
            AgentEntity(
                id = "agent-1",
                name = "Cyber Sentinel",
                description = "Security Guard",
                mcpEndpointUrl = "https://agent1.local",
                authType = "BEARER",
                authConfig = "{}",
                colorAccent = "#306EE8",
                securityMode = "ENFORCE",
                securityConfig = "{}"
            )
        )
        memoryDao.upsertMemory(
            MemoryEntity(
                id = "mem-1",
                layer = "SEMANTIC",
                agentId = "agent-1",
                conversationId = null,
                title = "Primary Directive",
                content = "Always verify safety number.",
                createdAt = 1000L,
                lastAccessedAt = 1000L,
                relevanceScore = 0.95f,
                tags = "[]",
                isActive = true
            )
        )

        testDispatcher.scheduler.advanceUntilIdle()

        val passphrase = "SecureMasterPassword123!"
        viewModel.exportVault(passphrase)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is BackupUiState.Success)
        val successMsg = (viewModel.uiState.value as BackupUiState.Success).message
        assertTrue(successMsg.contains("1 agents"))
        assertTrue(successMsg.contains("1 memories"))

        assertNotNull(vaultRepository.lastUploadedVaultData)
        assertEquals("Bearer test-jwt-secret", vaultRepository.lastUploadedAuthHeader)
        assertEquals("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef", vaultRepository.lastUploadedClientId)
        assertTrue(appPreferences.lastSyncTimestamp.first() > 0L)

        // Verify decrypted payload matches Version 2 envelope format
        val decryptedBytes = connectionSecurityManager.decryptFromBackup(
            vaultRepository.lastUploadedVaultData!!,
            passphrase.toCharArray()
        )
        val decryptedJson = JSONObject(String(decryptedBytes, Charsets.UTF_8))
        assertEquals(2, decryptedJson.getInt("version"))
        val agentsArr = decryptedJson.getJSONArray("agents")
        assertEquals(1, agentsArr.length())
        assertEquals("Cyber Sentinel", agentsArr.getJSONObject(0).getString("name"))

        val memoriesArr = decryptedJson.getJSONArray("memories")
        assertEquals(1, memoriesArr.length())
        assertEquals("Primary Directive", memoriesArr.getJSONObject(0).getString("title"))

        // Verify cryptographic audit log was recorded
        val auditLog = auditDao.getLastAuditLog()
        assertNotNull(auditLog)
        assertEquals("CryptographicVault", auditLog?.entityName)
        assertEquals("EXPORT", auditLog?.action)
        assertTrue(auditLog?.payloadJson?.contains("EXPORT_VAULT") == true)
    }

    @Test
    fun `test exportVault handles upload error`() = runTest {
        vaultRepository.uploadResult = Result.failure(RuntimeException("Cloud storage full"))

        viewModel.exportVault("testPassphrase")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is BackupUiState.Error)
        assertEquals("Cloud storage full", (viewModel.uiState.value as BackupUiState.Error).message)
    }

    @Test
    fun `test importVault restores version 2 envelope agents and memories`() = runTest {
        val passphrase = "RestorePassphrase456!"
        val envelope = JSONObject().apply {
            put("version", 2)
            put("timestamp", 123456789L)
            put("agents", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "agent-restored")
                    put("name", "Restored Vanguard")
                    put("description", "Imported agent")
                    put("mcpEndpointUrl", "https://vanguard.ai")
                    put("authType", "NONE")
                    put("authConfig", "{}")
                    put("colorAccent", "#00D2FF")
                    put("securityMode", "AUDIT")
                    put("securityConfig", "{}")
                })
            })
            put("memories", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "mem-restored")
                    put("agentId", "agent-restored")
                    put("title", "Historical Encounters")
                    put("content", "Restored long-term memory")
                    put("layer", "EPISODIC")
                    put("relevanceScore", 0.88)
                    put("isActive", true)
                })
            })
        }

        val encryptedVaultString = connectionSecurityManager.encryptForBackup(
            envelope.toString().toByteArray(Charsets.UTF_8),
            passphrase.toCharArray()
        )
        vaultRepository.downloadResult = Result.success(encryptedVaultString)

        viewModel.importVault(passphrase)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is BackupUiState.Success)
        val successMsg = (viewModel.uiState.value as BackupUiState.Success).message
        assertTrue(successMsg.contains("1 agent(s)"))
        assertTrue(successMsg.contains("1 memory items"))

        // Check restored agent in repository
        val restoredAgent = agentDao.getAgentById("agent-restored")
        assertNotNull(restoredAgent)
        assertEquals("Restored Vanguard", restoredAgent?.name)

        // Check restored memory in repository
        val restoredMemories = memoryDao.getAllMemories().first()
        assertEquals(1, restoredMemories.size)
        assertEquals("Historical Encounters", restoredMemories[0].title)

        // Verify cryptographic audit log
        val auditLog = auditDao.getLastAuditLog()
        assertNotNull(auditLog)
        assertEquals("CryptographicVault", auditLog?.entityName)
        assertEquals("IMPORT", auditLog?.action)
    }

    @Test
    fun `test importVault restores legacy version 1 array`() = runTest {
        val passphrase = "LegacyPassphrase789!"
        val legacyArray = JSONArray().apply {
            put(JSONObject().apply {
                put("id", "agent-legacy")
                put("name", "Legacy Sentinel")
                put("description", "V1 Agent format")
                put("mcpEndpointUrl", "https://legacy.ai")
                put("authType", "NONE")
                put("authConfig", "{}")
                put("colorAccent", "#10B981")
                put("securityMode", "NONE")
                put("securityConfig", "{}")
            })
        }

        val encryptedVaultString = connectionSecurityManager.encryptForBackup(
            legacyArray.toString().toByteArray(Charsets.UTF_8),
            passphrase.toCharArray()
        )
        vaultRepository.downloadResult = Result.success(encryptedVaultString)

        viewModel.importVault(passphrase)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is BackupUiState.Success)
        val restoredAgent = agentDao.getAgentById("agent-legacy")
        assertNotNull(restoredAgent)
        assertEquals("Legacy Sentinel", restoredAgent?.name)
    }

    @Test
    fun `test importVault with wrong passphrase fails decryption`() = runTest {
        val correctPass = "CorrectPassword123!"
        val wrongPass = "WrongPassword999!"

        val envelope = JSONObject().apply {
            put("version", 2)
            put("agents", JSONArray())
            put("memories", JSONArray())
        }

        val encryptedVaultString = connectionSecurityManager.encryptForBackup(
            envelope.toString().toByteArray(Charsets.UTF_8),
            correctPass.toCharArray()
        )
        vaultRepository.downloadResult = Result.success(encryptedVaultString)

        viewModel.importVault(wrongPass)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uiState.value is BackupUiState.Error)
    }

    @Test
    fun `test clearStatus resets uiState to Idle`() = runTest {
        vaultRepository.uploadResult = Result.failure(RuntimeException("Fail"))
        viewModel.exportVault("test")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.value is BackupUiState.Error)

        viewModel.clearStatus()
        assertEquals(BackupUiState.Idle, viewModel.uiState.value)
    }
}
