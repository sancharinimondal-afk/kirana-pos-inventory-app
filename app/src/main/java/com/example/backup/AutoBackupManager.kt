package com.example.backup

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.data.KiranaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class AutoBackupResult(
    val success: Boolean,
    val file: File? = null,
    val productsCount: Int = 0,
    val salesCount: Int = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)

data class AutoBackupState(
    val isEnabled: Boolean = true,
    val lastBackupTimestamp: Long = 0L,
    val lastBackupStatus: String = "Not yet run",
    val lastBackupFileName: String = "",
    val availableBackupsCount: Int = 0,
    val latestBackupFile: File? = null
)

/**
 * Manages automatic local backups in dedicated internal storage.
 * - Stores files completely separate from user-initiated manual exports.
 * - Guarantees non-blocking read-only snapshots from Room database.
 * - Enforces minimum throttle intervals to avoid excessive disk operations.
 * - Automatically prunes older backups to preserve device storage.
 * - If storage is unavailable or an error occurs, logs a clear failure without touching the database.
 */
object AutoBackupManager {

    private const val TAG = "AutoBackupManager"
    private const val PREFS_NAME = "kirana_auto_backup_prefs"
    private const val KEY_ENABLED = "auto_backup_enabled"
    private const val KEY_LAST_BACKUP_TIME = "last_backup_timestamp"
    private const val KEY_LAST_BACKUP_STATUS = "last_backup_status"
    private const val KEY_LAST_BACKUP_FILE = "last_backup_filename"
    private const val KEY_PRODUCTS_COUNT = "last_backup_products_count"
    private const val KEY_SALES_COUNT = "last_backup_sales_count"

