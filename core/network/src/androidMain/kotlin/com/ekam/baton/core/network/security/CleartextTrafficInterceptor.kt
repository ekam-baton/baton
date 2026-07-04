package com.ekam.baton.core.network.security

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.InetAddress

class CleartextTrafficInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val isCleartext = request.url.scheme == "http" || request.url.scheme == "ws"
        
        if (isCleartext) {
            val host = request.url.host
            
            val isLocalhost = host == "localhost" || host == "127.0.0.1" || host == "::1"
            if (!isLocalhost) {
                try {
                    val address = InetAddress.getByName(host)
                    if (!address.isSiteLocalAddress && !address.isLoopbackAddress && !address.isLinkLocalAddress) {
                        throw IOException("Cleartext traffic (HTTP/WS) is strictly forbidden for public internet addresses ($host). Please use HTTPS or WSS.")
                    }
                } catch (e: java.net.UnknownHostException) {
                    throw IOException("Cleartext traffic (HTTP/WS) is forbidden and host resolution failed for $host.", e)
                }
            }
        }
        
        return chain.proceed(request)
    }
}
