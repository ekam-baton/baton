package com.ekam.baton.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.first
import org.koin.android.ext.android.getKoin
import org.koin.android.ext.android.inject

private const val TAG = "BatonFCMService"

/**
 * Baton Firebase Messaging Service — Pillar 1, V2
 *
 * This service is invoked by the Android OS when:
 *  1. A push notification arrives while the app is backgrounded (onMessageReceived).
 *  2. The FCM registration token is refreshed (onNewToken).
 *
 * Security design:
 * - We intentionally do NOT display ANY notification to the user here.
 * - The FCM message payload contains ZERO content — it is a blank wakeup signal.
 * - On wakeup, we reconnect to the Cloud Router over WSS and pull the encrypted
 *   message queue. All content is decrypted on-device by the Double Ratchet engine.
 *
 * This means: Firebase / Google never sees any message content or metadata.
 */
class BatonFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val fcmTokenManager: FcmTokenManager by inject()

    /**
     * Called when a blank wakeup push is received from the Cloud Router.
     * We silently trigger a WebSocket reconnect to pull the E2EE queue.
     */
    @Suppress("TooGenericExceptionCaught")
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Verify this is a legitimate Baton wakeup (not an unrelated FCM message)
        val messageType = remoteMessage.data["type"]
        if (messageType != "baton_wakeup") {
            Log.d(TAG, "Ignoring unknown FCM message type: $messageType")
            return
        }

        Log.d(TAG, "Baton wakeup received. Triggering WebSocket reconnect to pull E2EE queue.")

        // Broadcast an intent to the McpWebSocketTransport / McpConnectionManager
        // to wake up and reconnect to the Cloud Router.
        serviceScope.launch {
            try {
                // Send a local broadcast that the connection manager listens for.
                val intent = android.content.Intent("com.ekam.baton.ACTION_FCM_WAKEUP")
                applicationContext.sendBroadcast(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send wakeup broadcast: ${e.message}")
            }
        }
    }

    /**
     * Called when the FCM token is rotated by Firebase.
     * Re-registers the new token with the Cloud Router automatically.
     */
    override fun onNewToken(token: String) {
        Log.d(TAG, "FCM token refreshed. Re-registering with Cloud Router.")
        serviceScope.launch {
            try {
                val appPrefs: com.ekam.baton.core.data.preferences.AppPreferences = getKoin().get()
                val routerBaseUrl = appPrefs.backendUrl.first()
                val clientId = appPrefs.clientId.first()
                fcmTokenManager.onTokenRefresh(
                    newToken = token,
                    routerBaseUrl = routerBaseUrl,
                    clientId = clientId
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read preferences for FCM token refresh", e)
            }
        }
    }
}
