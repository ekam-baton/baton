package com.ekam.baton.core.network.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityKeyManagerTest {

    @Test
    fun testGetPublicKeyHex_returnsValidHex() {
        val hex = IdentityKeyManager.getPublicKeyHex()
        assertNotNull(hex)
        assertEquals(64, hex.length)
        assertTrue(hex.all { it in "0123456789abcdefABCDEF" })
    }

    @Test
    fun testTestPublicKeyHexOverride() {
        val original = IdentityKeyManager.testPublicKeyHex
        try {
            val custom = "abcdef0123456789abcdef0123456789abcdef0123456789abcdef0123456789"
            IdentityKeyManager.testPublicKeyHex = custom
            assertEquals(custom, IdentityKeyManager.getPublicKeyHex())
        } finally {
            IdentityKeyManager.testPublicKeyHex = original
        }
    }
}
