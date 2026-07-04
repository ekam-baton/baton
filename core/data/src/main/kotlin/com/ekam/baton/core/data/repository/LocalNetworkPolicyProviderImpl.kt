package com.ekam.baton.core.data.repository

import com.ekam.baton.core.data.preferences.AppPreferences
import com.ekam.baton.core.network.security.LocalNetworkPolicyProvider
import kotlinx.coroutines.flow.first

class LocalNetworkPolicyProviderImpl(
    private val appPreferences: AppPreferences
) : LocalNetworkPolicyProvider {
    override suspend fun isLocalNetworkAllowed(): Boolean {
        return appPreferences.allowLocalNetworkAgents.first()
    }
}
