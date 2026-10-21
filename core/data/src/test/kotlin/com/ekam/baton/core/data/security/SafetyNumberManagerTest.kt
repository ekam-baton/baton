package com.ekam.baton.core.data.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyNumberManagerTest {

    private val keyA = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
    private val keyB = "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210"

    @Test
    fun computeSafetyNumber_withValidKeys_returnsFormattedFingerprint() {
        val safetyNumber = SafetyNumberManager.computeSafetyNumber(keyA, keyB)
        assertNotEquals("INVALID KEY LENGTH", safetyNumber)
        val parts = safetyNumber.split(" ")
        assertEquals(12, parts.size)
        for (part in parts) {
            assertEquals(5, part.length)
            assertTrue(part.all { it.isDigit() })
        }
    }

    @Test
    fun computeSafetyNumber_isCommutative() {
        val sn1 = SafetyNumberManager.computeSafetyNumber(keyA, keyB)
        val sn2 = SafetyNumberManager.computeSafetyNumber(keyB, keyA)
        assertEquals("Safety numbers must match regardless of key order", sn1, sn2)
    }

    @Test
    fun computeSafetyNumber_withOddLengthOrMalformedHex_survivesWithoutException() {
        val oddKey = "12345"
        val res1 = SafetyNumberManager.computeSafetyNumber(oddKey, keyB)
        assertEquals("INVALID KEY LENGTH", res1)

        val nonHexKey = "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz"
        val res2 = SafetyNumberManager.computeSafetyNumber(nonHexKey, keyB)
        assertEquals("INVALID KEY LENGTH", res2)

        val emptyKey = ""
        val res3 = SafetyNumberManager.computeSafetyNumber(emptyKey, keyB)
        assertEquals("INVALID KEY LENGTH", res3)
    }

    @Test
    fun computeSafetyNumber_withWhitespace_trimsCorrectly() {
        val spacedKeyA = "  $keyA \n"
        val spacedKeyB = " \t$keyB\r\n "
        val sn1 = SafetyNumberManager.computeSafetyNumber(spacedKeyA, spacedKeyB)
        val sn2 = SafetyNumberManager.computeSafetyNumber(keyA, keyB)
        assertEquals(sn2, sn1)
    }
}
