package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.preferences.AppPreferences
import com.ekam.baton.core.network.security.LocalNetworkPolicyProvider
import kotlinx.coroutines.flow.first

class LocalNetworkPolicyProviderImpl(
    private val appPreferences: AppPreferences
) : LocalNetworkPolicyProvider {
    override suspend fun isLocalNetworkAllowed(hostname: String): Boolean {
        // Here we could check against a specific list of paired hosts.
        // For now, we still read the global toggle, but ideally this would
        // query the database to see if hostname belongs to a trusted local agent.
        return appPreferences.allowLocalNetworkAgents.first()
    }
}
