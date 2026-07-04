package com.ekam.baton.core.network.security

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

interface LocalNetworkPolicyProvider {
    suspend fun isLocalNetworkAllowed(): Boolean
}

class SsrfProtectionDns(
    private val policyProvider: LocalNetworkPolicyProvider,
    private val delegate: Dns = Dns.SYSTEM
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        
        // Fast path: if there are no addresses, let it pass (it will fail anyway)
        if (addresses.isEmpty()) return addresses

        // Check policy synchronously because OkHttp DNS lookup is synchronous
        val isAllowed = runBlocking { policyProvider.isLocalNetworkAllowed() }
        
        if (!isAllowed) {
            for (address in addresses) {
                if (isLocalOrPrivate(address)) {
                    throw UnknownHostException("SSRF Protection: Access to local network address (${address.hostAddress}) is blocked for hostname $hostname. Enable 'Allow Local Network Agents' in Settings to connect.")
                }
            }
        }
        
        return addresses
    }

    private fun isLocalOrPrivate(address: InetAddress): Boolean {
        return address.isAnyLocalAddress ||
               address.isLoopbackAddress ||
               address.isLinkLocalAddress ||
               address.isSiteLocalAddress ||
               isUniqueLocalAddress(address)
    }

    private fun isUniqueLocalAddress(address: InetAddress): Boolean {
        // IPv6 Unique Local Addresses (fc00::/7)
        val bytes = address.address
        if (bytes.size == 16) {
            val firstByte = bytes[0].toInt() and 0xFF
            return (firstByte and 0xFE) == 0xFC
        }
        return false
    }
}
