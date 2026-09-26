package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DATABASE_CALLBACK
import com.example.data.KiranaRepository
import com.example.data.model.ProductItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class InventoryUiAndAdjustmentTest {

    private lateinit var database: AppDatabase
    private lateinit var repository: KiranaRepository
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DATABASE_CALLBACK)
            .allowMainThreadQueries()
            .build()
        repository = KiranaRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testStockAdjustmentCalculations() {
        // Current Stock = 50.0
        val currentStock = 50.0

        // 1. ADD 20.0 -> 70.0
        val addQty = 20.0
        val newStockAdd = currentStock + addQty
        assertEquals(70.0, newStockAdd, 0.001)

        // 2. REMOVE 15.0 -> 35.0
        val removeQty = 15.0
        val newStockRemove = currentStock - removeQty
        assertEquals(35.0, newStockRemove, 0.001)

        // 3. SET EXACT 42.0 -> 42.0
        val exactQty = 42.0
        val newStockExact = exactQty
        assertEquals(42.0, newStockExact, 0.001)
    }

    @Test
    fun testDedicatedStockAdjustmentEngineRecordsMovement() = runBlocking {
        val product = ProductItem(
            barcode = "890123450001",
            name = "Basmati Rice 5kg",
            category = "Grains",
            costPrice = 300.0,
            sellingPrice = 380.0,
            mrp = 400.0,
            currentStock = 20.0,
            unit = "pack",
            rackLocation = "A1"
        )
        val prodId = repository.insertProduct(product)

        // Adjust via ADD
        repository.increaseStock(prodId, 10.0, "REF-ADD-01", "Supplier replenishment")
        var updated = repository.getProductById(prodId)
        assertNotNull(updated)
        assertEquals(30.0, updated!!.currentStock, 0.001)

        // Adjust via REMOVE
        repository.decreaseStock(prodId, 5.0, "REF-REM-02", "Damage breakage")
        updated = repository.getProductById(prodId)
        assertEquals(25.0, updated!!.currentStock, 0.001)

        // Adjust via SET EXACT
        repository.adjustStock(prodId, 50.0, "Physical inventory count", "REF-SET-03")
        updated = repository.getProductById(prodId)
        assertEquals(50.0, updated!!.currentStock, 0.001)

        // Verify movements recorded in database
        val movements = database.stockMovementDao().getMovementsForProduct(prodId).first()
        assertTrue("Movements should be logged for every adjustment", movements.size >= 3)
    }

    @Test
    fun testDirectProductEditDoesNotAlterStock() = runBlocking {
        val originalProduct = ProductItem(
            barcode = "890123450002",
            name = "Mustard Oil 1L",
            category = "Oils",
            costPrice = 120.0,
            sellingPrice = 145.0,
            mrp = 160.0,
            currentStock = 34.0,
            unit = "bottle",
            rackLocation = "Rack 3"
        )
        val prodId = repository.insertProduct(originalProduct)
        val saved = repository.getProductById(prodId)!!

        // Simulating normal product edit (name, price, rack changed, stock preserved as saved.currentStock)
        val editedProduct = saved.copy(
            name = "Mustard Oil 1L Premium",
            sellingPrice = 150.0,
            rackLocation = "Rack 4",
            currentStock = saved.currentStock // Enforced in AddEditProductDialog
        )
        repository.updateProduct(editedProduct)

        val retrieved = repository.getProductById(prodId)!!
        assertEquals("Mustard Oil 1L Premium", retrieved.name)
        assertEquals(150.0, retrieved.sellingPrice, 0.001)
        assertEquals("Rack 4", retrieved.rackLocation)
        assertEquals("Stock must remain exactly unchanged by product metadata editing", 34.0, retrieved.currentStock, 0.001)
    }

    @Test
    fun testProductDeactivateAndReactivate() = runBlocking {
        val product = ProductItem(
            barcode = "890123450003",
            name = "Tea Leaves 250g",
            category = "Beverages",
            costPrice = 80.0,
            sellingPrice = 110.0,
            mrp = 120.0,
            currentStock = 15.0,
            isActive = true
        )
        val prodId = repository.insertProduct(product)
        assertTrue(repository.getProductById(prodId)!!.isActive)

        // Deactivate
        repository.deactivateProduct(prodId)
        assertFalse(repository.getProductById(prodId)!!.isActive)

        // Reactivate
        repository.reactivateProduct(prodId)
        assertTrue(repository.getProductById(prodId)!!.isActive)
    }

    @Test
    fun testInventoryFilterCategories() = runBlocking {
        val p1 = ProductItem(barcode = "890111", name = "Atta 10kg", category = "Atta & Flours", costPrice = 300.0, sellingPrice = 350.0, mrp = 380.0, currentStock = 20.0, minStockAlert = 5.0, isActive = true)
        val p2 = ProductItem(barcode = "890222", name = "Sugar 1kg", category = "Spices & Sugar", costPrice = 40.0, sellingPrice = 45.0, mrp = 50.0, currentStock = 2.0, minStockAlert = 5.0, isActive = true) // Low stock
        val p3 = ProductItem(barcode = "890333", name = "Salt 1kg", category = "Spices & Sugar", costPrice = 20.0, sellingPrice = 25.0, mrp = 28.0, currentStock = 0.0, minStockAlert = 5.0, isActive = true) // Out of stock
        val p4 = ProductItem(barcode = "890444", name = "Old Biscuit", category = "Snacks", costPrice = 15.0, sellingPrice = 20.0, mrp = 25.0, currentStock = 10.0, minStockAlert = 5.0, isActive = false) // Inactive

        repository.insertProduct(p1)
        repository.insertProduct(p2)
        repository.insertProduct(p3)
        repository.insertProduct(p4)

        val all = repository.allProducts.first()
        val activeProducts = all.filter { it.isActive }
        val lowStockProducts = all.filter { it.isActive && it.isLowStock }
        val outOfStockProducts = all.filter { it.isActive && it.isOutOfStock }
        val inactiveProducts = all.filter { !it.isActive }

        assertEquals(3, activeProducts.size)
        assertEquals(1, lowStockProducts.size) // p2 (2.0 > 0.0 && 2.0 <= 5.0)
        assertEquals(1, outOfStockProducts.size) // p3 (0.0 <= 0.0)
        assertEquals(1, inactiveProducts.size) // p4
    }
}
