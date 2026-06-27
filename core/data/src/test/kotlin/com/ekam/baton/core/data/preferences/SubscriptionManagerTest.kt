package com.ekam.baton.core.data.preferences

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class SubscriptionManagerTest {

    private val subscriptionManager = SubscriptionManager()

    @Test
    fun testIsTrialActive_whenTrialRecentlyStarted() {
        val startTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(10) // 10 days ago
        assertTrue(subscriptionManager.isTrialActive(startTime))
    }

    @Test
    fun testIsTrialActive_whenTrialExpired() {
        val startTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(181) // 181 days ago
        assertFalse(subscriptionManager.isTrialActive(startTime))
    }

    @Test
    fun testIsTrialActive_whenNoStartTime() {
        assertFalse(subscriptionManager.isTrialActive(0L))
    }

    @Test
    fun testGetTrialDaysRemaining_whenTrialStartedNow() {
        val startTime = System.currentTimeMillis()
        val daysRemaining = subscriptionManager.getTrialDaysRemaining(startTime)
        assertEquals(180L, daysRemaining)
    }

    @Test
    fun testGetTrialDaysRemaining_whenHalfwayThrough() {
        val startTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(90) // 90 days ago
        val daysRemaining = subscriptionManager.getTrialDaysRemaining(startTime)
        assertEquals(90L, daysRemaining)
    }

    @Test
    fun testGetTrialDaysRemaining_whenExpired() {
        val startTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(200) // 200 days ago
        val daysRemaining = subscriptionManager.getTrialDaysRemaining(startTime)
        assertEquals(0L, daysRemaining)
    }

    @Test
    fun testIsTrialExpired_whenActive() {
        val startTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(150)
        assertFalse(subscriptionManager.isTrialExpired(startTime))
    }

    @Test
    fun testIsTrialExpired_whenExpired() {
        val startTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(180)
        assertTrue(subscriptionManager.isTrialExpired(startTime))
    }

    @Test
    fun testIsAccessGranted_whenTrialActive_andNotPremium() {
        val startTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
        assertTrue(subscriptionManager.isAccessGranted(startTime, isPremiumUnlocked = false))
    }

    @Test
    fun testIsAccessGranted_whenTrialExpired_andPremiumUnlocked() {
        val startTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(200)
        assertTrue(subscriptionManager.isAccessGranted(startTime, isPremiumUnlocked = true))
    }

    @Test
    fun testIsAccessGranted_whenTrialExpired_andNotPremium() {
        val startTime = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(200)
        assertFalse(subscriptionManager.isAccessGranted(startTime, isPremiumUnlocked = false))
    }
}
