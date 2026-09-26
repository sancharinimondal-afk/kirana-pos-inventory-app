package com.example.backup

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.data.AppDatabase
import com.example.data.KiranaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background WorkManager worker for executing non-blocking automatic backups.
 * Executes on Dispatchers.IO to ensure zero UI freezing or interference with billing.
 */
class AutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val tag = "AutoBackupWorker"
        Log.i(tag, "AutoBackupWorker started (id: $id, runAttemptCount: $runAttemptCount)")

        try {
            val database = AppDatabase.getDatabase(applicationContext)
            val repository = KiranaRepository(database)
            val force = inputData.getBoolean(KEY_FORCE_BACKUP, false)

            val result = AutoBackupManager.performAutoBackup(
                context = applicationContext,
                repository = repository,
                force = force
            )

            if (result.success) {
                Log.i(tag, "AutoBackupWorker finished successfully. File: ${result.file?.name}")
                Result.success()
            } else {
                Log.w(tag, "AutoBackupWorker ended with failure message: ${result.errorMessage}")
                // Never delete or touch the database on backup failure
                if (runAttemptCount < 2) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "AutoBackupWorker encountered unexpected error: ${e.message}", e)
            // Database is safe and untouched
            Result.failure()
        }
    }

    companion object {
        const val KEY_FORCE_BACKUP = "force_backup"
        const val WORK_NAME_DAILY = "daily_auto_backup_work"
        const val WORK_NAME_DB_CHANGE = "auto_backup_db_change_work"
    }
}
