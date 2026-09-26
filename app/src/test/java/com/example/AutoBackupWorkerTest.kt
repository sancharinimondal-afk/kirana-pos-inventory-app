package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import com.example.backup.AutoBackupManager
import com.example.backup.AutoBackupScheduler
import com.example.backup.AutoBackupWorker
import com.example.data.AppDatabase
import com.example.data.KiranaRepository
import com.example.data.model.CartItem
import com.example.data.model.ProductItem
import com.example.data.model.ShopSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AutoBackupWorkerTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: KiranaRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Configure in-memory Room database
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        AppDatabase.setTestDatabase(db)

        repository = KiranaRepository(
            productDao = db.productDao(),
            transactionDao = db.transactionDao(),
            shopSettingsDao = db.shopSettingsDao(),
            invoiceSequenceDao = db.invoiceSequenceDao(),
            saleItemDao = db.saleItemDao(),
            database = db
        )

        // Clear existing auto-backups folder and preferences before test
        val autoBackupDir = AutoBackupManager.getAutoBackupDirectory(context)
        autoBackupDir.deleteRecursively()
        autoBackupDir.mkdirs()

        AutoBackupManager.setAutoBackupEnabled(context, true)
    }

    @After
    fun tearDown() {
        db.close()
        AppDatabase.setTestDatabase(null)
        val autoBackupDir = AutoBackupManager.getAutoBackupDirectory(context)
        autoBackupDir.deleteRecursively()
    }

    /**
     * MANDATORY USER CUJ TEST:
     * Create product
     * → create sale
     * → modify stock
     * → wait/run Worker
     * → verify backup is generated.
     * Then restore the generated backup and verify integrity.
     */
    @Test
    fun testMandatoryCUJ_CreateProduct_CreateSale_ModifyStock_RunWorker_VerifyBackup_Restore_VerifyIntegrity() {
        runBlocking {
            // Step 1: Create product
            val initialProduct = ProductItem(
                name = "Fortune Kachi Ghani Mustard Oil 1L",
                bengaliName = "ফরচুন সরিষার তেল ১ লিটার",
                barcode = "890103000001",
                category = "Edible Oil",
                unit = "Litre",
                costPrice = 125.0,
                sellingPrice = 150.0,
                mrp = 160.0,
                gstRate = 5.0,
                currentStock = 50.0,
                minStockAlert = 10.0,
                rackLocation = "Aisle-1-Rack-B"
            )
            val productId = repository.insertProduct(initialProduct)
            assertTrue("Product must be inserted with valid ID", productId > 0)

            // Save shop profile
            val shopProfile = ShopSettings(
                shopName = "Bhowmick Kirana Store",
                tagline = "Quality Ration & Daily Essentials",
                ownerName = "Ashok Bhowmick",
                phone = "9830012345",
                address = "Station Road, Ranaghat",
                city = "Nadia, West Bengal",
                gstin = "19AAACB1234F1Z5"
            )
            repository.saveShopSettings(shopProfile)

            // Step 2: Create sale
            val cartItem = CartItem(
                productId = productId,
                barcode = initialProduct.barcode,
                name = initialProduct.name,
                unit = initialProduct.unit,
                rate = initialProduct.sellingPrice,
                quantity = 5.0,
                mrp = initialProduct.mrp,
                costPrice = initialProduct.costPrice,
                gstRate = initialProduct.gstRate
            )

            val saleResult = repository.completeSaleTransaction(
                items = listOf(cartItem),
                customerName = "Subhash Bose",
                customerPhone = "9876543210",
                paymentMode = "CASH",
                discountAmount = 10.0
            )

            assertNotNull("Sale transaction must be created", saleResult.transaction)
            val invoiceNumber = saleResult.transaction.invoiceNumber
            assertTrue("Invoice must have proper prefix", invoiceNumber.startsWith("INV-"))

            // Verify stock deducted to 45.0 after selling 5.0
            val productAfterSale = repository.getProductById(productId)
            assertNotNull(productAfterSale)
            assertEquals(45.0, productAfterSale!!.currentStock, 0.001)

            // Step 3: Modify stock
            // Receive fresh stock shipment of +20.0 units
            repository.increaseStock(productId, 20.0)
            val productAfterStockMod = repository.getProductById(productId)
            assertNotNull(productAfterStockMod)
            assertEquals("Stock after modification must be 65.0", 65.0, productAfterStockMod!!.currentStock, 0.001)

            // Step 4: Run AutoBackupWorker
            val worker = TestListenableWorkerBuilder<AutoBackupWorker>(context)
                .setInputData(workDataOf(AutoBackupWorker.KEY_FORCE_BACKUP to true))
                .build()

            val workerResult = worker.doWork()
            assertEquals("AutoBackupWorker must return success", ListenableWorker.Result.success(), workerResult)

            // Step 5: Verify backup file is generated in the dedicated folder
            val backupsList = AutoBackupManager.listAutoBackups(context)
            assertEquals("Exactly one auto-backup file should be generated", 1, backupsList.size)

            val generatedBackupFile = backupsList.first()
            assertTrue("Backup file must exist on disk", generatedBackupFile.exists())
            assertTrue("Backup file must not be empty", generatedBackupFile.length() > 0)
            assertTrue("File name must follow naming convention", generatedBackupFile.name.startsWith("auto_backup_"))
            assertTrue("File name must end with .json", generatedBackupFile.name.endsWith(".json"))

            val backupJsonString = generatedBackupFile.readText(Charsets.UTF_8)
            assertTrue("JSON must contain product name", backupJsonString.contains("Fortune Kachi Ghani Mustard Oil"))
            assertTrue("JSON must contain invoice number", backupJsonString.contains(invoiceNumber))
            assertTrue("JSON must contain shop name", backupJsonString.contains("Bhowmick Kirana Store"))

            // Step 6: Simulate a second fresh database to test restoration
            val restoreDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
                .allowMainThreadQueries()
                .build()
            val restoreRepository = KiranaRepository(
                productDao = restoreDb.productDao(),
                transactionDao = restoreDb.transactionDao(),
                shopSettingsDao = restoreDb.shopSettingsDao(),
                invoiceSequenceDao = restoreDb.invoiceSequenceDao(),
                saleItemDao = restoreDb.saleItemDao(),
                database = restoreDb
            )

            // Step 7: Restore the generated backup and verify complete integrity
            val restoreResult = restoreRepository.restoreFromBackupJson(backupJsonString)
            assertTrue("Restoration must succeed: ${restoreResult.message}", restoreResult.success)
            assertEquals(1, restoreResult.productsRestored)
            assertEquals(1, restoreResult.salesRestored)
            assertEquals(1, restoreResult.saleItemsRestored)

            // Verify restored product
            val restoredProducts = restoreRepository.getAllProductsDirect()
            assertEquals(1, restoredProducts.size)
            val restoredProduct = restoredProducts.first()
            assertEquals("Fortune Kachi Ghani Mustard Oil 1L", restoredProduct.name)
            assertEquals("890103000001", restoredProduct.barcode)
            // Verify stock matches the modified stock value (65.0)
            assertEquals(65.0, restoredProduct.currentStock, 0.001)
            assertEquals(125.0, restoredProduct.costPrice, 0.001)
            assertEquals(150.0, restoredProduct.sellingPrice, 0.001)

            // Verify restored transaction
            val restoredTransactions = restoreRepository.getAllTransactionsDirect()
            assertEquals(1, restoredTransactions.size)
            val restoredTx = restoredTransactions.first()
            assertEquals(invoiceNumber, restoredTx.invoiceNumber)
            assertEquals("Subhash Bose", restoredTx.customerName)
            assertEquals("CASH", restoredTx.paymentMode)

            // Verify restored sale item historical snapshot
            val restoredSaleItems = restoreRepository.getAllSaleItemsDirect()
            assertEquals(1, restoredSaleItems.size)
            val restoredSaleItem = restoredSaleItems.first()
            assertEquals("Fortune Kachi Ghani Mustard Oil 1L", restoredSaleItem.productName)
            assertEquals(5.0, restoredSaleItem.quantity, 0.001)
            assertEquals(150.0, restoredSaleItem.sellingPrice, 0.001)

            // Verify restored shop profile
            val restoredSettings = restoreRepository.getShopSettingsSync()
            assertNotNull(restoredSettings)
            assertEquals("Bhowmick Kirana Store", restoredSettings!!.shopName)
            assertEquals("Ashok Bhowmick", restoredSettings.ownerName)

            restoreDb.close()
        }
    }

    /**
     * Requirement: Keep automatic backup files separate from manual exports.
     */
    @Test
    fun testAutoBackupFilesSeparatedFromManualExports() {
        val autoDir = AutoBackupManager.getAutoBackupDirectory(context)
        val manualDir = File(context.getExternalFilesDir("backups") ?: context.filesDir, "manual_backups")

        assertNotEquals(
            "Auto backup directory must be distinct from manual export path",
            autoDir.absolutePath,
            manualDir.absolutePath
        )
        assertTrue(
            "Auto backup directory path must contain 'auto_backups'",
            autoDir.absolutePath.contains("auto_backups")
        )
    }

    /**
     * Requirement: Avoid excessive backup operations (throttling check).
     */
    @Test
    fun testAvoidExcessiveBackupOperations() {
        runBlocking {
            // Insert sample product
            repository.insertProduct(
                ProductItem(
                    barcode = "8901234567899",
                    name = "Sugar 1kg",
                    category = "Staples",
                    costPrice = 38.0,
                    sellingPrice = 45.0,
                    mrp = 50.0,
                    currentStock = 20.0
                )
            )

            // First backup (forced)
            val result1 = AutoBackupManager.performAutoBackup(context, repository, force = true)
            assertTrue("First forced backup must succeed", result1.success)
            assertEquals(1, AutoBackupManager.listAutoBackups(context).size)

            // Immediate subsequent backup without force flag should be throttled
            val result2 = AutoBackupManager.performAutoBackup(context, repository, force = false)
            assertTrue("Throttled result indicates safety", result2.success)
            assertTrue(
                "ErrorMessage should mention throttling",
                result2.errorMessage?.contains("throttled", ignoreCase = true) == true
            )

            // Count of files on disk should remain 1 (no excessive disk writes)
            assertEquals(1, AutoBackupManager.listAutoBackups(context).size)
        }
    }

    /**
     * Requirement: If storage is unavailable: show/log clear backup failure.
     * Do NOT delete the database if backup fails.
     */
    @Test
    fun testStorageUnavailable_DoesNotDeleteDatabase() {
        runBlocking {
            // Create products and verify data exists
            repository.insertProduct(
                ProductItem(
                    barcode = "8901234567898",
                    name = "Atta 10kg",
                    category = "Staples",
                    costPrice = 320.0,
                    sellingPrice = 380.0,
                    mrp = 420.0,
                    currentStock = 15.0
                )
            )
            assertEquals("Initial product count should be 1", 1, repository.getProductCountDirect())

            // Create a read-only file where the directory is supposed to be, simulating an unwriteable path
            val autoBackupDir = AutoBackupManager.getAutoBackupDirectory(context)
            autoBackupDir.deleteRecursively()
            // Create as file instead of directory to simulate storage creation error
            autoBackupDir.createNewFile()
            autoBackupDir.setReadOnly()

            // Attempt backup
            val result = AutoBackupManager.performAutoBackup(context, repository, force = true)

            // Verify failure was handled gracefully
            assertFalse("Backup must fail when storage is unavailable", result.success)
            assertNotNull("Error message must be present", result.errorMessage)
            assertTrue(
                "Error message must indicate storage issue",
                result.errorMessage!!.contains("Storage unavailable") || result.errorMessage!!.contains("Failed")
            )

            // CRITICAL: Verify database is intact and NOT deleted
            val productsAfterFailure = repository.getAllProductsDirect()
            assertEquals("Database must NOT be deleted on backup failure", 1, productsAfterFailure.size)
            assertEquals("Atta 10kg", productsAfterFailure.first().name)

            // Check that failure was recorded in preferences
            val status = AutoBackupManager.getLastBackupStatus(context)
            assertTrue("Status must record failure", status.startsWith("Failed:"))

            // Clean up
            autoBackupDir.delete()
        }
    }

    /**
     * Requirement: Pruning old backups keeps storage bounded.
     */
    @Test
    fun testPruningOldBackups() {
        runBlocking {
            val dir = AutoBackupManager.getAutoBackupDirectory(context)
            // Create 10 dummy auto-backup files
            for (i in 1..10) {
                val file = File(dir, "auto_backup_2026090${i}_120000.json")
                file.writeText("{}", Charsets.UTF_8)
                file.setLastModified(1000L * i)
            }

            assertEquals(10, AutoBackupManager.listAutoBackups(context).size)

            // Prune to keep latest 7
            AutoBackupManager.pruneOldBackups(context, maxKeep = 7)

            val remaining = AutoBackupManager.listAutoBackups(context)
            assertEquals("Must retain exactly 7 files after pruning", 7, remaining.size)
        }
    }

    /**
     * Verify WorkManager scheduling helpers run without exceptions.
     */
    @Test
    fun testAutoBackupSchedulerInvocations() {
        // Test daily schedule enqueue
        AutoBackupScheduler.scheduleDailyBackup(context)

        // Test database-change schedule enqueue
        AutoBackupScheduler.scheduleBackupAfterDatabaseChange(context)

        // Test immediate backup enqueue
        AutoBackupScheduler.triggerImmediateBackup(context)

        // Test cancel scheduled work
        AutoBackupScheduler.cancelScheduledWork(context)
    }
}
