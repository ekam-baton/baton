package com.ekam.baton.core.network.security

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

interface LocalNetworkPolicyProvider {
    suspend fun isLocalNetworkAllowed(hostname: String): Boolean
}

class SsrfProtectionDns(
    private val policyProvider: LocalNetworkPolicyProvider,
    private val delegate: Dns = Dns.SYSTEM
) : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val addresses = delegate.lookup(hostname)
        
        if (addresses.isEmpty()) return addresses

        // Check policy synchronously because OkHttp DNS lookup is synchronous
        val isAllowed = runBlocking { policyProvider.isLocalNetworkAllowed(hostname) }
        
        if (!isAllowed) {
            for (address in addresses) {
                if (NetworkSecurityConstraints.isLocalOrPrivate(address)) {
                    throw UnknownHostException("SSRF Protection: Access to local network address (${address.hostAddress}) is blocked for hostname $hostname. Enable 'Allow Local Network Agents' in Settings to connect.")
                }
            }
        }
        
        return addresses
    }
}
