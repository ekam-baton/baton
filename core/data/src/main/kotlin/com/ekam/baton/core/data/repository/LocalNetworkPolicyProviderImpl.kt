package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.db.dao.AgentDao
import com.ekam.baton.core.data.preferences.AppPreferences
import com.ekam.baton.core.network.security.LocalNetworkPolicyProvider
import kotlinx.coroutines.flow.first
import java.util.Collections

class LocalNetworkPolicyProviderImpl(
    private val appPreferences: AppPreferences,
    private val agentDao: AgentDao
) : LocalNetworkPolicyProvider {
    // We temporarily allow hosts that are currently being paired by caching them in memory.
    // Thread-safe because it's accessed concurrently from OkHttp's DNS resolution threads.
    private val pairingHosts = Collections.synchronizedSet(mutableSetOf<String>())

    override fun allowDuringPairing(hostname: String) {
        pairingHosts.add(hostname)
    }

    override fun removePairingHost(hostname: String) {
        pairingHosts.remove(hostname)
    }

    override suspend fun isLocalNetworkAllowed(hostname: String): Boolean {
        // If the global toggle is off, no local network access is allowed whatsoever.
        if (!appPreferences.allowLocalNetworkAgents.first()) {
            return false
        }
        
        // If this host is currently being paired, allow the initial validation probe.
        if (pairingHosts.contains(hostname)) {
            return true
        }

        // Only allow if the hostname explicitly belongs to a trusted, already paired agent.
        val agents = agentDao.getAllAgents().first()
        for (agent in agents) {
            val agentHost = try {
                java.net.URL(agent.mcpEndpointUrl).host
            } catch (e: Exception) {
                null
            }
            if (agentHost == hostname) {
                return true
            }
        }

        // If it's a local/private IP but not paired, block it to prevent SSRF.
        return false
    }
}
