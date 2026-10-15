package com.ekam.baton

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ekam.baton.core.data.di.dataModule
import com.ekam.baton.core.network.di.networkModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import java.util.concurrent.TimeUnit

class BatonApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    private val appLockObserver = AppLockObserver()

    override fun onCreate() {
        super.onCreate()

        // 1. Initialize Dependency Injection with minimal reflection overhead in release
        startKoin {
            androidLogger(if (BuildConfig.DEBUG) Level.INFO else Level.ERROR)
            androidContext(this@BatonApplication)
            modules(com.ekam.baton.di.appModule, networkModule, dataModule)
        }

        // 2. Register lifecycle observers immediately
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(appLockObserver)
        registerActivityLifecycleCallbacks(appLockObserver)

        // 3. Offload I/O, WorkManager database scheduling, and SQLCipher pre-warming to background
        CoroutineScope(Dispatchers.IO).launch {
            createNotificationChannel()
            createComplianceNotificationChannel()
            scheduleTunnelMonitor()
            scheduleComplianceReminder()

            // Pre-warm SQLCipher database connection pool in background
            try {
                val prewarmer = org.koin.java.KoinJavaComponent.getKoin().getOrNull<com.ekam.baton.core.data.db.DatabasePrewarmer>()
                prewarmer?.prewarm()
            } catch (_: Exception) {
                // Non-fatal if DB not yet initialized; standard queries will initialize
            }
        }
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

    /**
     * Creates the notification channel for IT Rules compliance reminders.
     * Required on API 26+ (Android 8.0+).
     */
    private fun createComplianceNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ComplianceNotificationWorker.CHANNEL_ID,
                "Policy & Compliance Reminders",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Periodic reminders about BATON Privacy Policy and Terms of Service, as required by Indian IT Rules."
            }
            val notificationManager: NotificationManager =
                getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Schedules the ComplianceNotificationWorker to run every 90 days.
     *
     * Mandated by IT (Intermediary Guidelines and Digital Media Ethics Code) Rules, 2021
     * (as amended in 2026): platforms must remind users of their Privacy Policy,
     * Terms of Service, and consequences of non-compliance at least every 90 days.
     */
    private fun scheduleComplianceReminder() {
        val workRequest = PeriodicWorkRequestBuilder<ComplianceNotificationWorker>(90, TimeUnit.DAYS)
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ComplianceNotificationWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    private fun scheduleTunnelMonitor() {
        val workRequest = PeriodicWorkRequestBuilder<com.ekam.baton.feature.agents.tunnel.TunnelConnectivityMonitor>(
            15,
            TimeUnit.MINUTES
        )
            .build()

        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "TunnelConnectivityMonitor",
            ExistingPeriodicWorkPolicy.UPDATE,
            workRequest
        )
    }
}
