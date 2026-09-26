package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.billing.BillingCalculator
import com.example.billing.PurchaseCalculator
import com.example.data.AppDatabase
import com.example.data.DATABASE_CALLBACK
import com.example.data.KiranaRepository
import com.example.data.PurchaseItemInput
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
 * Phase 7 Automated Test Suite:
 * - PurchaseCalculator: Strictly GST-exclusive calculation model
 * - Purchase total: taxableAmount = (quantity × purchaseRate) - discount
 * - GST: GST = taxableAmount × GST rate / 100, Grand Total = taxableAmount + GST
 * - Overall & line discounts applied correctly to taxable base
 * - Atomic purchase completion: Purchase + Purchase Items + Stock Increase + Stock Movement + Cost Price Update
 * - New product creation: No arbitrary cost × 1.15; uses explicit user selling price/MRP OR configurable markup
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PurchaseModuleTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var repository: KiranaRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .addCallback(DATABASE_CALLBACK)
            .build()
        AppDatabase.setTestDatabase(database)
        repository = KiranaRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
        AppDatabase.setTestDatabase(null)
    }

    @Test
    fun testPurchaseCalculatorGstExclusiveFormula() {
        // Quantity = 10, Rate = 100.0, Discount = 0, GST = 18%
        // Taxable = 10 * 100 = 1000.0
        // GST = 1000 * 18 / 100 = 180.0
        // Grand Total = 1000 + 180 = 1180.0
        val item = PurchaseCalculator.calculateSingleItem(
            quantity = 10.0,
            purchaseRate = 100.0,
            gstRate = 18.0,
            lineDiscount = 0.0
        )

        assertEquals(1000.0, item.grossAmount, 0.001)
        assertEquals(0.0, item.lineDiscount, 0.001)
        assertEquals(1000.0, item.taxableAmount, 0.001)
        assertEquals(180.0, item.gstAmount, 0.001)
        assertEquals(1180.0, item.lineTotal, 0.001)
    }

    @Test
    fun testPurchaseTotalWithMultipleItemsAndDifferentGstRates() {
        // Item 1: 20 qty @ 50.0, GST 5% -> taxable = 1000.0, gst = 50.0, total = 1050.0
        // Item 2: 10 qty @ 200.0, GST 12% -> taxable = 2000.0, gst = 240.0, total = 2240.0
        // Item 3: 5 qty @ 500.0, GST 18% -> taxable = 2500.0, gst = 450.0, total = 2950.0
        // Item 4: 50 qty @ 20.0, GST 0% (Nil) -> taxable = 1000.0, gst = 0.0, total = 1000.0
        val items = listOf(
            PurchaseCalculator.ItemInput(quantity = 20.0, purchaseRate = 50.0, gstRate = 5.0),
            PurchaseCalculator.ItemInput(quantity = 10.0, purchaseRate = 200.0, gstRate = 12.0),
            PurchaseCalculator.ItemInput(quantity = 5.0, purchaseRate = 500.0, gstRate = 18.0),
            PurchaseCalculator.ItemInput(quantity = 50.0, purchaseRate = 20.0, gstRate = 0.0)
        )

        val result = PurchaseCalculator.calculatePurchase(items, overallDiscount = 0.0)

        assertEquals(PurchaseCalculator.MODEL_GST_EXCLUSIVE, result.calculationModel)
        assertEquals(6500.0, result.grossSubtotal, 0.001)
        assertEquals(0.0, result.totalDiscount, 0.001)
        assertEquals(6500.0, result.totalTaxableAmount, 0.001)
        assertEquals(740.0, result.totalGst, 0.001)
        assertEquals(7240.0, result.grandTotal, 0.001)
    }

    @Test
    fun testGstAndLineDiscountCalculation() {
        // 10 units @ 100.0 = 1000.0 gross
        // Line discount = 100.0
        // Taxable = 1000.0 - 100.0 = 900.0
        // GST (18%) = 900.0 * 18 / 100 = 162.0
        // Total = 900.0 + 162.0 = 1062.0
        val item = PurchaseCalculator.calculateSingleItem(
            quantity = 10.0,
            purchaseRate = 100.0,
            gstRate = 18.0,
            lineDiscount = 100.0
        )

        assertEquals(1000.0, item.grossAmount, 0.001)
        assertEquals(100.0, item.lineDiscount, 0.001)
        assertEquals(900.0, item.taxableAmount, 0.001)
        assertEquals(162.0, item.gstAmount, 0.001)
        assertEquals(1062.0, item.lineTotal, 0.001)
    }

    @Test
    fun testOverallDiscountProportionalAllocation() {
        // Item 1: 10 qty @ 100 = 1000.0 base taxable, GST 5%
        // Item 2: 10 qty @ 100 = 1000.0 base taxable, GST 18%
        // Total base taxable = 2000.0
        // Overall discount = 200.0 (allocated 100.0 to Item 1, 100.0 to Item 2)
        // Item 1: taxable = 900.0, GST (5%) = 45.0, total = 945.0
        // Item 2: taxable = 900.0, GST (18%) = 162.0, total = 1062.0
        // Grand total = 945.0 + 1062.0 = 2007.0
        val items = listOf(
            PurchaseCalculator.ItemInput(quantity = 10.0, purchaseRate = 100.0, gstRate = 5.0),
            PurchaseCalculator.ItemInput(quantity = 10.0, purchaseRate = 100.0, gstRate = 18.0)
        )

        val result = PurchaseCalculator.calculatePurchase(items, overallDiscount = 200.0)

        assertEquals(2000.0, result.grossSubtotal, 0.001)
        assertEquals(200.0, result.overallDiscount, 0.001)
        assertEquals(200.0, result.totalDiscount, 0.001)
        assertEquals(1800.0, result.totalTaxableAmount, 0.001)
        assertEquals(207.0, result.totalGst, 0.001)
        assertEquals(2007.0, result.grandTotal, 0.001)

        assertEquals(900.0, result.items[0].taxableAmount, 0.001)
        assertEquals(45.0, result.items[0].gstAmount, 0.001)
        assertEquals(945.0, result.items[0].lineTotal, 0.001)

        assertEquals(900.0, result.items[1].taxableAmount, 0.001)
        assertEquals(162.0, result.items[1].gstAmount, 0.001)
        assertEquals(1062.0, result.items[1].lineTotal, 0.001)
    }

    @Test
    fun testAtomicPurchaseCompletionAndStockIncrease() = runBlocking {
        // Initial product: stock = 15.0, cost = 80.0
        val prodId = repository.insertProduct(
            ProductItem(
                name = "Aashirvaad Atta 5kg",
                barcode = "890103000111",
                sellingPrice = 250.0,
                mrp = 270.0,
                costPrice = 80.0,
                currentStock = 15.0,
                unit = "Packet",
                category = "Grains & Flours"
            )
        )

        val purchaseInput = PurchaseItemInput(
            productId = prodId,
            productName = "Aashirvaad Atta 5kg",
            barcode = "890103000111",
            unit = "Packet",
            quantity = 25.0,
            purchaseRate = 90.0,
            gstRate = 5.0,
            updateProductCostPrice = true
        )

        val result = repository.completePurchaseTransaction(
            items = listOf(purchaseInput),
            supplierName = "ITC Wholesale Distributor",
            supplierPhone = "9876543210",
            paymentMode = "Bank Transfer",
            discount = 0.0
        )

        // 1. Verify Purchase Entity
        assertNotNull(result.purchase)
        assertTrue(result.purchase.id > 0)
        assertEquals("ITC Wholesale Distributor", result.purchase.supplierName)
        assertEquals(2250.0, result.purchase.subtotal, 0.001) // 25 * 90
        assertEquals(112.5, result.purchase.tax, 0.001) // 2250 * 5%
        assertEquals(2362.5, result.purchase.grandTotal, 0.001)

        // 2. Verify Purchase Item
        assertEquals(1, result.items.size)
        assertEquals(prodId, result.items[0].productId)
        assertEquals(25.0, result.items[0].quantity, 0.001)
        assertEquals(2362.5, result.items[0].lineTotal, 0.001)

        // 3. Verify Product Stock Increase (15.0 + 25.0 = 40.0)
        val updatedProduct = repository.getProductById(prodId)
        assertNotNull(updatedProduct)
        assertEquals(40.0, updatedProduct!!.currentStock, 0.001)

        // 4. Verify Cost Price Update (80.0 -> 90.0)
        assertEquals(90.0, updatedProduct.costPrice, 0.001)

        // 5. Verify Stock Movement recorded
        val movements = repository.getStockMovementsForProductDirect(prodId)
        val purchaseMovement = movements.firstOrNull { it.operationType == StockMovementEntity.OP_PURCHASE }
        assertNotNull("Stock movement for purchase must be recorded", purchaseMovement)
        assertEquals(25.0, purchaseMovement!!.quantity, 0.001)
        assertEquals(40.0, purchaseMovement.newStock, 0.001)
    }

    @Test
    fun testCostPriceUpdateOnPurchaseWhenFlagIsFalse() = runBlocking {
        // Initial product: stock = 10.0, cost = 120.0
        val prodId = repository.insertProduct(
            ProductItem(
                name = "Tata Tea Gold 500g",
                barcode = "890103000222",
                category = "Beverages",
                sellingPrice = 160.0,
                mrp = 175.0,
                costPrice = 120.0,
                currentStock = 10.0
            )
        )

        val purchaseInput = PurchaseItemInput(
            productId = prodId,
            productName = "Tata Tea Gold 500g",
            quantity = 20.0,
            purchaseRate = 135.0,
            gstRate = 5.0,
            updateProductCostPrice = false // Do not update cost price
        )

        repository.completePurchaseTransaction(
            items = listOf(purchaseInput),
            supplierName = "Tata Consumer Wholesale"
        )

        val product = repository.getProductById(prodId)
        assertNotNull(product)
        assertEquals(30.0, product!!.currentStock, 0.001) // Stock increased: 10 + 20 = 30
        assertEquals(120.0, product.costPrice, 0.001) // Cost price preserved at 120.0
    }

    @Test
    fun testNewProductCreationThroughPurchaseWithExplicitUserSellingPriceAndMrp() = runBlocking {
        val newBarcode = "890123456789"
        assertNull(repository.getProductByBarcode(newBarcode))

        val purchaseInput = PurchaseItemInput(
            productId = 0L,
            productName = "Premium Basmati Rice 1kg",
            barcode = newBarcode,
            unit = "Packet",
            quantity = 50.0,
            purchaseRate = 80.0,
            gstRate = 5.0,
            sellingPrice = 110.0, // Explicit user selling price
            mrp = 125.0,          // Explicit user MRP
            category = "Grains & Flours"
        )

        val result = repository.completePurchaseTransaction(
            items = listOf(purchaseInput),
            supplierName = "Punjab Rice Mill"
        )

        // Verify product was created
        val createdProduct = repository.getProductByBarcode(newBarcode)
        assertNotNull("New product must be automatically registered in catalog", createdProduct)
        assertEquals("Premium Basmati Rice 1kg", createdProduct!!.name)
        assertEquals(80.0, createdProduct.costPrice, 0.001)

        // Must use explicit user prices, NOT arbitrary cost × 1.15 (which would be 92.0)
        assertEquals(110.0, createdProduct.sellingPrice, 0.001)
        assertEquals(125.0, createdProduct.mrp, 0.001)

        // Stock must match inward purchase quantity (50.0)
        assertEquals(50.0, createdProduct.currentStock, 0.001)

        // Stock movement must be recorded
        val movements = repository.getStockMovementsForProductDirect(createdProduct.id)
        val movement = movements.firstOrNull { it.operationType == StockMovementEntity.OP_PURCHASE }
        assertNotNull(movement)
        assertEquals(50.0, movement!!.quantity, 0.001)
        assertEquals(50.0, movement.newStock, 0.001)
    }

    @Test
    fun testNewProductCreationThroughPurchaseWithConfigurableMarkup() = runBlocking {
        val newBarcode = "890987654321"
        assertNull(repository.getProductByBarcode(newBarcode))

        // Cost rate = 200.0, No user selling price provided, Configurable markup = 30.0%
        // Expected Selling Price = 200.0 * (1 + 0.30) = 260.0
        // (Must NOT be arbitrary cost × 1.15 = 230.0)
        val purchaseInput = PurchaseItemInput(
            productId = 0L,
            productName = "Cold Pressed Mustard Oil 1L",
            barcode = newBarcode,
            unit = "L",
            quantity = 20.0,
            purchaseRate = 200.0,
            gstRate = 5.0,
            sellingPrice = null, // No user price
            mrp = null,
            category = "Edible Oil & Ghee"
        )

        repository.completePurchaseTransaction(
            items = listOf(purchaseInput),
            supplierName = "Village Oil Mills",
            configurableMarkupPercentage = 30.0
        )

        val createdProduct = repository.getProductByBarcode(newBarcode)
        assertNotNull(createdProduct)
        assertEquals(200.0, createdProduct!!.costPrice, 0.001)

        // Verify markup is 30% = 260.0 (NOT 1.15 × 200 = 230.0)
        assertEquals(260.0, createdProduct.sellingPrice, 0.001)
        assertNotEquals(230.0, createdProduct.sellingPrice, 0.001)

        // MRP must be at least selling price
        assertTrue("MRP must be >= selling price", createdProduct.mrp >= createdProduct.sellingPrice)
        assertEquals(20.0, createdProduct.currentStock, 0.001)
    }

    @Test
    fun testInvalidPurchaseThrowsException() = runBlocking {
        // Zero or negative quantity
        try {
            repository.completePurchaseTransaction(
                items = listOf(
                    PurchaseItemInput(productName = "Item 1", quantity = 0.0, purchaseRate = 50.0)
                ),
                supplierName = "Supplier"
            )
            fail("Should throw IllegalArgumentException on zero quantity")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Blank supplier name
        try {
            repository.completePurchaseTransaction(
                items = listOf(
                    PurchaseItemInput(productName = "Item 1", quantity = 5.0, purchaseRate = 50.0)
                ),
                supplierName = "   "
            )
            fail("Should throw IllegalArgumentException on blank supplier name")
        } catch (e: IllegalArgumentException) {
            // Expected
        }

        // Negative purchase rate
        try {
            repository.completePurchaseTransaction(
                items = listOf(
                    PurchaseItemInput(productName = "Item 1", quantity = 5.0, purchaseRate = -10.0)
                ),
                supplierName = "Supplier"
            )
            fail("Should throw IllegalArgumentException on negative purchase rate")
        } catch (e: IllegalArgumentException) {
            // Expected
        }
    }
}
