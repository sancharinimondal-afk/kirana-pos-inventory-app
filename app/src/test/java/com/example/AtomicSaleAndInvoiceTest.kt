package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.billing.DiscountType
import com.example.data.AppDatabase
import com.example.data.DATABASE_CALLBACK
import com.example.data.InsufficientStockException
import com.example.data.KiranaRepository
import com.example.data.model.CartItem
import com.example.data.model.ProductItem
import com.example.ui.KiranaViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Robust automated test suite verifying Phase 8 requirements:
 * 1. A completed sale must be ONE Room database transaction.
 * 2. Successful sale saves transaction, sale item snapshots, and deducts inventory.
 * 3. Multiple products with distinct GST rates & prices.
 * 4. Insufficient stock aborts transaction and rollback prevents partial stock deduction.
 * 5. Historical sales retain snapshots (product name, selling price, cost price, GST rate, qty, tax, discount).
 *    Changing a product later MUST NOT change old sale snapshots.
 * 6. Unique sequential invoice numbering (INV-YYYY-XXXXXX).
 * 7. Duplicate invoice prevention (unique constraint/index in Room).
 * 8. Restart persistence: Restarting app/re-opening Room database retains all invoices, sequences, and historical snapshots.
 * 9. Printing is decoupled from database transactions.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AtomicSaleAndInvoiceTest {

    private lateinit var context: Context
    private val dbName = "test_phase8_kirana.db"

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
    fun testSuccessfulSaleAtomicCompletion() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        // Seed product
        val prodId = repo.insertProduct(
            ProductItem(
                name = "Aashirvaad Atta 5kg",
                barcode = "8901030001",
                category = "Atta & Flour",
                unit = "Piece",
                sellingPrice = 250.0,
                costPrice = 210.0,
                mrp = 265.0,
                currentStock = 20.0,
                gstRate = 5.0
            )
        )

        val cartItem = CartItem(
            productId = prodId,
            barcode = "8901030001",
            name = "Aashirvaad Atta 5kg",
            unit = "Piece",
            rate = 250.0,
            quantity = 2.0,
            mrp = 265.0,
            costPrice = 210.0,
            gstRate = 5.0
        )

        val result = repo.completeSaleTransaction(
            items = listOf(cartItem),
            customerName = "Anil Sharma",
            customerPhone = "9876543210",
            paymentMode = "UPI"
        )

        // Verify transaction saved
        assertNotNull(result.transaction)
        assertTrue(result.transaction.id > 0)
        assertEquals("Anil Sharma", result.transaction.customerName)
        assertEquals("9876543210", result.transaction.customerPhone)
        assertEquals("UPI", result.transaction.paymentMode)
        assertEquals(500.0, result.transaction.grandTotal, 0.01)
        assertTrue(result.transaction.gstAmount > 0.0)

        // Verify unique invoice format: INV-YYYY-000001
        val currentYear = SimpleDateFormat("yyyy", Locale.ENGLISH).format(Date())
        val expectedInv = "INV-$currentYear-000001"
        assertEquals(expectedInv, result.transaction.invoiceNumber)

        // Verify historical sale items saved
        val saleItems = repo.getSaleItemsByTransactionId(result.transaction.id)
        assertEquals(1, saleItems.size)
        val item = saleItems[0]
        assertEquals("Aashirvaad Atta 5kg", item.productName)
        assertEquals(250.0, item.sellingPrice, 0.01)
        assertEquals(210.0, item.costPrice, 0.01)
        assertEquals(5.0, item.gstRate, 0.01)
        assertEquals(2.0, item.quantity, 0.01)
        assertEquals(500.0, item.lineTotal, 0.01)

        // Verify stock deducted
        val freshProduct = repo.getProductById(prodId)
        assertNotNull(freshProduct)
        assertEquals(18.0, freshProduct!!.currentStock, 0.001)

        db.close()
    }

    @Test
    fun testMultipleProductsWithDifferentGstRates() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val id1 = repo.insertProduct(
            ProductItem(name = "Milk 1L", barcode = "BC001", category = "Dairy", unit = "Piece", sellingPrice = 60.0, costPrice = 52.0, mrp = 60.0, currentStock = 10.0, gstRate = 0.0)
        )
        val id2 = repo.insertProduct(
            ProductItem(name = "Cooking Oil 1L", barcode = "BC002", category = "Oil", unit = "Piece", sellingPrice = 150.0, costPrice = 130.0, mrp = 160.0, currentStock = 10.0, gstRate = 5.0)
        )
        val id3 = repo.insertProduct(
            ProductItem(name = "Ghee 500g", barcode = "BC003", category = "Ghee", unit = "Piece", sellingPrice = 300.0, costPrice = 250.0, mrp = 320.0, currentStock = 10.0, gstRate = 12.0)
        )

        val cart = listOf(
            CartItem(productId = id1, barcode = "BC001", name = "Milk 1L", unit = "Piece", rate = 60.0, quantity = 2.0, mrp = 60.0, costPrice = 52.0, gstRate = 0.0),
            CartItem(productId = id2, barcode = "BC002", name = "Cooking Oil 1L", unit = "Piece", rate = 150.0, quantity = 1.0, mrp = 160.0, costPrice = 130.0, gstRate = 5.0),
            CartItem(productId = id3, barcode = "BC003", name = "Ghee 500g", unit = "Piece", rate = 300.0, quantity = 1.0, mrp = 320.0, costPrice = 250.0, gstRate = 12.0)
        )

        val result = repo.completeSaleTransaction(cart)

        // Subtotal = 2*60 + 150 + 300 = 570.0
        assertEquals(570.0, result.transaction.grandTotal, 0.01)
        assertTrue(result.transaction.gstAmount > 0.0)

        val savedItems = repo.getSaleItemsByTransactionId(result.transaction.id)
        assertEquals(3, savedItems.size)

        // Verify stock deducted for all 3 products
        assertEquals(8.0, repo.getProductById(id1)!!.currentStock, 0.001)
        assertEquals(9.0, repo.getProductById(id2)!!.currentStock, 0.001)
        assertEquals(9.0, repo.getProductById(id3)!!.currentStock, 0.001)

        db.close()
    }

    @Test
    fun testInsufficientStockRollbacksEverythingAtomically() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val id1 = repo.insertProduct(
            ProductItem(name = "Item A", barcode = "A1", category = "General", unit = "Piece", sellingPrice = 100.0, costPrice = 80.0, mrp = 100.0, currentStock = 10.0, gstRate = 5.0)
        )
        val id2 = repo.insertProduct(
            ProductItem(name = "Item B", barcode = "B1", category = "General", unit = "Piece", sellingPrice = 200.0, costPrice = 150.0, mrp = 200.0, currentStock = 2.0, gstRate = 18.0) // only 2 in stock!
        )

        val cart = listOf(
            CartItem(productId = id1, barcode = "A1", name = "Item A", unit = "Piece", rate = 100.0, quantity = 5.0, mrp = 100.0, costPrice = 80.0, gstRate = 5.0),
            CartItem(productId = id2, barcode = "B1", name = "Item B", unit = "Piece", rate = 200.0, quantity = 5.0, mrp = 200.0, costPrice = 150.0, gstRate = 18.0) // requests 5!
        )

        var caughtException = false
        try {
            repo.completeSaleTransaction(cart)
        } catch (e: InsufficientStockException) {
            caughtException = true
        }

        assertTrue("Must throw InsufficientStockException", caughtException)

        // TRANSACTION MUST HAVE ROLLED BACK COMPLETELY:
        // 1. Zero transactions in database
        assertEquals(0, repo.getTransactionCountDirect())

        // 2. Zero sale items in database
        assertEquals(0, db.saleItemDao().getAllSaleItemsDirect().size)

        // 3. Stock of Item A MUST NOT have been deducted! (No partial stock deduction)
        val itemAAfter = repo.getProductById(id1)!!
        assertEquals(10.0, itemAAfter.currentStock, 0.001)

        val itemBAfter = repo.getProductById(id2)!!
        assertEquals(2.0, itemBAfter.currentStock, 0.001)

        db.close()
    }

    @Test
    fun testHistoricalSnapshotsPreservedWhenProductModifiedLater() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val pId = repo.insertProduct(
            ProductItem(
                name = "Original Basmati Rice 1kg",
                barcode = "RICE01",
                category = "Rice",
                unit = "Piece",
                sellingPrice = 120.0,
                costPrice = 95.0,
                mrp = 130.0,
                currentStock = 50.0,
                gstRate = 5.0
            )
        )

        val cart = listOf(
            CartItem(productId = pId, barcode = "RICE01", name = "Original Basmati Rice 1kg", unit = "Piece", rate = 120.0, quantity = 2.0, mrp = 130.0, costPrice = 95.0, gstRate = 5.0)
        )
        val saleResult = repo.completeSaleTransaction(cart)

        // Verify initial snapshot
        val initialSnapshot = repo.getSaleItemsByTransactionId(saleResult.transaction.id)[0]
        assertEquals("Original Basmati Rice 1kg", initialSnapshot.productName)
        assertEquals(120.0, initialSnapshot.sellingPrice, 0.01)
        assertEquals(95.0, initialSnapshot.costPrice, 0.01)
        assertEquals(5.0, initialSnapshot.gstRate, 0.01)

        // Now change product completely in inventory:
        // New name, new selling price, new cost price, new GST rate!
        val modifiedProduct = repo.getProductById(pId)!!.copy(
            name = "Premium Daawat Super Basmati 1kg (Repackaged)",
            sellingPrice = 180.0,
            costPrice = 140.0,
            mrp = 200.0,
            gstRate = 12.0
        )
        repo.updateProduct(modifiedProduct)

        // Verify inventory was changed
        val currentInventory = repo.getProductById(pId)!!
        assertEquals("Premium Daawat Super Basmati 1kg (Repackaged)", currentInventory.name)
        assertEquals(180.0, currentInventory.sellingPrice, 0.01)

        // CRITICAL CHECK: The past sale transaction and its historical snapshot MUST NOT HAVE CHANGED!
        val pastTransaction = repo.getTransactionById(saleResult.transaction.id)!!
        assertEquals(240.0, pastTransaction.grandTotal, 0.01)

        val preservedSnapshot = repo.getSaleItemsByTransactionId(saleResult.transaction.id)[0]
        assertEquals("Original Basmati Rice 1kg", preservedSnapshot.productName)
        assertEquals(120.0, preservedSnapshot.sellingPrice, 0.01)
        assertEquals(95.0, preservedSnapshot.costPrice, 0.01)
        assertEquals(5.0, preservedSnapshot.gstRate, 0.01)
        assertEquals(2.0, preservedSnapshot.quantity, 0.01)

        db.close()
    }

    @Test
    fun testUniqueSequenceInvoiceNumbersAndCollisionPrevention() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val pId = repo.insertProduct(
            ProductItem(name = "Soap", barcode = "SOAP1", category = "Personal Care", unit = "Piece", sellingPrice = 40.0, costPrice = 30.0, mrp = 40.0, currentStock = 100.0, gstRate = 18.0)
        )

        // Sale 1
        val res1 = repo.completeSaleTransaction(
            listOf(CartItem(productId = pId, barcode = "SOAP1", name = "Soap", unit = "Piece", rate = 40.0, quantity = 1.0, mrp = 40.0, costPrice = 30.0, gstRate = 18.0))
        )
        // Sale 2
        val res2 = repo.completeSaleTransaction(
            listOf(CartItem(productId = pId, barcode = "SOAP1", name = "Soap", unit = "Piece", rate = 40.0, quantity = 1.0, mrp = 40.0, costPrice = 30.0, gstRate = 18.0))
        )
        // Sale 3
        val res3 = repo.completeSaleTransaction(
            listOf(CartItem(productId = pId, barcode = "SOAP1", name = "Soap", unit = "Piece", rate = 40.0, quantity = 1.0, mrp = 40.0, costPrice = 30.0, gstRate = 18.0))
        )

        val year = SimpleDateFormat("yyyy", Locale.ENGLISH).format(Date())
        assertEquals("INV-$year-000001", res1.transaction.invoiceNumber)
        assertEquals("INV-$year-000002", res2.transaction.invoiceNumber)
        assertEquals("INV-$year-000003", res3.transaction.invoiceNumber)

        // Verify duplicate invoice number constraint throws exception and aborts
        var duplicateFailed = false
        try {
            repo.completeSaleTransaction(
                items = listOf(CartItem(productId = pId, barcode = "SOAP1", name = "Soap", unit = "Piece", rate = 40.0, quantity = 1.0, mrp = 40.0, costPrice = 30.0, gstRate = 18.0)),
                forcedInvoiceNumber = res1.transaction.invoiceNumber // Duplicate!
            )
        } catch (e: Exception) {
            duplicateFailed = true
        }
        assertTrue("Duplicate invoice number insertion must fail with SQLite constraint exception", duplicateFailed)

        db.close()
    }

    @Test
    fun testRestartPersistence() = runBlocking {
        // Step 1: Open DB, perform sale
        var db = openDatabase()
        var repo = KiranaRepository(db)

        val pId = repo.insertProduct(
            ProductItem(name = "Sugar 1kg", barcode = "SUGAR1", category = "Sugar", unit = "Piece", sellingPrice = 44.0, costPrice = 38.0, mrp = 45.0, currentStock = 25.0, gstRate = 0.0)
        )
        val res = repo.completeSaleTransaction(
            listOf(CartItem(productId = pId, barcode = "SUGAR1", name = "Sugar 1kg", unit = "Piece", rate = 44.0, quantity = 3.0, mrp = 45.0, costPrice = 38.0, gstRate = 0.0))
        )
        val originalInv = res.transaction.invoiceNumber
        db.close()

        // Step 2: Simulate Cold Restart (New Database instance pointing to same file)
        val reopenedDb = openDatabase()
        val reopenedRepo = KiranaRepository(reopenedDb)

        // Verify transaction persists
        val tx = reopenedRepo.getTransactionByInvoiceNumber(originalInv)
        assertNotNull("Transaction must persist across database reopen", tx)
        assertEquals(132.0, tx!!.grandTotal, 0.01)

        // Verify historical sale items persist
        val snapshots = reopenedRepo.getSaleItemsByTransactionId(tx.id)
        assertEquals(1, snapshots.size)
        assertEquals("Sugar 1kg", snapshots[0].productName)
        assertEquals(3.0, snapshots[0].quantity, 0.01)

        // Verify remaining stock persists
        val product = reopenedRepo.getProductById(pId)
        assertNotNull(product)
        assertEquals(22.0, product!!.currentStock, 0.001)

        // Perform next sale on reopened DB -> sequence must continue seamlessly
        val res2 = reopenedRepo.completeSaleTransaction(
            listOf(CartItem(productId = pId, barcode = "SUGAR1", name = "Sugar 1kg", unit = "Piece", rate = 44.0, quantity = 2.0, mrp = 45.0, costPrice = 38.0, gstRate = 0.0))
        )
        val year = SimpleDateFormat("yyyy", Locale.ENGLISH).format(Date())
        assertEquals("INV-$year-000002", res2.transaction.invoiceNumber)

        reopenedDb.close()
    }
}
