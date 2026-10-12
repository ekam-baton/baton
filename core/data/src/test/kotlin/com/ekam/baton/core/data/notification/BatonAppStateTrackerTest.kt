package com.ekam.baton.core.data.notification

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BatonAppStateTrackerTest {

    private lateinit var tracker: BatonAppStateTracker

    @Before
    fun setUp() {
        tracker = BatonAppStateTracker()
    }

    @Test
    fun `when app is in background, shouldNotify is always true`() {
        tracker.setAppInForeground(false)
        tracker.setActiveConversationId("conv-123") // Should be cleared on background anyway

        assertTrue(tracker.shouldNotify("conv-123"))
        assertTrue(tracker.shouldNotify("conv-456"))
    }

    @Test
    fun `when app is in foreground and user views active conversation, shouldNotify is false`() {
        tracker.setAppInForeground(true)
        tracker.setActiveConversationId("conv-123")

        assertFalse(tracker.shouldNotify("conv-123"))
    }

    @Test
    fun `when app is in foreground but user is viewing different conversation, shouldNotify is true`() {
        tracker.setAppInForeground(true)
        tracker.setActiveConversationId("conv-123")

        assertTrue(tracker.shouldNotify("conv-456"))
    }

    @Test
    fun `when app is in foreground with no active conversation, shouldNotify is true`() {
        tracker.setAppInForeground(true)
        tracker.setActiveConversationId(null)

        assertTrue(tracker.shouldNotify("conv-123"))
    }

    @Test
    fun `transitioning to background automatically clears active conversation`() {
        tracker.setAppInForeground(true)
        tracker.setActiveConversationId("conv-123")

        tracker.setAppInForeground(false)
        assertTrue(tracker.shouldNotify("conv-123"))
    }
}
