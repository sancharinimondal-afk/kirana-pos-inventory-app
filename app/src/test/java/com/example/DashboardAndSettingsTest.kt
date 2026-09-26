package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.BillingSettingsManager
import com.example.data.DATABASE_CALLBACK
import com.example.data.KiranaRepository
import com.example.data.model.CartItem
import com.example.data.model.Customer
import com.example.data.model.LedgerEntry
import com.example.data.model.ProductItem
import com.example.data.model.ShopSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DashboardAndSettingsTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: KiranaRepository
    private val dbName = "test_dashboard_settings.db"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        context.deleteDatabase(dbName)
        database = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addCallback(DATABASE_CALLBACK)
            .allowMainThreadQueries()
            .build()
        repository = KiranaRepository(database)
    }

    @After
    fun tearDown() {
        if (::database.isInitialized && database.isOpen) {
            database.close()
        }
        context.deleteDatabase(dbName)
    }

    @Test
    fun testDashboardMetricsCalculation() = runBlocking {
        // Insert sample products: normal, low stock, out of stock
        val p1 = ProductItem(
            barcode = "8901234567890",
            name = "Aashirvaad Atta 5kg",
            category = "Grains",
            unit = "Packet",
            costPrice = 220.0,
            sellingPrice = 260.0,
            mrp = 280.0,
            currentStock = 15.0,
            minStockAlert = 5.0
        )
        val p2 = ProductItem(
            barcode = "8901234567891",
            name = "Tata Salt 1kg",
            category = "Spices",
            unit = "Packet",
            costPrice = 20.0,
            sellingPrice = 28.0,
            mrp = 30.0,
            currentStock = 3.0, // Low stock (< 5)
            minStockAlert = 5.0
        )
        val p3 = ProductItem(
            barcode = "8901234567892",
            name = "Maggi 2-Min Noodles",
            category = "Snacks",
            unit = "Packet",
            costPrice = 10.0,
            sellingPrice = 14.0,
            mrp = 15.0,
            currentStock = 0.0, // Out of stock
            minStockAlert = 5.0
        )

        val id1 = repository.insertProduct(p1)
        val id2 = repository.insertProduct(p2)
        val id3 = repository.insertProduct(p3)

        // 1. Total Products
        val products = repository.getAllProductsDirect()
        assertEquals("Should have 3 products", 3, products.size)

        // 2. Inventory Value (at cost price): 15 * 220 (3300) + 3 * 20 (60) + 0 * 10 (0) = 3360
        val totalCost = repository.totalCostValue.first()
        assertEquals("Total inventory cost valuation must equal 3360.0", 3360.0, totalCost ?: 0.0, 0.01)

        // 3. Register Customer and Create Sale
        val customer = Customer(name = "Suresh Sharma", phone = "9876543210")
        val customerId = repository.insertCustomer(customer)

        val cart = listOf(
            CartItem(
                productId = id1,
                barcode = p1.barcode,
                name = p1.name,
                unit = p1.unit,
                rate = p1.sellingPrice,
                quantity = 2.0, // 2 * 260 = 520
                mrp = p1.mrp,
                costPrice = p1.costPrice // 220
            )
        )

        // Checkout Sale
        val saleResult = repository.completeSaleTransaction(
            items = cart,
            customerName = "Suresh Sharma",
            customerPhone = "9876543210",
            paymentMode = "CASH",
            customerId = customerId
        )
        assertNotNull("Sale transaction must be created", saleResult.transaction)
        assertEquals(520.0, saleResult.transaction.grandTotal, 0.01)

        // 4. Verify Today's Sales & Bills count
        val startOfDay = System.currentTimeMillis() - 60000L
        val todayTotal = repository.getTodaySalesTotal(startOfDay).first()
        val todayCount = repository.getTodaySalesCount(startOfDay).first()
        assertEquals("Today's sales must be 520.0", 520.0, todayTotal ?: 0.0, 0.01)
        assertEquals("Today's bills count must be 1", 1, todayCount)

        // 5. Verify stock deduction: original was 15, sold 2 -> 13 remaining
        val updatedP1 = repository.getProductById(id1)
        assertEquals(13.0, updatedP1?.currentStock ?: 0.0, 0.01)
    }

    @Test
    fun testKhataOutstandingMetric() = runBlocking {
        val cust = Customer(name = "Rajesh Gupta", phone = "9123456789")
        val cId = repository.insertCustomer(cust)

        // Add Khata credit transaction
        repository.insertLedgerEntry(
            LedgerEntry(
                customerId = cId,
                date = System.currentTimeMillis(),
                type = LedgerEntry.TYPE_CREDIT_SALE,
                amount = 450.0,
                reference = "INV-1002",
                note = "Grocery ration credit"
            )
        )

        val totalOutstanding = repository.totalKhataOutstanding.first()
        assertEquals("Total Khata outstanding must equal 450.0", 450.0, totalOutstanding, 0.01)
    }

    @Test
    fun testBillingSettingsManager() {
        // Test GST settings
        BillingSettingsManager.setGstEnabled(context, true)
        assertTrue(BillingSettingsManager.isGstEnabled(context))
        BillingSettingsManager.setDefaultGstRate(context, 18.0)
        assertEquals(18.0, BillingSettingsManager.getDefaultGstRate(context), 0.01)

        // Test Invoice Prefix
        BillingSettingsManager.setInvoicePrefix(context, "BILL-")
        assertEquals("BILL-", BillingSettingsManager.getInvoicePrefix(context))

        // Test Rounding
        BillingSettingsManager.setRoundingEnabled(context, true)
        assertTrue(BillingSettingsManager.isRoundingEnabled(context))

        // Test Default Payment Mode
        BillingSettingsManager.setDefaultPaymentMode(context, "UPI")
        assertEquals("UPI", BillingSettingsManager.getDefaultPaymentMode(context))

        // Test Discount settings
        BillingSettingsManager.setMaxDiscountPercent(context, 25.0)
        assertEquals(25.0, BillingSettingsManager.getMaxDiscountPercent(context), 0.01)
        BillingSettingsManager.setDefaultDiscountPercent(context, 5.0)
        assertEquals(5.0, BillingSettingsManager.getDefaultDiscountPercent(context), 0.01)

        // Test Inventory settings
        BillingSettingsManager.setNegativeStockAllowed(context, true)
        assertTrue(BillingSettingsManager.isNegativeStockAllowed(context))
        BillingSettingsManager.setBarcodeAutoAdd(context, false)
        assertFalse(BillingSettingsManager.isBarcodeAutoAdd(context))
    }

    @Test
    fun testShopSettingsUpdate() = runBlocking {
        val newSettings = ShopSettings(
            id = 1,
            shopName = "Maa Annapurna Kirana Store",
            tagline = "Best Grocery in Town",
            ownerName = "Ramavatar Agarwal",
            phone = "9876501234",
            address = "Station Road, Ward No 4",
            city = "Jaipur - 302001",
            gstin = "08AAAAA1111A1Z9",
            upiId = "annapurna@upi",
            printerPaperWidth = "80mm"
        )

        repository.saveShopSettings(newSettings)
        val loaded = repository.getShopSettingsSync()
        assertNotNull(loaded)
        assertEquals("Maa Annapurna Kirana Store", loaded?.shopName)
        assertEquals("Ramavatar Agarwal", loaded?.ownerName)
        assertEquals("9876501234", loaded?.phone)
        assertEquals("80mm", loaded?.printerPaperWidth)
        assertEquals("annapurna@upi", loaded?.upiId)
    }
}
