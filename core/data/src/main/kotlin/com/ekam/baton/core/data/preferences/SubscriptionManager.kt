package com.ekam.baton.core.data.preferences

import java.util.concurrent.TimeUnit

class SubscriptionManager constructor() {

    companion object {
        // 6 months free usage = 180 days
        const val TRIAL_DURATION_DAYS = 180L
    }

    /**
     * Checks if the 180-day trial is currently active.
     */
    fun isTrialActive(trialStartTime: Long): Boolean {
        if (trialStartTime <= 0L) return false
        val diffMs = System.currentTimeMillis() - trialStartTime
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)
        return diffDays in 0 until TRIAL_DURATION_DAYS
    }

    /**
     * Calculates the remaining days in the trial. Returns 0 if expired.
     */
    fun getTrialDaysRemaining(trialStartTime: Long): Long {
        if (trialStartTime <= 0L) return 0L
        val diffMs = System.currentTimeMillis() - trialStartTime
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)
        val remaining = TRIAL_DURATION_DAYS - diffDays
        return remaining.coerceAtLeast(0L)
    }

    /**
     * Checks if the trial has expired.
     */
    fun isTrialExpired(trialStartTime: Long): Boolean {
        if (trialStartTime <= 0L) return true
        val diffMs = System.currentTimeMillis() - trialStartTime
        val diffDays = TimeUnit.MILLISECONDS.toDays(diffMs)
        return diffDays >= TRIAL_DURATION_DAYS
    }

    /**
     * Evaluates if access should be granted to the user.
     * Access is granted if:
     * 1. The premium package is purchased/unlocked, OR
     * 2. The 6-month free trial is still active.
     */
    fun isAccessGranted(trialStartTime: Long, isPremiumUnlocked: Boolean): Boolean {
        return isPremiumUnlocked || isTrialActive(trialStartTime)
    }
}
