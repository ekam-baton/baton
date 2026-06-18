package com.ekam.baton

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.lifecycle.*
import com.ekam.baton.core.data.preferences.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLockObserver @Inject constructor(
    private val appPreferences: AppPreferences
) : DefaultLifecycleObserver, Application.ActivityLifecycleCallbacks {

    private var backgroundedTime: Long = 0
    private var isLocked = false
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
        
        // Use lifecycleScope of the activity
        activity.lifecycleScope.launch {
            val isEnabled = appPreferences.appLockEnabled.first()
            if (!isEnabled) return@launch

            val timeInBackground = System.currentTimeMillis() - backgroundedTime
            // Lock if backgrounded for more than 1 minute (60000 ms) and not the very first launch
            if (backgroundedTime > 0 && timeInBackground > 60000 && !isLocked) {
                isLocked = true
                showBiometricPrompt(activity)
            }
        }
    }

    private fun showBiometricPrompt(activity: AppCompatActivity) {
        val biometricManager = BiometricManager.from(activity)
        if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS) {
            isLocked = false
            return
        }

        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                super.onAuthenticationError(errorCode, errString)
                // If user cancels, we could close the app, or leave it locked underneath a scrim.
                // For simplicity, we just keep retrying if they hit cancel.
                if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                    activity.finish()
                }
            }

            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                super.onAuthenticationSucceeded(result)
                isLocked = false
                backgroundedTime = 0
            }
        })

        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock BATON")
            .setSubtitle("Authenticate to resume")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL)
            .build()

        prompt.authenticate(promptInfo)
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
