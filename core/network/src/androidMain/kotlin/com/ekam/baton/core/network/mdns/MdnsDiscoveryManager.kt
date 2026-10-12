package com.ekam.baton.core.network.mdns

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DiscoveredAgent(
    val name: String,
    val ipAddress: String,
    val port: Int,
    val url: String,
    val ownerName: String? = null
)

open class MdnsDiscoveryManager(protected val context: Context? = null) {
    private val nsdManager: NsdManager? = try { context?.getSystemService(Context.NSD_SERVICE) as? NsdManager } catch (_: Exception) { null }
    protected val _discoveredAgents = MutableStateFlow<List<DiscoveredAgent>>(emptyList())
    open val discoveredAgents: StateFlow<List<DiscoveredAgent>> = _discoveredAgents.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    open val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val SERVICE_TYPE = "_baton._tcp."
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val TAG = "MdnsDiscoveryManager"
    private var probeScope: kotlinx.coroutines.CoroutineScope? = null

    private fun probeLocalRuntimes() {
        probeScope?.cancel()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
        probeScope = scope

        scope.launch {
            val candidates = listOf(
                Triple("localhost", 11434, "Local Ollama Node"),
                Triple("10.0.2.2", 11434, "Host Machine Ollama"),
                Triple("localhost", 1234, "Local LM Studio"),
                Triple("10.0.2.2", 1234, "Host Machine LM Studio"),
                Triple("localhost", 8080, "Local MCP Host"),
                Triple("10.0.2.2", 8080, "Host Machine MCP")
            )

            for ((host, port, displayName) in candidates) {
                if (!isActive) break
                try {
                    java.net.Socket().use { socket ->
                        socket.connect(java.net.InetSocketAddress(host, port), 200)
                        val url = "http://$host:$port"
                        val agent = DiscoveredAgent(
                            name = displayName,
                            ipAddress = host,
                            port = port,
                            url = url,
                            ownerName = "Local Subnet"
                        )
                        _discoveredAgents.update { current ->
                            val filtered = current.filterNot { it.url == agent.url }
                            filtered + agent
                        }
                    }
                } catch (_: Exception) {
                    // Port not responding
                }
            }
        }
    }

    open fun startDiscovery() {
        if (_isDiscovering.value) return
        _isDiscovering.value = true
        probeLocalRuntimes()
        
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started")
            }

            @Suppress("DEPRECATION")
            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service discovery success: $service")
                if (service.serviceType.contains("_baton._tcp")) {
                    nsdManager?.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            Log.d(TAG, "Resolve Succeeded. $serviceInfo")
                            val host = serviceInfo.host.hostAddress
                            val port = serviceInfo.port
                            if (host != null) {
                                val url = "http://$host:$port"
                                
                                val ownerNameAttr = serviceInfo.attributes?.get("owner_name")
                                val ownerName = ownerNameAttr?.let { String(it, Charsets.UTF_8) }
                                
                                val newAgent = DiscoveredAgent(
                                    name = serviceInfo.serviceName,
                                    ipAddress = host,
                                    port = port,
                                    url = url,
                                    ownerName = ownerName
                                )
                                _discoveredAgents.update { current ->
                                    val filtered = current.filterNot { it.name == newAgent.name || it.url == newAgent.url }
                                    filtered + newAgent
                                }
                            }
                        }
                    })
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.e(TAG, "service lost: $service")
                _discoveredAgents.update { current ->
                    current.filterNot { it.name == service.serviceName }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.i(TAG, "Discovery stopped: $serviceType")
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
                stopDiscovery()
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Discovery failed: Error code:$errorCode")
                stopDiscovery()
            }
        }
        
        try {
            nsdManager?.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
            _isDiscovering.value = false
        }
    }

    open fun stopDiscovery() {
        if (!_isDiscovering.value) return
        probeScope?.cancel()
        probeScope = null
        try {
            discoveryListener?.let { nsdManager?.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop discovery", e)
        } finally {
            discoveryListener = null
            _isDiscovering.value = false
            _discoveredAgents.value = emptyList()
        }
    }
}
