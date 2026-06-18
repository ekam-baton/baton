package com.ekam.baton

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * BATON Application class.
 *
 * Annotated with [HiltAndroidApp] to trigger Hilt's code generation and set up
 * the application-level DI component. This must be the first entry point Hilt
 * processes in the app process lifecycle.
 */
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class BatonApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    @Inject
    lateinit var appLockObserver: AppLockObserver

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        scheduleTunnelMonitor()
        
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(appLockObserver)
        registerActivityLifecycleCallbacks(appLockObserver)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "baton_tunnel_alerts",
                "Agent connectivity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Alerts for local agent connectivity status"
            }
            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun scheduleTunnelMonitor() {
        val workRequest = PeriodicWorkRequestBuilder<com.ekam.baton.feature.agents.tunnel.TunnelConnectivityMonitor>(5, TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "TunnelConnectivityMonitor",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
