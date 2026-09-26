package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.backup.BackupManager
import com.example.data.AppDatabase
import com.example.data.KiranaRepository
import com.example.data.model.CartItem
import com.example.data.model.Customer
import com.example.data.model.ProductItem
import com.example.data.model.ShopSettings
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BackupExportTest {

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
    fun testBackupExportComprehensiveDataIntegrity() = runBlocking {
        // 1. Seed shop settings without manual omission
        val settings = ShopSettings(
            id = 1,
            shopName = "Bawali Kirana Bhandar",
            tagline = "Pure Desi Ration & Spices",
            ownerName = "Sourav Das",
            phone = "9876543210",
            address = "Station Road, Budge Budge",
            city = "Kolkata",
            gstin = "19AAACP1234M1Z5",
            upiId = "bawali@upi",
            printerPaperWidth = "58mm",
            receiptFooterNote = "ধন্যবাদ! আবার আসবেন",
            termsNote = "Goods returnable in 2 days",
            lowStockThresholdDefault = 8.0
        )
        repository.saveShopSettings(settings)

        // 2. Seed products with exact known IDs
        val p1Id = repository.insertProduct(
            ProductItem(
                id = 101,
                barcode = "8901234567890",
                name = "Aashirvaad Shudh Chakki Atta 5kg",
                bengaliName = "আশীর্বাদ আটা ৫ কেজি",
                category = "Flour & Grains",
                unit = "Packet",
                costPrice = 210.0,
                sellingPrice = 245.0,
                mrp = 260.0,
                currentStock = 25.0,
                minStockAlert = 5.0,
                gstRate = 5.0,
                rackLocation = "Rack A-1"
            )
        )
        val p2Id = repository.insertProduct(
            ProductItem(
                id = 102,
                barcode = "8902345678901",
                name = "Fortune Kachi Ghani Mustard Oil 1L",
                bengaliName = "ফরচুন খাঁটি সরিষার তেল ১ লিটার",
                category = "Edible Oils",
                unit = "Bottle",
                costPrice = 135.0,
                sellingPrice = 155.0,
                mrp = 165.0,
                currentStock = 40.0,
                minStockAlert = 10.0,
                gstRate = 5.0,
                rackLocation = "Shelf Oil-2"
            )
        )

        // 3. Complete an atomic sale transaction
        val prod1 = repository.getProductById(p1Id)!!
        val prod2 = repository.getProductById(p2Id)!!
        val cartItems = listOf(
            CartItem(
                productId = prod1.id,
                barcode = prod1.barcode,
                name = prod1.name,
                unit = prod1.unit,
                rate = prod1.sellingPrice,
                costPrice = prod1.costPrice,
                mrp = prod1.mrp,
                gstRate = prod1.gstRate,
                quantity = 2.0
            ),
            CartItem(
                productId = prod2.id,
                barcode = prod2.barcode,
                name = prod2.name,
                unit = prod2.unit,
                rate = prod2.sellingPrice,
                costPrice = prod2.costPrice,
                mrp = prod2.mrp,
                gstRate = prod2.gstRate,
                quantity = 3.0
            )
        )
        val custId = repository.insertCustomer(
            Customer(
                name = "Animesh Roy",
                phone = "9830012345",
                address = "Kolkata",
                openingBalance = 0.0
            )
        )
        val saleResult = repository.completeSaleTransaction(
            items = cartItems,
            customerName = "Animesh Roy",
            customerPhone = "9830012345",
            paymentMode = "KHATA",
            customerId = custId
        )
        val invoiceNumber = saleResult.transaction.invoiceNumber

        assertTrue(invoiceNumber.startsWith("INV-"))

        // Initial snapshot counts before export
        val initialProductCount = repository.getProductCountDirect()
        val initialTxCount = repository.getTransactionCountDirect()
        val initialSaleItems = repository.getAllSaleItemsDirect()
        val initialSequences = repository.getAllSequencesDirect()
        val initialCustomers = repository.getAllCustomersDirect()
        val initialLedger = repository.getAllLedgerEntriesDirect()

        assertEquals(2, initialProductCount)
        assertEquals(1, initialTxCount)
        assertEquals(2, initialSaleItems.size)
        assertTrue(initialSequences.isNotEmpty())
        assertEquals(1, initialCustomers.size)
        assertEquals(1, initialLedger.size)

        // 4. Perform Backup Export
        val products = repository.getAllProductsDirect()
        val transactions = repository.getAllTransactionsDirect()
        val saleItems = repository.getAllSaleItemsDirect()
        val sequences = repository.getAllSequencesDirect()
        val fetchedSettings = repository.getShopSettingsSync()
        val customers = repository.getAllCustomersDirect()
        val ledger = repository.getAllLedgerEntriesDirect()

        val jsonString = BackupManager.buildBackupJsonString(
            products = products,
            transactions = transactions,
            saleItems = saleItems,
            settings = fetchedSettings,
            invoiceSequences = sequences,
            customers = customers,
            ledger = ledger
        )

        // 5. Verify Backup is 100% READ-ONLY with respect to Database
        assertEquals("Product count unchanged", initialProductCount, repository.getProductCountDirect())
        assertEquals("Transaction count unchanged", initialTxCount, repository.getTransactionCountDirect())
        assertEquals("Sale items unchanged", initialSaleItems.size, repository.getAllSaleItemsDirect().size)
        assertEquals("Sequences unchanged", initialSequences.size, repository.getAllSequencesDirect().size)
        assertEquals("Customers unchanged", initialCustomers.size, repository.getAllCustomersDirect().size)
        assertEquals("Ledger unchanged", initialLedger.size, repository.getAllLedgerEntriesDirect().size)

        // 6. Inspect JSON content
        val root = JSONObject(jsonString)

        // Verify Version and Timestamp
        assertTrue(root.has("backupVersion"))
        assertEquals(BackupManager.BACKUP_VERSION, root.getInt("backupVersion"))
        assertTrue(root.has("creationTimestamp"))
        assertTrue(root.getLong("creationTimestamp") > 0)
        assertTrue(root.has("exportDate"))

        // Verify Metadata Record Counts
        val metadata = root.getJSONObject("metadata")
        assertEquals(2, metadata.getInt("productsCount"))
        assertEquals(1, metadata.getInt("salesCount"))
        assertEquals(2, metadata.getInt("saleItemsCount"))

        // Verify Shop Settings without omission
        val settingsJson = root.getJSONObject("shopSettings")
        assertEquals("Bawali Kirana Bhandar", settingsJson.getString("shopName"))
        assertEquals("Pure Desi Ration & Spices", settingsJson.getString("tagline"))
        assertEquals("Sourav Das", settingsJson.getString("ownerName"))
        assertEquals("9876543210", settingsJson.getString("phone"))
        assertEquals("Station Road, Budge Budge", settingsJson.getString("address"))
        assertEquals("Kolkata", settingsJson.getString("city"))
        assertEquals("19AAACP1234M1Z5", settingsJson.getString("gstin"))
        assertEquals("bawali@upi", settingsJson.getString("upiId"))
        assertEquals("58mm", settingsJson.getString("printerPaperWidth"))
        assertEquals("ধন্যবাদ! আবার আসবেন", settingsJson.getString("receiptFooterNote"))
        assertEquals("Goods returnable in 2 days", settingsJson.getString("termsNote"))
        assertEquals(8.0, settingsJson.getDouble("lowStockThresholdDefault"), 0.001)

        // Verify Products (Preserves exact database IDs, barcodes, names)
        val productsArray = root.getJSONArray("products")
        assertEquals(2, productsArray.length())
        val p1Json = productsArray.getJSONObject(0)
        assertEquals(p1Id, p1Json.getLong("id"))
        assertEquals("8901234567890", p1Json.getString("barcode"))
        assertEquals("Aashirvaad Shudh Chakki Atta 5kg", p1Json.getString("name"))
        assertEquals("আশীর্বাদ আটা ৫ কেজি", p1Json.getString("bengaliName"))
        assertEquals(245.0, p1Json.getDouble("sellingPrice"), 0.001)

        val p2Json = productsArray.getJSONObject(1)
        assertEquals(p2Id, p2Json.getLong("id"))
        assertEquals("8902345678901", p2Json.getString("barcode"))

        // Verify Sales / Transactions
        val salesArray = root.getJSONArray("sales")
        assertEquals(1, salesArray.length())
        val saleObj = salesArray.getJSONObject(0)
        val txId = saleObj.getLong("id")
        assertEquals(invoiceNumber, saleObj.getString("invoiceNumber"))
        assertEquals("Animesh Roy", saleObj.getString("customerName"))
        assertEquals("9830012345", saleObj.getString("customerPhone"))
        assertEquals("KHATA", saleObj.getString("paymentMode"))
        assertEquals(955.0, saleObj.getDouble("grandTotal"), 0.001)

        // Verify Sale Items & Relationships
        val saleItemsArray = root.getJSONArray("saleItems")
        assertEquals(2, saleItemsArray.length())
        for (i in 0 until saleItemsArray.length()) {
            val itemObj = saleItemsArray.getJSONObject(i)
            assertEquals("Foreign key transactionId matches sale ID", txId, itemObj.getLong("transactionId"))
            assertTrue(itemObj.getLong("productId") == p1Id || itemObj.getLong("productId") == p2Id)
        }

        // Verify Customers
        val customersArray = root.getJSONArray("customers")
        assertEquals(1, customersArray.length())
        val custObj = customersArray.getJSONObject(0)
        assertEquals("Animesh Roy", custObj.getString("name"))
        assertEquals("9830012345", custObj.getString("phone"))

        // Verify Ledger
        val ledgerArray = root.getJSONArray("ledger")
        assertEquals(1, ledgerArray.length())
        val ledgerObj = ledgerArray.getJSONObject(0)
        assertEquals(invoiceNumber, ledgerObj.getString("reference"))
        assertEquals(955.0, ledgerObj.getDouble("amount"), 0.001)

        // Verify Invoice Sequence
        val seqArray = root.getJSONArray("invoiceSequence")
        assertTrue(seqArray.length() >= 1)
        val seqObj = seqArray.getJSONObject(0)
        assertTrue(seqObj.has("prefix"))
        assertEquals(1L, seqObj.getLong("lastSequenceNumber"))
    }

    @Test
    fun testExportBackupToFile() = runBlocking {
        val settings = ShopSettings(shopName = "Quick Test Shop")
        val file: File? = BackupManager.exportBackupToJson(
            context = context,
            products = emptyList(),
            transactions = emptyList(),
            settings = settings,
            saleItems = emptyList(),
            invoiceSequences = emptyList()
        )

        assertNotNull(file)
        assertTrue(file!!.exists())
        assertTrue(file.length() > 0)
        val text = file.readText()
        val json = JSONObject(text)
        assertEquals(BackupManager.BACKUP_VERSION, json.getInt("backupVersion"))
        assertEquals("Quick Test Shop", json.getJSONObject("shopSettings").getString("shopName"))
    }

    @Test
    fun testEmptyTablesStayEmptyAndAreNeverReconstructed() {
        // Phase 2 mandate: If a table is empty, backup that section as empty.
        // Never reconstruct customers or ledger from sales.
        val fakeSale = com.example.data.model.SaleTransaction(
            id = 1,
            invoiceNumber = "INV-2026-000001",
            timestamp = 1711000000000L,
            customerName = "Walk-in Buyer",
            customerPhone = "9999999999",
            paymentMode = "CASH",
            subtotal = 100.0,
            discount = 0.0,
            gstAmount = 0.0,
            grandTotal = 100.0,
            itemsJson = "[]"
        )

        val jsonString = BackupManager.buildBackupJsonString(
            products = emptyList(),
            transactions = listOf(fakeSale),
            saleItems = emptyList(),
            purchases = emptyList(),
            purchaseItems = emptyList(),
            customers = emptyList(),
            ledger = emptyList(),
            stockMovements = emptyList(),
            invoiceSequences = emptyList()
        )

        val root = JSONObject(jsonString)
        val customersArray = root.getJSONArray("customers")
        val ledgerArray = root.getJSONArray("ledger")
        val purchasesArray = root.getJSONArray("purchases")
        val movementsArray = root.getJSONArray("stockMovements")

        assertEquals("Customers must remain empty when customer table is empty", 0, customersArray.length())
        assertEquals("Ledger must remain empty when ledger table is empty", 0, ledgerArray.length())
        assertEquals("Purchases must remain empty when purchases table is empty", 0, purchasesArray.length())
        assertEquals("Stock movements must remain empty when movement table is empty", 0, movementsArray.length())
    }
}
