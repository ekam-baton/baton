package com.ekam.baton.core.network.tunnel

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.webrtc.PeerConnection

class A2ASessionTest {

    @Test
    fun `A2ASession initializes with null factory without throwing`() = runTest {
        val session = A2ASession(id = "test-session-1")
        assertEquals("test-session-1", session.id)
        assertEquals(PeerConnection.PeerConnectionState.NEW, session.connectionState.first())
    }

    @Test
    fun `A2ASession accepts custom ICE servers without throwing`() = runTest {
        val customIce = listOf(
            PeerConnection.IceServer.builder("stun:stun.custom.org:3478").createIceServer()
        )
        val session = A2ASession(id = "test-session-2", customIceServers = customIce)
        assertEquals("test-session-2", session.id)
    }

    @Test
    fun `createOffer returns error when peerConnection is null`() = runTest {
        val session = A2ASession(id = "test-session-3")
        val offer = session.createOffer()
        assertNotNull(offer)
        assertTrue(offer!!.startsWith("ERROR:"))
    }

    @Test
    fun `handleOfferAndCreateAnswer returns error when peerConnection is null`() = runTest {
        val session = A2ASession(id = "test-session-4")
        val answer = session.handleOfferAndCreateAnswer("dummy-offer")
        assertNotNull(answer)
        assertTrue(answer!!.startsWith("ERROR:"))
    }

    @Test
    fun `disconnect transitions connectionState to CLOSED and clears state`() = runTest {
        val session = A2ASession(id = "test-session-5")
        session.disconnect()
        assertEquals(PeerConnection.PeerConnectionState.CLOSED, session.connectionState.first())
    }
}
