package com.ekam.baton.core.network.di

import com.ekam.baton.core.network.BuildConfig
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/**
 * Hilt module that provides the application-level network singletons:
 * - [OkHttpClient] — shared HTTP client with logging and timeouts
 * - [Json] — kotlinx.serialization JSON instance (lenient, ignores unknowns)
 * - [Retrofit] — configured with the serialization converter factory
 *
 * Additional API service bindings should be declared in feature-specific
 * `@Module` classes installed in [SingletonComponent] or appropriate scopes.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: com.ekam.baton.core.network.auth.AuthInterceptor,
        securityInterceptor: com.ekam.baton.core.network.security.SecurityInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(securityInterceptor)
        .addInterceptor(loggingInterceptor)
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    @Provides
    @Singleton
    fun provideSecurityInterceptor(
        securityManager: com.ekam.baton.core.network.security.ConnectionSecurityManager,
        configProvider: com.ekam.baton.core.network.security.AgentSecurityConfigProvider
    ): com.ekam.baton.core.network.security.SecurityInterceptor {
        return com.ekam.baton.core.network.security.SecurityInterceptor(securityManager, configProvider)
    }

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        prettyPrint = false
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json
    ): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.placeholder.baton/") // overridden per-environment via DataStore
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides
    @Singleton
    fun provideMcpApi(retrofit: Retrofit): com.ekam.baton.core.network.api.McpApi {
        return retrofit.create(com.ekam.baton.core.network.api.McpApi::class.java)
    }
    @Provides
    @Singleton
    fun provideMcpTransport(
        okHttpClient: OkHttpClient,
        json: Json
    ): com.ekam.baton.core.network.mcp.McpTransport {
        return com.ekam.baton.core.network.mcp.HttpSseMcpTransport(okHttpClient, json)
    }

    @Provides
    @Singleton
    fun provideMcpConnectionManager(
        transport: com.ekam.baton.core.network.mcp.McpTransport
    ): com.ekam.baton.core.network.mcp.McpConnectionManager {
        return com.ekam.baton.core.network.mcp.McpConnectionManager(transport)
    }

    @Provides
    @Singleton
    fun provideMcpMessageSender(
        connectionManager: com.ekam.baton.core.network.mcp.McpConnectionManager,
        transport: com.ekam.baton.core.network.mcp.McpTransport
    ): com.ekam.baton.core.network.mcp.McpMessageSender {
        return com.ekam.baton.core.network.mcp.McpMessageSender(connectionManager, transport)
    }
}
