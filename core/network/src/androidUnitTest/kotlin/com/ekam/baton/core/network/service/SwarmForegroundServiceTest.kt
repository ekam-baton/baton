package com.ekam.baton.core.network.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwarmForegroundServiceTest {

    @Test
    fun `action constants are formatted correctly`() {
        assertEquals("com.ekam.baton.action.START_SWARM", SwarmForegroundService.ACTION_START_SWARM)
        assertEquals("com.ekam.baton.action.STOP_SWARM", SwarmForegroundService.ACTION_STOP_SWARM)
    }

    @Test
    fun `extra keys are properly defined`() {
        assertEquals("extra_swarm_id", SwarmForegroundService.EXTRA_SWARM_ID)
        assertEquals("extra_swarm_name", SwarmForegroundService.EXTRA_SWARM_NAME)
        assertEquals("extra_active_nodes_count", SwarmForegroundService.EXTRA_ACTIVE_NODES_COUNT)
    }

    @Test
    fun `start and stop actions are distinct`() {
        assertTrue(SwarmForegroundService.ACTION_START_SWARM != SwarmForegroundService.ACTION_STOP_SWARM)
    }
}
