package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.MIGRATION_1_2
import com.example.data.MIGRATION_2_3
import com.example.data.MIGRATION_3_4
import com.example.data.MIGRATION_4_5
import com.example.data.MIGRATION_5_6
import com.example.data.MIGRATION_6_7
import com.example.data.MIGRATION_7_8
import com.example.data.MIGRATION_8_9
import com.example.data.model.Customer
import com.example.data.model.LedgerEntry
import com.example.data.model.ShopSettings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class RoomMigrationTest {

    private lateinit var context: Context
    private val testDbName = "test_migration_kirana.db"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(testDbName)
    }

    @Test
    fun testMigration1To2PreservesProductsAndAddsTransactions() {
        // Step 1: Create a v1 database directly using SQLite
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val helperConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // v1 products table
                    db.execSQL(
                        """
                        CREATE TABLE `products` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `barcode` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `category` TEXT NOT NULL,
                            `unit` TEXT NOT NULL,
                            `costPrice` REAL NOT NULL,
                            `sellingPrice` REAL NOT NULL,
                            `mrp` REAL NOT NULL,
                            `currentStock` REAL NOT NULL,
                            `minStockAlert` REAL NOT NULL,
                            `gstRate` REAL NOT NULL,
                            `lastUpdated` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val openHelper = helperFactory.create(helperConfig)
        val v1Db = openHelper.writableDatabase

        // Insert inventory in v1
        v1Db.execSQL(
            """
            INSERT INTO `products` (
                `barcode`, `name`, `category`, `unit`, `costPrice`,
                `sellingPrice`, `mrp`, `currentStock`, `minStockAlert`, `gstRate`, `lastUpdated`
            ) VALUES (
                '890103000001', 'Aashirvaad Shudh Chakki Atta 5kg', 'Atta & Flours', 'Packet',
                195.0, 220.0, 235.0, 18.0, 5.0, 0.0, 1700000000000
            )
            """.trimIndent()
        )
        v1Db.close()

        // Step 2: Run MIGRATION_1_2 on the database
        val v2HelperConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                    if (oldVersion == 1 && newVersion == 2) {
                        MIGRATION_1_2.migrate(db)
                    }
                }
            })
            .build()

        val v2Helper = helperFactory.create(v2HelperConfig)
        val v2Db = v2Helper.writableDatabase

        // Step 3: Verify existing products remain
        val cursor = v2Db.query("SELECT id, barcode, name, currentStock, sellingPrice, bengaliName, rackLocation FROM products WHERE barcode = '890103000001'")
        assertTrue("Product must remain after migration 1->2", cursor.moveToFirst())
        assertEquals("890103000001", cursor.getString(cursor.getColumnIndexOrThrow("barcode")))
        assertEquals("Aashirvaad Shudh Chakki Atta 5kg", cursor.getString(cursor.getColumnIndexOrThrow("name")))
        assertEquals(18.0, cursor.getDouble(cursor.getColumnIndexOrThrow("currentStock")), 0.001)
        assertEquals(220.0, cursor.getDouble(cursor.getColumnIndexOrThrow("sellingPrice")), 0.001)
        // Newly added columns have safe defaults
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("bengaliName")))
        assertEquals("", cursor.getString(cursor.getColumnIndexOrThrow("rackLocation")))
        cursor.close()

        // Verify transactions table was created
        val txCursor = v2Db.query("SELECT count(*) FROM transactions")
        assertTrue(txCursor.moveToFirst())
        assertEquals(0, txCursor.getInt(0))
        txCursor.close()

        v2Db.close()
    }

    @Test
    fun testMigration2To3PreservesProductsAndSalesAndAddsSettings() = runBlocking {
        // Step 1: Create v2 database with products and transactions
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val helperConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `products` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `barcode` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `bengaliName` TEXT NOT NULL,
                            `category` TEXT NOT NULL,
                            `unit` TEXT NOT NULL,
                            `costPrice` REAL NOT NULL,
                            `sellingPrice` REAL NOT NULL,
                            `mrp` REAL NOT NULL,
                            `currentStock` REAL NOT NULL,
                            `minStockAlert` REAL NOT NULL,
                            `gstRate` REAL NOT NULL,
                            `rackLocation` TEXT NOT NULL,
                            `lastUpdated` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`)")
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `transactions` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `invoiceNumber` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `customerName` TEXT NOT NULL,
                            `customerPhone` TEXT NOT NULL,
                            `paymentMode` TEXT NOT NULL,
                            `subtotal` REAL NOT NULL,
                            `discount` REAL NOT NULL,
                            `gstAmount` REAL NOT NULL,
                            `grandTotal` REAL NOT NULL,
                            `itemsJson` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val openHelper = helperFactory.create(helperConfig)
        val v2Db = openHelper.writableDatabase

        // Insert an inventory product
        v2Db.execSQL(
            """
            INSERT INTO `products` (
                `barcode`, `name`, `bengaliName`, `category`, `unit`, `costPrice`,
                `sellingPrice`, `mrp`, `currentStock`, `minStockAlert`, `gstRate`, `rackLocation`, `lastUpdated`
            ) VALUES (
                '890103000002', 'Fortune Sunlite Sunflower Oil 1L', 'ফরচুন সূর্যমুখী তেল ১লি', 'Edible Oils & Ghee', 'Pouch',
                115.0, 135.0, 150.0, 32.0, 6.0, 5.0, 'Rack A-2', 1700000000000
            )
            """.trimIndent()
        )

        // Insert a completed sale transaction
        v2Db.execSQL(
            """
            INSERT INTO `transactions` (
                `invoiceNumber`, `timestamp`, `customerName`, `customerPhone`, `paymentMode`,
                `subtotal`, `discount`, `gstAmount`, `grandTotal`, `itemsJson`
            ) VALUES (
                'INV-2026-0001', 1700000000000, 'Ramesh Babu', '9876543210', 'UPI',
                270.0, 10.0, 13.5, 273.5, '[{"productId":1,"name":"Fortune Oil","rate":135.0,"quantity":2}]'
            )
            """.trimIndent()
        )
        v2Db.close()

        // Step 2: Open with Room v9 and apply all migrations
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .build()

        // Step 3: Verify existing products remain via ProductDao
        val products = roomDb.productDao().getAllProducts().first()
        assertEquals(1, products.size)
        val product = products[0]
        assertEquals("890103000002", product.barcode)
        assertEquals("Fortune Sunlite Sunflower Oil 1L", product.name)
        assertEquals("ফরচুন সূর্যমুখী তেল ১লি", product.bengaliName)
        assertEquals(32.0, product.currentStock, 0.001)
        assertEquals(135.0, product.sellingPrice, 0.001)
        assertTrue("Product should be active by default after migration", product.isActive)

        // Step 4: Verify existing sales remain via TransactionDao
        val sales = roomDb.transactionDao().getAllTransactions().first()
        assertEquals(1, sales.size)
        val sale = sales[0]
        assertEquals("INV-2026-0001", sale.invoiceNumber)
        assertEquals("Ramesh Babu", sale.customerName)
        assertEquals(273.5, sale.grandTotal, 0.001)

        // Step 5: Verify existing settings exist / initialized via ShopSettingsDao
        val settings = roomDb.shopSettingsDao().getSettingsSync()
        assertNotNull("Settings should be initialized", settings)
        assertEquals("Shree Ganesh Kirana & General Store", settings?.shopName)
        assertEquals("58mm", settings?.printerPaperWidth)

        // Verify settings updates work and persist
        val updatedSettings = settings!!.copy(shopName = "Maa Tara Kirana Store", upiId = "maatarakirana@upi")
        roomDb.shopSettingsDao().insertOrUpdate(updatedSettings)
        val reloadedSettings = roomDb.shopSettingsDao().getSettingsSync()
        assertEquals("Maa Tara Kirana Store", reloadedSettings?.shopName)
        assertEquals("maatarakirana@upi", reloadedSettings?.upiId)

        // Step 6: Verify Room v5 tables: sale_items and invoice_sequence
        val nextSeq = (roomDb.invoiceSequenceDao().getLastSequence("2026") ?: 0L) + 1L
        roomDb.invoiceSequenceDao().saveSequence(com.example.data.model.InvoiceSequence("2026", nextSeq))
        assertEquals(1L, roomDb.invoiceSequenceDao().getLastSequence("2026"))

        roomDb.close()
    }

    @Test
    fun testFullChainMigrationFromV1ToV3PreservesAllData() = runBlocking {
        // Step 1: Create v1 database
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val helperConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE `products` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `barcode` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `category` TEXT NOT NULL,
                            `unit` TEXT NOT NULL,
                            `costPrice` REAL NOT NULL,
                            `sellingPrice` REAL NOT NULL,
                            `mrp` REAL NOT NULL,
                            `currentStock` REAL NOT NULL,
                            `minStockAlert` REAL NOT NULL,
                            `gstRate` REAL NOT NULL,
                            `lastUpdated` INTEGER NOT NULL
                        )
                        """.trimIndent()
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val v1Db = helperFactory.create(helperConfig).writableDatabase
        v1Db.execSQL(
            """
            INSERT INTO `products` (
                `barcode`, `name`, `category`, `unit`, `costPrice`,
                `sellingPrice`, `mrp`, `currentStock`, `minStockAlert`, `gstRate`, `lastUpdated`
            ) VALUES (
                '890103000099', 'Tata Salt 1kg Vacuum Evaporated', 'Spices & Condiments', 'Packet',
                22.0, 28.0, 30.0, 45.0, 10.0, 0.0, 1700000000000
            )
            """.trimIndent()
        )
        v1Db.close()

        // Step 2: Open directly with Room at version 9 with chained migrations
        val roomDb = Room.databaseBuilder(context, AppDatabase::class.java, testDbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .build()

        // Step 3: Verify existing products remain
        val products = roomDb.productDao().getAllProducts().first()
        assertEquals(1, products.size)
        assertEquals("890103000099", products[0].barcode)
        assertEquals("Tata Salt 1kg Vacuum Evaporated", products[0].name)
        assertEquals(45.0, products[0].currentStock, 0.001)

        // Step 4: Verify sales can be recorded and read
        val newSale = com.example.data.model.SaleTransaction(
            invoiceNumber = "INV-2026-V1V3",
            timestamp = System.currentTimeMillis(),
            customerName = "Direct Migration Customer",
            customerPhone = "9999999999",
            paymentMode = "Cash",
            subtotal = 28.0,
            discount = 0.0,
            gstAmount = 0.0,
            grandTotal = 28.0,
            itemsJson = "[]"
        )
        val saleId = roomDb.transactionDao().insertTransaction(newSale)
        assertTrue(saleId > 0)
        val sales = roomDb.transactionDao().getAllTransactions().first()
        assertEquals(1, sales.size)
        assertEquals("INV-2026-V1V3", sales[0].invoiceNumber)

        // Step 5: Verify settings remain initialized
        val settings = roomDb.shopSettingsDao().getSettingsSync()
        assertNotNull(settings)
        assertEquals("Shree Ganesh Kirana & General Store", settings?.shopName)

        // Step 6: Verify v6 customer and khata ledger persistence
        val customerId = roomDb.customerDao().insertCustomer(
            Customer(name = "Full Chain Customer", phone = "9123456780", address = "Main Market")
        )
        assertTrue(customerId > 0)
        val ledgerId = roomDb.ledgerDao().insertLedgerEntry(
            LedgerEntry(
                customerId = customerId,
                date = System.currentTimeMillis(),
                type = "CREDIT_SALE",
                amount = 150.0,
                reference = "INV-2026-V1V3",
                note = "Full chain credit"
            )
        )
        assertTrue(ledgerId > 0)
        val balance = roomDb.ledgerDao().getCustomerBalanceDirect(customerId)
        assertEquals(150.0, balance, 0.001)

        roomDb.close()
    }

    @Test
    fun testMigration5To6AddsCustomersAndLedgerTables() {
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val helperConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(5) {
                override fun onCreate(db: SupportSQLiteDatabase) {}
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val db = helperFactory.create(helperConfig).writableDatabase
        MIGRATION_5_6.migrate(db)

        // Verify customers table exists and allows insertion
        db.execSQL("INSERT INTO `customers` (`name`, `phone`, `address`) VALUES ('Ramesh', '9876543210', 'Market')")
        val cCursor = db.query("SELECT id, name, phone, address FROM customers WHERE phone = '9876543210'")
        assertTrue(cCursor.moveToFirst())
        val custId = cCursor.getLong(0)
        assertEquals("Ramesh", cCursor.getString(1))
        cCursor.close()

        // Verify ledger_entries table exists and allows insertion
        db.execSQL("INSERT INTO `ledger_entries` (`customerId`, `date`, `type`, `amount`, `reference`, `note`) VALUES ($custId, 1700000000, 'CREDIT_SALE', 150.0, 'INV-001', 'Test credit')")
        val lCursor = db.query("SELECT id, customerId, amount, type FROM ledger_entries WHERE customerId = $custId")
        assertTrue(lCursor.moveToFirst())
        assertEquals(150.0, lCursor.getDouble(2), 0.001)
        assertEquals("CREDIT_SALE", lCursor.getString(3))
        lCursor.close()

        db.close()
    }

    @Test
    fun testMigration6To7AddsPurchasesAndStockMovements() {
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val helperConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(6) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // v6 minimal schema
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `products` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `barcode` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `bengaliName` TEXT NOT NULL,
                            `category` TEXT NOT NULL,
                            `unit` TEXT NOT NULL,
                            `costPrice` REAL NOT NULL,
                            `sellingPrice` REAL NOT NULL,
                            `mrp` REAL NOT NULL,
                            `currentStock` REAL NOT NULL,
                            `minStockAlert` REAL NOT NULL,
                            `gstRate` REAL NOT NULL,
                            `rackLocation` TEXT NOT NULL,
                            `lastUpdated` INTEGER NOT NULL,
                            `isActive` INTEGER NOT NULL DEFAULT 1
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `transactions` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `invoiceNumber` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `customerName` TEXT NOT NULL,
                            `customerPhone` TEXT NOT NULL,
                            `paymentMode` TEXT NOT NULL,
                            `subtotal` REAL NOT NULL,
                            `discount` REAL NOT NULL,
                            `gstAmount` REAL NOT NULL,
                            `grandTotal` REAL NOT NULL,
                            `itemsJson` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `customers` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `name` TEXT NOT NULL,
                            `phone` TEXT NOT NULL,
                            `address` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `ledger_entries` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `customerId` INTEGER NOT NULL,
                            `date` INTEGER NOT NULL,
                            `type` TEXT NOT NULL,
                            `amount` REAL NOT NULL,
                            `reference` TEXT NOT NULL,
                            `note` TEXT NOT NULL
                        )
                        """.trimIndent()
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val db = helperFactory.create(helperConfig).writableDatabase
        MIGRATION_6_7.migrate(db)

        // Verify purchases table exists
        db.execSQL("INSERT INTO `purchases` (`purchaseNumber`, `supplierName`, `supplierPhone`, `purchaseDate`, `paymentMode`, `subtotal`, `discount`, `tax`, `grandTotal`, `note`, `timestamp`) VALUES ('PUR-2026-0001', 'ABC Traders', '9876543210', 1700000000, 'Cash', 1000.0, 50.0, 50.0, 1000.0, 'Test purchase', 1700000000)")
        val pCursor = db.query("SELECT id, purchaseNumber, supplierName, grandTotal FROM purchases WHERE purchaseNumber = 'PUR-2026-0001'")
        assertTrue(pCursor.moveToFirst())
        val purId = pCursor.getLong(0)
        assertEquals("PUR-2026-0001", pCursor.getString(1))
        assertEquals("ABC Traders", pCursor.getString(2))
        assertEquals(1000.0, pCursor.getDouble(3), 0.001)
        pCursor.close()

        // Verify stock_movements table exists
        db.execSQL("INSERT INTO `stock_movements` (`productId`, `quantity`, `oldStock`, `newStock`, `operationType`, `referenceNumber`, `timestamp`, `reason`) VALUES (1, 10.0, 5.0, 15.0, 'PURCHASE', 'PUR-2026-0001', 1700000000, 'Purchase received')")
        val smCursor = db.query("SELECT id, productId, quantity, operationType FROM stock_movements WHERE referenceNumber = 'PUR-2026-0001'")
        assertTrue(smCursor.moveToFirst())
        assertEquals(1, smCursor.getInt(1))
        assertEquals(10.0, smCursor.getDouble(2), 0.001)
        assertEquals("PURCHASE", smCursor.getString(3))
        smCursor.close()

        db.close()
    }

    @Test
    fun testMigration7To8AddsSaleTransactionFields() {
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val helperConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(7) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // v7 schema with transactions lacking the new fields
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `transactions` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `invoiceNumber` TEXT NOT NULL,
                            `timestamp` INTEGER NOT NULL,
                            `customerName` TEXT NOT NULL,
                            `customerPhone` TEXT NOT NULL,
                            `paymentMode` TEXT NOT NULL,
                            `subtotal` REAL NOT NULL,
                            `discount` REAL NOT NULL,
                            `gstAmount` REAL NOT NULL,
                            `grandTotal` REAL NOT NULL,
                            `itemsJson` TEXT NOT NULL,
                            `isCancelled` INTEGER NOT NULL DEFAULT 0,
                            `cancellationReason` TEXT NOT NULL DEFAULT ''
                        )
                        """.trimIndent()
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val db = helperFactory.create(helperConfig).writableDatabase
        db.execSQL(
            """
            INSERT INTO `transactions` (
                `invoiceNumber`, `timestamp`, `customerName`, `customerPhone`, `paymentMode`,
                `subtotal`, `discount`, `gstAmount`, `grandTotal`, `itemsJson`, `isCancelled`, `cancellationReason`
            ) VALUES (
                'INV-V7-001', 1700000000000, 'Arun', '9876543210', 'CASH',
                100.0, 0.0, 5.0, 105.0, '[]', 0, ''
            )
            """.trimIndent()
        )

        MIGRATION_7_8.migrate(db)

        val cursor = db.query("SELECT id, invoiceNumber, customerId, cashReceived, changeDue, paymentReference, createdAt, cancelledAt FROM transactions WHERE invoiceNumber = 'INV-V7-001'")
        assertTrue(cursor.moveToFirst())
        assertEquals("INV-V7-001", cursor.getString(1))
        assertTrue(cursor.isNull(2)) // customerId is null
        assertEquals(0.0, cursor.getDouble(3), 0.001) // cashReceived
        assertEquals(0.0, cursor.getDouble(4), 0.001) // changeDue
        assertEquals("", cursor.getString(5)) // paymentReference
        assertEquals(1700000000000L, cursor.getLong(6)) // createdAt populated from timestamp!
        assertTrue(cursor.isNull(7)) // cancelledAt is null
        cursor.close()

        db.close()
    }

    @Test
    fun testMigration8To9AddsAllocatedDiscountToSaleItems() {
        val helperFactory = FrameworkSQLiteOpenHelperFactory()
        val helperConfig = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(testDbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(8) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS `sale_items` (
                            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                            `transactionId` INTEGER NOT NULL,
                            `productId` INTEGER NOT NULL,
                            `barcode` TEXT NOT NULL,
                            `productName` TEXT NOT NULL,
                            `unit` TEXT NOT NULL,
                            `sellingPrice` REAL NOT NULL,
                            `costPrice` REAL NOT NULL,
                            `mrp` REAL NOT NULL,
                            `gstRate` REAL NOT NULL,
                            `quantity` REAL NOT NULL,
                            `lineDiscount` REAL NOT NULL,
                            `lineTax` REAL NOT NULL,
                            `lineTotal` REAL NOT NULL
                        )
                        """.trimIndent()
                    )
                }
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        val db = helperFactory.create(helperConfig).writableDatabase
        db.execSQL(
            """
            INSERT INTO `sale_items` (
                `transactionId`, `productId`, `barcode`, `productName`, `unit`,
                `sellingPrice`, `costPrice`, `mrp`, `gstRate`, `quantity`, `lineDiscount`, `lineTax`, `lineTotal`
            ) VALUES (
                1, 101, '890123', 'Atta 5kg', 'kg',
                200.0, 170.0, 220.0, 0.0, 1.0, 10.0, 0.0, 190.0
            )
            """.trimIndent()
        )

        MIGRATION_8_9.migrate(db)

        val cursor = db.query("SELECT id, allocatedDiscount, lineDiscount, lineTotal FROM sale_items WHERE id = 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(1L, cursor.getLong(0))
        assertEquals(0.0, cursor.getDouble(1), 0.001) // allocatedDiscount default
        assertEquals(10.0, cursor.getDouble(2), 0.001)
        assertEquals(190.0, cursor.getDouble(3), 0.001)
        cursor.close()

        db.close()
    }
}
