package com.ekam.baton.notification

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ekam.baton.core.data.notification.AgentNotificationDispatcher
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * AgentTaskSyncWorker
 *
 * Background WorkManager worker that monitors long-running asynchronous tasks
 * executed on the Desktop Bridge or remote agent hub.
 *
 * When an agent initiates a build, test suite, or complex shell command:
 * 1. Baton enqueues this worker with task metadata.
 * 2. This worker polls the bridge `/tasks/:taskId` endpoint in the background,
 *    even if Baton is killed or the phone is asleep.
 * 3. On completion, it triggers a WhatsApp/Telegram-level heads-up notification
 *    with the command output summary.
 */
class AgentTaskSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val notificationDispatcher: AgentNotificationDispatcher by inject()
    private val httpClient: OkHttpClient by inject()

    companion object {
        const val KEY_AGENT_ID = "agent_id"
        const val KEY_AGENT_NAME = "agent_name"
        const val KEY_ENDPOINT_URL = "endpoint_url"
        const val KEY_TOKEN = "token"
        const val KEY_TASK_ID = "task_id"
        const val KEY_TASK_TITLE = "task_title"
        const val KEY_CONVERSATION_ID = "conversation_id"

        private const val MAX_POLL_DURATION_MS = 15 * 60 * 1000L // 15 minutes max
        private const val INITIAL_POLL_INTERVAL_MS = 2000L // 2 seconds

        /**
         * Enqueues background monitoring for an asynchronous agent task.
         */
        @Suppress("LongParameterList")
        fun enqueueTaskMonitoring(
            context: Context,
            agentId: String,
            agentName: String,
            endpointUrl: String,
            token: String?,
            taskId: String,
            taskTitle: String,
            conversationId: String
        ) {
            val inputData = Data.Builder()
                .putString(KEY_AGENT_ID, agentId)
                .putString(KEY_AGENT_NAME, agentName)
                .putString(KEY_ENDPOINT_URL, endpointUrl)
                .putString(KEY_TOKEN, token)
                .putString(KEY_TASK_ID, taskId)
                .putString(KEY_TASK_TITLE, taskTitle)
                .putString(KEY_CONVERSATION_ID, conversationId)
                .build()

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = OneTimeWorkRequestBuilder<AgentTaskSyncWorker>()
                .setInputData(inputData)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "AgentTask_$taskId",
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    @Suppress("NestedBlockDepth", "ReturnCount", "TooGenericExceptionCaught", "SwallowedException")
    override suspend fun doWork(): Result {
        val agentId = inputData.getString(KEY_AGENT_ID) ?: return Result.failure()
        val agentName = inputData.getString(KEY_AGENT_NAME) ?: "Agent"
        val endpointUrl = inputData.getString(KEY_ENDPOINT_URL) ?: return Result.failure()
        val token = inputData.getString(KEY_TOKEN)
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val taskTitle = inputData.getString(KEY_TASK_TITLE) ?: "Remote Task"
        val conversationId = inputData.getString(KEY_CONVERSATION_ID) ?: ""

        val baseUrl = endpointUrl.trimEnd('/')
        val taskUrl = "$baseUrl/tasks/$taskId"

        val startTime = System.currentTimeMillis()
        var pollInterval = INITIAL_POLL_INTERVAL_MS

        // Post initial ongoing progress notification
        notificationDispatcher.postAgentTaskProgressNotification(
            agentId = agentId,
            agentName = agentName,
            taskTitle = taskTitle,
            progressPercent = null
        )

        while (System.currentTimeMillis() - startTime < MAX_POLL_DURATION_MS) {
            try {
                val requestBuilder = Request.Builder().url(taskUrl).get()
                if (!token.isNullOrBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $token")
                }

                val response = httpClient.newCall(requestBuilder.build()).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val json = JSONObject(bodyString)
                    val status = json.optString("status", "running")

                    when (status) {
                        "completed" -> {
                            val stdout = json.optString("stdout", "").trim()
                            val durationMs = json.optLong("durationMs", 0L)
                            val summary = if (stdout.isNotBlank()) {
                                if (stdout.length > 300) stdout.take(300) + "..." else stdout
                            } else {
                                "Task completed successfully (${durationMs}ms)."
                            }

                            notificationDispatcher.postAgentTaskCompletedNotification(
                                agentId = agentId,
                                agentName = agentName,
                                conversationId = conversationId,
                                taskTitle = taskTitle,
                                resultSummary = summary,
                                isSuccess = true
                            )
                            return Result.success()
                        }

                        "failed" -> {
                            val stderr = json.optString("stderr", "").trim()
                            val errMessage = json.optString("error", stderr)
                            val summary = if (errMessage.isNotBlank()) {
                                if (errMessage.length > 300) errMessage.take(300) + "..." else errMessage
                            } else {
                                "Task failed with non-zero exit code."
                            }

                            notificationDispatcher.postAgentTaskCompletedNotification(
                                agentId = agentId,
                                agentName = agentName,
                                conversationId = conversationId,
                                taskTitle = taskTitle,
                                resultSummary = summary,
                                isSuccess = false
                            )
                            return Result.failure()
                        }

                        else -> {
                            // Still running -> update progress
                            val percent = if (json.has("progress")) json.optInt("progress") else null
                            notificationDispatcher.postAgentTaskProgressNotification(
                                agentId = agentId,
                                agentName = agentName,
                                taskTitle = taskTitle,
                                progressPercent = percent
                            )
                        }
                    }
                }
            } catch (e: java.io.IOException) {
                android.util.Log.w("AgentTaskSyncWorker", "Transient network glitch polling task $taskId: ${e.message}")
            }

            delay(pollInterval)
            // Backoff interval up to 8s
            if (pollInterval < 8000L) {
                pollInterval = (pollInterval * 1.5).toLong().coerceAtMost(8000L)
            }
        }

        // Timed out
        notificationDispatcher.postAgentTaskCompletedNotification(
            agentId = agentId,
            agentName = agentName,
            conversationId = conversationId,
            taskTitle = taskTitle,
            resultSummary = "Task monitoring timed out after 15 minutes.",
            isSuccess = false
        )
        return Result.failure()
    }
}
