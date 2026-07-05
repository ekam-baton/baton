package com.ekam.baton.core.data.forensic

import android.os.SystemClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class TrustedTimeProvider {

    private var ntpTimeOffsetMs: Long? = null
    private var lastSyncTimeMs: Long = 0
    private val SYNC_INTERVAL_MS = 1000 * 60 * 60 * 24 // 24 hours

    /**
     * Returns a forensically reliable timestamp.
     * Uses NTP offset from SystemClock.elapsedRealtime() to prevent users
     * from spoofing timestamps by changing their device clock.
     * Fallback to System.currentTimeMillis() if NTP has never synced.
     */
    suspend fun getTrustedTimestamp(): Long {
        if (ntpTimeOffsetMs == null || (SystemClock.elapsedRealtime() - lastSyncTimeMs) > SYNC_INTERVAL_MS) {
            syncWithNtp()
        }
        
        return ntpTimeOffsetMs?.let { offset ->
            SystemClock.elapsedRealtime() + offset
        } ?: System.currentTimeMillis()
    }

    /**
     * Queries time.google.com via SNTP to calculate the offset between the true
     * network time and the device's monotonic uptime clock.
     */
    private suspend fun syncWithNtp() = withContext(Dispatchers.IO) {
        try {
            val serverName = "time.google.com"
            val ntpPort = 123
            val timeoutMs = 5000

            DatagramSocket().use { socket ->
                socket.soTimeout = timeoutMs
                val address = InetAddress.getByName(serverName)
                
                // NTP payload (Client mode)
                val buffer = ByteArray(48)
                buffer[0] = 0x1B.toByte() 
                val request = DatagramPacket(buffer, buffer.size, address, ntpPort)
                
                val requestTime = System.currentTimeMillis()
                val requestTicks = SystemClock.elapsedRealtime()
                
                socket.send(request)
                
                val response = DatagramPacket(buffer, buffer.size)
                socket.receive(response)
                
                val responseTicks = SystemClock.elapsedRealtime()

                // Extract Transmit Timestamp (bytes 40-47)
                val transmitTimestamp = readTimestamp(buffer, 40)
                
                // Calculate round-trip delay and true time
                val roundTripDelay = responseTicks - requestTicks
                val trueTime = transmitTimestamp + (roundTripDelay / 2)

                // The offset is the difference between true time and the monotonic clock
                ntpTimeOffsetMs = trueTime - responseTicks
                lastSyncTimeMs = SystemClock.elapsedRealtime()
            }
        } catch (e: Exception) {
            // Log failure, fallback will be used
            e.printStackTrace()
        }
    }

    private fun readTimestamp(buffer: ByteArray, offset: Int): Long {
        var seconds = 0L
        for (i in 0..3) {
            seconds = (seconds shl 8) or (buffer[offset + i].toLong() and 0xFFL)
        }
        var fraction = 0L
        for (i in 4..7) {
            fraction = (fraction shl 8) or (buffer[offset + i].toLong() and 0xFFL)
        }
        // NTP uses an epoch of Jan 1, 1900. Java uses Jan 1, 1970.
        // Difference is 2208988800 seconds.
        val ntpEpochOffsetSeconds = 2208988800L
        return ((seconds - ntpEpochOffsetSeconds) * 1000) + ((fraction * 1000L) / 0x100000000L)
    }
}
