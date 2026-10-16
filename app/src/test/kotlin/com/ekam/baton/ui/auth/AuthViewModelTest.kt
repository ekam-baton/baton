package com.ekam.baton.ui.auth

import com.ekam.baton.core.data.preferences.AppPreferences
import com.ekam.baton.core.data.preferences.SessionManager
import com.ekam.baton.core.data.repository.WipeDataManager
import com.ekam.baton.core.network.security.ConnectionSecurityManager
import com.ekam.baton.core.network.tunnel.ConnectionPoolManager
import com.ekam.baton.core.network.tunnel.PoolableConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeAppPreferences : AppPreferences(_context = null) {
        val duressFlow = MutableStateFlow<String?>("9999")
        val appLockPinFlow = MutableStateFlow<String?>("1234")
        val isRegisteredFlow = MutableStateFlow(true)

        override val duressPin: Flow<String?> get() = duressFlow
        override val appLockPin: Flow<String?> get() = appLockPinFlow
        override val isRegistered: Flow<Boolean> get() = isRegisteredFlow

        override suspend fun clearRegistration() {
            isRegisteredFlow.value = false
        }
    }

    private class FakeWipeDataManager : WipeDataManager(database = null) {
        var shredCalled = false
        override suspend fun emergencyCryptographicShred() {
            shredCalled = true
        }
    }

    private class MockPoolableConnection(
        override val id: String,
        override val lastAccessedAt: Long = System.currentTimeMillis()
    ) : PoolableConnection {
        var disconnected = false
        override suspend fun disconnect() {
            disconnected = true
        }
    }

    private lateinit var appPreferences: FakeAppPreferences
    private lateinit var sessionManager: SessionManager
    private lateinit var wipeDataManager: FakeWipeDataManager
    private lateinit var connectionPoolManager: ConnectionPoolManager
    private lateinit var viewModel: AuthViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        appPreferences = FakeAppPreferences()
        sessionManager = SessionManager(appPreferences)
        wipeDataManager = FakeWipeDataManager()
        connectionPoolManager = ConnectionPoolManager()

        viewModel = AuthViewModel(
            sessionManager = sessionManager,
            appPreferences = appPreferences,
            httpClient = OkHttpClient(),
            securityManager = ConnectionSecurityManager(null),
            wipeDataManager = wipeDataManager,
            connectionPoolManager = connectionPoolManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testValidPinSucceeds() = runTest(testDispatcher) {
        var successCalled = false
        var failedCalled = false

        viewModel.verifyPin(
            pin = "1234",
            onSuccess = { successCalled = true },
            onDuress = { fail("Should not trigger duress") },
            onFailed = { failedCalled = true }
        )
        advanceUntilIdle()

        assertTrue(successCalled)
        assertFalse(failedCalled)
        assertTrue(sessionManager.isLoggedIn.value)
    }

    @Test
    fun testIncorrectPinFails() = runTest(testDispatcher) {
        var successCalled = false
        var failedCalled = false

        viewModel.verifyPin(
            pin = "0000",
            onSuccess = { successCalled = true },
            onDuress = { fail("Should not trigger duress") },
            onFailed = { failedCalled = true }
        )
        advanceUntilIdle()

        assertFalse(successCalled)
        assertTrue(failedCalled)
    }

    @Test
    fun testDuressPinTriggersCryptographicShredAndDisconnect() = runTest(testDispatcher) {
        val conn = MockPoolableConnection("conn-test")
        connectionPoolManager.registerConnection(conn)

        var duressCalled = false

        viewModel.verifyPin(
            pin = "9999",
            onSuccess = { fail("Should not call regular success") },
            onDuress = { duressCalled = true },
            onFailed = { fail("Should not call failed") }
        )
        advanceUntilIdle()

        assertTrue("Duress callback must be invoked", duressCalled)
        assertTrue("Active network connections must be terminated", conn.disconnected)
        assertTrue("Cryptographic shred must be invoked", wipeDataManager.shredCalled)
        assertFalse("Registration must be cleared", appPreferences.isRegisteredFlow.value)
    }
}
