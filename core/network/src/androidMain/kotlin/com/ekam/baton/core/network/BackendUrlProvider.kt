package com.ekam.baton.core.network

interface BackendUrlProvider {
    suspend fun getBackendUrl(): String
}
