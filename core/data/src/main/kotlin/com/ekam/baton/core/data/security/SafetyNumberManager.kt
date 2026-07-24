package com.ekam.baton.core.data.security

import java.security.MessageDigest

/**
 * Computes a safety number for verifying out-of-band that the E2EE keys have not been MITM'd.
 * Uses the exact same SHA-512 + lexicographical sort algorithm as the Desktop Hub.
 */
object SafetyNumberManager {
    fun computeSafetyNumber(myEd25519PubKeyHex: String, theirEd25519PubKeyHex: String): String {
        val myBytes = hexStringToByteArray(myEd25519PubKeyHex)
        val theirBytes = hexStringToByteArray(theirEd25519PubKeyHex)

        if (myBytes.size != 32 || theirBytes.size != 32) {
            return "INVALID KEY LENGTH"
        }

        // Sort keys lexicographically
        val combined = if (compareByteArrays(myBytes, theirBytes) < 0) {
            myBytes + theirBytes
        } else {
            theirBytes + myBytes
        }

        val md = MessageDigest.getInstance("SHA-512")
        val hash = md.digest(combined)

        val sb = java.lang.StringBuilder()
        for (i in 0 until 12) {
            val offset = i * 5
            if (offset + 4 >= hash.size) break

            // Take 5 bytes, convert to u64
            val chunk = ByteArray(8)
            System.arraycopy(hash, offset, chunk, 3, 5)
            val v = java.nio.ByteBuffer.wrap(chunk).long
            val digits = v % 100000

            if (i > 0) sb.append(" ")
            sb.append(String.format("%05d", digits))
        }

        return sb.toString()
    }

    private fun compareByteArrays(a: ByteArray, b: ByteArray): Int {
        val len = minOf(a.size, b.size)
        for (i in 0 until len) {
            val unsignedA = a[i].toInt() and 0xFF
            val unsignedB = b[i].toInt() and 0xFF
            if (unsignedA != unsignedB) {
                return unsignedA.compareTo(unsignedB)
            }
        }
        return a.size.compareTo(b.size)
    }

    private fun hexStringToByteArray(s: String): ByteArray {
        val len = s.length
        val data = ByteArray(len / 2)
        var i = 0
        while (i < len) {
            data[i / 2] = ((Character.digit(s[i], 16) shl 4)
                    + Character.digit(s[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }
}
