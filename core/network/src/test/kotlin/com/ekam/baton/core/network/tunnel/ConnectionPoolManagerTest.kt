package com.ekam.baton.core.network.tunnel

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.util.UUID

class DummyConnection(override val id: String) : PoolableConnection {
    var disconnected = false
    private var _lastAccessedAt: Long = System.currentTimeMillis()
    
    override val lastAccessedAt: Long
        get() = _lastAccessedAt
        
    fun setLastAccessedAt(time: Long) {
        _lastAccessedAt = time
    }

    override suspend fun disconnect() {
        disconnected = true
    }
}

class ConnectionPoolManagerTest {

    private lateinit var manager: ConnectionPoolManager

    @Before
    fun setup() {
        manager = ConnectionPoolManager()
    }

    @Test
    fun `registerConnection respects MAX_ACTIVE_CONNECTIONS and evicts LRU`() = runTest {
        // Register 15 connections
        val connections = (1..15).map { 
            DummyConnection("conn-$it").apply {
                setLastAccessedAt(1000L * it) // conn-1 is the oldest
            }
        }
        
        connections.forEach { manager.registerConnection(it) }
        assertEquals(15, manager.getActiveConnectionCount())
        assertFalse(connections[0].disconnected)

        // Register 16th connection, should evict conn-1
        val newConn = DummyConnection("conn-16").apply {
            setLastAccessedAt(16000L)
        }
        manager.registerConnection(newConn)
        
        assertEquals(15, manager.getActiveConnectionCount())
        assertTrue(connections[0].disconnected) // The oldest should be disconnected
        assertFalse(connections[1].disconnected)
    }

    @Test
    fun `evictIdleConnections removes connections exceeding maxIdleTimeMs`() = runTest {
        val now = System.currentTimeMillis()
        
        val idleConn = DummyConnection("idle").apply { setLastAccessedAt(now - 10000) }
        val activeConn = DummyConnection("active").apply { setLastAccessedAt(now - 1000) }
        
        manager.registerConnection(idleConn)
        manager.registerConnection(activeConn)
        
        assertEquals(2, manager.getActiveConnectionCount())
        
        // Evict older than 5000ms
        manager.evictIdleConnections(5000)
        
        assertEquals(1, manager.getActiveConnectionCount())
        assertTrue(idleConn.disconnected)
        assertFalse(activeConn.disconnected)
    }
}
