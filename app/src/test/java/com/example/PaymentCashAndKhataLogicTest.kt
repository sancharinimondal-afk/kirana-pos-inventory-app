package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DATABASE_CALLBACK
import com.example.data.InsufficientCashException
import com.example.data.InsufficientStockException
import com.example.data.KhataCustomerRequiredException
import com.example.data.KiranaRepository
import com.example.data.model.CartItem
import com.example.data.model.Customer
import com.example.data.model.LedgerEntry
import com.example.data.model.ProductItem
import com.example.ui.KiranaViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 5 Automated Test Suite:
 * - Exact cash
 * - Excess cash
 * - Insufficient cash (checkout blocked & atomic rollback)
 * - UPI (with reference / transaction ID)
 * - Card (with reference / transaction ID)
 * - Khata (strictly requiring exact customerId, stored in SaleTransaction and LedgerEntry)
 * - Failed transaction (full rollback of invoice, sale, items, stock, and ledger)
 * - Cart not cleared until transaction succeeds
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PaymentCashAndKhataLogicTest {

    private lateinit var context: Context
    private val dbName = "test_phase5_payment.db"

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
    fun testExactCashPayment() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val prodId = repo.insertProduct(
            ProductItem(
                name = "Tata Salt 1kg",
                barcode = "8901234567801",
                sellingPrice = 28.0,
                costPrice = 22.0,
                mrp = 30.0,
                currentStock = 50.0,
                unit = "pkt",
                category = "Groceries",
                gstRate = 0.0
            )
        )

        val cart = listOf(
            CartItem(
                productId = prodId,
                barcode = "8901234567801",
                name = "Tata Salt 1kg",
                rate = 28.0,
                costPrice = 22.0,
                mrp = 30.0,
                quantity = 2.0,
                gstRate = 0.0,
                unit = "pkt"
            )
        )
        // Grand total = 28.0 * 2 = 56.0. Exact cash = 56.0
        val result = repo.completeSaleTransaction(
            items = cart,
            paymentMode = "CASH",
            cashReceived = 56.0
        )

        val tx = result.transaction
        assertEquals("CASH", tx.paymentMode)
        assertEquals(56.0, tx.grandTotal, 0.001)
        assertEquals(56.0, tx.cashReceived, 0.001)
        assertEquals(0.0, tx.changeDue, 0.001)

        val persisted = repo.getTransactionById(tx.id)
        assertNotNull(persisted)
        assertEquals(56.0, persisted!!.cashReceived, 0.001)
        assertEquals(0.0, persisted.changeDue, 0.001)

        // Verify stock deducted
        val updatedProd = repo.getProductById(prodId)
        assertEquals(48.0, updatedProd!!.currentStock, 0.001)

        db.close()
    }

    @Test
    fun testExcessCashPayment() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val prodId = repo.insertProduct(
            ProductItem(
                name = "Aashirvaad Atta 5kg",
                barcode = "8901234567802",
                sellingPrice = 240.0,
                costPrice = 210.0,
                mrp = 260.0,
                currentStock = 20.0,
                unit = "pkt",
                category = "Atta",
                gstRate = 5.0
            )
        )

        val cart = listOf(
            CartItem(
                productId = prodId,
                barcode = "8901234567802",
                name = "Aashirvaad Atta 5kg",
                rate = 240.0,
                costPrice = 210.0,
                mrp = 260.0,
                quantity = 1.0,
                gstRate = 5.0,
                unit = "pkt"
            )
        )
        // Grand total = 240.0. Customer tenders 500.0. Change due = 260.0
        val result = repo.completeSaleTransaction(
            items = cart,
            paymentMode = "CASH",
            cashReceived = 500.0
        )

        val tx = result.transaction
        assertEquals("CASH", tx.paymentMode)
        assertEquals(240.0, tx.grandTotal, 0.001)
        assertEquals(500.0, tx.cashReceived, 0.001)
        assertEquals(260.0, tx.changeDue, 0.001)

        val persisted = repo.getTransactionById(tx.id)
        assertEquals(500.0, persisted!!.cashReceived, 0.001)
        assertEquals(260.0, persisted.changeDue, 0.001)

        db.close()
    }

    @Test
    fun testInsufficientCashPaymentBlocksCheckout() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val prodId = repo.insertProduct(
            ProductItem(
                name = "Fortune Oil 1L",
                barcode = "8901234567803",
                sellingPrice = 150.0,
                costPrice = 130.0,
                mrp = 160.0,
                currentStock = 15.0,
                unit = "litre",
                category = "Oils",
                gstRate = 5.0
            )
        )

        val cart = listOf(
            CartItem(
                productId = prodId,
                barcode = "8901234567803",
                name = "Fortune Oil 1L",
                rate = 150.0,
                costPrice = 130.0,
                mrp = 160.0,
                quantity = 1.0,
                gstRate = 5.0,
                unit = "litre"
            )
        )
        // Grand total = 150.0. Customer only offers 100.0 cash.
        try {
            repo.completeSaleTransaction(
                items = cart,
                paymentMode = "CASH",
                cashReceived = 100.0
            )
            fail("Expected InsufficientCashException was not thrown!")
        } catch (e: InsufficientCashException) {
            assertTrue(e.message!!.contains("less than grand total"))
            assertTrue(e.message!!.contains("BLOCKED"))
        }

        // Verify full rollback: no transaction created, stock untouched
        val txs = repo.getAllTransactionsDirect()
        assertTrue(txs.isEmpty())

        val untouchedProd = repo.getProductById(prodId)
        assertEquals(15.0, untouchedProd!!.currentStock, 0.001)

        db.close()
    }

    @Test
    fun testUpiPaymentWithReferenceId() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val prodId = repo.insertProduct(
            ProductItem(
                name = "Maggi 2-Min Noodles",
                barcode = "8901234567804",
                sellingPrice = 14.0,
                costPrice = 11.0,
                mrp = 14.0,
                currentStock = 100.0,
                unit = "pkt",
                category = "Snacks",
                gstRate = 12.0
            )
        )

        val cart = listOf(
            CartItem(
                productId = prodId,
                barcode = "8901234567804",
                name = "Maggi 2-Min Noodles",
                rate = 14.0,
                costPrice = 11.0,
                mrp = 14.0,
                quantity = 5.0,
                gstRate = 12.0,
                unit = "pkt"
            )
        )

        val upiRef = "UPI/2026/889911223344"
        val result = repo.completeSaleTransaction(
            items = cart,
            paymentMode = "UPI",
            paymentReference = upiRef
        )

        val tx = result.transaction
        assertEquals("UPI", tx.paymentMode)
        assertEquals(upiRef, tx.paymentReference)
        assertEquals(70.0, tx.grandTotal, 0.001)

        val persisted = repo.getTransactionById(tx.id)
        assertEquals("UPI", persisted!!.paymentMode)
        assertEquals(upiRef, persisted.paymentReference)

        db.close()
    }

    @Test
    fun testCardPaymentWithReferenceId() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val prodId = repo.insertProduct(
            ProductItem(
                name = "Basmati Rice 1kg",
                barcode = "8901234567805",
                sellingPrice = 120.0,
                costPrice = 95.0,
                mrp = 130.0,
                currentStock = 40.0,
                unit = "kg",
                category = "Rice",
                gstRate = 0.0
            )
        )

        val cart = listOf(
            CartItem(
                productId = prodId,
                barcode = "8901234567805",
                name = "Basmati Rice 1kg",
                rate = 120.0,
                costPrice = 95.0,
                mrp = 130.0,
                quantity = 2.0,
                gstRate = 0.0,
                unit = "kg"
            )
        )

        val cardRef = "AUTH-HDFC-991204"
        val result = repo.completeSaleTransaction(
            items = cart,
            paymentMode = "CARD",
            paymentReference = cardRef
        )

        val tx = result.transaction
        assertEquals("CARD", tx.paymentMode)
        assertEquals(cardRef, tx.paymentReference)
        assertEquals(240.0, tx.grandTotal, 0.001)

        val persisted = repo.getTransactionById(tx.id)
        assertEquals("CARD", persisted!!.paymentMode)
        assertEquals(cardRef, persisted.paymentReference)

        db.close()
    }

    @Test
    fun testKhataPaymentWithExactCustomerId() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        // Register customer
        val custId = repo.insertCustomer(
            Customer(
                name = "Ramesh Sharma",
                phone = "9876543210",
                address = "Shop #4, Main Market",
                openingBalance = 0.0
            )
        )
        assertTrue(custId > 0)

        val prodId = repo.insertProduct(
            ProductItem(
                name = "Amul Butter 100g",
                barcode = "8901234567806",
                sellingPrice = 58.0,
                costPrice = 52.0,
                mrp = 60.0,
                currentStock = 30.0,
                unit = "pkt",
                category = "Dairy",
                gstRate = 12.0
            )
        )

        val cart = listOf(
            CartItem(
                productId = prodId,
                barcode = "8901234567806",
                name = "Amul Butter 100g",
                rate = 58.0,
                costPrice = 52.0,
                mrp = 60.0,
                quantity = 2.0,
                gstRate = 12.0,
                unit = "pkt"
            )
        )

        val result = repo.completeSaleTransaction(
            items = cart,
            paymentMode = "KHATA",
            customerId = custId
        )

        val tx = result.transaction
        assertEquals("KHATA", tx.paymentMode)
        assertEquals(custId, tx.customerId)
        assertEquals(116.0, tx.grandTotal, 0.001)
        assertEquals("Ramesh Sharma", tx.customerName)
        assertEquals("9876543210", tx.customerPhone)

        // Verify atomic LedgerEntry creation
        val entries = repo.getCustomerEntries(custId).first()
        assertEquals(1, entries.size)
        val ledger = entries[0]
        assertEquals(custId, ledger.customerId)
        assertEquals(LedgerEntry.TYPE_CREDIT_SALE, ledger.type)
        assertEquals(116.0, ledger.amount, 0.001)
        assertEquals(tx.invoiceNumber, ledger.reference)

        // Verify customer balance updated
        val custBalance = repo.getCustomerBalanceDirect(custId)
        assertEquals(116.0, custBalance, 0.001)

        db.close()
    }

    @Test
    fun testKhataPaymentWithoutCustomerIdIsBlocked() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val prodId = repo.insertProduct(
            ProductItem(
                name = "Sugar 1kg",
                barcode = "8901234567807",
                sellingPrice = 42.0,
                costPrice = 38.0,
                mrp = 45.0,
                currentStock = 50.0,
                unit = "kg",
                category = "Groceries",
                gstRate = 5.0
            )
        )

        val cart = listOf(
            CartItem(
                productId = prodId,
                barcode = "8901234567807",
                name = "Sugar 1kg",
                rate = 42.0,
                costPrice = 38.0,
                mrp = 45.0,
                quantity = 1.0,
                gstRate = 5.0,
                unit = "kg"
            )
        )

        // Supplying only name or phone without exact customerId must be BLOCKED
        try {
            repo.completeSaleTransaction(
                items = cart,
                customerName = "Walk-in Guest",
                customerPhone = "9988776655",
                paymentMode = "KHATA",
                customerId = null
            )
            fail("Expected KhataCustomerRequiredException was not thrown!")
        } catch (e: KhataCustomerRequiredException) {
            assertTrue(e.message!!.contains("exact registered customer ID"))
            assertTrue(e.message!!.contains("BLOCKED"))
        }

        // Verify rollback: no transaction and no ledger entry
        val allTx = repo.getAllTransactionsDirect()
        assertTrue(allTx.isEmpty())

        // Verify stock untouched
        val p = repo.getProductById(prodId)
        assertEquals(50.0, p!!.currentStock, 0.001)

        db.close()
    }

    @Test
    fun testFailedTransactionAtomicallyRollsBackAllTables() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val custId = repo.insertCustomer(
            Customer(
                name = "Suresh Verma",
                phone = "9112233445"
            )
        )

        val prodId = repo.insertProduct(
            ProductItem(
                name = "Chana Dal 1kg",
                barcode = "8901234567808",
                sellingPrice = 90.0,
                costPrice = 75.0,
                mrp = 95.0,
                currentStock = 5.0, // only 5 available
                unit = "kg",
                category = "Pulses",
                gstRate = 0.0
            )
        )

        // Request 10 kg -> Insufficient Stock
        val cart = listOf(
            CartItem(
                productId = prodId,
                barcode = "8901234567808",
                name = "Chana Dal 1kg",
                rate = 90.0,
                costPrice = 75.0,
                mrp = 95.0,
                quantity = 10.0,
                gstRate = 0.0,
                unit = "kg"
            )
        )

        try {
            repo.completeSaleTransaction(
                items = cart,
                paymentMode = "KHATA",
                customerId = custId
            )
            fail("Expected InsufficientStockException was not thrown!")
        } catch (e: InsufficientStockException) {
            assertTrue(e.message!!.contains("Insufficient stock"))
        }

        // Verify full rollback: no sale, no sale item, no ledger entry, no stock deduction
        assertEquals(0, repo.getAllTransactionsDirect().size)
        val customerEntries = repo.getCustomerEntries(custId).first()
        assertTrue(customerEntries.isEmpty())
        val product = repo.getProductById(prodId)
        assertEquals(5.0, product!!.currentStock, 0.001)

        db.close()
    }

    @Test
    fun testCartNotClearedOnTransactionFailure() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)
        val testDispatcher = StandardTestDispatcher()
        val viewModel = KiranaViewModel(repository = repo, ioDispatcher = testDispatcher)

        val prodId = repo.insertProduct(
            ProductItem(
                id = 0L,
                name = "Good Day Biscuit Special",
                barcode = "8909999999999",
                sellingPrice = 30.0,
                costPrice = 24.0,
                mrp = 35.0,
                currentStock = 20.0,
                unit = "pkt",
                category = "Bakery",
                gstRate = 18.0
            )
        )
        val prod = repo.getProductById(prodId)!!

        // Add to cart in ViewModel
        viewModel.addToCart(prod, 1.0)
        assertEquals(1, viewModel.cartItems.value.size)

        // Set payment mode to CASH but provide insufficient cash (10.0 < 30.0)
        viewModel.paymentMode.value = "CASH"
        viewModel.cashReceived.value = 10.0

        // Attempt checkout
        viewModel.completeSale()
        testDispatcher.scheduler.advanceUntilIdle()

        // Verify cart is NOT cleared
        assertEquals(1, viewModel.cartItems.value.size)
        val msg = viewModel.userMessage.value ?: ""
        assertTrue(msg.contains("less than total") || msg.contains("BLOCKED"))

        db.close()
    }
}
