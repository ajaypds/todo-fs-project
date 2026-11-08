package com.example.todoflow.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.todoflow.TodoFlowApplication
import java.util.concurrent.TimeUnit

/** Uploads changes that were made offline as soon as the device has a connection again. */
class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as TodoFlowApplication
        if (!app.sessionManager.isLoggedIn.value) return Result.success()

        val result = app.taskRepository.flushPendingOps()
        return when {
            result.remaining == 0 -> Result.success()
            // Give up after a while; the next app start or refresh tries again
            runAttemptCount >= MAX_ATTEMPTS -> Result.success()
            else -> Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_NAME = "pending_sync"
        private const val MAX_ATTEMPTS = 8

        fun schedule(context: Context) {
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork(UNIQUE_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
        }
    }
}
