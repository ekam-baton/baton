package com.ekam.baton.core.data.memory

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import org.koin.core.component.inject

class EpisodicMemoryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), org.koin.core.component.KoinComponent {

    private val episodicMemoryGenerator: EpisodicMemoryGenerator by inject()

    override suspend fun doWork(): Result {
        val conversationId = inputData.getString("conversationId") ?: return Result.failure()
        val agentId = inputData.getString("agentId") ?: return Result.failure()

        return try {
            episodicMemoryGenerator.generateEpisodicSummary(conversationId, agentId)
            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            if (runAttemptCount < 3) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
