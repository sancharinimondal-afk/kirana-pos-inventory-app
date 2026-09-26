package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DATABASE_CALLBACK
import com.example.data.DuplicateBarcodeException
import com.example.data.InsufficientStockException
import com.example.data.KiranaRepository
import com.example.data.MIGRATION_1_2
import com.example.data.MIGRATION_2_3
import com.example.data.PurchaseItemInput
import com.example.data.StockManagementEngine
import com.example.data.model.CartItem
import com.example.data.model.Customer
import com.example.data.model.ProductItem
import com.example.data.model.SaleItemEntity
import com.example.data.model.StockMovementEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ProductInventoryLogicTest {

    private fun createRepository(db: AppDatabase): KiranaRepository {
        return KiranaRepository(db)
    }

    private fun buildInMemoryDb(context: Context): AppDatabase {
        return Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DATABASE_CALLBACK)
            .allowMainThreadQueries()
            .build()
    }

    // 1. Add Product Test
    @Test
    fun testAddProduct() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            val product = ProductItem(
                name = "Aashirvaad Shudh Chakki Atta 5kg",
                bengaliName = "আশীর্বাদ আটা ৫ কেজি",
                barcode = "8901030383718",
                category = "Atta & Flour",
                unit = "kg",
                costPrice = 210.0,
                sellingPrice = 245.0,
                mrp = 260.0,
                currentStock = 25.0,
                minStockAlert = 5.0,
                gstRate = 0.0,
                rackLocation = "Rack A-1"
            )

            val id = repository.insertProduct(product)
            assertTrue("Product ID must be greater than 0", id > 0)

            val fetched = repository.getProductById(id)
            assertNotNull("Inserted product must be retrievable", fetched)
            assertEquals("Aashirvaad Shudh Chakki Atta 5kg", fetched!!.name)
            assertEquals("8901030383718", fetched.barcode)
            assertEquals(210.0, fetched.costPrice, 0.01)
            assertEquals(245.0, fetched.sellingPrice, 0.01)
            assertEquals(260.0, fetched.mrp, 0.01)
            assertEquals(25.0, fetched.currentStock, 0.01)
            assertEquals(5.0, fetched.minStockAlert, 0.01)
            assertEquals(0.0, fetched.gstRate, 0.01)
            assertEquals("Rack A-1", fetched.rackLocation)
        } finally {
            db.close()
        }
    }

    // 2. Edit Product Test
    @Test
    fun testEditProduct() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            val id = repository.insertProduct(
                ProductItem(
                    name = "Tata Salt 1kg",
                    barcode = "8901030000001",
                    category = "Salt & Spices",
                    costPrice = 22.0,
                    sellingPrice = 28.0,
                    mrp = 28.0,
                    currentStock = 40.0,
                    minStockAlert = 10.0,
                    gstRate = 0.0
                )
            )

            val original = repository.getProductById(id)!!
            val updated = original.copy(
                name = "Tata Salt Vacuum Evaporated 1kg",
                costPrice = 23.0,
                sellingPrice = 30.0,
                mrp = 30.0,
                gstRate = 5.0,
                minStockAlert = 12.0
            )
            repository.updateProduct(updated)

            val fetched = repository.getProductById(id)!!
            assertEquals("Tata Salt Vacuum Evaporated 1kg", fetched.name)
            assertEquals(23.0, fetched.costPrice, 0.01)
            assertEquals(30.0, fetched.sellingPrice, 0.01)
            assertEquals(30.0, fetched.mrp, 0.01)
            assertEquals(5.0, fetched.gstRate, 0.01)
            assertEquals(12.0, fetched.minStockAlert, 0.01)
        } finally {
            db.close()
        }
    }

    // 3. Duplicate Barcode Test (Must be rejected)
    @Test
    fun testDuplicateBarcode() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            repository.insertProduct(
                ProductItem(
                    name = "Maggi 2-Minute Noodles 70g",
                    barcode = "8901058852333",
                    category = "Noodles & Instant",
                    costPrice = 11.5,
                    sellingPrice = 14.0,
                    mrp = 14.0,
                    currentStock = 50.0
                )
            )

            // Attempt to insert duplicate barcode
            var duplicateRejected = false
            try {
                repository.insertProduct(
                    ProductItem(
                        name = "Yippee Noodles 70g",
                        barcode = "8901058852333", // Same barcode!
                        category = "Noodles & Instant",
                        costPrice = 10.0,
                        sellingPrice = 12.0,
                        mrp = 12.0,
                        currentStock = 20.0
                    )
                )
            } catch (e: DuplicateBarcodeException) {
                duplicateRejected = true
            } catch (e: Exception) {
                // SQLite constraint also acceptable as fallback rejection
                duplicateRejected = true
            }

            assertTrue("Duplicate barcode MUST be rejected!", duplicateRejected)

            // Also test edit to duplicate barcode
            val id2 = repository.insertProduct(
                ProductItem(
                    name = "Top Ramen 70g",
                    barcode = "8901058852444",
                    category = "Noodles & Instant",
                    costPrice = 10.0,
                    sellingPrice = 12.0,
                    mrp = 12.0,
                    currentStock = 20.0
                )
            )

            var editDuplicateRejected = false
            try {
                val p2 = repository.getProductById(id2)!!
                repository.updateProduct(p2.copy(barcode = "8901058852333")) // Conflict with Maggi
            } catch (e: DuplicateBarcodeException) {
                editDuplicateRejected = true
            } catch (e: Exception) {
                editDuplicateRejected = true
            }
            assertTrue("Editing to duplicate barcode MUST be rejected!", editDuplicateRejected)
        } finally {
            db.close()
        }
    }

    // 4. No Barcode / Empty Barcode Test (Must not conflict)
    @Test
    fun testNoBarcode() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            // Insert product with empty barcode string
            val id1 = repository.insertProduct(
                ProductItem(
                    name = "Loose Basmati Rice (Loose/Unbranded)",
                    barcode = "",
                    category = "Rice & Dals",
                    costPrice = 75.0,
                    sellingPrice = 90.0,
                    mrp = 90.0,
                    currentStock = 100.0
                )
            )

            // Insert another product with empty barcode string
            val id2 = repository.insertProduct(
                ProductItem(
                    name = "Loose Chana Dal (Unbranded)",
                    barcode = "",
                    category = "Rice & Dals",
                    costPrice = 85.0,
                    sellingPrice = 105.0,
                    mrp = 105.0,
                    currentStock = 50.0
                )
            )

            // Insert third product with whitespace barcode
            val id3 = repository.insertProduct(
                ProductItem(
                    name = "Local Sugar M-Grade (Loose)",
                    barcode = "   ",
                    category = "Sugar & Jaggery",
                    costPrice = 40.0,
                    sellingPrice = 46.0,
                    mrp = 46.0,
                    currentStock = 200.0
                )
            )

            assertTrue("First product with no barcode must succeed", id1 > 0)
            assertTrue("Second product with no barcode must succeed without conflict", id2 > 0)
            assertTrue("Third product with whitespace barcode must succeed without conflict", id3 > 0)

            val all = repository.allProducts.first()
            assertEquals("All 3 products without barcode must exist simultaneously", 3, all.size)

            val p1 = repository.getProductById(id1)!!
            val p2 = repository.getProductById(id2)!!
            val p3 = repository.getProductById(id3)!!

            assertEquals("", p1.barcode)
            assertEquals("", p2.barcode)
            assertEquals("", p3.barcode)

            // Verify SKU fallback
            assertTrue("SKU must be valid", p1.sku.startsWith("SKU-"))
            assertTrue("SKU must be valid", p2.sku.startsWith("SKU-"))
            assertNotEquals("SKUs must be unique", p1.sku, p2.sku)
        } finally {
            db.close()
        }
    }

    // 5. Stock Increase Test
    @Test
    fun testStockIncrease() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            val id = repository.insertProduct(
                ProductItem(
                    name = "Fortune Mustard Oil 1L",
                    barcode = "8906007281015",
                    category = "Edible Oils & Ghee",
                    costPrice = 135.0,
                    sellingPrice = 155.0,
                    mrp = 165.0,
                    currentStock = 12.0
                )
            )

            val updated = repository.increaseStock(id, 8.0)
            assertEquals(20.0, updated.currentStock, 0.001)

            val fromDb = repository.getProductById(id)!!
            assertEquals(20.0, fromDb.currentStock, 0.001)
        } finally {
            db.close()
        }
    }

    // 6. Stock Decrease Test
    @Test
    fun testStockDecrease() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            val id = repository.insertProduct(
                ProductItem(
                    name = "Amul Butter 100g",
                    barcode = "8901262010048",
                    category = "Dairy & Bakery",
                    costPrice = 48.0,
                    sellingPrice = 56.0,
                    mrp = 56.0,
                    currentStock = 15.0
                )
            )

            val updated = repository.decreaseStock(id, 5.0)
            assertEquals(10.0, updated.currentStock, 0.001)

            val fromDb = repository.getProductById(id)!!
            assertEquals(10.0, fromDb.currentStock, 0.001)
        } finally {
            db.close()
        }
    }

    // 7. Low Stock Rule Test: stock > 0 AND stock <= minimumStock
    @Test
    fun testLowStock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            // Product with stock > 0 and stock <= minStockAlert -> LOW STOCK
            val idLow = repository.insertProduct(
                ProductItem(
                    name = "Red Label Tea 250g",
                    barcode = "890103001001",
                    category = "Tea & Coffee",
                    costPrice = 110.0,
                    sellingPrice = 130.0,
                    mrp = 135.0,
                    currentStock = 3.0,
                    minStockAlert = 5.0
                )
            )

            // Product with stock > minStockAlert -> NORMAL (NOT low)
            val idNormal = repository.insertProduct(
                ProductItem(
                    name = "Taj Mahal Tea 250g",
                    barcode = "890103001002",
                    category = "Tea & Coffee",
                    costPrice = 130.0,
                    sellingPrice = 155.0,
                    mrp = 160.0,
                    currentStock = 10.0,
                    minStockAlert = 5.0
                )
            )

            // Product with stock <= 0 -> OUT OF STOCK (NOT low stock according to rule)
            val idOut = repository.insertProduct(
                ProductItem(
                    name = "Wagh Bakri Tea 250g",
                    barcode = "890103001003",
                    category = "Tea & Coffee",
                    costPrice = 115.0,
                    sellingPrice = 135.0,
                    mrp = 140.0,
                    currentStock = 0.0,
                    minStockAlert = 5.0
                )
            )

            val pLow = repository.getProductById(idLow)!!
            val pNormal = repository.getProductById(idNormal)!!
            val pOut = repository.getProductById(idOut)!!

            assertTrue("pLow must be low stock (stock > 0 and stock <= minStockAlert)", pLow.isLowStock)
            assertFalse("pLow must not be out of stock", pLow.isOutOfStock)

            assertFalse("pNormal must not be low stock", pNormal.isLowStock)
            assertFalse("pNormal must not be out of stock", pNormal.isOutOfStock)

            assertFalse("pOut must NOT be low stock (stock <= 0)", pOut.isLowStock)
            assertTrue("pOut must be out of stock", pOut.isOutOfStock)

            // Check DAO query
            val lowStockList = repository.lowStockProducts.first()
            assertEquals("Only 1 product should be in low stock list", 1, lowStockList.size)
            assertEquals(idLow, lowStockList[0].id)
        } finally {
            db.close()
        }
    }

    // 8. Out of Stock Rule Test: stock <= 0
    @Test
    fun testOutOfStock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            val idZero = repository.insertProduct(
                ProductItem(
                    name = "Surf Excel Quick Wash 1kg",
                    barcode = "890103002001",
                    category = "Cleaning & Household",
                    costPrice = 140.0,
                    sellingPrice = 165.0,
                    mrp = 170.0,
                    currentStock = 0.0,
                    minStockAlert = 5.0
                )
            )

            val pZero = repository.getProductById(idZero)!!
            assertTrue("Stock == 0 must be out of stock", pZero.isOutOfStock)
            assertFalse("Stock == 0 must not be low stock", pZero.isLowStock)

            val outOfStockList = repository.outOfStockProducts.first()
            assertEquals(1, outOfStockList.size)
            assertEquals(idZero, outOfStockList[0].id)
        } finally {
            db.close()
        }
    }

    // 9. Insufficient Stock Test (Prevent negative stock unless explicit setting allows it)
    @Test
    fun testInsufficientStock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            val id = repository.insertProduct(
                ProductItem(
                    name = "Dettol Soap 75g",
                    barcode = "890103003001",
                    category = "Personal Care",
                    costPrice = 30.0,
                    sellingPrice = 38.0,
                    mrp = 40.0,
                    currentStock = 2.0
                )
            )

            // Default: allowNegativeStock is false
            assertFalse("Default policy must prevent negative stock", repository.allowNegativeStock)

            var exceptionThrown = false
            try {
                repository.decreaseStock(id, 5.0) // 2.0 - 5.0 = -3.0
            } catch (e: InsufficientStockException) {
                exceptionThrown = true
            }

            assertTrue("Decreasing stock below 0 must throw InsufficientStockException", exceptionThrown)

            // Verify stock was not changed
            val unchanged = repository.getProductById(id)!!
            assertEquals(2.0, unchanged.currentStock, 0.001)

            // Also test updateStock with negative value
            var negativeUpdateBlocked = false
            try {
                repository.updateStock(id, -1.0)
            } catch (e: InsufficientStockException) {
                negativeUpdateBlocked = true
            }
            assertTrue("Direct update to negative stock must be prevented", negativeUpdateBlocked)

            // Now test when explicit setting allows it:
            repository.allowNegativeStock = true
            val allowedResult = repository.decreaseStock(id, 5.0)
            assertEquals(-3.0, allowedResult.currentStock, 0.001)
            val updatedInDb = repository.getProductById(id)!!
            assertEquals(-3.0, updatedInDb.currentStock, 0.001)
        } finally {
            db.close()
        }
    }

    // 10. Restart Application Test (Persistence across database restart)
    @Test
    fun testRestartApplication() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbFile = File(context.filesDir, "test_restart_inventory.db")
        if (dbFile.exists()) dbFile.delete()

        var db1: AppDatabase? = null
        var insertedId = 0L

        try {
            // First run: Open database and insert product
            db1 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(DATABASE_CALLBACK)
                .allowMainThreadQueries()
                .build()

            val repo1 = createRepository(db1)
            insertedId = repo1.insertProduct(
                ProductItem(
                    name = "Everest Garam Masala 100g",
                    bengaliName = "এভারেস্ট গরম মশলা",
                    barcode = "8901786111111",
                    category = "Salt & Spices",
                    unit = "Packet",
                    costPrice = 68.0,
                    sellingPrice = 82.0,
                    mrp = 85.0,
                    currentStock = 18.0,
                    minStockAlert = 4.0,
                    gstRate = 5.0,
                    rackLocation = "Rack C-3"
                )
            )
            repo1.increaseStock(insertedId, 5.0) // 18 + 5 = 23
        } finally {
            db1?.close()
        }

        // Simulate app kill & restart: Open a completely new Room database instance on the same file
        var db2: AppDatabase? = null
        try {
            db2 = Room.databaseBuilder(context, AppDatabase::class.java, dbFile.absolutePath)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .addCallback(DATABASE_CALLBACK)
                .allowMainThreadQueries()
                .build()

            val repo2 = createRepository(db2)
            val fetchedAfterRestart = repo2.getProductById(insertedId)

            assertNotNull("Product must persist across application restart", fetchedAfterRestart)
            assertEquals("Everest Garam Masala 100g", fetchedAfterRestart!!.name)
            assertEquals("এভারেস্ট গরম মশলা", fetchedAfterRestart.bengaliName)
            assertEquals("8901786111111", fetchedAfterRestart.barcode)
            assertEquals(68.0, fetchedAfterRestart.costPrice, 0.01)
            assertEquals(82.0, fetchedAfterRestart.sellingPrice, 0.01)
            assertEquals(85.0, fetchedAfterRestart.mrp, 0.01)
            assertEquals(23.0, fetchedAfterRestart.currentStock, 0.01) // Stock change survived
            assertEquals(4.0, fetchedAfterRestart.minStockAlert, 0.01)
            assertEquals(5.0, fetchedAfterRestart.gstRate, 0.01)
            assertEquals("Rack C-3", fetchedAfterRestart.rackLocation)
        } finally {
            db2?.close()
            dbFile.delete()
        }
    }

    // 11. Database-Empty Check & No Auto Sample Products Test
    @Test
    fun testDatabaseEmptyCheck() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            // Fresh DB: product count must be 0
            val initialCount = repository.getProductCountDirect()
            assertEquals("Fresh user database must be empty (count = 0)", 0, initialCount)

            val flowCount = repository.productCount.first()
            assertEquals(0, flowCount)

            // Adding a product updates the count
            repository.insertProduct(
                ProductItem(
                    name = "User's First Product",
                    barcode = "1112223334445",
                    category = "General",
                    costPrice = 10.0,
                    sellingPrice = 15.0,
                    mrp = 15.0,
                    currentStock = 5.0
                )
            )

            val afterCount = repository.getProductCountDirect()
            assertEquals("Product count must be 1 after inserting a product", 1, afterCount)
        } finally {
            db.close()
        }
    }

    // 12. Phase 2: Product Historical Preservation via Deactivation Test
    @Test
    fun testProductHistoricalPreservationViaDeactivation() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            val prodId = repository.insertProduct(
                ProductItem(
                    name = "Preserved Mustard Oil 1L",
                    barcode = "8901234999999",
                    category = "Oils",
                    costPrice = 110.0,
                    sellingPrice = 135.0,
                    mrp = 150.0,
                    currentStock = 20.0
                )
            )

            // No history yet -> deleteProductById physically removes it
            val tempId = repository.insertProduct(
                ProductItem(
                    name = "Temporary Product",
                    barcode = "TEMP_001",
                    category = "General",
                    costPrice = 40.0,
                    sellingPrice = 50.0,
                    mrp = 50.0,
                    currentStock = 10.0
                )
            )
            val tempDeactivated = repository.deleteProductById(tempId)
            assertFalse("Product with NO history must be physically removed (wasDeactivated = false)", tempDeactivated)
            assertNull(repository.getProductById(tempId))

            // Now make a sale with prodId
            val saleResult = repository.completeSaleTransaction(
                items = listOf(
                    CartItem(
                        productId = prodId,
                        barcode = "8901234999999",
                        name = "Preserved Mustard Oil 1L",
                        unit = "Litre",
                        rate = 135.0,
                        costPrice = 110.0,
                        mrp = 150.0,
                        quantity = 2.0
                    )
                ),
                customerName = "Sunil Pal",
                paymentMode = "CASH"
            )
            assertTrue(repository.hasProductHistory(prodId))

            // When user tries to delete a product that has historical sales:
            val wasDeactivated = repository.deleteProductById(prodId)
            assertTrue("Product with historical sales must NOT be permanently deleted; must be deactivated", wasDeactivated)

            // The product record MUST still exist in database for historical continuity
            val preserved = repository.getProductById(prodId)
            assertNotNull("Product record must NOT be destroyed", preserved)
            assertFalse("Product must have isActive = false", preserved!!.isActive)

            // Active products query must exclude it
            val activeList = repository.getActiveProductsDirect()
            assertFalse(activeList.any { it.id == prodId })

            // Historical sale items still link correctly to product
            val saleItems = repository.getSaleItemsByTransactionId(saleResult.transaction.id)
            assertEquals(1, saleItems.size)
            assertEquals(prodId, saleItems[0].productId)
            assertEquals("Preserved Mustard Oil 1L", saleItems[0].productName)

            // Attempting to bill the deactivated product must fail
            var billingFailed = false
            try {
                repository.completeSaleTransaction(
                    items = listOf(
                        CartItem(
                            productId = prodId,
                            barcode = "8901234999999",
                            name = "Preserved Mustard Oil 1L",
                            unit = "Litre",
                            rate = 135.0,
                            mrp = 150.0,
                            costPrice = 110.0,
                            quantity = 1.0
                        )
                    )
                )
            } catch (e: IllegalStateException) {
                billingFailed = true
                assertTrue(e.message?.contains("inactive") == true)
            }
            assertTrue("Cannot bill inactive product", billingFailed)

            // Reactivating the product
            repository.reactivateProduct(prodId)
            val reactivated = repository.getProductById(prodId)
            assertTrue("Product should be active again after reactivation", reactivated!!.isActive)
        } finally {
            db.close()
        }
    }

    // 13. Phase 2: Stock Movement Operation Types and Returns Test
    @Test
    fun testStockMovementOperationTypesAndReturns() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            // Verify all required operation types are defined
            val requiredTypes = listOf(
                "INITIAL", "PURCHASE", "SALE", "SALE_CANCEL",
                "ADJUSTMENT", "CUSTOMER_RETURN", "SUPPLIER_RETURN"
            )
            for (t in requiredTypes) {
                assertTrue("StockMovementEntity must support $t", StockMovementEntity.ALL_OPERATION_TYPES.contains(t))
            }

            val prodId = repository.insertProduct(
                ProductItem(
                    name = "Basmati Rice 1kg",
                    barcode = "8909999000001",
                    category = "Rice",
                    costPrice = 100.0,
                    sellingPrice = 120.0,
                    mrp = 130.0,
                    currentStock = 50.0
                )
            )

            // 1. Customer Return: customer returns 3kg
            val returnedProduct = repository.recordCustomerReturn(
                productId = prodId,
                quantity = 3.0,
                referenceNumber = "RET-CUST-001",
                reason = "Damaged packet return"
            )
            assertEquals(53.0, returnedProduct.currentStock, 0.001)

            // 2. Supplier Return: shopkeeper returns 5kg to supplier
            val supplierReturnProd = repository.recordSupplierReturn(
                productId = prodId,
                quantity = 5.0,
                referenceNumber = "RET-SUPP-001",
                reason = "Expired lot returned"
            )
            assertEquals(48.0, supplierReturnProd.currentStock, 0.001)

            // Verify stock movement logs
            val movements = repository.getStockMovementsForProductDirect(prodId)
            val custRet = movements.find { it.operationType == StockMovementEntity.OP_CUSTOMER_RETURN }
            assertNotNull(custRet)
            assertEquals(3.0, custRet!!.quantity, 0.001)
            assertEquals(50.0, custRet.oldStock, 0.001)
            assertEquals(53.0, custRet.newStock, 0.001)
            assertEquals("RET-CUST-001", custRet.referenceNumber)

            val suppRet = movements.find { it.operationType == StockMovementEntity.OP_SUPPLIER_RETURN }
            assertNotNull(suppRet)
            assertEquals(5.0, suppRet!!.quantity, 0.001)
            assertEquals(53.0, suppRet.oldStock, 0.001)
            assertEquals(48.0, suppRet.newStock, 0.001)
            assertEquals("RET-SUPP-001", suppRet.referenceNumber)
        } finally {
            db.close()
        }
    }

    // 14. Phase 2: SaleTransaction Full Fields and Cancellation Audit Test
    @Test
    fun testSaleTransactionFullFieldsAndCancellationAudit() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            val prodId = repository.insertProduct(
                ProductItem(
                    name = "Atta 10kg",
                    barcode = "8905555000001",
                    category = "Atta",
                    costPrice = 340.0,
                    sellingPrice = 380.0,
                    mrp = 400.0,
                    currentStock = 10.0
                )
            )

            val custId = repository.insertCustomer(
                Customer(name = "Babulal", phone = "9876543210")
            )

            // Complete sale with all audit fields
            val saleResult = repository.completeSaleTransaction(
                items = listOf(
                    CartItem(
                        productId = prodId,
                        barcode = "8905555000001",
                        name = "Atta 10kg",
                        unit = "Bag",
                        rate = 380.0,
                        mrp = 400.0,
                        costPrice = 340.0,
                        quantity = 1.0
                    )
                ),
                customerName = "Babulal",
                customerPhone = "9876543210",
                paymentMode = "CASH",
                customerId = custId,
                cashReceived = 500.0,
                changeDue = 120.0,
                paymentReference = "CASH-PAY-01"
            )

            val tx = repository.getTransactionById(saleResult.transaction.id)
            assertNotNull(tx)
            assertEquals(custId, tx!!.customerId)
            assertEquals(500.0, tx.cashReceived, 0.001)
            assertEquals(120.0, tx.changeDue, 0.001)
            assertEquals("CASH-PAY-01", tx.paymentReference)
            assertTrue("createdAt must be set", tx.createdAt > 0)
            assertFalse(tx.isCancelled)
            assertNull(tx.cancelledAt)

            // Cancel the sale
            val cancelled = repository.cancelSaleTransaction(tx.id, "Customer requested refund")
            assertNotNull(cancelled)
            assertTrue(cancelled!!.isCancelled)
            assertEquals("Customer requested refund", cancelled.cancellationReason)
            assertNotNull(cancelled.cancelledAt)
            assertTrue(cancelled.cancelledAt!! > 0)

            // Verify stock restored
            val productAfterCancel = repository.getProductById(prodId)
            assertEquals(10.0, productAfterCancel!!.currentStock, 0.001)

            // Verify stock movement log for cancellation
            val movements = repository.getStockMovementsForProductDirect(prodId)
            val cancelLog = movements.find { it.operationType == StockMovementEntity.OP_SALE_CANCEL }
            assertNotNull(cancelLog)
            assertEquals(1.0, cancelLog!!.quantity, 0.001)
            assertEquals(tx.invoiceNumber, cancelLog.referenceNumber)
        } finally {
            db.close()
        }
    }

    // ==========================================
    // PHASE 3 — CENTRALIZED STOCK ENGINE TESTS
    // ==========================================

    @Test
    fun testPurchaseStockIncreasesCorrectlyAndLogsMovement() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            val prodId = repository.insertProduct(
                ProductItem(
                    name = "Fortune Mustard Oil 1L",
                    barcode = "8906007280011",
                    category = "Edible Oils",
                    costPrice = 130.0,
                    sellingPrice = 160.0,
                    mrp = 175.0,
                    currentStock = 10.0
                )
            )

            // Perform wholesale purchase of 25 units
            val purchaseResult = repository.completePurchaseTransaction(
                supplierName = "Adani Wilmar Distributor",
                supplierPhone = "9876543210",
                paymentMode = "CASH",
                items = listOf(
                    PurchaseItemInput(
                        barcode = "8906007280011",
                        productName = "Fortune Mustard Oil 1L",
                        unit = "L",
                        quantity = 25.0,
                        purchaseRate = 128.0,
                        gstRate = 5.0,
                        lineDiscount = 0.0,
                        updateProductCostPrice = true
                    )
                ),
                discount = 0.0,
                note = "Monthly oil restock"
            )

            // Rule 5: Purchase: oldStock + purchaseQuantity = newStock (10.0 + 25.0 = 35.0)
            val updatedProd = repository.getProductById(prodId)!!
            assertEquals(35.0, updatedProd.currentStock, 0.001)
            assertEquals(128.0, updatedProd.costPrice, 0.001)

            // Rule 7: Every stock change creates a StockMovement record
            val movements = repository.getStockMovementsForProductDirect(prodId)
            val purchaseMovement = movements.find { it.operationType == StockMovementEntity.OP_PURCHASE }
            assertNotNull("Stock movement for purchase must exist", purchaseMovement)
            assertEquals(10.0, purchaseMovement!!.oldStock, 0.001)
            assertEquals(25.0, purchaseMovement.quantity, 0.001)
            assertEquals(35.0, purchaseMovement.newStock, 0.001)
            assertEquals(purchaseResult.purchase.purchaseNumber, purchaseMovement.referenceNumber)
        } finally {
            db.close()
        }
    }

    @Test
    fun testSaleStockDecreasesCorrectlyAndLogsMovement() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            val prodId = repository.insertProduct(
                ProductItem(
                    name = "Aashirvaad Atta 5kg",
                    barcode = "8901030010022",
                    category = "Atta & Flours",
                    costPrice = 210.0,
                    sellingPrice = 260.0,
                    mrp = 280.0,
                    currentStock = 15.0
                )
            )

            // Process retail sale of 4 units
            val saleResult = repository.completeSaleTransaction(
                items = listOf(
                    CartItem(
                        productId = prodId,
                        name = "Aashirvaad Atta 5kg",
                        barcode = "8901030010022",
                        rate = 260.0,
                        mrp = 280.0,
                        costPrice = 210.0,
                        quantity = 4.0,
                        unit = "BAG",
                        gstRate = 0.0
                    )
                ),
                customerName = "Ramesh Gupta",
                paymentMode = "CASH"
            )

            // Rule 4: Sale: oldStock - soldQuantity = newStock (15.0 - 4.0 = 11.0)
            val updatedProd = repository.getProductById(prodId)!!
            assertEquals(11.0, updatedProd.currentStock, 0.001)

            // Rule 7: Every stock change creates a StockMovement record
            val movements = repository.getStockMovementsForProductDirect(prodId)
            val saleMovement = movements.find { it.operationType == StockMovementEntity.OP_SALE }
            assertNotNull("Stock movement for retail sale must exist", saleMovement)
            assertEquals(15.0, saleMovement!!.oldStock, 0.001)
            assertEquals(4.0, saleMovement.quantity, 0.001)
            assertEquals(11.0, saleMovement.newStock, 0.001)
            assertEquals(saleResult.transaction.invoiceNumber, saleMovement.referenceNumber)
        } finally {
            db.close()
        }
    }

    @Test
    fun testSaleCancellationStockRestoration() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            val prodId = repository.insertProduct(
                ProductItem(
                    name = "Maggi 2-Minute Noodles 70g",
                    barcode = "8901058852333",
                    category = "Instant Food",
                    costPrice = 11.0,
                    sellingPrice = 14.0,
                    mrp = 14.0,
                    currentStock = 20.0
                )
            )

            // Sell 6 units
            val saleResult = repository.completeSaleTransaction(
                items = listOf(
                    CartItem(
                        productId = prodId,
                        name = "Maggi 2-Minute Noodles 70g",
                        barcode = "8901058852333",
                        rate = 14.0,
                        mrp = 14.0,
                        costPrice = 11.0,
                        quantity = 6.0,
                        unit = "PCS",
                        gstRate = 0.0
                    )
                ),
                customerName = "Anita Sharma",
                paymentMode = "UPI"
            )
            assertEquals(14.0, repository.getProductById(prodId)!!.currentStock, 0.001)

            // Cancel the sale transaction
            val cancelledTx = repository.cancelSaleTransaction(saleResult.transaction.id, "Wrong billing by cashier")
            assertNotNull(cancelledTx)
            assertTrue(cancelledTx!!.isCancelled)

            // Rule 6: Cancelled sale: oldStock + cancelledQuantity = newStock (14.0 + 6.0 = 20.0)
            val restoredProd = repository.getProductById(prodId)!!
            assertEquals(20.0, restoredProd.currentStock, 0.001)

            // Verify SALE_CANCEL movement record
            val movements = repository.getStockMovementsForProductDirect(prodId)
            val cancelMovement = movements.find { it.operationType == StockMovementEntity.OP_SALE_CANCEL }
            assertNotNull("Stock movement for sale cancellation must exist", cancelMovement)
            assertEquals(14.0, cancelMovement!!.oldStock, 0.001)
            assertEquals(6.0, cancelMovement.quantity, 0.001)
            assertEquals(20.0, cancelMovement.newStock, 0.001)
            assertEquals(saleResult.transaction.invoiceNumber, cancelMovement.referenceNumber)
        } finally {
            db.close()
        }
    }

    @Test
    fun testManualStockAdjustmentRulesAndReason() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            val prodId = repository.insertProduct(
                ProductItem(
                    name = "Sugar / Chini 1kg",
                    barcode = "8901234567801",
                    category = "Staples",
                    costPrice = 38.0,
                    sellingPrice = 45.0,
                    mrp = 48.0,
                    currentStock = 50.0
                )
            )

            // Rule 9: Stock adjustment must have a reason
            var blankReasonFailed = false
            try {
                repository.adjustStock(prodId, 45.0, reason = "   ")
            } catch (e: IllegalArgumentException) {
                blankReasonFailed = true
            }
            assertTrue("adjustStock with empty/blank reason must fail", blankReasonFailed)

            // Perform valid adjustment (physical audit discrepancy: 50 -> 46)
            val adjusted = repository.adjustStock(prodId, 46.0, reason = "Physical inventory audit count")
            assertEquals(46.0, adjusted.currentStock, 0.001)

            val movementsAfterAdjust = repository.getStockMovementsForProductDirect(prodId)
            val adjMovement = movementsAfterAdjust.find { it.operationType == StockMovementEntity.OP_ADJUSTMENT }
            assertNotNull(adjMovement)
            assertEquals(50.0, adjMovement!!.oldStock, 0.001)
            assertEquals(46.0, adjMovement.newStock, 0.001)
            assertEquals(4.0, adjMovement.quantity, 0.001)
            assertEquals("Physical inventory audit count", adjMovement.reason)

            // Rule 10: Do not create a stock movement when oldStock == newStock
            val movementCountBeforeNoop = repository.getStockMovementsForProductDirect(prodId).size
            val noopResult = repository.adjustStock(prodId, 46.0, reason = "Redundant recount")
            assertEquals(46.0, noopResult.currentStock, 0.001)
            val movementCountAfterNoop = repository.getStockMovementsForProductDirect(prodId).size
            assertEquals("No stock movement should be recorded when oldStock == newStock", movementCountBeforeNoop, movementCountAfterNoop)
        } finally {
            db.close()
        }
    }

    @Test
    fun testInsufficientStockValidationInsideTransaction() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            val prodId = repository.insertProduct(
                ProductItem(
                    name = "Basmati Rice 1kg",
                    barcode = "8901234567802",
                    category = "Rice & Grains",
                    costPrice = 80.0,
                    sellingPrice = 110.0,
                    mrp = 120.0,
                    currentStock = 3.0
                )
            )

            // Rule 1 & 2: Stock must never become negative unless allowNegativeStock setting exists
            // Validate stock again inside Room transaction
            var saleFailed = false
            try {
                repository.completeSaleTransaction(
                    items = listOf(
                        CartItem(
                            productId = prodId,
                            name = "Basmati Rice 1kg",
                            barcode = "8901234567802",
                            rate = 110.0,
                            mrp = 120.0,
                            costPrice = 80.0,
                            quantity = 5.0, // Requested 5, only 3 in stock!
                            unit = "KG",
                            gstRate = 0.0
                        )
                    ),
                    customerName = "Customer with excessive request",
                    paymentMode = "CASH"
                )
            } catch (e: InsufficientStockException) {
                saleFailed = true
            }
            assertTrue("Selling more than available stock without allowNegativeStock must fail", saleFailed)

            // Verify stock remained unchanged at 3.0
            assertEquals(3.0, repository.getProductById(prodId)!!.currentStock, 0.001)

            // Rule 3: Never trust stock values supplied only by the UI
            var decreaseFailed = false
            try {
                repository.decreaseStock(prodId, 10.0)
            } catch (e: InsufficientStockException) {
                decreaseFailed = true
            }
            assertTrue("Direct decrease beyond stock must fail", decreaseFailed)
            assertEquals(3.0, repository.getProductById(prodId)!!.currentStock, 0.001)

            // Now enable allowNegativeStock explicitly
            repository.allowNegativeStock = true
            val allowedSale = repository.completeSaleTransaction(
                items = listOf(
                    CartItem(
                        productId = prodId,
                        name = "Basmati Rice 1kg",
                        barcode = "8901234567802",
                        rate = 110.0,
                        mrp = 120.0,
                        costPrice = 80.0,
                        quantity = 5.0,
                        unit = "KG",
                        gstRate = 0.0
                    )
                ),
                customerName = "Allowed Negative Sale",
                paymentMode = "CASH"
            )
            assertNotNull(allowedSale)
            // Stock was 3.0 - 5.0 = -2.0
            assertEquals(-2.0, repository.getProductById(prodId)!!.currentStock, 0.001)
        } finally {
            db.close()
        }
    }

    @Test
    fun testProductInformationEditDoesNotSilentlyChangeStock() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val repository = createRepository(db)
        try {
            val prodId = repository.insertProduct(
                ProductItem(
                    name = "Haldiram Bhujia 400g",
                    barcode = "8904004400112",
                    category = "Snacks",
                    costPrice = 85.0,
                    sellingPrice = 110.0,
                    mrp = 115.0,
                    currentStock = 28.0
                )
            )

            // Rule 8: Editing product information must NOT silently change stock.
            // Even if an external caller or UI form passes a different currentStock, updateProduct must preserve DB stock.
            val fetched = repository.getProductById(prodId)!!
            val modifiedProduct = fetched.copy(
                name = "Haldiram Premium Sev Bhujia 400g",
                sellingPrice = 115.0,
                mrp = 120.0,
                currentStock = 999.0 // UI tries to tamper or pass modified stock!
            )
            repository.updateProduct(modifiedProduct)

            val reloaded = repository.getProductById(prodId)!!
            assertEquals("Haldiram Premium Sev Bhujia 400g", reloaded.name)
            assertEquals(115.0, reloaded.sellingPrice, 0.001)
            assertEquals(120.0, reloaded.mrp, 0.001)
            // CRITICAL CHECK: currentStock MUST remain 28.0!
            assertEquals("Editing product info must preserve inventory stock", 28.0, reloaded.currentStock, 0.001)
        } finally {
            db.close()
        }
    }

    @Test
    fun testCentralizedStockManagementEngineDirectly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = buildInMemoryDb(context)
        val engine = StockManagementEngine(db, db.productDao(), db.stockMovementDao(), allowNegativeStock = false)
        try {
            val p = ProductItem(
                id = 0,
                barcode = "ENGINE-TEST-001",
                name = "Engine Direct Test Item",
                category = "General",
                costPrice = 50.0,
                sellingPrice = 60.0,
                mrp = 65.0,
                currentStock = 0.0
            )
            val pId = db.productDao().insertProduct(p)

            // 1. setInitialStock
            val initProd = engine.setInitialStock(pId, 10.0)
            assertEquals(10.0, initProd.currentStock, 0.001)
            val initMovements = db.stockMovementDao().getMovementsForProductDirect(pId)
            assertEquals(1, initMovements.size)
            assertEquals(StockMovementEntity.OP_INITIAL, initMovements[0].operationType)

            // 2. increaseStock
            val incProd = engine.increaseStock(pId, 5.0, reason = "Stock Inward")
            assertEquals(15.0, incProd.currentStock, 0.001)

            // 3. decreaseStock
            val decProd = engine.decreaseStock(pId, 3.0, reason = "Stock Outward")
            assertEquals(12.0, decProd.currentStock, 0.001)

            // 4. adjustStock
            val adjProd = engine.adjustStock(pId, 14.0, reason = "Damaged packet discovered")
            assertEquals(14.0, adjProd.currentStock, 0.001)

            // 5. reverseSaleStock
            val saleItems: List<SaleItemEntity> = listOf(
                SaleItemEntity(
                    id = 1,
                    transactionId = 99,
                    productId = pId,
                    barcode = "ENGINE-TEST-001",
                    productName = "Engine Direct Test Item",
                    sellingPrice = 60.0,
                    costPrice = 50.0,
                    mrp = 65.0,
                    quantity = 4.0,
                    unit = "PCS",
                    lineTotal = 240.0
                )
            )
            engine.reverseSaleStock(saleItems, "INV-99", "Customer cancellation")
            val restored = db.productDao().getProductById(pId)!!
            assertEquals(18.0, restored.currentStock, 0.001)
        } finally {
            db.close()
        }
    }
}
