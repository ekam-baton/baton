package com.ekam.baton.core.data.memory

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class EpisodicMemoryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val episodicMemoryGenerator: EpisodicMemoryGenerator
) : CoroutineWorker(context, params) {

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
