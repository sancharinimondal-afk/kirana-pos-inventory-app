package com.example.backup

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Schedules background automatic backups using Android WorkManager.
 * - Periodic daily backup to maintain daily safety restore points.
 * - Debounced event-driven backup after important database mutations (sales, stock changes, products).
 * - Avoids excessive backup operations using 15-second debounce window and Replace policy.
 */
object AutoBackupScheduler {

    private const val TAG = "AutoBackupScheduler"

    /**
     * Schedules a recurring 24-hour daily automatic backup.
     */
    fun scheduleDailyBackup(context: Context) {
        try {
            val constraints = Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build()

            val dailyWork = PeriodicWorkRequestBuilder<AutoBackupWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                AutoBackupWorker.WORK_NAME_DAILY,
                ExistingPeriodicWorkPolicy.KEEP,
                dailyWork
            )
            Log.i(TAG, "Enqueued daily periodic auto-backup work")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule daily auto-backup work: ${e.message}", e)
        }
    }

    /**
     * Schedules a debounced backup after important database changes (new sale, inventory stock change, product modification).
     * Uses ExistingWorkPolicy.REPLACE with a 15-second delay so rapid successive changes
     * (e.g., adding multiple products or rapid transactions) collapse into a single backup.
     */
    fun scheduleBackupAfterDatabaseChange(context: Context) {
        try {
            if (!AutoBackupManager.isAutoBackupEnabled(context)) {
                return
            }

            val constraints = Constraints.Builder()
                .setRequiresStorageNotLow(true)
                .build()

            val changeWork = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setInitialDelay(15, TimeUnit.SECONDS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                AutoBackupWorker.WORK_NAME_DB_CHANGE,
                ExistingWorkPolicy.REPLACE,
                changeWork
            )
            Log.d(TAG, "Enqueued debounced auto-backup following database change (15s delay)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule change-triggered auto-backup: ${e.message}", e)
        }
    }

    /**
     * Immediately triggers an automatic backup via WorkManager (bypassing debounce).
     */
    fun triggerImmediateBackup(context: Context) {
        try {
            val immediateWork = OneTimeWorkRequestBuilder<AutoBackupWorker>()
                .setInputData(workDataOf(AutoBackupWorker.KEY_FORCE_BACKUP to true))
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                "immediate_auto_backup_${System.currentTimeMillis()}",
                ExistingWorkPolicy.REPLACE,
                immediateWork
            )
            Log.i(TAG, "Enqueued immediate auto-backup work")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue immediate auto-backup: ${e.message}", e)
        }
    }

    /**
     * Cancels scheduled automatic backup work if the user disables auto-backup in settings.
     */
    fun cancelScheduledWork(context: Context) {
        try {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork(AutoBackupWorker.WORK_NAME_DAILY)
            wm.cancelUniqueWork(AutoBackupWorker.WORK_NAME_DB_CHANGE)
            Log.i(TAG, "Cancelled scheduled auto-backup tasks")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel auto-backup work: ${e.message}", e)
        }
    }
}
