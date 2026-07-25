package com.ekam.baton.push

import android.util.Log
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

private const val TAG = "FcmTokenManager"

/**
 * FCM Token Manager — Pillar 1, V2
 *
 * On app startup, fetches the device's FCM registration token and registers it
 * with the Baton Cloud Router (POST /register-push). The router uses this token
 * to fire a blank, content-free wakeup when a message arrives for this device
 * while it is backgrounded or in Doze mode.
 *
 * Privacy guarantee: the wakeup push contains ZERO message content and ZERO
 * sender identity. It is purely a knock on the door — all content is E2EE
 * and pulled directly from the Cloud Router over WSS after wakeup.
 */
class FcmTokenManager(
    private val okHttpClient: OkHttpClient,
    private val tunnelEndpointValidator: com.ekam.baton.core.network.tunnel.TunnelEndpointValidator,
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO)
) {

    /**
     * Call on every app startup. Gets the FCM token (cached by Firebase SDK)
     * and registers it with the Cloud Router.
     */
    @Suppress("TooGenericExceptionCaught")
    fun registerOnStartup(routerBaseUrl: String, clientId: String) {
        scope.launch {
            try {
                val token = FirebaseMessaging.getInstance().token.await()
                Log.d(TAG, "FCM token obtained. Registering with router.")
                registerTokenWithRouter(routerBaseUrl, clientId, token)
            } catch (e: Exception) {
                // Non-fatal: the app works without push notifications,
                // messages will just be delivered when the user opens the app.
                Log.w(TAG, "Could not register FCM token: ${e.message}")
            }
        }
    }

    /**
     * Called by BatonFirebaseMessagingService when the token is refreshed.
     * Re-registers the new token to keep the router in sync.
     */
    fun onTokenRefresh(newToken: String, routerBaseUrl: String, clientId: String) {
        scope.launch {
            Log.d(TAG, "FCM token refreshed. Re-registering with router.")
            registerTokenWithRouter(routerBaseUrl, clientId, newToken)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun registerTokenWithRouter(routerBaseUrl: String, clientId: String, fcmToken: String) {
        if (!tunnelEndpointValidator.isUrlSafe(routerBaseUrl)) {
            Log.w(TAG, "SSRF Protection: Refusing to register FCM token. Router URL resolves to a private or reserved address.")
            return
        }
        try {
            val body = JSONObject().apply {
                put("client_id", clientId)
                put("fcm_token", fcmToken)
            }.toString()

            val request = Request.Builder()
                .url("$routerBaseUrl/register-push")
                .post(body.toRequestBody("application/json".toMediaTypeOrNull()))
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Log.i(TAG, "FCM token registered with Cloud Router successfully.")
                } else {
                    Log.w(TAG, "Cloud Router rejected FCM token registration: ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register FCM token with router: ${e.message}")
        }
    }
}
