package com.ekam.baton.core.network.mdns

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class DiscoveredAgent(
    val name: String,
    val ipAddress: String,
    val port: Int,
    val url: String
)

class MdnsDiscoveryManager(private val context: Context) {
    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val _discoveredAgents = MutableStateFlow<List<DiscoveredAgent>>(emptyList())
    val discoveredAgents: StateFlow<List<DiscoveredAgent>> = _discoveredAgents.asStateFlow()

    private val SERVICE_TYPE = "_baton._tcp."
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscovering = false
    private val TAG = "MdnsDiscoveryManager"

    fun startDiscovery() {
        if (isDiscovering) return
        
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "Service discovery started")
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service discovery success: $service")
                if (service.serviceType == SERVICE_TYPE) {
                    nsdManager.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.e(TAG, "Resolve failed: $errorCode")
                        }

                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            Log.d(TAG, "Resolve Succeeded. $serviceInfo")
                            val host = serviceInfo.host.hostAddress
                            val port = serviceInfo.port
                            if (host != null) {
                                val url = "http://$host:$port"
                                val newAgent = DiscoveredAgent(
                                    name = serviceInfo.serviceName,
                                    ipAddress = host,
                                    port = port,
                                    url = url
                                )
                                _discoveredAgents.update { current ->
                                    // Remove if already exists to update it, then add
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
            nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
            isDiscovering = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start discovery", e)
        }
    }

    fun stopDiscovery() {
        if (!isDiscovering) return
        try {
            discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop discovery", e)
        } finally {
            discoveryListener = null
            isDiscovering = false
            _discoveredAgents.value = emptyList()
        }
    }
}
