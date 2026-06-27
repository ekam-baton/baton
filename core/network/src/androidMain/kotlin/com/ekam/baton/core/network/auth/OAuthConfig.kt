package com.ekam.baton.core.network.auth

import kotlinx.serialization.Serializable

@Serializable
data class OAuthConfig(
    val clientId: String,
    val authorizationUrl: String,
    val tokenUrl: String,
    val scopes: List<String>,
    val redirectUri: String = "com.ekam.baton://oauth/callback"
)
