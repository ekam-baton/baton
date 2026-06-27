package com.ekam.baton.core.data.preferences

import com.ekam.baton.core.network.BackendUrlProvider
import kotlinx.coroutines.flow.first

class AppPreferencesBackendUrlProvider(
    private val appPreferences: AppPreferences
) : BackendUrlProvider {
    override suspend fun getBackendUrl(): String {
        return appPreferences.backendUrl.first()
    }
}
