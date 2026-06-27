package com.ekam.baton

import android.app.Activity
import android.app.Application
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.*
import com.ekam.baton.core.data.preferences.AppPreferences
import com.ekam.baton.core.data.preferences.SessionManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.core.component.inject

class AppLockObserver : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks, org.koin.core.component.KoinComponent {

    private val appPreferences: AppPreferences by inject()
    private val sessionManager: SessionManager by inject()

    private var backgroundedTime: Long = 0
    private var currentActivity: AppCompatActivity? = null

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        checkLockState()
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        backgroundedTime = System.currentTimeMillis()
    }

    private fun checkLockState() {
        val activity = currentActivity ?: return
        
        activity.lifecycleScope.launch {
            val isEnabled = appPreferences.appLockEnabled.first()
            if (!isEnabled) return@launch

            val timeInBackground = System.currentTimeMillis() - backgroundedTime
            // Lock if backgrounded for more than 1 minute (60000 ms)
            if (backgroundedTime > 0 && timeInBackground > 60000) {
                sessionManager.setLoggedIn(false)
            }
        }
    }

    // ActivityLifecycleCallbacks
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityStarted(activity: Activity) {}
    override fun onActivityResumed(activity: Activity) {
        if (activity is AppCompatActivity) {
            currentActivity = activity
        }
    }
    override fun onActivityPaused(activity: Activity) {
        if (currentActivity == activity) {
            currentActivity = null
        }
    }
    override fun onActivityStopped(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}
}
