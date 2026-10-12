package com.ekam.baton.core.network.security

import java.net.InetAddress

object NetworkSecurityConstraints {
    fun isLocalOrPrivate(address: InetAddress): Boolean {
        // Unpack IPv4-mapped IPv6 addresses (e.g. ::ffff:127.0.0.1)
        val targetAddress = normalizeAddress(address)

        if (targetAddress.isAnyLocalAddress ||
            targetAddress.isLoopbackAddress ||
            targetAddress.isLinkLocalAddress ||
            targetAddress.isSiteLocalAddress ||
            isUniqueLocalAddress(targetAddress) ||
            isCarrierGradeNat(targetAddress)) {
            return true
        }

        // Explicit check on raw IPv4 bytes for defense-in-depth
        val raw = targetAddress.address
        if (raw.size == 4) {
            val b0 = raw[0].toInt() and 0xFF
            val b1 = raw[1].toInt() and 0xFF
            // 10.0.0.0/8
            if (b0 == 10) return true
            // 172.16.0.0/12
            if (b0 == 172 && (b1 in 16..31)) return true
            // 192.168.0.0/16
            if (b0 == 192 && b1 == 168) return true
            // 127.0.0.0/8
            if (b0 == 127) return true
            // 169.254.0.0/16
            if (b0 == 169 && b1 == 254) return true
            // 0.0.0.0/8
            if (b0 == 0) return true
        }

        return false
    }

    private fun normalizeAddress(address: InetAddress): InetAddress {
        val raw = address.address
        if (raw.size == 16) {
            val isMapped = (0..9).all { raw[it] == 0.toByte() } &&
                           raw[10] == 0xFF.toByte() &&
                           raw[11] == 0xFF.toByte()
            if (isMapped) {
                val ipv4Bytes = ByteArray(4)
                System.arraycopy(raw, 12, ipv4Bytes, 0, 4)
                return try {
                    InetAddress.getByAddress(ipv4Bytes)
                } catch (e: Exception) {
                    address
                }
            }
        }
        return address
    }

    private fun isCarrierGradeNat(address: InetAddress): Boolean {
        val raw = address.address
        if (raw.size == 4) {
            val b0 = raw[0].toInt() and 0xFF
            val b1 = raw[1].toInt() and 0xFF
            // 100.64.0.0/10 (100.64.0.0 to 100.127.255.255)
            return b0 == 100 && (b1 and 0xC0) == 64
        }
        return false
    }

    private fun isUniqueLocalAddress(address: InetAddress): Boolean {
        val bytes = address.address
        if (bytes.size == 16) {
            val firstByte = bytes[0].toInt() and 0xFF
            return (firstByte and 0xFE) == 0xFC
        }
        return false
    }
}