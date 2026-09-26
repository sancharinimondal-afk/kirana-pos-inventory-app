package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.backup.BackupManager
import com.example.data.AppDatabase
import com.example.data.DATABASE_CALLBACK
import com.example.data.KiranaRepository
import com.example.data.PurchaseItemInput
import com.example.data.model.CartItem
import com.example.data.model.Customer
import com.example.data.model.LedgerEntry
import com.example.data.model.ProductItem
import com.example.data.model.StockMovementEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Verifies atomic database operations and audit logging:
 * - Atomic wholesale purchases with stock movements and cost price updates.
 * - Non-destructive sale cancellation with inventory restoration and audit logs.
 * - Khata ledger integrity (derived balance, append-only history).
 * - Complete database export & backup roundtrip.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AtomicBusinessLogicAndAuditTest {

    private lateinit var context: Context
    private val dbName = "test_audit_kirana.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun openDatabase(): AppDatabase {
        return Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addCallback(DATABASE_CALLBACK)
            .allowMainThreadQueries()
            .build()
    }

    @Test
    fun testPurchaseTransactionAtomicStockIncreaseAndAuditLog() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val prodId = repo.insertProduct(
            ProductItem(
                name = "Fortune Mustard Oil 1L",
                barcode = "8901030099",
                category = "Edible Oils",
                unit = "Bottle",
                sellingPrice = 160.0,
                costPrice = 130.0,
                mrp = 175.0,
                currentStock = 10.0,
                gstRate = 5.0
            )
        )

        val purchaseInput = listOf(
            PurchaseItemInput(
                productId = prodId,
                productName = "Fortune Mustard Oil 1L",
                barcode = "8901030099",
                unit = "Bottle",
                quantity = 15.0,
                purchaseRate = 125.0,
                gstRate = 5.0,
                updateProductCostPrice = true
            )
        )

        val purchaseResult = repo.completePurchaseTransaction(
            items = purchaseInput,
            supplierName = "City FMCG Wholesale",
            supplierPhone = "9876543210",
            paymentMode = "Bank Transfer",
            note = "Weekly bulk restock"
        )

        assertNotNull(purchaseResult)
        assertTrue(purchaseResult.purchase.id > 0)
        assertEquals("City FMCG Wholesale", purchaseResult.purchase.supplierName)
        assertEquals(1, purchaseResult.items.size)

        // Verify stock increased from 10.0 to 25.0
        val updatedProd = repo.getProductById(prodId)
        assertNotNull(updatedProd)
        assertEquals(25.0, updatedProd!!.currentStock, 0.001)
        assertEquals(125.0, updatedProd.costPrice, 0.001) // costPrice updated

        // Verify stock_movement audit log (1 for INITIAL registration + 1 for PURCHASE)
        val movements = db.stockMovementDao().getMovementsForProduct(prodId).first()
        assertEquals(2, movements.size)
        val purchaseMovement = movements.find { it.operationType == StockMovementEntity.OP_PURCHASE }
        assertNotNull(purchaseMovement)
        assertEquals(10.0, purchaseMovement!!.oldStock, 0.001)
        assertEquals(25.0, purchaseMovement.newStock, 0.001)
        assertEquals(15.0, purchaseMovement.quantity, 0.001)
        assertEquals(purchaseResult.purchase.purchaseNumber, purchaseMovement.referenceNumber)

        db.close()
    }

    @Test
    fun testCancelSaleRestoresStockAndLogsMovement() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val prodId = repo.insertProduct(
            ProductItem(
                name = "Tata Tea Gold 500g",
                barcode = "8901030088",
                category = "Tea & Beverages",
                unit = "Packet",
                sellingPrice = 310.0,
                costPrice = 260.0,
                mrp = 330.0,
                currentStock = 20.0,
                gstRate = 5.0
            )
        )

        // Complete sale of 3 units
        val cartItem = CartItem(
            productId = prodId,
            barcode = "8901030088",
            name = "Tata Tea Gold 500g",
            unit = "Packet",
            rate = 310.0,
            quantity = 3.0,
            mrp = 330.0,
            costPrice = 260.0,
            gstRate = 5.0
        )

        val saleResult = repo.completeSaleTransaction(
            items = listOf(cartItem),
            customerName = "Vikram Sen",
            customerPhone = "9123456789",
            paymentMode = "Cash"
        )

        val stockAfterSale = repo.getProductById(prodId)!!.currentStock
        assertEquals(17.0, stockAfterSale, 0.001)

        // Cancel the sale
        val cancelled = repo.cancelSaleTransaction(
            id = saleResult.transaction.id,
            reason = "Customer returned items immediately"
        )
        assertNotNull(cancelled)
        assertTrue(cancelled!!.isCancelled)
        assertEquals("Customer returned items immediately", cancelled.cancellationReason)

        // Verify sale record in DB is marked cancelled, NOT deleted
        val tx = repo.getTransactionById(saleResult.transaction.id)
        assertNotNull(tx)
        assertTrue(tx!!.isCancelled)
        assertEquals("Customer returned items immediately", tx.cancellationReason)

        // Verify product stock restored to 20.0
        val stockAfterCancel = repo.getProductById(prodId)!!.currentStock
        assertEquals(20.0, stockAfterCancel, 0.001)

        // Verify stock_movements contains INITIAL, SALE and SALE_CANCEL
        val movements = db.stockMovementDao().getMovementsForProduct(prodId).first()
        assertEquals(3, movements.size)
        val cancelMovement = movements.find { it.operationType == StockMovementEntity.OP_SALE_CANCEL }
        assertNotNull(cancelMovement)
        assertEquals(17.0, cancelMovement!!.oldStock, 0.001)
        assertEquals(20.0, cancelMovement.newStock, 0.001)
        assertEquals(3.0, cancelMovement.quantity, 0.001)

        db.close()
    }

    @Test
    fun testKhataLedgerHistoryAndDerivedBalance() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val custId = repo.insertCustomer(
            Customer(name = "Sunil Pal", phone = "9876543211", address = "Station Road")
        )
        assertTrue(custId > 0)

        // Add credit sale of 500
        db.ledgerDao().insertLedgerEntry(
            LedgerEntry(
                customerId = custId,
                date = System.currentTimeMillis(),
                type = "CREDIT_SALE",
                amount = 500.0,
                reference = "INV-2026-0001",
                note = "Monthly ration"
            )
        )

        var balance = repo.getCustomerBalanceDirect(custId)
        assertEquals(500.0, balance, 0.001)

        // Add payment of 200
        repo.addKhataPayment(
            customerId = custId,
            amount = 200.0,
            reference = "UPI-GPay",
            note = "Part payment via GPay"
        )

        balance = repo.getCustomerBalanceDirect(custId)
        assertEquals(300.0, balance, 0.001)

        // Historical ledger entries must remain intact (append-only)
        val ledgerEntries = db.ledgerDao().getEntriesByCustomerIdDirect(custId)
        assertEquals(2, ledgerEntries.size)
        assertEquals("CREDIT_SALE", ledgerEntries[0].type)
        assertEquals(500.0, ledgerEntries[0].amount, 0.001)
        assertEquals(LedgerEntry.TYPE_PAYMENT_RECEIVED, ledgerEntries[1].type)
        assertEquals(200.0, ledgerEntries[1].amount, 0.001)

        db.close()
    }

    @Test
    fun testCompleteDatabaseExportIncludesAllEntities() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val prodId = repo.insertProduct(
            ProductItem(
                name = "Surf Excel 1kg",
                barcode = "8901030077",
                category = "Detergents",
                unit = "Packet",
                sellingPrice = 140.0,
                costPrice = 115.0,
                mrp = 150.0,
                currentStock = 12.0,
                gstRate = 18.0
            )
        )

        val custId = repo.insertCustomer(Customer(name = "Kishore Kumar", phone = "9800011122"))
        db.ledgerDao().insertLedgerEntry(
            LedgerEntry(
                customerId = custId,
                date = System.currentTimeMillis(),
                type = "CREDIT_SALE",
                amount = 250.0,
                reference = "INV-EX-01"
            )
        )

        repo.completePurchaseTransaction(
            items = listOf(
                PurchaseItemInput(
                    productId = prodId,
                    productName = "Surf Excel 1kg",
                    barcode = "8901030077",
                    unit = "Packet",
                    quantity = 10.0,
                    purchaseRate = 115.0,
                    gstRate = 18.0
                )
            ),
            supplierName = "HUL Distributor",
            paymentMode = "Bank Transfer"
        )

        val json = BackupManager.buildBackupJsonString(
            products = repo.getAllProductsDirect(),
            transactions = repo.getAllTransactionsDirect(),
            saleItems = repo.getAllSaleItemsDirect(),
            settings = repo.getShopSettingsSync(),
            invoiceSequences = repo.getAllSequencesDirect(),
            customers = repo.getAllCustomersDirect(),
            ledger = repo.getAllLedgerEntriesDirect(),
            purchases = repo.getAllPurchasesDirect(),
            purchaseItems = repo.getAllPurchaseItemsDirect(),
            stockMovements = repo.getAllStockMovementsDirect()
        )

        assertTrue(json.contains("Surf Excel 1kg"))
        assertTrue(json.contains("Kishore Kumar"))
        assertTrue(json.contains("HUL Distributor"))
        assertTrue(json.contains("stockMovements"))
        assertTrue(json.contains("purchases"))

        db.close()
    }
}