    const val AUTO_BACKUPS_DIR_NAME = "auto_backups"
    const val DEFAULT_MAX_RETENTION = 7
    const val MIN_BACKUP_INTERVAL_MS = 30_000L // 30-second debounce throttle

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun isAutoBackupEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ENABLED, true)
    }

    fun setAutoBackupEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
        Log.i(TAG, "Auto-backup enabled set to: $enabled")
    }

    fun getLastBackupTimestamp(context: Context): Long {
        return getPrefs(context).getLong(KEY_LAST_BACKUP_TIME, 0L)
    }

    fun getLastBackupStatus(context: Context): String {
        return getPrefs(context).getString(KEY_LAST_BACKUP_STATUS, "Not yet run") ?: "Not yet run"
    }

    /**
     * Dedicated local internal directory for automatic backups.
     * Guaranteed separate from manual exports (which are saved in external/SAF folders).
     */
    fun getAutoBackupDirectory(context: Context): File {
        val dir = File(context.filesDir, AUTO_BACKUPS_DIR_NAME)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    /**
     * Lists all stored auto-backup files sorted from newest to oldest.
     */
    fun listAutoBackups(context: Context): List<File> {
        val dir = getAutoBackupDirectory(context)
        val files = dir.listFiles { file ->
            file.isFile && file.name.startsWith("auto_backup_") && file.name.endsWith(".json")
        } ?: emptyArray()
        return files.sortedByDescending { it.lastModified() }
    }

    fun getLatestAutoBackup(context: Context): File? {
        return listAutoBackups(context).firstOrNull()
    }

    fun getAutoBackupState(context: Context): AutoBackupState {
        val prefs = getPrefs(context)
        val backups = listAutoBackups(context)
        return AutoBackupState(
            isEnabled = prefs.getBoolean(KEY_ENABLED, true),
            lastBackupTimestamp = prefs.getLong(KEY_LAST_BACKUP_TIME, 0L),
            lastBackupStatus = prefs.getString(KEY_LAST_BACKUP_STATUS, "Not yet run") ?: "Not yet run",
            lastBackupFileName = prefs.getString(KEY_LAST_BACKUP_FILE, "") ?: "",
            availableBackupsCount = backups.size,
            latestBackupFile = backups.firstOrNull()
        )
    }

    /**
     * Prunes old auto-backups, retaining only the most recent [maxKeep] files.
     */
    fun pruneOldBackups(context: Context, maxKeep: Int = DEFAULT_MAX_RETENTION) {
        val backups = listAutoBackups(context)
        if (backups.size > maxKeep) {
            val toDelete = backups.drop(maxKeep)
            for (file in toDelete) {
                try {
                    val deleted = file.delete()
                    if (deleted) {
                        Log.d(TAG, "Pruned older auto-backup: ${file.name}")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Could not delete old auto-backup ${file.name}: ${e.message}")
                }
            }
        }
    }

    /**
     * Performs an automatic local backup.
     * - Never blocks the UI thread (runs on Dispatchers.IO).
     * - Reads data via non-locking Room SELECT queries without interfering with active billing.
     * - Checks storage availability and logs clear failure if storage is unavailable.
     * - NEVER deletes or touches the database upon failure.
     */
    suspend fun performAutoBackup(
        context: Context,
        repository: KiranaRepository,
        force: Boolean = false
    ): AutoBackupResult = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()

        // Step 1: Check if feature is enabled
        if (!isAutoBackupEnabled(context) && !force) {
            val msg = "Auto-backup skipped: Disabled in settings"
            Log.d(TAG, msg)
            return@withContext AutoBackupResult(success = false, errorMessage = msg)
        }

        // Step 2: Rate-limiting / Debounce to avoid excessive backup writes
        val lastBackupTime = getLastBackupTimestamp(context)
        if (!force && (now - lastBackupTime) < MIN_BACKUP_INTERVAL_MS) {
            val msg = "Auto-backup throttled: Last backup was ${(now - lastBackupTime) / 1000}s ago"
            Log.d(TAG, msg)
            return@withContext AutoBackupResult(
                success = true,
                errorMessage = msg,
                timestamp = lastBackupTime
            )
        }

        // Step 3: Check storage availability
        val backupDir = File(context.filesDir, AUTO_BACKUPS_DIR_NAME)
        if (backupDir.exists() && !backupDir.isDirectory) {
            val errorMsg = "Storage unavailable: auto_backups path exists but is not a directory"
            Log.e(TAG, errorMsg)
            recordBackupFailure(context, errorMsg)
            return@withContext AutoBackupResult(success = false, errorMessage = errorMsg)
        }

        if (!backupDir.exists()) {
            val created = backupDir.mkdirs()
            if (!created && !backupDir.exists()) {
                val errorMsg = "Storage unavailable: Failed to create auto_backups directory at ${backupDir.absolutePath}"
                Log.e(TAG, errorMsg)
                recordBackupFailure(context, errorMsg)
                // Database is completely safe and untouched!
                return@withContext AutoBackupResult(success = false, errorMessage = errorMsg)
            }
        }

        if (!backupDir.canWrite()) {
            val errorMsg = "Storage unavailable: auto_backups directory is not writable"
            Log.e(TAG, errorMsg)
            recordBackupFailure(context, errorMsg)
            // Database is completely safe and untouched!
            return@withContext AutoBackupResult(success = false, errorMessage = errorMsg)
        }

        // Step 4: Perform read-only data extraction from Room repository
        // Does not block billing or lock database writes
        val products = repository.getAllProductsDirect()
        val transactions = repository.getAllTransactionsDirect()
        val saleItems = repository.getAllSaleItemsDirect()
        val settings = repository.getShopSettingsSync()
        val sequences = repository.getAllSequencesDirect()
        val customers = repository.getAllCustomersDirect()
        val ledger = repository.getAllLedgerEntriesDirect()
        val purchases = repository.getAllPurchasesDirect()
        val purchaseItems = repository.getAllPurchaseItemsDirect()
        val stockMovements = repository.getAllStockMovementsDirect()

        // Step 5: Serialize into standardized versioned JSON
        val jsonContent = BackupManager.buildBackupJsonString(
            products = products,
            transactions = transactions,
            saleItems = saleItems,
            settings = settings,
            invoiceSequences = sequences,
            customers = customers,
            ledger = ledger,
            purchases = purchases,
            purchaseItems = purchaseItems,
            stockMovements = stockMovements
        )

        // Step 6: Atomic file write via temp file
        val timeStampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date(now))
        val targetFile = File(backupDir, "auto_backup_$timeStampStr.json")
        val tempFile = File(backupDir, "auto_backup_tmp_${now}.tmp")

        try {
            tempFile.writeText(jsonContent, Charsets.UTF_8)
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                targetFile.writeText(jsonContent, Charsets.UTF_8)
                tempFile.delete()
            }
        } catch (e: IOException) {
            tempFile.delete()
            val errorMsg = "Storage unavailable: I/O failure writing auto-backup: ${e.message}"
            Log.e(TAG, errorMsg, e)
            recordBackupFailure(context, errorMsg)
            // Database is completely untouched!
            return@withContext AutoBackupResult(success = false, errorMessage = errorMsg)
        } catch (e: Exception) {
            tempFile.delete()
            val errorMsg = "Storage unavailable: Unexpected error writing auto-backup: ${e.message}"
            Log.e(TAG, errorMsg, e)
            recordBackupFailure(context, errorMsg)
            // Database is completely untouched!
            return@withContext AutoBackupResult(success = false, errorMessage = errorMsg)
        }

        // Step 7: Prune older backups to prevent unbounded disk growth
        pruneOldBackups(context, DEFAULT_MAX_RETENTION)

        // Step 8: Update persistent record of successful backup
        recordBackupSuccess(
            context = context,
            fileName = targetFile.name,
            timestamp = now,
            productsCount = products.size,
            salesCount = transactions.size
        )

        Log.i(TAG, "Automatic backup succeeded: ${targetFile.name} (${products.size} products, ${transactions.size} sales)")

        return@withContext AutoBackupResult(
            success = true,
            file = targetFile,
            productsCount = products.size,
            salesCount = transactions.size,
            timestamp = now
        )
    }

    private fun recordBackupSuccess(
        context: Context,
        fileName: String,
        timestamp: Long,
        productsCount: Int,
        salesCount: Int
    ) {
        getPrefs(context).edit()
            .putLong(KEY_LAST_BACKUP_TIME, timestamp)
            .putString(KEY_LAST_BACKUP_STATUS, "Success ($productsCount products, $salesCount sales)")
            .putString(KEY_LAST_BACKUP_FILE, fileName)
            .putInt(KEY_PRODUCTS_COUNT, productsCount)
            .putInt(KEY_SALES_COUNT, salesCount)
            .apply()
    }

    private fun recordBackupFailure(context: Context, errorMsg: String) {
        getPrefs(context).edit()
            .putString(KEY_LAST_BACKUP_STATUS, "Failed: $errorMsg")
            .apply()
    }
}
