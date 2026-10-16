package com.ekam.baton.core.network.connectivity

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

open class NetworkConnectivityMonitor constructor(
    private val context: Context? = null
) {
    private val _isConnected = MutableStateFlow(true)
    open val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    init {
        context?.let { ctx ->
            try {
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                if (cm != null) {
                    val activeNetwork = cm.activeNetwork
                    val caps = cm.getNetworkCapabilities(activeNetwork)
                    val online = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
                    _isConnected.value = online

                    val request = NetworkRequest.Builder()
                        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                        .build()

                    val callback = object : ConnectivityManager.NetworkCallback() {
                        override fun onAvailable(network: Network) {
                            _isConnected.value = true
                        }

                        override fun onLost(network: Network) {
                            _isConnected.value = false
                        }

                        override fun onUnavailable() {
                            _isConnected.value = false
                        }
                    }
                    networkCallback = callback
                    cm.registerNetworkCallback(request, callback)
                }
            } catch (_: Exception) {
                _isConnected.value = true
            }
        }
    }

    open fun setConnectedForTesting(connected: Boolean) {
        _isConnected.value = connected
    }
}
