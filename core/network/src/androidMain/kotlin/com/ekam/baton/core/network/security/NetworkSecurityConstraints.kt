package com.ekam.baton.core.network.security

import java.net.InetAddress

object NetworkSecurityConstraints {
    fun isLocalOrPrivate(address: InetAddress): Boolean {
        return address.isAnyLocalAddress ||
               address.isLoopbackAddress ||
               address.isLinkLocalAddress ||
               address.isSiteLocalAddress ||
               isUniqueLocalAddress(address)
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