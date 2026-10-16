package com.ekam.baton.core.network.connectivity

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class NetworkConnectivityMonitorTest {

    @Test
    fun testDefaultStateIsOnline() = runTest {
        val monitor = NetworkConnectivityMonitor(context = null)
        assertTrue(monitor.isConnected.first())
    }

    @Test
    fun testOfflineAndOnlineTransitions() = runTest {
        val monitor = NetworkConnectivityMonitor(context = null)

        // Simulate entering elevator / dead zone
        monitor.setConnectedForTesting(false)
        assertFalse(monitor.isConnected.first())

        // Simulate exiting elevator / regaining 5G
        monitor.setConnectedForTesting(true)
        assertTrue(monitor.isConnected.first())
    }
}
