package com.ekam.baton.core.data.notification

/**
 * Interface for dispatching Telegram & WhatsApp-grade background notifications
 * when sovereign agents complete tasks, stream replies, or experience failures.
 */
interface AgentNotificationDispatcher {

    /**
     * Posts a rich MessagingStyle notification for an incoming agent message.
     * Supports Android Direct Reply (RemoteInput) and one-tap deep link.
     */
    fun postAgentMessageNotification(
        agentId: String,
        agentName: String,
        conversationId: String,
        content: String,
        timestamp: Long = System.currentTimeMillis()
    )

    /**
     * Posts a high-priority task completion alert with big text result preview,
     * one-tap copy button, and link to conversation.
     */
    fun postAgentTaskCompletedNotification(
        agentId: String,
        agentName: String,
        conversationId: String,
        taskTitle: String,
        resultSummary: String,
        isSuccess: Boolean = true
    )

    /**
     * Posts an ongoing progress notification for long-running agentic tasks.
     */
    fun postAgentTaskProgressNotification(
        agentId: String,
        agentName: String,
        taskTitle: String,
        progressPercent: Int? = null
    )

    /**
     * Cancels an active notification by notification ID.
     */
    fun dismissNotification(notificationId: Int)

    /**
     * Cancels all notifications for a specific conversation.
     */
    fun dismissConversationNotifications(conversationId: String)
}
