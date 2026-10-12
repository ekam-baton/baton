package com.ekam.baton.notification

import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.ekam.baton.core.data.repository.ChatRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * AgentNotificationActionReceiver
 *
 * Handles background actions triggered directly from the Android notification shade:
 * 1. Direct Reply (RemoteInput): Dispatches the user's quick reply to ChatRepository
 *    without opening the app, exactly like WhatsApp and Telegram.
 * 2. Copy Output: Copies the agent's task output to the system clipboard.
 */
class AgentNotificationActionReceiver : BroadcastReceiver(), KoinComponent {

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val chatRepository: ChatRepository by inject()
    private val notificationManager: BatonNotificationManager by inject()

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            BatonNotificationManager.ACTION_AGENT_REPLY -> {
                handleDirectReply(intent)
            }

            BatonNotificationManager.ACTION_AGENT_COPY -> {
                handleCopyOutput(context, intent)
            }
        }
    }

    @Suppress("ReturnCount", "TooGenericExceptionCaught")
    private fun handleDirectReply(intent: Intent) {
        val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return
        val replyText = remoteInput.getCharSequence(BatonNotificationManager.KEY_TEXT_REPLY)?.toString()
        if (replyText.isNullOrBlank()) return

        val agentId = intent.getStringExtra(BatonNotificationManager.EXTRA_AGENT_ID) ?: return
        val agentName = intent.getStringExtra(BatonNotificationManager.EXTRA_AGENT_NAME) ?: "Agent"
        val conversationId = intent.getStringExtra(BatonNotificationManager.EXTRA_CONVERSATION_ID) ?: return
        val notificationId = intent.getIntExtra(BatonNotificationManager.EXTRA_NOTIFICATION_ID, 0)

        // 1. Immediately update notification shade with user's inline reply
        notificationManager.updateWithUserReply(
            conversationId = conversationId,
            agentId = agentId,
            agentName = agentName,
            userReplyText = replyText,
            notificationId = notificationId
        )

        // 2. Dispatch message to ChatRepository asynchronously
        receiverScope.launch {
            try {
                chatRepository.sendMessageWithResponse(
                    conversationId = conversationId,
                    content = replyText,
                    attachments = emptyList(),
                    authHeader = null,
                    extractFacts = false
                ).collect()
            } catch (e: Exception) {
                // If failed, show error notification
                notificationManager.postAgentMessageNotification(
                    agentId = agentId,
                    agentName = agentName,
                    conversationId = conversationId,
                    content = "Failed to send direct reply: ${e.message}"
                )
            }
        }
    }

    private fun handleCopyOutput(context: Context, intent: Intent) {
        val textToCopy = intent.getStringExtra(BatonNotificationManager.EXTRA_TEXT_TO_COPY) ?: return
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard != null) {
            val clip = ClipData.newPlainText("Baton Agent Output", textToCopy)
            clipboard.setPrimaryClip(clip)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Copied agent output to clipboard", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
