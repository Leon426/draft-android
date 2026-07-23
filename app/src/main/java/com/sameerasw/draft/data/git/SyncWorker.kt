package com.sameerasw.draft.data.git

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val gitSyncManager = GitSyncManager(applicationContext)
        return if (gitSyncManager.isConfigured()) {
            val syncResult = gitSyncManager.sync()
            if (syncResult.isSuccess) Result.success() else Result.retry()
        } else {
            Result.failure()
        }
    }
}
