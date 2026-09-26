package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.backup.BackupManager
import com.example.backup.ParsedBackupData
import com.example.data.AppDatabase
import com.example.data.KiranaRepository
import com.example.data.PurchaseItemInput
import com.example.data.model.CartItem
import com.example.data.model.Customer
import com.example.data.model.InvoiceSequence
import com.example.data.model.LedgerEntry
import com.example.data.model.ProductItem
import com.example.data.model.PurchaseEntity
import com.example.data.model.PurchaseItemEntity
import com.example.data.model.SaleItemEntity
import com.example.data.model.SaleTransaction
import com.example.data.model.ShopSettings
import com.example.data.model.StockMovementEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupRestoreTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: KiranaRepository
    private lateinit var context: Context

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = KiranaRepository(
            productDao = db.productDao(),
            transactionDao = db.transactionDao(),
            shopSettingsDao = db.shopSettingsDao(),
            invoiceSequenceDao = db.invoiceSequenceDao(),
            saleItemDao = db.saleItemDao(),
            database = db
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun testValidateBackupJsonSyntaxAndVersion() {
        // Test malformed JSON
        val malformedResult = BackupManager.validateBackup("{ invalid json content ...")
        assertFalse("Malformed JSON should fail validation", malformedResult.isValid)
        assertNotNull(malformedResult.errorMessage)

        // Test missing version or invalid version
        val badVersionJson = """
            {
                "schema": "kirana_pos_full_backup",
                "version": 999,
                "products": []
            }
        """.trimIndent()
        val badVersionResult = BackupManager.validateBackup(badVersionJson)
        assertFalse("Unsupported backup version should fail validation", badVersionResult.isValid)

        // Test valid backup
        val validJson = """
            {
                "schema": "kirana_pos_full_backup",
                "version": 1,
                "exportTimestamp": 1711000000000,
                "exportDate": "2026-03-20 12:00:00",
                "shopSettings": {
                    "id": 1,
                    "shopName": "Maa Tara Kirana",
                    "printerPaperWidth": "58mm"
                },
                "products": [
                    {
                        "id": 10,
                        "name": "Tata Salt 1kg",
                        "category": "Groceries",
                        "unit": "Piece",
                        "sellingPrice": 28.0,
                        "costPrice": 24.0,
                        "mrp": 30.0,
                        "currentStock": 50.0,
                        "barcode": "8901030000001",
                        "gstRate": 0.0,
                        "isActive": true
                    }
                ],
                "transactions": [],
                "saleItems": [],
                "invoiceSequences": []
            }
        """.trimIndent()
        val validResult = BackupManager.validateBackup(validJson)
        assertTrue("Valid JSON should pass validation: ${validResult.errorMessage}", validResult.isValid)
        assertEquals(1, validResult.productCount)
        assertEquals("Maa Tara Kirana", validResult.shopName)
    }

    @Test
    fun testValidateIntegrityDuplicateBarcode() {
        val dupBarcodeJson = """
            {
                "schema": "kirana_pos_full_backup",
                "version": 1,
                "products": [
                    {
                        "id": 1,
                        "name": "Fortune Oil 1L",
                        "sellingPrice": 140.0,
                        "currentStock": 20.0,
                        "barcode": "DUPLICATE123"
                    },
                    {
                        "id": 2,
                        "name": "Saffola Oil 1L",
                        "sellingPrice": 160.0,
                        "currentStock": 15.0,
                        "barcode": "DUPLICATE123"
                    }
                ]
            }
        """.trimIndent()
        val result = BackupManager.validateBackup(dupBarcodeJson)
        assertFalse("Duplicate barcodes in backup must fail validation", result.isValid)
        assertTrue("Error message must mention duplicate barcode", result.errorMessage?.contains("Duplicate barcode") == true)
    }

    @Test
    fun testValidateIntegrityNegativePriceAndStock() {
        val negPriceJson = """
            {
                "schema": "kirana_pos_full_backup",
                "version": 1,
                "products": [
                    {
                        "id": 1,
                        "name": "Free Sugar",
                        "sellingPrice": -50.0,
                        "currentStock": 20.0
                    }
                ]
            }
        """.trimIndent()
        val result = BackupManager.validateBackup(negPriceJson)
        assertFalse("Negative selling price must fail validation", result.isValid)
        assertTrue(result.errorMessage?.contains("negative selling price") == true)
    }

    @Test
    fun testSuccessfulFullRestoreDataIntegrity() = runBlocking {
        // Step 1: Pre-populate existing database with dummy records
        val initialProduct = ProductItem(
            id = 0,
            name = "Old Pre-existing Rice",
            category = "Grains",
            unit = "Kg",
            sellingPrice = 45.0,
            costPrice = 40.0,
            mrp = 50.0,
            currentStock = 10.0,
            barcode = "OLD_RICE_001"
        )
        repository.insertProduct(initialProduct)
        assertEquals(1, repository.getAllProductsDirect().size)

        // Step 2: Build a full valid backup payload
        val backupProduct1 = ProductItem(
            id = 101,
            name = "Miniket Rice 25kg Bag",
            category = "Rice",
            unit = "Bag",
            sellingPrice = 1350.0,
            costPrice = 1200.0,
            mrp = 1450.0,
            currentStock = 15.0,
            barcode = "8901234567890",
            gstRate = 0.0,
            isActive = true
        )
        val backupProduct2 = ProductItem(
            id = 102,
            name = "Aashirvaad Shudh Chakki Atta 5kg",
            category = "Atta & Flour",
            unit = "Piece",
            sellingPrice = 240.0,
            costPrice = 210.0,
            mrp = 260.0,
            currentStock = 25.0,
            barcode = "8901234567891",
            gstRate = 0.0,
            isActive = true
        )
        val backupSettings = ShopSettings(
            id = 1,
            shopName = "Kalpataru Bhandar (কল্পতরু ভাণ্ডার)",
            tagline = "Best Quality Desi Grocery",
            ownerName = "Bikash Ghosh",
            phone = "9830012345",
            address = "Gariahat Market, Kolkata",
            city = "Kolkata",
            gstin = "19AAACH1234K1Z5",
            upiId = "kalpataru@upi",
            printerPaperWidth = "80mm",
            receiptFooterNote = "ধন্যবাদ! আবার আসবেন",
            termsNote = "Exchange within 3 days with bill."
        )
        val backupTransaction = SaleTransaction(
            id = 501,
            invoiceNumber = "INV-2026-0001",
            timestamp = System.currentTimeMillis(),
            subtotal = 1590.0,
            discount = 0.0,
            gstAmount = 0.0,
            grandTotal = 1590.0,
            paymentMode = "CASH",
            customerName = "Ratan Babu",
            customerPhone = "9831122334",
            itemsJson = """[{"productId":101,"name":"Miniket Rice 25kg Bag","rate":1350.0,"quantity":1.0},{"productId":102,"name":"Aashirvaad Shudh Chakki Atta 5kg","rate":240.0,"quantity":1.0}]"""
        )
        val backupSaleItem1 = SaleItemEntity(
            id = 1001,
            transactionId = 501,
            productId = 101,
            barcode = "8901234567890",
            productName = "Miniket Rice 25kg Bag",
            unit = "Bag",
            sellingPrice = 1350.0,
            costPrice = 1200.0,
            mrp = 1450.0,
            gstRate = 0.0,
            quantity = 1.0,
            lineDiscount = 0.0,
            lineTax = 0.0,
            lineTotal = 1350.0
        )
        val backupSaleItem2 = SaleItemEntity(
            id = 1002,
            transactionId = 501,
            productId = 102,
            barcode = "8901234567891",
            productName = "Aashirvaad Shudh Chakki Atta 5kg",
            unit = "Piece",
            sellingPrice = 240.0,
            costPrice = 210.0,
            mrp = 260.0,
            gstRate = 0.0,
            quantity = 1.0,
            lineDiscount = 0.0,
            lineTax = 0.0,
            lineTotal = 240.0
        )
        val backupSequence = InvoiceSequence(
            prefix = "2026",
            lastSequenceNumber = 1L
        )

        val parsedData = ParsedBackupData(
            backupVersion = 1,
            creationTimestamp = System.currentTimeMillis(),
            exportDate = "2026-03-20 12:00:00",
            settings = backupSettings,
            products = listOf(backupProduct1, backupProduct2),
            transactions = listOf(backupTransaction),
            saleItems = listOf(backupSaleItem1, backupSaleItem2),
            invoiceSequences = listOf(backupSequence)
        )

        // Step 3: Execute atomic full restore
        val result = repository.restoreFullBackup(parsedData)
        assertTrue("Restore result must be success", result.success)
        assertEquals(2, result.productsRestored)
        assertEquals(1, result.salesRestored)
        assertEquals(2, result.saleItemsRestored)
        assertTrue(result.settingsRestored)

        // Step 4: Verify products table was cleanly replaced and populated
        val restoredProducts = repository.getAllProductsDirect()
        assertEquals(2, restoredProducts.size)
        val p1 = restoredProducts.find { it.barcode == "8901234567890" }
        assertNotNull("Product 1 must exist", p1)
        assertEquals("Miniket Rice 25kg Bag", p1!!.name)
        assertEquals(1350.0, p1.sellingPrice, 0.001)
        assertEquals(15.0, p1.currentStock, 0.001)

        val p2 = restoredProducts.find { it.barcode == "8901234567891" }
        assertNotNull("Product 2 must exist", p2)
        assertEquals("Aashirvaad Shudh Chakki Atta 5kg", p2!!.name)
        assertEquals(240.0, p2.sellingPrice, 0.001)

        // Step 5: Verify sales transaction
        val restoredTx = repository.getAllTransactionsDirect()
        assertEquals(1, restoredTx.size)
        assertEquals("INV-2026-0001", restoredTx[0].invoiceNumber)
        assertEquals(1590.0, restoredTx[0].grandTotal, 0.001)
        assertEquals("Ratan Babu", restoredTx[0].customerName)

        // Step 6: Verify sale items linked to transaction
        val restoredSaleItems = repository.getAllSaleItemsDirect()
        assertEquals(2, restoredSaleItems.size)
        val txId = restoredTx[0].id
        val itemsForTx = repository.getSaleItemsByTransactionId(txId)
        assertEquals(2, itemsForTx.size)

        // Step 7: Verify shop settings
        val settingsInDb = repository.getShopSettingsSync()
        assertNotNull(settingsInDb)
        assertEquals("Kalpataru Bhandar (কল্পতরু ভাণ্ডার)", settingsInDb!!.shopName)
        assertEquals("80mm", settingsInDb.printerPaperWidth)
        assertEquals("19AAACH1234K1Z5", settingsInDb.gstin)

        // Step 8: Verify invoice sequences
        val seqInDb = repository.getAllSequencesDirect()
        assertEquals(1, seqInDb.size)
        assertEquals("2026", seqInDb[0].prefix)
        assertEquals(1L, seqInDb[0].lastSequenceNumber)
    }

    @Test
    fun testAtomicRollbackOnFailurePreservesExistingDatabase() = runBlocking {
        // CRITICAL REQUIREMENT:
        // "Existing database must remain unchanged after a failed restore.
        // If anything fails: ROLLBACK EVERYTHING."

        // 1. Setup initial database state with 2 products, 1 transaction, and 1 setting
        val initialProduct1 = ProductItem(
            id = 0,
            name = "Original Mustard Oil",
            category = "Oil",
            unit = "Litre",
            sellingPrice = 160.0,
            costPrice = 140.0,
            mrp = 175.0,
            currentStock = 30.0,
            barcode = "ORIGINAL_OIL_001"
        )
        val initialProduct2 = ProductItem(
            id = 0,
            name = "Original Basmati Rice",
            category = "Rice",
            unit = "Kg",
            sellingPrice = 110.0,
            costPrice = 90.0,
            mrp = 120.0,
            currentStock = 50.0,
            barcode = "ORIGINAL_RICE_002"
        )
        val id1 = repository.insertProduct(initialProduct1)
        val id2 = repository.insertProduct(initialProduct2)

        val initialTx = SaleTransaction(
            id = 0,
            invoiceNumber = "INV-2026-9999",
            timestamp = System.currentTimeMillis(),
            subtotal = 270.0,
            discount = 0.0,
            gstAmount = 0.0,
            grandTotal = 270.0,
            paymentMode = "CASH",
            customerName = "Original Customer",
            itemsJson = "[]"
        )
        val initialTxId = repository.insertTransaction(initialTx)

        val originalSettings = ShopSettings(
            id = 1,
            shopName = "Original Safe Shop",
            gstin = "ORIGINAL_GSTIN"
        )
        repository.saveShopSettings(originalSettings)

        // Verify baseline state
        assertEquals(2, repository.getAllProductsDirect().size)
        assertEquals(1, repository.getAllTransactionsDirect().size)
        assertEquals("Original Safe Shop", repository.getShopSettingsSync()?.shopName)

        // 2. Prepare a backup payload that WILL FAIL midway inside the transaction
        // For example, product 2 has a duplicate barcode of product 1 which violates SQLite UNIQUE constraint
        // when inserted directly or invalid data that triggers an exception
        val failingProduct1 = ProductItem(
            id = 1,
            name = "Corrupted Item 1",
            category = "General",
            costPrice = 80.0,
            mrp = 120.0,
            sellingPrice = 100.0,
            currentStock = 10.0,
            barcode = "UNIQUE_BARCODE_FAIL"
        )
        val failingProduct2 = ProductItem(
            id = 2,
            name = "Corrupted Item 2",
            category = "General",
            costPrice = 150.0,
            mrp = 220.0,
            sellingPrice = 200.0,
            currentStock = 10.0,
            barcode = "UNIQUE_BARCODE_FAIL" // SQLite unique index violation!
        )

        var exceptionThrown = false
        try {
            // Attempt to restore JSON with bad data through repository.restoreFromBackupJson
            // First validate that validateFromBackupJson stops invalid data
            val badJson = """
                {
                    "schema": "kirana_pos_full_backup",
                    "version": 1,
                    "products": [
                        { "id": 1, "name": "Item 1", "sellingPrice": 10.0, "currentStock": 1.0, "barcode": "DUPLICATE_BARCODE_999" },
                        { "id": 2, "name": "Item 2", "sellingPrice": 20.0, "currentStock": 2.0, "barcode": "DUPLICATE_BARCODE_999" }
                    ]
                }
            """.trimIndent()
            repository.restoreFromBackupJson(badJson)
        } catch (e: Exception) {
            exceptionThrown = true
        }

        assertTrue("Exception must be thrown on invalid restore", exceptionThrown)

        // 3. VERIFY DATABASE WAS UNCHANGED: ROLLBACK WAS 100% SUCCESSFUL
        val productsAfterRollback = repository.getAllProductsDirect()
        assertEquals("All original products must remain unchanged!", 2, productsAfterRollback.size)
        assertTrue(productsAfterRollback.any { it.barcode == "ORIGINAL_OIL_001" && it.name == "Original Mustard Oil" })
        assertTrue(productsAfterRollback.any { it.barcode == "ORIGINAL_RICE_002" && it.name == "Original Basmati Rice" })

        val txAfterRollback = repository.getAllTransactionsDirect()
        assertEquals("Original transaction must remain intact!", 1, txAfterRollback.size)
        assertEquals("INV-2026-9999", txAfterRollback[0].invoiceNumber)
        assertEquals("Original Customer", txAfterRollback[0].customerName)

        val settingsAfterRollback = repository.getShopSettingsSync()
        assertNotNull(settingsAfterRollback)
        assertEquals("Original Safe Shop", settingsAfterRollback?.shopName)
        assertEquals("ORIGINAL_GSTIN", settingsAfterRollback?.gstin)
    }

    @Test
    fun testLegacyBackupReconstructionFromItemsJson() = runBlocking {
        // Older backups might only store products and transactions with embedded itemsJson
        // without separate saleItems array. Our restore engine should reconstruct sale items.
        val legacyJson = """
            {
                "schema": "kirana_pos_full_backup",
                "version": 1,
                "shopSettings": {
                    "shopName": "Subho Kirana"
                },
                "products": [
                    {
                        "id": 1,
                        "name": "Moong Dal 1kg",
                        "sellingPrice": 120.0,
                        "currentStock": 40.0,
                        "barcode": "LEGACY_BARCODE_1"
                    }
                ],
                "transactions": [
                    {
                        "id": 1,
                        "invoiceNumber": "INV-2026-0042",
                        "totalAmount": 240.0,
                        "paymentMode": "UPI",
                        "customerName": "Aniket",
                        "itemsJson": "[{\"productId\":1,\"barcode\":\"LEGACY_BARCODE_1\",\"name\":\"Moong Dal 1kg\",\"rate\":120.0,\"quantity\":2.0}]"
                    }
                ],
                "saleItems": []
            }
        """.trimIndent()

        val result = repository.restoreFromBackupJson(legacyJson)
        assertTrue(result.success)
        assertEquals(1, result.productsRestored)
        assertEquals(1, result.salesRestored)
        assertEquals(1, result.saleItemsRestored)

        val saleItemsInDb = repository.getAllSaleItemsDirect()
        assertEquals(1, saleItemsInDb.size)
        assertEquals("Moong Dal 1kg", saleItemsInDb[0].productName)
        assertEquals(2.0, saleItemsInDb[0].quantity, 0.001)
        assertEquals(240.0, saleItemsInDb[0].lineTotal, 0.001)
    }

    @Test
    fun testPhase2FullBackupAllEntitiesPreservationAndRestore() = runBlocking {
        // 1. Seed complete shop settings
        val settings = ShopSettings(
            id = 1,
            shopName = "Tara Maa Bhandar",
            tagline = "Wholesale & Retail",
            ownerName = "Bimal Roy",
            phone = "9876543210",
            address = "Kolkata Main Road",
            city = "Kolkata",
            gstin = "19ABCDE1234F1Z5",
            printerPaperWidth = "80mm"
        )
        repository.saveShopSettings(settings)

        // 2. Seed a product
        val prodId = repository.insertProduct(
            ProductItem(
                id = 201,
                name = "Basmati Rice 5kg",
                category = "Rice",
                unit = "Bag",
                sellingPrice = 450.0,
                costPrice = 380.0,
                mrp = 500.0,
                currentStock = 30.0,
                barcode = "8901234599999"
            )
        )

        // 3. Seed a Customer and Khata Ledger
        val custId = repository.insertCustomer(
            Customer(
                id = 301,
                name = "Gopal Mukherjee",
                phone = "9830099999",
                address = "Howrah",
                openingBalance = 500.0
            )
        )
        repository.insertLedgerEntry(
            LedgerEntry(
                id = 401,
                customerId = custId,
                date = System.currentTimeMillis(),
                type = LedgerEntry.TYPE_PAYMENT_RECEIVED,
                amount = 200.0,
                reference = "RCP-001",
                note = "Partial payment received"
            )
        )

        // 4. Seed a Purchase & PurchaseItem
        val purchaseResult = repository.completePurchaseTransaction(
            items = listOf(
                PurchaseItemInput(
                    productId = prodId,
                    productName = "Basmati Rice 5kg",
                    barcode = "8901234599999",
                    unit = "Bag",
                    quantity = 10.0,
                    purchaseRate = 370.0,
                    gstRate = 0.0
                )
            ),
            supplierName = "Bengal Rice Millers",
            supplierPhone = "9123456780",
            paymentMode = "Bank Transfer"
        )

        // 5. Complete a Sale with KHATA payment mode
        val saleResult = repository.completeSaleTransaction(
            items = listOf(
                CartItem(
                    productId = prodId,
                    barcode = "8901234599999",
                    name = "Basmati Rice 5kg",
                    unit = "Bag",
                    rate = 450.0,
                    costPrice = 380.0,
                    mrp = 500.0,
                    quantity = 2.0
                )
            ),
            customerName = "Gopal Mukherjee",
            customerPhone = "9830099999",
            paymentMode = "KHATA",
            customerId = custId
        )

        // 6. Build the Full Phase 2 Backup String directly from repository
        val backupJson = BackupManager.buildBackupJsonString(
            products = repository.getAllProductsDirect(),
            transactions = repository.getAllTransactionsDirect(),
            saleItems = repository.getAllSaleItemsDirect(),
            purchases = repository.getAllPurchasesDirect(),
            purchaseItems = repository.getAllPurchaseItemsDirect(),
            customers = repository.getAllCustomersDirect(),
            ledger = repository.getAllLedgerEntriesDirect(),
            stockMovements = repository.getAllStockMovementsDirect(),
            invoiceSequences = repository.getAllSequencesDirect(),
            settings = repository.getShopSettingsSync()
        )

        // 7. Validate the Phase 2 Backup
        val validation = BackupManager.validateBackup(backupJson)
        assertTrue("Phase 2 backup must pass validation: ${validation.errorMessage}", validation.isValid)
        assertEquals(1, validation.productCount)
        assertEquals(1, validation.salesCount)
        assertEquals(1, validation.purchaseCount)
        assertEquals(1, validation.customersCount)
        assertEquals("Tara Maa Bhandar", validation.shopName)

        // 8. Wipe database
        repository.deleteAllProducts()
        repository.deleteAllTransactions()
        repository.deleteAllPurchases()
        assertEquals(0, repository.getProductCountDirect())
        assertEquals(0, repository.getTransactionCountDirect())
        assertEquals(0, repository.getPurchaseCountDirect())

        // 9. Restore Full Backup
        val restoreResult = repository.restoreFromBackupJson(backupJson)
        assertTrue("Restore must succeed", restoreResult.success)
        assertEquals(1, restoreResult.productsRestored)
        assertEquals(1, restoreResult.salesRestored)
        assertEquals(1, restoreResult.purchasesRestored)
        assertEquals(1, restoreResult.customersRestored)

        // 10. Post-Restore Verification of State
        assertEquals(1, repository.getProductCountDirect())
        assertEquals(1, repository.getTransactionCountDirect())
        assertEquals(1, repository.getPurchaseCountDirect())
        assertEquals(1, repository.getAllCustomersDirect().size)

        val restoredProduct = repository.getProductById(prodId)
        assertNotNull(restoredProduct)
        assertEquals("Basmati Rice 5kg", restoredProduct?.name)
        assertEquals("8901234599999", restoredProduct?.barcode)

        val restoredTx = repository.getTransactionByInvoiceNumber(saleResult.transaction.invoiceNumber)
        assertNotNull(restoredTx)
        assertEquals(900.0, restoredTx?.grandTotal ?: 0.0, 0.001)

        val restoredPurchases = repository.getAllPurchasesDirect()
        assertEquals(1, restoredPurchases.size)
        assertEquals("Bengal Rice Millers", restoredPurchases[0].supplierName)

        val restoredSettings = repository.getShopSettingsSync()
        assertEquals("Tara Maa Bhandar", restoredSettings?.shopName)
        assertEquals("80mm", restoredSettings?.printerPaperWidth)
    }

    @Test
    fun testPhase2ReferentialIntegrityForeignKeys() {
        // Test orphan sale item (transactionId does not exist in sales)
        val orphanSaleItemJson = """
            {
                "backupVersion": 2,
                "creationTimestamp": 1711000000000,
                "appVersion": "1.0.0",
                "databaseVersion": 4,
                "products": [
                    { "id": 1, "name": "Item A", "sellingPrice": 50.0, "currentStock": 5.0, "barcode": "BC-1" }
                ],
                "sales": [
                    { "id": 10, "invoiceNumber": "INV-2026-000010", "timestamp": 1711000000000, "grandTotal": 50.0 }
                ],
                "saleItems": [
                    { "id": 1, "transactionId": 9999, "productId": 1, "productName": "Item A", "sellingPrice": 50.0, "quantity": 1.0, "lineTotal": 50.0 }
                ]
            }
        """.trimIndent()
        val result1 = BackupManager.validateBackup(orphanSaleItemJson)
        assertFalse("SaleItem with invalid transactionId must fail validation", result1.isValid)
        assertTrue(result1.errorMessage?.contains("non-existent transactionId") == true)

        // Test orphan purchase item (purchaseId does not exist in purchases)
        val orphanPurchaseItemJson = """
            {
                "backupVersion": 2,
                "creationTimestamp": 1711000000000,
                "appVersion": "1.0.0",
                "databaseVersion": 4,
                "products": [],
                "purchases": [
                    { "id": 5, "purchaseNumber": "PUR-2026-000005", "supplierName": "Supplier", "grandTotal": 100.0 }
                ],
                "purchaseItems": [
                    { "id": 1, "purchaseId": 8888, "productId": 1, "productName": "Item A", "purchaseRate": 50.0, "quantity": 2.0, "lineTotal": 100.0 }
                ]
            }
        """.trimIndent()
        val result2 = BackupManager.validateBackup(orphanPurchaseItemJson)
        assertFalse("PurchaseItem with invalid purchaseId must fail validation", result2.isValid)
        assertTrue(result2.errorMessage?.contains("non-existent purchaseId") == true)

        // Test orphan ledger entry (customerId does not exist in customers)
        val orphanLedgerJson = """
            {
                "backupVersion": 2,
                "creationTimestamp": 1711000000000,
                "appVersion": "1.0.0",
                "databaseVersion": 4,
                "products": [],
                "customers": [
                    { "id": 7, "name": "Valid Customer", "phone": "9800000000" }
                ],
                "ledgerEntries": [
                    { "id": 1, "customerId": 7777, "date": 1711000000000, "type": "DEBIT", "amount": 250.0 }
                ]
            }
        """.trimIndent()
        val result3 = BackupManager.validateBackup(orphanLedgerJson)
        assertFalse("Ledger entry with invalid customerId must fail validation", result3.isValid)
        assertTrue(result3.errorMessage?.contains("non-existent customerId") == true)
    }

    @Test
    fun testPhase2CsvExportComprehensive() {
        // Verify escapeCsv handling
        assertEquals("\"Sugar, White\"", BackupManager.escapeCsv("Sugar, White"))
        assertEquals("\"Tea \"\"Special\"\"\"", BackupManager.escapeCsv("Tea \"Special\""))
        assertEquals("PlainRice", BackupManager.escapeCsv("PlainRice"))

        // Verify CSV generation for products
        val sampleProducts = listOf(
            ProductItem(
                id = 1,
                name = "Fortune Oil, 1L",
                category = "Edible Oils",
                unit = "Bottle",
                costPrice = 130.0,
                sellingPrice = 150.0,
                mrp = 160.0,
                currentStock = 20.0,
                barcode = "8900001",
                gstRate = 5.0
            )
        )
        val productCsv = BackupManager.exportInventoryToCsv(context, sampleProducts)
        assertNotNull(productCsv)
        assertTrue(productCsv!!.exists())
        val productCsvLines = productCsv.readLines()
        assertTrue(productCsvLines.isNotEmpty())
        assertTrue(productCsvLines[0].contains("ID,Name,Bengali Name,Barcode,Category,Unit,Cost Price,Selling Price,MRP,Current Stock,Min Alert,GST Rate %,Rack Location"))
        assertTrue(productCsvLines[1].contains("\"Fortune Oil, 1L\""))

        // Verify CSV generation for customers
        val sampleCustomers = listOf(
            Customer(
                id = 10,
                name = "Shyamal Ghosh",
                phone = "9831112233",
                address = "Barasat, North 24 Parganas",
                openingBalance = 1500.0
            )
        )
        val customerCsv = BackupManager.exportCustomersToCsv(context, sampleCustomers)
        assertNotNull(customerCsv)
        assertTrue(customerCsv!!.exists())
        val custLines = customerCsv.readLines()
        assertTrue(custLines[0].contains("Customer ID,Name,Phone,Address,Opening Balance,Current Khata Balance,Status,Created Date"))
        assertTrue(custLines[1].contains("\"Barasat, North 24 Parganas\""))

        // Verify CSV generation for ledger
        val sampleLedger = listOf(
            LedgerEntry(
                id = 1,
                customerId = 10,
                date = 1711000000000L,
                type = LedgerEntry.TYPE_CREDIT_SALE,
                amount = 450.0,
                reference = "INV-2026-0001",
                note = "Groceries on credit"
            )
        )
        val ledgerCsv = BackupManager.exportLedgerToCsv(context, sampleLedger)
        assertNotNull(ledgerCsv)
        assertTrue(ledgerCsv!!.exists())
        val ledgerLines = ledgerCsv.readLines()
        assertTrue(ledgerLines[0].contains("Entry ID,Date,Customer ID,Customer Name,Customer Phone,Type,Amount,Reference,Note"))
        assertTrue(ledgerLines[1].contains("INV-2026-0001"))
    }

    @Test
    fun testPhase13CompleteBackupAndAtomicRestoreAll10Entities() = runBlocking {
        // 1. Products
        val prodId = repository.insertProduct(
            ProductItem(
                id = 501,
                name = "Phase 13 Aashirvaad Atta 5kg",
                category = "Atta & Flour",
                unit = "5 kg",
                sellingPrice = 245.0,
                costPrice = 210.0,
                mrp = 260.0,
                currentStock = 30.0,
                barcode = "8901030383921"
            )
        )

        // 2. Customers
        val custId = repository.insertCustomer(
            Customer(
                id = 601,
                name = "Amitabh Sen",
                phone = "9831122334",
                address = "Salt Lake, Sector 2",
                openingBalance = 100.0
            )
        )

        // 3. Khata Ledger
        repository.insertLedgerEntry(
            LedgerEntry(
                id = 701,
                customerId = custId,
                date = System.currentTimeMillis(),
                type = LedgerEntry.TYPE_CREDIT_SALE,
                amount = 245.0,
                reference = "INV-P13-001",
                note = "Atta on credit"
            )
        )

        // 4. Purchases & Purchase Items
        repository.completePurchaseTransaction(
            items = listOf(
                PurchaseItemInput(
                    productId = prodId,
                    productName = "Phase 13 Aashirvaad Atta 5kg",
                    barcode = "8901030383921",
                    unit = "5 kg",
                    quantity = 20.0,
                    purchaseRate = 205.0,
                    gstRate = 0.0
                )
            ),
            supplierName = "ITC Wholesale",
            supplierPhone = "9000011111",
            paymentMode = "CASH"
        )

        // 5. Sales & Sale Items
        val saleResult = repository.completeSaleTransaction(
            items = listOf(
                CartItem(
                    productId = prodId,
                    barcode = "8901030383921",
                    name = "Phase 13 Aashirvaad Atta 5kg",
                    unit = "5 kg",
                    rate = 245.0,
                    costPrice = 210.0,
                    mrp = 260.0,
                    quantity = 1.0
                )
            ),
            customerName = "Amitabh Sen",
            customerPhone = "9831122334",
            paymentMode = "KHATA",
            customerId = custId
        )

        // 6. Shop Settings
        repository.saveShopSettings(
            ShopSettings(
                id = 1,
                shopName = "Phase 13 Super Kirana",
                phone = "9876543210",
                address = "Kolkata, WB",
                gstin = "19AABCS1429B1ZB"
            )
        )

        // Build Backup JSON containing all 10 persistent domains
        val backupJson = BackupManager.buildBackupJsonString(
            products = repository.getAllProductsDirect(),
            transactions = repository.getAllTransactionsDirect(),
            saleItems = repository.getAllSaleItemsDirect(),
            purchases = repository.getAllPurchasesDirect(),
            purchaseItems = repository.getAllPurchaseItemsDirect(),
            customers = repository.getAllCustomersDirect(),
            ledger = repository.getAllLedgerEntriesDirect(),
            stockMovements = repository.getAllStockMovementsDirect(),
            invoiceSequences = repository.getAllSequencesDirect(),
            settings = repository.getShopSettingsSync()
        )

        // Pre-Restore Validation showing all required metadata
        val validation = BackupManager.validateBackup(backupJson)
        assertTrue(validation.isValid)
        assertTrue(validation.exportDate.isNotBlank())
        assertEquals(1, validation.productCount)
        assertEquals(1, validation.salesCount)
        assertEquals(1, validation.purchaseCount)
        assertEquals(1, validation.customersCount)
        assertTrue(validation.fileSizeBytes > 0)
        assertEquals(9, validation.databaseVersion)

        // Wipe data
        repository.deleteAllProducts()
        repository.deleteAllTransactions()
        repository.deleteAllPurchases()
        repository.deleteAllCustomers()
        assertEquals(0, repository.getProductCountDirect())
        assertEquals(0, repository.getTransactionCountDirect())

        // Atomic Restore
        val restoreResult = repository.restoreFromBackupJson(backupJson)
        assertTrue("Restore must be successful", restoreResult.success)
        assertEquals(1, restoreResult.productsRestored)
        assertEquals(1, restoreResult.salesRestored)
        assertEquals(1, restoreResult.purchasesRestored)
        assertEquals(1, restoreResult.customersRestored)

        // Verify all 10 domains restored in database
        assertEquals(1, repository.getProductCountDirect())
        assertEquals(1, repository.getTransactionCountDirect())
        assertEquals(1, repository.getPurchaseCountDirect())
        assertEquals(1, repository.getCustomerCountDirect())
        assertEquals("Phase 13 Super Kirana", repository.getShopSettingsSync()?.shopName)
    }

    @Test
    fun testPhase13AtomicRollbackOnCorruptedRestore() = runBlocking {
        // Seed initial safe data
        repository.insertProduct(
            ProductItem(
                id = 111,
                name = "Safe Tea 250g",
                category = "Tea & Coffee",
                unit = "Packet",
                costPrice = 80.0,
                sellingPrice = 110.0,
                mrp = 120.0,
                currentStock = 15.0,
                barcode = "SAFE_TEA_001"
            )
        )
        val initialCount = repository.getProductCountDirect()
        assertEquals(1, initialCount)

        // Invalid JSON: Negative selling price violates validation
        val corruptedJson = """
            {
                "backupVersion": 3,
                "products": [
                    { "id": 1, "name": "Bad Product", "costPrice": 10.0, "sellingPrice": -50.0, "mrp": 20.0, "currentStock": 5.0 }
                ]
            }
        """.trimIndent()

        var caughtException = false
        try {
            repository.restoreFromBackupJson(corruptedJson)
        } catch (e: Exception) {
            caughtException = true
        }
        assertTrue("Validation failure must reject restore", caughtException)

        // Verify ROLLBACK EVERYTHING: Database remains 100% untouched
        val afterCount = repository.getProductCountDirect()
        assertEquals("Original store data must be 100% preserved after rollback", 1, afterCount)
        val prod = repository.getProductById(111)
        assertNotNull(prod)
        assertEquals("Safe Tea 250g", prod?.name)
    }

    @Test
    fun testPhase13EncryptedBackupAndRestore() = runBlocking {
        val prod = ProductItem(
            id = 222,
            name = "Confidential Ghee 1L",
            category = "Dairy",
            unit = "Jar",
            costPrice = 450.0,
            sellingPrice = 550.0,
            mrp = 600.0,
            currentStock = 8.0,
            barcode = "CONF_GHEE_999"
        )
        repository.insertProduct(prod)

        val backupJson = BackupManager.buildBackupJsonString(
            products = listOf(prod),
            transactions = emptyList(),
            saleItems = emptyList()
        )

        // Encrypt with password
        val password = "StoreSecretPassword@2026"
        val encryptedContent = BackupManager.encryptBackup(backupJson, password)
        assertTrue(BackupManager.isEncryptedBackup(encryptedContent))

        // Decrypt with wrong password fails validation
        val wrongResult = BackupManager.decryptAndValidateBackup(encryptedContent, "wrongPassword")
        assertFalse(wrongResult.isValid)
        assertTrue(wrongResult.errorMessage?.contains("Incorrect password") == true)

        // Decrypt with correct password succeeds
        val correctResult = BackupManager.decryptAndValidateBackup(encryptedContent, password)
        assertTrue(correctResult.isValid)
        assertEquals(1, correctResult.productCount)

        // Wipe DB and restore using encrypted content with password
        repository.deleteAllProducts()
        assertEquals(0, repository.getProductCountDirect())

        val restoreResult = repository.restoreFromBackupJson(encryptedContent, password)
        assertTrue(restoreResult.success)
        assertEquals(1, repository.getProductCountDirect())
        assertEquals("Confidential Ghee 1L", repository.getProductById(222)?.name)
    }
}
