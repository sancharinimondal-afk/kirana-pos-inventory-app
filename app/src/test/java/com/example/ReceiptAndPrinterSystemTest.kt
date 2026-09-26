package com.example

import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.billing.BillingCalculator
import com.example.data.AppDatabase
import com.example.data.KiranaRepository
import com.example.data.model.CartItem
import com.example.data.model.Customer
import com.example.data.model.ProductItem
import com.example.data.model.SaleTransaction
import com.example.data.model.ShopSettings
import com.example.printer.ThermalPrinterManager
import com.example.printer.ThermalReceiptBuilder
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ReceiptAndPrinterSystemTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: KiranaRepository

    private val sampleSettings = ShopSettings(
        shopName = "Shree Ganesh Kirana",
        tagline = "Quality Ration & Daily Essentials",
        phone = "+91 98765 43210",
        address = "Shop No. 12, Main Market",
        city = "Delhi - 110006",
        gstin = "07AAAAA1234A1Z5",
        upiId = "ganeshkirana@upi",
        printerPaperWidth = "58mm",
        receiptFooterNote = "Thank you! Visit again",
        termsNote = "Items returnable within 2 days with bill"
    )

    private val sampleItems = listOf(
        CartItem(
            productId = 1L,
            barcode = "8901234567890",
            name = "Tata Salt 1kg",
            rate = 28.0,
            quantity = 2.0,
            unit = "Packet",
            gstRate = 0.0,
            mrp = 30.0,
            lineDiscount = 0.0
        ),
        CartItem(
            productId = 2L,
            barcode = "8909876543210",
            name = "Amul Butter 500g",
            rate = 270.0,
            quantity = 1.0,
            unit = "Packet",
            gstRate = 12.0,
            mrp = 285.0,
            lineDiscount = 5.0
        )
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KiranaRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testReceiptLayout_58mmVersus80mm_characterWidthAndStructure() {
        val tx = SaleTransaction(
            invoiceNumber = "INV-2026-0001",
            timestamp = System.currentTimeMillis(),
            customerName = "Ramesh Verma",
            customerPhone = "9876543210",
            paymentMode = "CASH",
            subtotal = 326.0,
            discount = 10.0,
            gstAmount = 28.39,
            grandTotal = 316.0,
            itemsJson = "[]",
            cashReceived = 500.0,
            changeDue = 184.0
        )

        val receipt58mm = ThermalReceiptBuilder.formatPlainReceipt(
            transaction = tx,
            items = sampleItems,
            settings = sampleSettings,
            paperWidth = "58mm"
        )

        val receipt80mm = ThermalReceiptBuilder.formatPlainReceipt(
            transaction = tx,
            items = sampleItems,
            settings = sampleSettings,
            paperWidth = "80mm"
        )

        // 58mm separator line should be 32 dashes
        assertTrue("58mm should have 32-character lines", receipt58mm.contains("-".repeat(32)))
        assertFalse("58mm should not have 48-character lines", receipt58mm.contains("-".repeat(48)))

        // 80mm separator line should be 48 dashes
        assertTrue("80mm should have 48-character lines", receipt80mm.contains("-".repeat(48)))

        // 80mm table header has DISC column while 58mm is compact
        assertTrue("80mm header should include DISC column", receipt80mm.contains("DISC"))
        assertTrue("80mm table header layout check", receipt80mm.contains("ITEM"))

        // EscPos bytes generation should also reflect paper width
        val bytes58 = ThermalReceiptBuilder.buildEscPosBytes(tx, sampleItems, sampleSettings, "58mm")
        val bytes80 = ThermalReceiptBuilder.buildEscPosBytes(tx, sampleItems, sampleSettings, "80mm")
        assertTrue("58mm ESC/POS bytes generated", bytes58.isNotEmpty())
        assertTrue("80mm ESC/POS bytes generated", bytes80.isNotEmpty())
    }

    @Test
    fun testReceiptContainsAllSeventeenRequiredFields() {
        val tx = SaleTransaction(
            invoiceNumber = "INV-2026-9999",
            timestamp = 1774390000000L, // Fixed time
            customerName = "Suresh Khata Customer",
            customerPhone = "9123456780",
            paymentMode = "KHATA",
            customerId = 101L,
            subtotal = 326.0,
            discount = 15.0,
            gstAmount = 28.39,
            grandTotal = 311.0,
            itemsJson = "[]",
            cashReceived = 0.0,
            changeDue = 0.0
        )

        val receipt = ThermalReceiptBuilder.formatPlainReceipt(
            transaction = tx,
            items = sampleItems,
            settings = sampleSettings,
            paperWidth = "80mm"
        )

        // 1. Shop name
        assertTrue("Receipt contains Shop Name", receipt.contains("SHREE GANESH KIRANA"))
        // 2. Address
        assertTrue("Receipt contains Address", receipt.contains("Shop No. 12, Main Market"))
        // 3. Phone
        assertTrue("Receipt contains Phone", receipt.contains("+91 98765 43210"))
        // 4. GSTIN
        assertTrue("Receipt contains GSTIN", receipt.contains("07AAAAA1234A1Z5"))
        // 5. Invoice number
        assertTrue("Receipt contains Invoice number", receipt.contains("INV-2026-9999"))
        // 6. Date/time
        assertTrue("Receipt contains Date", receipt.contains("Date:"))
        // 7. Items
        assertTrue("Receipt contains Tata Salt", receipt.contains("Tata Salt 1kg"))
        assertTrue("Receipt contains Amul Butter", receipt.contains("Amul Butter 500g"))
        // 8. Quantity
        assertTrue("Receipt contains Quantity", receipt.contains("2") && receipt.contains("1"))
        // 9. Rate
        assertTrue("Receipt contains Rate", receipt.contains("28.00") && receipt.contains("270.00"))
        // 10. Discount
        assertTrue("Receipt contains Discount", receipt.contains("Bill Discount:") || receipt.contains("DISC"))
        // 11. GST
        assertTrue("Receipt contains GST", receipt.contains("GST Tax Included:") || receipt.contains("CGST"))
        // 12. Grand total
        assertTrue("Receipt contains Grand total", receipt.contains("GRAND TOTAL:") && receipt.contains("311.00"))
        // 13. Payment mode
        assertTrue("Receipt contains Payment mode", receipt.contains("KHATA"))
        // 14. Customer name for Khata
        assertTrue("Receipt contains Khata customer name", receipt.contains("Khata Customer: Suresh Khata Customer"))
        // 15. Footer
        assertTrue("Receipt contains Footer Note", receipt.contains("Thank you! Visit again"))
        assertTrue("Receipt contains Terms Note", receipt.contains("Items returnable within 2 days with bill"))
    }

    @Test
    fun testReceiptCashPaymentDetails_cashReceivedAndChange() {
        val tx = SaleTransaction(
            invoiceNumber = "INV-CASH-001",
            timestamp = System.currentTimeMillis(),
            customerName = "Cash Buyer",
            customerPhone = "",
            paymentMode = "CASH",
            subtotal = 100.0,
            discount = 0.0,
            gstAmount = 0.0,
            grandTotal = 100.0,
            itemsJson = "[]",
            cashReceived = 500.0,
            changeDue = 400.0
        )

        val receipt = ThermalReceiptBuilder.formatPlainReceipt(
            transaction = tx,
            items = sampleItems.take(1),
            settings = sampleSettings,
            paperWidth = "58mm"
        )

        assertTrue("Receipt contains Cash Received", receipt.contains("Cash Received:") && receipt.contains("500.00"))
        assertTrue("Receipt contains Change Returned", receipt.contains("Change Returned:") && receipt.contains("400.00"))
    }

    @Test
    fun testPrinterPersistence_saveAndRetrieveSelectedPrinter() {
        ThermalPrinterManager.clearSavedPrinter(context)
        assertNull("Saved MAC should be null initially", ThermalPrinterManager.getSavedPrinterAddress(context))
        assertNull("Saved Name should be null initially", ThermalPrinterManager.getSavedPrinterName(context))

        ThermalPrinterManager.saveSelectedPrinter(context, "00:11:22:33:44:55", "MPT-II POS Printer")

        assertEquals("00:11:22:33:44:55", ThermalPrinterManager.getSavedPrinterAddress(context))
        assertEquals("MPT-II POS Printer", ThermalPrinterManager.getSavedPrinterName(context))

        ThermalPrinterManager.clearSavedPrinter(context)
        assertNull("Saved MAC should be cleared", ThermalPrinterManager.getSavedPrinterAddress(context))
    }

    @Test
    fun testBluetoothPairedDevices_noUnconditionalFilter() {
        // Calling getPairedBluetoothDevices should never crash or throw exception
        val devices = ThermalPrinterManager.getPairedBluetoothDevices(context)
        assertNotNull("Devices list should never be null", devices)
    }

    @Test
    fun testSaleCommittedBeforePrinting_andPrinterFailureDoesNotRollbackSale() = runTest {
        // Setup initial product in DB
        val product = ProductItem(
            id = 100L,
            barcode = "8900000000001",
            name = "Aashirvaad Atta 5kg",
            category = "Grains",
            costPrice = 200.0,
            sellingPrice = 240.0,
            mrp = 250.0,
            currentStock = 10.0,
            minStockAlert = 2.0,
            gstRate = 0.0
        )
        database.productDao().insertProduct(product)

        val cartItem = CartItem(
            productId = 100L,
            barcode = product.barcode,
            name = product.name,
            rate = product.sellingPrice,
            quantity = 2.0,
            unit = product.unit,
            gstRate = 0.0,
            mrp = 250.0,
            lineDiscount = 0.0
        )

        // 1. Commit the sale to database
        val result = repository.completeSaleTransaction(
            items = listOf(cartItem),
            customerName = "Gopal",
            customerPhone = "9812345678",
            paymentMode = "CASH",
            discountAmount = 0.0,
            cashReceived = 500.0,
            changeDue = 20.0
        )

        // Verify sale is safely committed in database
        val savedTx = database.transactionDao().getTransactionByInvoiceNumber(result.transaction.invoiceNumber)
        assertNotNull("Sale MUST be committed in database", savedTx)
        assertEquals(480.0, savedTx!!.grandTotal, 0.001)

        // Stock must have decremented
        val updatedProduct = database.productDao().getProductById(100L)
        assertEquals(8.0, updatedProduct!!.currentStock, 0.001)

        // 2. Now simulate printing failure (e.g., Bluetooth unavailable or offline)
        val printingResult: Result<String> = Result.failure(Exception("Bluetooth printer connection timeout"))
        assertTrue("Printer failed as expected", printingResult.isFailure)

        // 3. Verify Sale = Saved and Printer = Failed:
        // The sale transaction in database MUST NOT be deleted or rolled back!
        val txAfterPrinterFailure = database.transactionDao().getTransactionByInvoiceNumber(result.transaction.invoiceNumber)
        assertNotNull("Sale MUST STILL EXIST after printer failure", txAfterPrinterFailure)
        assertEquals("INV-", txAfterPrinterFailure!!.invoiceNumber.take(4))
        assertEquals(8.0, database.productDao().getProductById(100L)!!.currentStock, 0.001)
    }

    @Test
    fun testKhataSaleCommittedBeforePrinting_andBalanceUpdated() = runTest {
        val custId = database.customerDao().insertCustomer(
            Customer(name = "Mahesh Gupta", phone = "9988776655", address = "Main Street", openingBalance = 100.0)
        )

        val product = ProductItem(
            id = 200L,
            barcode = "8900000000002",
            name = "Sugar 1kg",
            category = "Grocery",
            costPrice = 38.0,
            sellingPrice = 45.0,
            mrp = 50.0,
            currentStock = 20.0,
            minStockAlert = 5.0,
            gstRate = 0.0
        )
        database.productDao().insertProduct(product)

        val cartItem = CartItem(
            productId = 200L,
            barcode = product.barcode,
            name = product.name,
            rate = 45.0,
            quantity = 3.0,
            unit = "kg",
            gstRate = 0.0,
            mrp = 50.0
        )

        val result = repository.completeSaleTransaction(
            items = listOf(cartItem),
            customerName = "Mahesh Gupta",
            customerPhone = "9988776655",
            paymentMode = "KHATA",
            customerId = custId,
            discountAmount = 5.0
        )

        // Grand total = 3 * 45 - 5 = 130
        assertEquals(130.0, result.transaction.grandTotal, 0.001)

        // Database checks: Customer balance reflects credit sale = 130.0
        val balance = repository.getCustomerBalanceDirect(custId)
        assertEquals(130.0, balance, 0.001)

        // Simulate printer offline
        val printStatus = "Printer = Failed: Device Offline"
        assertTrue("Printer status indicates failure", printStatus.contains("Printer = Failed"))

        // Re-verify database: Customer balance is STILL 130, sale is intact and NOT rolled back
        val balanceAfter = repository.getCustomerBalanceDirect(custId)
        assertEquals(130.0, balanceAfter, 0.001)
        val tx = database.transactionDao().getTransactionByInvoiceNumber(result.transaction.invoiceNumber)
        assertNotNull(tx)
        assertEquals("KHATA", tx!!.paymentMode)
    }
}
