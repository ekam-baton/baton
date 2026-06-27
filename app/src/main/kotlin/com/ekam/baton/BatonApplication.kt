package com.ekam.baton

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.work.Configuration
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.ExistingPeriodicWorkPolicy
import com.ekam.baton.core.data.di.dataModule
import com.ekam.baton.core.network.di.networkModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import java.util.concurrent.TimeUnit

class BatonApplication : Application(), Configuration.Provider {

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().build()

    private val appLockObserver = AppLockObserver()

    override fun onCreate() {
        super.onCreate()
        
        startKoin {
            androidLogger()
            androidContext(this@BatonApplication)
            modules(com.ekam.baton.di.appModule, networkModule, dataModule)
        }

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
