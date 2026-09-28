package com.example.my_mpesa_tracker.ui.dashboard

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ListenableWorker.Result as WorkResult
import java.util.concurrent.TimeUnit

/**
 * Runs BackupManager.backupNow in the background. Skips quietly (success, not failure)
 * if backup was never enabled — this worker can end up scheduled-but-idle rather than
 * needing to be precisely cancelled the moment someone signs out, since it's a no-op
 * either way once DriveAuthManager.isAuthorized is false.
 */
class BackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): WorkResult {
        if (!DriveAuthManager.isAuthorized(applicationContext)) {
            return WorkResult.success()
        }
        val result = BackupManager.backupNow(applicationContext)
        return if (result.isSuccess) WorkResult.success() else WorkResult.retry()
    }
}

object AutoBackupScheduler {
    private const val WORK_NAME = "pesalyzer_auto_backup"

    /** Safe to call on every app launch — KEEP means it won't reschedule if already queued. */
    fun scheduleIfEnabled(context: Context) {
        if (!DriveAuthManager.isAuthorized(context)) return
        val request = PeriodicWorkRequestBuilder<BackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}
