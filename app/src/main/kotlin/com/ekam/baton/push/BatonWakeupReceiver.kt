package com.ekam.baton.push

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ekam.baton.notification.BatonNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class BatonWakeupReceiver : BroadcastReceiver(), KoinComponent {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val notificationManager: BatonNotificationManager by inject()

    companion object {
        const val ACTION_FCM_WAKEUP = "com.ekam.baton.ACTION_FCM_WAKEUP"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_FCM_WAKEUP) {
            Log.d("BatonWakeupReceiver", "FCM Wakeup broadcast received: active listener caught wakeup signal")
            val pendingResult = goAsync()
            scope.launch {
                try {
                    // Active receiver caught wakeup - allows background sync processing
                } catch (e: Exception) {
                    Log.e("BatonWakeupReceiver", "Error processing background wakeup", e)
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
