package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.backup.BackupManager
import com.example.data.AppDatabase
import com.example.data.DATABASE_CALLBACK
import com.example.data.KiranaRepository
import com.example.data.model.CartItem
import com.example.data.model.ProductItem
import com.example.data.model.ShopSettings
import com.example.printer.ThermalPrinterManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Validates hardware integration safety (camera permissions, bluetooth printer failure recovery)
 * and verifies complete end-to-end offline business journey without network connectivity.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class HardwareAndOfflineTest {

    private lateinit var context: Context
    private val dbName = "offline_cuj_test.db"
    private lateinit var db: AppDatabase
    private lateinit var repository: KiranaRepository

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addCallback(DATABASE_CALLBACK)
            .allowMainThreadQueries()
            .build()
        repository = KiranaRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
        context.deleteDatabase(dbName)
    }

    // --- HARDWARE TESTS ---

    @Test
    fun testCameraPermissionDeclaredAndCheckable() {
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val permissions = packageInfo.requestedPermissions ?: emptyArray()
        assertTrue(
            "CAMERA permission must be declared in AndroidManifest for barcode scanning",
            permissions.contains(Manifest.permission.CAMERA)
        )
    }

    @Test
    fun testPrinterFailureHandledGracefullyWithoutCrashing() = runTest {
        // Test graceful query when bluetooth adapter or device is unavailable
        val pairedPrinters = ThermalPrinterManager.getPairedBluetoothPrinters(context)
        // Should not crash and return an empty list or valid list
        assertNotNull("Paired printers query should return gracefully without crashing", pairedPrinters)
    }

    // --- OFFLINE JOURNEY TEST ---
    // Journey: Open app -> add product -> sell -> update stock -> reports -> backup -> restore
    @Test
    fun testCompleteOfflineBusinessJourney() = runTest {
        // 1. OPEN APP: Database is opened offline and settings are accessible
        repository.saveShopSettings(ShopSettings(shopName = "Offline Kirana Store"))
        val settings = repository.shopSettings.first()
        assertNotNull(settings)

        // 2. ADD PRODUCT: Store item locally in Room
        val product = ProductItem(
            name = "Offline Basmati Rice 5kg",
            barcode = "8901234567800",
            category = "Grains",
            unit = "Bag",
            costPrice = 350.0,
            sellingPrice = 450.0,
            mrp = 480.0,
            currentStock = 20.0,
            minStockAlert = 5.0,
            gstRate = 0.0,
            isActive = true
        )
        val productId = repository.insertProduct(product)
        assertTrue("Product must be saved with positive ID", productId > 0)

        val inserted = repository.getProductById(productId)
        assertNotNull(inserted)
        assertEquals(20.0, inserted!!.currentStock, 0.001)

        // 3. SELL: Perform POS checkout offline with atomic transaction
        val cartItem = CartItem(
            productId = productId,
            barcode = inserted.barcode,
            name = inserted.name,
            unit = inserted.unit,
            rate = inserted.sellingPrice,
            costPrice = inserted.costPrice,
            mrp = inserted.mrp,
            gstRate = inserted.gstRate,
            quantity = 3.0
        )
        val saleResult = repository.completeSaleTransaction(
            items = listOf(cartItem),
            customerName = "Walk-in Offline Customer",
            customerPhone = "9876543210",
            paymentMode = "CASH"
        )
        assertNotNull("Offline sale transaction must succeed atomically", saleResult)
        assertEquals(1350.0, saleResult.transaction.grandTotal, 0.001)

        // 4. UPDATE STOCK: Verify stock was deducted offline from 20 to 17
        val afterSaleProduct = repository.getProductById(productId)!!
        assertEquals(17.0, afterSaleProduct.currentStock, 0.001)

        // Further manual stock in update offline (restock 5 units -> 22)
        val updatedProduct = repository.updateStock(productId, 22.0)
        assertNotNull("Stock update must succeed", updatedProduct)
        assertEquals(22.0, repository.getProductById(productId)!!.currentStock, 0.001)

        // 5. REPORTS: Query sales & transaction count offline
        val totalTransactions = repository.getTransactionCountDirect()
        assertEquals(1, totalTransactions)
        val allTx = repository.getAllTransactionsDirect()
        assertEquals(1, allTx.size)
        assertEquals(1350.0, allTx[0].grandTotal, 0.001)

        // 6. BACKUP: Export local database to a JSON string offline
        val products = repository.getAllProductsDirect()
        val transactions = repository.getAllTransactionsDirect()
        val saleItems = repository.getAllSaleItemsDirect()
        val sequences = repository.getAllSequencesDirect()
        val shopSettings = repository.getShopSettingsSync()

        val jsonString = BackupManager.buildBackupJsonString(
            products = products,
            transactions = transactions,
            saleItems = saleItems,
            settings = shopSettings,
            invoiceSequences = sequences
        )
        assertNotNull("Backup JSON must be generated offline", jsonString)
        assertTrue(jsonString.contains("Offline Basmati Rice 5kg"))
        assertTrue(jsonString.contains(allTx[0].invoiceNumber))

        // Write to temporary local file to simulate file export
        val tempBackupFile = File(context.cacheDir, "offline_backup_test.json")
        tempBackupFile.writeText(jsonString)
        assertTrue(tempBackupFile.exists())
        assertTrue(tempBackupFile.length() > 0)

        // 7. RESTORE: Delete product with history deactivates it, then simulate data wipe and restore
        val wasDeactivated = repository.deleteProductById(productId)
        assertTrue("Product with sales history must be deactivated, not deleted", wasDeactivated)
        assertFalse(repository.getProductById(productId)!!.isActive)

        // Simulate database wipe / data loss before restore
        repository.deleteAllProducts()
        assertNull(repository.getProductById(productId))

        val restoreResult = repository.restoreFromBackupJson(tempBackupFile.readText())
        assertTrue("Offline restore must succeed", restoreResult.success)

        // Verify data restored completely offline
        val restoredProducts = repository.getAllProductsDirect()
        val restoredItem = restoredProducts.find { it.barcode == "8901234567800" }
        assertNotNull("Restored product must be recovered", restoredItem)
        assertEquals("Offline Basmati Rice 5kg", restoredItem!!.name)
        assertEquals(22.0, restoredItem.currentStock, 0.001)

        val restoredSales = repository.getAllTransactionsDirect()
        assertEquals(1, restoredSales.size)
        assertEquals(allTx[0].invoiceNumber, restoredSales[0].invoiceNumber)
        assertEquals(1350.0, restoredSales[0].grandTotal, 0.001)

        tempBackupFile.delete()
    }
}
