package com.ekam.baton.core.network.di

import com.ekam.baton.core.network.BuildConfig
import com.ekam.baton.core.network.auth.AuthInterceptor
import com.ekam.baton.core.network.security.AgentSecurityConfigProvider
import com.ekam.baton.core.network.security.ConnectionSecurityManager
import com.ekam.baton.core.network.security.SecurityInterceptor
import com.ekam.baton.core.network.mcp.HttpSseMcpTransport
import com.ekam.baton.core.network.mcp.McpConnectionManager
import com.ekam.baton.core.network.mcp.McpMessageSender
import com.ekam.baton.core.network.mcp.McpTransport
import com.ekam.baton.core.network.mcp.ToolAuthorizationManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import org.koin.dsl.module
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

val networkModule = module {

    single {
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    single {
        OkHttpClient.Builder()
            .addInterceptor(get<SecurityInterceptor>())
            .addInterceptor(get<HttpLoggingInterceptor>())
            .addInterceptor(get<AuthInterceptor>())
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    single {
        SecurityInterceptor(get<ConnectionSecurityManager>(), get<AgentSecurityConfigProvider>())
    }

    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            encodeDefaults = true
            prettyPrint = false
            coerceInputValues = true
        }
    }

    // Replace Retrofit with Ktor HttpClient
    single {
        val urlProvider = get<com.ekam.baton.core.network.BackendUrlProvider>()
        val backendUrlString = runBlocking { urlProvider.getBackendUrl() }
        
        HttpClient(OkHttp) {
            engine {
                preconfigured = get<OkHttpClient>()
            }
            install(ContentNegotiation) {
                json(get<Json>())
            }
            defaultRequest {
                // Read from AppPreferences, fallback is 127.0.0.1:8080 if not set
                url(backendUrlString)
            }
        }
    }

    single<McpTransport> { HttpSseMcpTransport(get(), get()) }
    single { McpConnectionManager(get()) }
    single { McpMessageSender(get(), get(), get()) }
    single { ToolAuthorizationManager() }
    single { com.ekam.baton.core.network.security.ConnectionSecurityManager(get<android.content.Context>()) }
    single { com.ekam.baton.core.network.auth.TokenStore(get<android.content.Context>()) }
    single { com.ekam.baton.core.network.auth.OAuthFlowManager(get(), get()) }
    single { com.ekam.baton.core.network.auth.AuthInterceptor(get(), get()) }
    single { com.ekam.baton.core.network.tunnel.TunnelEndpointValidator(get()) }
    single { com.ekam.baton.core.network.tunnel.ConnectionPoolManager() }
    single { com.ekam.baton.core.network.tunnel.A2AWebRtcTransport(get<android.content.Context>(), get()) }
}

