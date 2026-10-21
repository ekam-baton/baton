package com.ekam.baton.notification

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.ekam.baton.MainActivity
import com.ekam.baton.R
import com.ekam.baton.core.data.notification.AgentNotificationDispatcher
import java.util.concurrent.ConcurrentHashMap

/**
 * BatonNotificationManager
 *
 * Telegram and WhatsApp-grade notification engine for sovereign agents in BATON:
 * - Direct In-Notification Reply (RemoteInput) from notification shade or lockscreen.
 * - Threaded messaging style (NotificationCompat.MessagingStyle).
 * - Action buttons for 1-tap Copy Result and Open Conversation.
 * - Heads-up banners with custom alert vibration for task completions and agent replies.
 * - Distinct channels for High Importance Messages vs Ongoing Background Task Progress.
 */
class BatonNotificationManager(
    private val context: Context
) : AgentNotificationDispatcher {

    companion object {
        const val CHANNEL_AGENT_MESSAGES = "baton_agent_messages"
        const val CHANNEL_AGENT_TASKS = "baton_agent_tasks"
        const val CHANNEL_TUNNEL_ALERTS = "baton_tunnel_alerts"

        const val GROUP_AGENT_NOTIFICATIONS = "com.ekam.baton.AGENT_NOTIFICATIONS"
        const val SUMMARY_NOTIFICATION_ID = 9999

        const val KEY_TEXT_REPLY = "key_text_reply"
        const val ACTION_AGENT_REPLY = "com.ekam.baton.ACTION_AGENT_NOTIFICATION_REPLY"
        const val ACTION_AGENT_COPY = "com.ekam.baton.ACTION_AGENT_NOTIFICATION_COPY"
        const val ACTION_OPEN_CHAT = "com.ekam.baton.ACTION_OPEN_CHAT"

        const val EXTRA_AGENT_ID = "extra_agent_id"
        const val EXTRA_AGENT_NAME = "extra_agent_name"
        const val EXTRA_CONVERSATION_ID = "extra_conversation_id"
        const val EXTRA_NOTIFICATION_ID = "extra_notification_id"
        const val EXTRA_TEXT_TO_COPY = "extra_text_to_copy"
    }

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // In-memory conversation history cache for threaded MessagingStyle
    private val conversationHistory =
        ConcurrentHashMap<String, MutableList<NotificationCompat.MessagingStyle.Message>>()

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                .build()

            val defaultSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            // 1. High-importance channel: Agent Messages & Direct Replies
            val messagesChannel = NotificationChannel(
                CHANNEL_AGENT_MESSAGES,
                "Agent Messages & Direct Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "High-priority direct messages, responses, and task completions from AI agents"
                enableLights(true)
                lightColor = Color.parseColor("#00D2FF") // Cyber Cyan
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 220, 110, 220) // Telegram-style double pulse
                setSound(defaultSound, audioAttributes)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                setShowBadge(true)
            }

            // 2. Low-importance channel: Ongoing Task Progress
            val tasksChannel = NotificationChannel(
                CHANNEL_AGENT_TASKS,
                "Agent Task Execution & Progress",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Ongoing progress indicators for remote builds, commands, and agent executions"
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
                setShowBadge(false)
            }

            // 3. Default-importance channel: Tunnel & Bridge status
            val tunnelChannel = NotificationChannel(
                CHANNEL_TUNNEL_ALERTS,
                "Bridge & Tunnel Connectivity",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Desktop Bridge and Cloudflare tunnel connectivity notifications"
            }

            notificationManager.createNotificationChannels(
                listOf(messagesChannel, tasksChannel, tunnelChannel)
            )
        }
    }

    private fun hasPostNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun getNotificationIdForConversation(conversationId: String): Int {
        return (conversationId.hashCode() and 0x7FFFFFFF) % 100000 + 1000
    }

    private fun getTaskIdNotificationId(agentId: String, taskTitle: String): Int {
        return ((agentId + taskTitle).hashCode() and 0x7FFFFFFF) % 100000 + 20000
    }

    /**
     * Posts a Telegram/WhatsApp-grade MessagingStyle notification with Direct Reply.
     */
    @SuppressLint("MissingPermission")
    override fun postAgentMessageNotification(
        agentId: String,
        agentName: String,
        conversationId: String,
        content: String,
        timestamp: Long
    ) {
        if (!hasPostNotificationPermission()) return

        val notificationId = getNotificationIdForConversation(conversationId)

        // 1. Build Person objects
        val agentPerson = Person.Builder()
            .setKey(agentId)
            .setName(agentName)
            .setBot(true)
            .build()

        val userPerson = Person.Builder()
            .setName("You")
            .build()

        // 2. Cache threaded message history (bound display content to 4000 chars to prevent SystemUI lag/IPC issues)
        val safeDisplayContent = if (content.length > 4000) content.take(4000) + "… [truncated]" else content
        val safeCopyContent = if (content.length > 50000) content.take(50000) else content

        val historyList = conversationHistory.getOrPut(conversationId) { mutableListOf() }
        val newMessage = NotificationCompat.MessagingStyle.Message(safeDisplayContent, timestamp, agentPerson)
        synchronized(historyList) {
            historyList.add(newMessage)
            // Keep last 10 messages max in shade
            if (historyList.size > 10) historyList.removeAt(0)
        }

        // 3. Build MessagingStyle
        val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
            .setConversationTitle(agentName)
            .setGroupConversation(false)

        synchronized(historyList) {
            for (msg in historyList) {
                messagingStyle.addMessage(msg)
            }
        }

        // 4. Content Intent: Tapping notification opens MainActivity directly to chat
        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CHAT
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_AGENT_ID, agentId)
        }
        val pendingContentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // 5. Direct Reply Action (RemoteInput) — Telegram / WhatsApp style
        val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
            .setLabel("Reply to $agentName...")
            .build()

        val replyIntent = Intent(context, AgentNotificationActionReceiver::class.java).apply {
            action = ACTION_AGENT_REPLY
            putExtra(EXTRA_AGENT_ID, agentId)
            putExtra(EXTRA_AGENT_NAME, agentName)
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val replyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            replyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        )

        val replyAction = NotificationCompat.Action.Builder(
            R.drawable.ic_logo,
            "Reply",
            replyPendingIntent
        )
            .addRemoteInput(remoteInput)
            .setAllowGeneratedReplies(true)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .build()

        // 6. Quick Action: Copy Result to Clipboard (bound to 50k chars to prevent Binder TransactionTooLargeException)
        val copyIntent = Intent(context, AgentNotificationActionReceiver::class.java).apply {
            action = ACTION_AGENT_COPY
            putExtra(EXTRA_TEXT_TO_COPY, safeCopyContent)
        }
        val copyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val copyAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_save,
            "Copy",
            copyPendingIntent
        ).build()

        // 7. Assemble Notification
        val notification = NotificationCompat.Builder(context, CHANNEL_AGENT_MESSAGES)
            .setSmallIcon(R.drawable.ic_logo)
            .setColor(Color.parseColor("#00D2FF")) // Cyber Cyan
            .setStyle(messagingStyle)
            .setContentIntent(pendingContentIntent)
            .addAction(replyAction)
            .addAction(copyAction)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setGroup(GROUP_AGENT_NOTIFICATIONS)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)

        // 8. Publish Summary Notification for Android Grouping
        postGroupSummary()
    }

    /**
     * Posts a high-priority task completion alert with big text result preview.
     */
    @SuppressLint("MissingPermission")
    override fun postAgentTaskCompletedNotification(
        agentId: String,
        agentName: String,
        conversationId: String,
        taskTitle: String,
        resultSummary: String,
        isSuccess: Boolean
    ) {
        if (!hasPostNotificationPermission()) return

        val notificationId = getTaskIdNotificationId(agentId, taskTitle)

        val title = if (isSuccess) {
            "✅ $agentName • Task Completed"
        } else {
            "⚠️ $agentName • Task Failed"
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            action = ACTION_OPEN_CHAT
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_CONVERSATION_ID, conversationId)
            putExtra(EXTRA_AGENT_ID, agentId)
        }
        val pendingContentIntent = PendingIntent.getActivity(
            context,
            notificationId,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val safeDisplaySummary = if (resultSummary.length > 4000) resultSummary.take(4000) + "… [truncated]" else resultSummary
        val safeCopySummary = if (resultSummary.length > 50000) resultSummary.take(50000) else resultSummary

        val copyIntent = Intent(context, AgentNotificationActionReceiver::class.java).apply {
            action = ACTION_AGENT_COPY
            putExtra(EXTRA_TEXT_TO_COPY, safeCopySummary)
        }
        val copyPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            copyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bigTextStyle = NotificationCompat.BigTextStyle()
            .setBigContentTitle(title)
            .setSummaryText(taskTitle)
            .bigText(safeDisplaySummary)

        val notification = NotificationCompat.Builder(context, CHANNEL_AGENT_MESSAGES)
            .setSmallIcon(R.drawable.ic_logo)
            .setColor(if (isSuccess) Color.parseColor("#10B981") else Color.parseColor("#EF4444"))
            .setContentTitle(title)
            .setContentText(taskTitle)
            .setSubText(taskTitle)
            .setStyle(bigTextStyle)
            .setContentIntent(pendingContentIntent)
            .addAction(android.R.drawable.ic_menu_save, "Copy Output", copyPendingIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setGroup(GROUP_AGENT_NOTIFICATIONS)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
        postGroupSummary()
    }

    /**
     * Posts an ongoing progress notification for long-running background tasks.
     */
    @SuppressLint("MissingPermission")
    override fun postAgentTaskProgressNotification(
        agentId: String,
        agentName: String,
        taskTitle: String,
        progressPercent: Int?
    ) {
        if (!hasPostNotificationPermission()) return

        val notificationId = getTaskIdNotificationId(agentId, taskTitle)

        val builder = NotificationCompat.Builder(context, CHANNEL_AGENT_TASKS)
            .setSmallIcon(R.drawable.ic_logo)
            .setColor(Color.parseColor("#3D8EFF"))
            .setContentTitle("⚙️ $agentName is working...")
            .setContentText(taskTitle)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        if (progressPercent != null) {
            builder.setProgress(100, progressPercent.coerceIn(0, 100), false)
        } else {
            builder.setProgress(0, 0, true) // Indeterminate spinner
        }

        NotificationManagerCompat.from(context).notify(notificationId, builder.build())
    }

    /**
     * Updates an existing notification to show the user's inline reply in real time.
     */
    @SuppressLint("MissingPermission")
    fun updateWithUserReply(
        conversationId: String,
        agentId: String,
        agentName: String,
        userReplyText: String,
        notificationId: Int
    ) {
        if (!hasPostNotificationPermission()) return

        val userPerson = Person.Builder().setName("You").build()
        val agentPerson = Person.Builder().setKey(agentId).setName(agentName).setBot(true).build()

        val safeReplyText = if (userReplyText.length > 4000) userReplyText.take(4000) + "… [truncated]" else userReplyText
        val historyList = conversationHistory.getOrPut(conversationId) { mutableListOf() }
        synchronized(historyList) {
            historyList.add(
                NotificationCompat.MessagingStyle.Message(
                    safeReplyText,
                    System.currentTimeMillis(),
                    userPerson
                )
            )
        }

        val messagingStyle = NotificationCompat.MessagingStyle(userPerson)
            .setConversationTitle(agentName)
            .setGroupConversation(false)

        synchronized(historyList) {
            for (msg in historyList) {
                messagingStyle.addMessage(msg)
            }
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_AGENT_MESSAGES)
            .setSmallIcon(R.drawable.ic_logo)
            .setColor(Color.parseColor("#00D2FF"))
            .setStyle(messagingStyle)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setGroup(GROUP_AGENT_NOTIFICATIONS)
            .build()

        NotificationManagerCompat.from(context).notify(notificationId, notification)
    }

    @SuppressLint("MissingPermission")
    private fun postGroupSummary() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val summaryNotification = NotificationCompat.Builder(context, CHANNEL_AGENT_MESSAGES)
                .setSmallIcon(R.drawable.ic_logo)
                .setStyle(
                    NotificationCompat.InboxStyle()
                        .setSummaryText("Baton Sovereign Alerts")
                )
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setGroup(GROUP_AGENT_NOTIFICATIONS)
                .setGroupSummary(true)
                .build()

            NotificationManagerCompat.from(context).notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
        }
    }

    override fun dismissNotification(notificationId: Int) {
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    override fun dismissConversationNotifications(conversationId: String) {
        val notificationId = getNotificationIdForConversation(conversationId)
        dismissNotification(notificationId)
        conversationHistory.remove(conversationId)
    }
}
