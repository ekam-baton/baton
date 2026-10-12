package com.ekam.baton.core.data.notification

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Tracks whether Baton is currently in the foreground and which conversation
 * is currently visible to the user.
 *
 * This ensures WhatsApp/Telegram-level behavior:
 * - If the user is actively viewing Conversation A, messages for Conversation A do NOT buzz or post redundant banners.
 * - If the app is minimized, locked, or the user is looking at another screen, messages for Conversation A DO post heads-up alerts.
 */
class BatonAppStateTracker {

    private val _isAppInForeground = MutableStateFlow(false)
    val isAppInForeground: StateFlow<Boolean> = _isAppInForeground.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    fun setAppInForeground(inForeground: Boolean) {
        _isAppInForeground.value = inForeground
        if (!inForeground) {
            // When app goes to background, clear active conversation view
            _activeConversationId.value = null
        }
    }

    fun setActiveConversationId(conversationId: String?) {
        _activeConversationId.value = conversationId
    }

    /**
     * Determines whether a background alert should be posted.
     */
    fun shouldNotify(conversationId: String): Boolean {
        val inForeground = _isAppInForeground.value
        val activeConv = _activeConversationId.value

        // If app is in background or screen is off -> ALWAYS notify
        if (!inForeground) return true

        // If app is in foreground but user is NOT currently in this conversation -> notify
        return activeConv != conversationId
    }
}
