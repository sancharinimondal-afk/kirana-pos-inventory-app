package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.billing.BillingCalculator
import com.example.data.AppDatabase
import com.example.data.DATABASE_CALLBACK
import com.example.data.KiranaRepository
import com.example.data.model.CartItem
import com.example.data.model.Customer
import com.example.data.model.CustomerWithBalance
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
 * Phase 6 Automated Test Suite:
 * - Balance formula:
 *     Opening Balance + Credit Sales + Debit Adjustments - Payments - Credit Adjustments = Current Balance
 * - Positive balance = customer owes shop
 * - Negative balance = shop owes customer
 * - Customer list must show LIVE current balance (NOT openingBalance)
 * - When creating opening balance: save customer, create one OPENING_BALANCE ledger entry, prevent duplicate opening entries
 * - Payment: amount > 0, create PAYMENT_RECEIVED ledger entry, update calculated balance
 * - Khata sale: save exact customerId, create CREDIT_SALE ledger entry
 * - Cancelled Khata sale:
 *     1. Find SaleTransaction
 *     2. Read stored customerId
 *     3. Find original ledger entry
 *     4. Create reversal
 *     5. Restore stock
 *     6. Create SALE_CANCEL stock movement
 *     7. Mark sale cancelled
 * - Never identify the customer only by phone number
 * - Customer detail metrics: Current balance, Opening balance, Credit sales, Payments, Adjustments, Running ledger
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class CustomerAndKhataLedgerTest {

    private lateinit var context: Context
    private val dbName = "test_phase6_khata.db"

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
    fun testBalanceFormula_exactCalculation() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        // Customer with Opening Balance = 500.0
        val custId = repo.insertCustomer(
            Customer(name = "Ramesh Kumar", phone = "9876543210", openingBalance = 500.0)
        )

        // + Credit Sales = 1200.0 (simulate two sales of 700.0 and 500.0)
        repo.insertLedgerEntry(
            LedgerEntry(
                customerId = custId,
                date = System.currentTimeMillis(),
                type = LedgerEntry.TYPE_CREDIT_SALE,
                amount = 700.0,
                reference = "INV-001",
                note = "Groceries on credit"
            )
        )
        repo.insertLedgerEntry(
            LedgerEntry(
                customerId = custId,
                date = System.currentTimeMillis() + 100,
                type = LedgerEntry.TYPE_CREDIT_SALE,
                amount = 500.0,
                reference = "INV-002",
                note = "Atta & Oil on credit"
            )
        )

        // + Debit Adjustments = 150.0
        repo.addKhataAdjustment(
            customerId = custId,
            amount = 150.0,
            isDebit = true,
            reference = "ADJ-DEBIT-01",
            note = "Carrying charge / missed item"
        )

        // - Payments = 800.0 (simulate two payments of 500.0 and 300.0)
        repo.addKhataPayment(
            customerId = custId,
            amount = 500.0,
            reference = "UPI-12345",
            note = "Partial payment via UPI"
        )
        repo.addKhataPayment(
            customerId = custId,
            amount = 300.0,
            reference = "CASH-REC",
            note = "Cash payment"
        )

        // - Credit Adjustments = 50.0
        repo.addKhataAdjustment(
            customerId = custId,
            amount = 50.0,
            isDebit = false,
            reference = "ADJ-CREDIT-01",
            note = "Loyalty festival discount waiver"
        )

        // Formula: 500 (Opening) + 1200 (Credit Sales) + 150 (Debit Adj) - 800 (Payments) - 50 (Credit Adj)
        // = 500 + 1200 + 150 - 800 - 50 = 1000.0
        val liveBalance = repo.getCustomerBalanceDirect(custId)
        assertEquals(1000.0, liveBalance, 0.001)

        db.close()
    }

    @Test
    fun testPositiveBalance_meansCustomerOwesShop() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val custId = repo.insertCustomer(
            Customer(name = "Amit Shah", phone = "9111111111", openingBalance = 450.0)
        )

        val balance = repo.getCustomerBalanceDirect(custId)
        assertTrue("Positive balance must mean customer owes shop", balance > 0)
        assertEquals(450.0, balance, 0.001)

        db.close()
    }

    @Test
    fun testNegativeBalance_meansShopOwesCustomer() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        // Customer with 0 opening balance pays advance 1000.0
        val custId = repo.insertCustomer(
            Customer(name = "Suresh Sen", phone = "9222222222", openingBalance = 0.0)
        )

        repo.addKhataPayment(
            customerId = custId,
            amount = 1000.0,
            reference = "ADV-PAY",
            note = "Monthly advance deposit"
        )

        val balance = repo.getCustomerBalanceDirect(custId)
        assertTrue("Negative balance must mean shop owes customer", balance < 0)
        assertEquals(-1000.0, balance, 0.001)

        db.close()
    }

    @Test
    fun testCustomerList_showsLiveCurrentBalance_notOpeningBalance() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        // Customer initially created with opening balance = 600.0
        val custId = repo.insertCustomer(
            Customer(name = "Pooja Roy", phone = "9333333333", openingBalance = 600.0)
        )

        // List initially shows 600.0
        var customersWithBalance = repo.getAllCustomersWithBalanceDirect()
        assertEquals(1, customersWithBalance.size)
        assertEquals(600.0, customersWithBalance[0].customer.openingBalance, 0.001)
        assertEquals(600.0, customersWithBalance[0].currentBalance, 0.001)

        // Customer pays 400.0
        repo.addKhataPayment(custId, 400.0, "CASH", "Partial payment")

        // The customer table still has openingBalance = 600.0,
        // BUT the customer list with LIVE current balance MUST show 200.0!
        customersWithBalance = repo.getAllCustomersWithBalanceDirect()
        val pCustomer = customersWithBalance.first { it.customer.id == custId }
        assertEquals("Customer record openingBalance remains 600.0", 600.0, pCustomer.customer.openingBalance, 0.001)
        assertEquals("Customer list MUST show LIVE current balance (200.0), NOT opening balance", 200.0, pCustomer.currentBalance, 0.001)

        db.close()
    }

    @Test
    fun testOpeningBalance_createsOneLedgerEntry_andPreventsDuplicateOpeningEntries() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val custId = repo.insertCustomer(
            Customer(name = "Manoj Das", phone = "9444444444", openingBalance = 350.0)
        )

        // Check ledger entries for customer
        val entries = repo.getCustomerEntries(custId).first()
        val openingEntries = entries.filter { it.type == LedgerEntry.TYPE_OPENING_BALANCE }

        assertEquals("Must create exactly one OPENING_BALANCE ledger entry", 1, openingEntries.size)
        assertEquals(350.0, openingEntries[0].amount, 0.001)

        // Attempting to create duplicate opening balance via createOpeningBalanceIfNotExists
        val secondAttempt = repo.createOpeningBalanceIfNotExists(custId, 350.0)
        assertNotNull(secondAttempt)
        assertEquals(openingEntries[0].id, secondAttempt!!.id)

        // Verify count is STILL 1
        val entriesAfter = repo.getCustomerEntries(custId).first()
        val openingEntriesAfter = entriesAfter.filter { it.type == LedgerEntry.TYPE_OPENING_BALANCE }
        assertEquals("Must prevent duplicate opening balance ledger entries", 1, openingEntriesAfter.size)

        db.close()
    }

    @Test
    fun testOpeningBalance_zeroAmount_doesNotCreateLedgerEntry() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val custId = repo.insertCustomer(
            Customer(name = "Alok Nath", phone = "9555555555", openingBalance = 0.0)
        )

        val entries = repo.getCustomerEntries(custId).first()
        assertTrue("No OPENING_BALANCE entry if opening balance is zero", entries.isEmpty())

        db.close()
    }

    @Test
    fun testPayment_amountMustBeGreaterThanZero() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val custId = repo.insertCustomer(
            Customer(name = "Ravi Verma", phone = "9666666666", openingBalance = 200.0)
        )

        // Payment <= 0 must fail
        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.addKhataPayment(custId, 0.0, "CASH", "Invalid payment")
            }
        }

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                repo.addKhataPayment(custId, -50.0, "CASH", "Negative payment")
            }
        }

        // Valid payment > 0 must succeed and create PAYMENT_RECEIVED entry
        val entry = repo.addKhataPayment(custId, 150.0, "UPI-REF-999", "GPay settlement")
        assertEquals(LedgerEntry.TYPE_PAYMENT_RECEIVED, entry.type)
        assertEquals(150.0, entry.amount, 0.001)
        assertEquals(custId, entry.customerId)

        // Balance updated to 50.0 (200 - 150)
        val balance = repo.getCustomerBalanceDirect(custId)
        assertEquals(50.0, balance, 0.001)

        db.close()
    }

    @Test
    fun testKhataSale_savesExactCustomerId_andCreatesCreditSaleLedgerEntry() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val prodId = repo.insertProduct(
            ProductItem(
                name = "Basmati Rice",
                barcode = "8901234567800",
                sellingPrice = 120.0,
                costPrice = 100.0,
                mrp = 130.0,
                currentStock = 50.0,
                unit = "kg",
                category = "Grains",
                gstRate = 0.0
            )
        )
        val custId = repo.insertCustomer(
            Customer(name = "Deepak Joshi", phone = "9777777777", openingBalance = 0.0)
        )

        val cart = listOf(
            CartItem(
                productId = prodId,
                name = "Basmati Rice",
                barcode = "8901234567800",
                rate = 120.0,
                mrp = 130.0,
                quantity = 2.0,
                unit = "kg",
                gstRate = 0.0
            )
        )

        val result = repo.completeSaleTransaction(
            items = cart,
            paymentMode = "KHATA",
            customerId = custId,
            customerName = "Deepak Joshi",
            customerPhone = "9777777777"
        )
        val sale = result.transaction

        // Exact customerId saved in SaleTransaction
        assertEquals("SaleTransaction must store exact customerId", custId, sale.customerId)
        assertEquals(240.0, sale.grandTotal, 0.001)

        // LedgerEntry created with exact customerId and TYPE_CREDIT_SALE
        val entries = repo.getCustomerEntries(custId).first()
        assertEquals(1, entries.size)
        assertEquals(LedgerEntry.TYPE_CREDIT_SALE, entries[0].type)
        assertEquals(custId, entries[0].customerId)
        assertEquals(240.0, entries[0].amount, 0.001)
        assertEquals(sale.invoiceNumber, entries[0].reference)

        // Live balance updated
        val balance = repo.getCustomerBalanceDirect(custId)
        assertEquals(240.0, balance, 0.001)

        db.close()
    }

    @Test
    fun testCancelledKhataSale_fullReversalWorkflow() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val prodId = repo.insertProduct(
            ProductItem(
                name = "Fortune Sunflower Oil",
                barcode = "8901234567801",
                sellingPrice = 160.0,
                costPrice = 140.0,
                mrp = 175.0,
                currentStock = 20.0,
                unit = "litre",
                category = "Edible Oils",
                gstRate = 5.0
            )
        )
        val custId = repo.insertCustomer(
            Customer(name = "Sunil Gavaskar", phone = "9888888888", openingBalance = 100.0)
        )

        val cart = listOf(
            CartItem(
                productId = prodId,
                name = "Fortune Sunflower Oil",
                barcode = "8901234567801",
                rate = 160.0,
                mrp = 175.0,
                quantity = 3.0,
                unit = "litre",
                gstRate = 5.0
            )
        )

        val result = repo.completeSaleTransaction(
            items = cart,
            paymentMode = "KHATA",
            customerId = custId,
            customerName = "Sunil Gavaskar",
            customerPhone = "9888888888"
        )
        val sale = result.transaction

        // Prior to cancellation:
        // Stock: 20 - 3 = 17
        assertEquals(17.0, repo.getProductById(prodId)!!.currentStock, 0.001)
        // Balance: 100 + 480 = 580
        assertEquals(580.0, repo.getCustomerBalanceDirect(custId), 0.001)

        // Execute Cancelled Khata sale (all 7 steps):
        // 1. Find SaleTransaction
        // 2. Read stored customerId
        // 3. Find original ledger entry
        // 4. Create reversal
        // 5. Restore stock
        // 6. Create SALE_CANCEL stock movement
        // 7. Mark sale cancelled
        val cancelledSale = repo.cancelSaleTransaction(sale.id, "Customer returned order")
        assertNotNull(cancelledSale)
        assertTrue("Sale must be marked cancelled", cancelledSale!!.isCancelled)
        assertEquals("Customer returned order", cancelledSale.cancellationReason)

        // Step 5: Stock restored from 17 back to 20
        val productAfterCancel = repo.getProductById(prodId)!!
        assertEquals("Stock must be restored to 20.0", 20.0, productAfterCancel.currentStock, 0.001)

        // Step 6: SALE_CANCEL stock movement created
        val movements = repo.getStockMovementsForProductDirect(prodId)
        val cancelMovement = movements.find { it.operationType == StockMovementEntity.OP_SALE_CANCEL }
        assertNotNull("Must create SALE_CANCEL stock movement", cancelMovement)
        assertEquals(3.0, cancelMovement!!.quantity, 0.001)
        assertEquals(sale.invoiceNumber, cancelMovement.referenceNumber)

        // Step 3 & 4: Reversal ledger entry created for stored customerId
        val entries = repo.getCustomerEntries(custId).first()
        val reversalEntry = entries.find { it.type == LedgerEntry.TYPE_CREDIT_ADJUSTMENT }
        assertNotNull("Must create reversal CREDIT_ADJUSTMENT ledger entry", reversalEntry)
        assertEquals(custId, reversalEntry!!.customerId)
        assertEquals(480.0, reversalEntry.amount, 0.001)
        assertEquals("CANCEL-${sale.invoiceNumber}", reversalEntry.reference)

        // Balance restored back to initial 100.0:
        // 100 (Opening) + 480 (Sale) - 480 (Reversal) = 100.0
        val finalBalance = repo.getCustomerBalanceDirect(custId)
        assertEquals("Balance must return to 100.0 after cancellation reversal", 100.0, finalBalance, 0.001)

        db.close()
    }

    @Test
    fun testCustomerNeverIdentifiedOnlyByPhone_onKhataOrCancellation() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        val prodId = repo.insertProduct(
            ProductItem(
                name = "Sugar",
                barcode = "8901234567802",
                sellingPrice = 45.0,
                costPrice = 38.0,
                mrp = 48.0,
                currentStock = 100.0,
                unit = "kg",
                category = "Groceries",
                gstRate = 0.0
            )
        )

        // Two customers with NO phone number or empty phone
        val cust1 = repo.insertCustomer(Customer(name = "Customer One", phone = "", openingBalance = 0.0))
        val cust2 = repo.insertCustomer(Customer(name = "Customer Two", phone = "", openingBalance = 0.0))

        val cart = listOf(
            CartItem(
                productId = prodId,
                name = "Sugar",
                barcode = "8901234567802",
                rate = 45.0,
                mrp = 48.0,
                quantity = 4.0,
                unit = "kg",
                gstRate = 0.0
            )
        )

        // Sale on Khata strictly for cust2
        val result = repo.completeSaleTransaction(
            items = cart,
            paymentMode = "KHATA",
            customerId = cust2,
            customerName = "Customer Two",
            customerPhone = ""
        )
        val sale = result.transaction

        assertEquals("Transaction must store cust2 ID", cust2, sale.customerId)

        // Cust1 balance should remain 0, Cust2 balance becomes 180.0
        assertEquals(0.0, repo.getCustomerBalanceDirect(cust1), 0.001)
        assertEquals(180.0, repo.getCustomerBalanceDirect(cust2), 0.001)

        // Cancel sale
        repo.cancelSaleTransaction(sale.id, "Wrong billing")

        // Reversal must be assigned to cust2, NOT cust1
        val cust2Entries = repo.getCustomerEntries(cust2).first()
        val cust1Entries = repo.getCustomerEntries(cust1).first()

        assertTrue("Cust1 has no ledger entries", cust1Entries.isEmpty())
        assertTrue("Cust2 has reversal entry", cust2Entries.any { it.type == LedgerEntry.TYPE_CREDIT_ADJUSTMENT })
        assertEquals(0.0, repo.getCustomerBalanceDirect(cust2), 0.001)

        db.close()
    }

    @Test
    fun testCustomerDetailMetricsAndRunningLedgerCalculations() = runBlocking {
        val db = openDatabase()
        val repo = KiranaRepository(db)

        // Customer opening balance: 1000.0
        val custId = repo.insertCustomer(
            Customer(name = "Vikas Sharma", phone = "9999911111", openingBalance = 1000.0)
        )

        val baseTime = System.currentTimeMillis()

        // 1. Credit Sale = +500.0
        repo.insertLedgerEntry(
            LedgerEntry(
                customerId = custId,
                date = baseTime + 10_000L,
                type = LedgerEntry.TYPE_CREDIT_SALE,
                amount = 500.0,
                reference = "INV-001",
                note = "Sale 1"
            )
        )

        // 2. Debit Adjustment = +200.0
        repo.insertLedgerEntry(
            LedgerEntry(
                customerId = custId,
                date = baseTime + 20_000L,
                type = LedgerEntry.TYPE_DEBIT_ADJUSTMENT,
                amount = 200.0,
                reference = "ADJ-D1",
                note = "Interest / Extra delivery fee"
            )
        )

        // 3. Payment Received = -800.0
        repo.insertLedgerEntry(
            LedgerEntry(
                customerId = custId,
                date = baseTime + 30_000L,
                type = LedgerEntry.TYPE_PAYMENT_RECEIVED,
                amount = 800.0,
                reference = "PAY-01",
                note = "UPI payment"
            )
        )

        // 4. Credit Adjustment = -100.0
        repo.insertLedgerEntry(
            LedgerEntry(
                customerId = custId,
                date = baseTime + 40_000L,
                type = LedgerEntry.TYPE_CREDIT_ADJUSTMENT,
                amount = 100.0,
                reference = "ADJ-C1",
                note = "Damaged goods waiver"
            )
        )

        val entries = repo.getCustomerEntries(custId).first()

        // Verify summary calculations:
        val openingBal = entries.filter { it.type == LedgerEntry.TYPE_OPENING_BALANCE }.sumOf { it.amount }
        val creditSales = entries.filter { it.type == LedgerEntry.TYPE_CREDIT_SALE }.sumOf { it.amount }
        val payments = entries.filter { it.type in listOf(LedgerEntry.TYPE_PAYMENT_RECEIVED, LedgerEntry.TYPE_PAYMENT) }.sumOf { it.amount }
        val debitAdj = entries.filter { it.type == LedgerEntry.TYPE_DEBIT_ADJUSTMENT }.sumOf { it.amount }
        val creditAdj = entries.filter { it.type == LedgerEntry.TYPE_CREDIT_ADJUSTMENT }.sumOf { it.amount }
        val currentBalance = openingBal + creditSales + debitAdj - payments - creditAdj

        assertEquals(1000.0, openingBal, 0.001)
        assertEquals(500.0, creditSales, 0.001)
        assertEquals(800.0, payments, 0.001)
        assertEquals(200.0, debitAdj, 0.001)
        assertEquals(100.0, creditAdj, 0.001)
        assertEquals(800.0, currentBalance, 0.001)

        // Verify running ledger:
        // Sort chronological ASC:
        var runningBal = 0.0
        val sortedAsc = entries.sortedWith(compareBy({ it.date }, { it.id }))
        val runningBalances = sortedAsc.map { entry ->
            val isPositive = LedgerEntry.isPositiveEffect(entry.type)
            if (isPositive) runningBal += entry.amount else runningBal -= entry.amount
            BillingCalculator.roundMoney(runningBal)
        }

        // Expected running balances:
        // 1. Opening Balance (1000.0) -> 1000.0
        // 2. Credit Sale (500.0) -> 1500.0
        // 3. Debit Adjustment (200.0) -> 1700.0
        // 4. Payment Received (800.0) -> 900.0
        // 5. Credit Adjustment (100.0) -> 800.0
        assertEquals(listOf(1000.0, 1500.0, 1700.0, 900.0, 800.0), runningBalances)

        db.close()
    }
}
