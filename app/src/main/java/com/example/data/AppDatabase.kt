package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.CustomerDao
import com.example.data.dao.InvoiceSequenceDao
import com.example.data.dao.LedgerDao
import com.example.data.dao.ProductDao
import com.example.data.dao.PurchaseDao
import com.example.data.dao.PurchaseItemDao
import com.example.data.dao.SaleItemDao
import com.example.data.dao.ShopSettingsDao
import com.example.data.dao.StockMovementDao
import com.example.data.dao.TransactionDao
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

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Ensure transactions table exists if migrating from a products-only v1
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

        // Check if bengaliName and rackLocation exist in products, add if missing
        val cursor = db.query("PRAGMA table_info(`products`)")
        val existingColumns = mutableSetOf<String>()
        val nameColIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (nameColIndex != -1) {
                existingColumns.add(cursor.getString(nameColIndex))
            }
        }
        cursor.close()

        if (!existingColumns.contains("bengaliName")) {
            db.execSQL("ALTER TABLE `products` ADD COLUMN `bengaliName` TEXT NOT NULL DEFAULT ''")
        }
        if (!existingColumns.contains("rackLocation")) {
            db.execSQL("ALTER TABLE `products` ADD COLUMN `rackLocation` TEXT NOT NULL DEFAULT ''")
        }

        db.execSQL("DROP INDEX IF EXISTS `index_products_barcode`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`) WHERE `barcode` != ''")
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Create shop_settings table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `shop_settings` (
                `id` INTEGER PRIMARY KEY NOT NULL,
                `shopName` TEXT NOT NULL,
                `tagline` TEXT NOT NULL,
                `ownerName` TEXT NOT NULL,
                `phone` TEXT NOT NULL,
                `address` TEXT NOT NULL,
                `city` TEXT NOT NULL,
                `gstin` TEXT NOT NULL,
                `upiId` TEXT NOT NULL,
                `printerPaperWidth` TEXT NOT NULL,
                `receiptFooterNote` TEXT NOT NULL,
                `termsNote` TEXT NOT NULL,
                `lowStockThresholdDefault` REAL NOT NULL
            )
            """.trimIndent()
        )

        // Seed default shop settings row (id = 1) if not already present
        db.execSQL(
            """
            INSERT OR IGNORE INTO `shop_settings` (
                `id`, `shopName`, `tagline`, `ownerName`, `phone`, `address`, `city`,
                `gstin`, `upiId`, `printerPaperWidth`, `receiptFooterNote`, `termsNote`, `lowStockThresholdDefault`
            ) VALUES (
                1,
                'Shree Ganesh Kirana & General Store',
                'Quality Ration & Daily Essentials at Best Price',
                'Rajesh Kumar Gupta',
                '+91 98765 43210',
                'Shop No. 12, Main Bazaar, Near Old Clock Tower',
                'Delhi - 110006',
                '07AAAAA0000A1Z5',
                'shreeganeshkirana@upi',
                '58mm',
                'Thank You! Visit Again',
                'Goods once sold can be returned within 2 days with bill.',
                5.0
            )
            """.trimIndent()
        )
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // Check if isActive exists in products, add if missing
        val cursor = db.query("PRAGMA table_info(`products`)")
        val existingColumns = mutableSetOf<String>()
        val nameColIndex = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (nameColIndex != -1) {
                existingColumns.add(cursor.getString(nameColIndex))
            }
        }
        cursor.close()

        if (!existingColumns.contains("isActive")) {
            db.execSQL("ALTER TABLE `products` ADD COLUMN `isActive` INTEGER NOT NULL DEFAULT 1")
        }
    }
}

val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create unique index for invoiceNumber on transactions
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_invoiceNumber` ON `transactions` (`invoiceNumber`)")

        // 2. Create invoice_sequence table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `invoice_sequence` (
                `prefix` TEXT NOT NULL,
                `lastSequenceNumber` INTEGER NOT NULL,
                PRIMARY KEY(`prefix`)
            )
            """.trimIndent()
        )

        // 3. Create sale_items table
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
                `lineTotal` REAL NOT NULL,
                FOREIGN KEY(`transactionId`) REFERENCES `transactions`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_transactionId` ON `sale_items` (`transactionId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_sale_items_productId` ON `sale_items` (`productId`)")
    }
}

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create customers table
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_customers_phone` ON `customers` (`phone`)")

        // 2. Create ledger_entries table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `ledger_entries` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `customerId` INTEGER NOT NULL,
                `date` INTEGER NOT NULL,
                `type` TEXT NOT NULL,
                `amount` REAL NOT NULL,
                `reference` TEXT NOT NULL,
                `note` TEXT NOT NULL,
                FOREIGN KEY(`customerId`) REFERENCES `customers`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_customerId` ON `ledger_entries` (`customerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_date` ON `ledger_entries` (`date`)")
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Upgrade customers table with openingBalance, createdAt, updatedAt, active
        val customerCursor = db.query("PRAGMA table_info(`customers`)")
        val customerCols = mutableSetOf<String>()
        val cNameIdx = customerCursor.getColumnIndex("name")
        while (customerCursor.moveToNext()) {
            if (cNameIdx != -1) customerCols.add(customerCursor.getString(cNameIdx))
        }
        customerCursor.close()

        if (!customerCols.contains("openingBalance")) {
            db.execSQL("ALTER TABLE `customers` ADD COLUMN `openingBalance` REAL NOT NULL DEFAULT 0.0")
        }
        if (!customerCols.contains("createdAt")) {
            db.execSQL("ALTER TABLE `customers` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
        }
        if (!customerCols.contains("updatedAt")) {
            db.execSQL("ALTER TABLE `customers` ADD COLUMN `updatedAt` INTEGER NOT NULL DEFAULT 0")
        }
        if (!customerCols.contains("active")) {
            db.execSQL("ALTER TABLE `customers` ADD COLUMN `active` INTEGER NOT NULL DEFAULT 1")
        }

        // 2. Upgrade transactions table with isCancelled and cancellationReason
        val txCursor = db.query("PRAGMA table_info(`transactions`)")
        val txCols = mutableSetOf<String>()
        val tNameIdx = txCursor.getColumnIndex("name")
        while (txCursor.moveToNext()) {
            if (tNameIdx != -1) txCols.add(txCursor.getString(tNameIdx))
        }
        txCursor.close()

        if (!txCols.contains("isCancelled")) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `isCancelled` INTEGER NOT NULL DEFAULT 0")
        }
        if (!txCols.contains("cancellationReason")) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `cancellationReason` TEXT NOT NULL DEFAULT ''")
        }

        // 3. Create purchases table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `purchases` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `purchaseNumber` TEXT NOT NULL,
                `supplierName` TEXT NOT NULL,
                `supplierPhone` TEXT NOT NULL,
                `purchaseDate` INTEGER NOT NULL,
                `paymentMode` TEXT NOT NULL,
                `subtotal` REAL NOT NULL,
                `discount` REAL NOT NULL,
                `tax` REAL NOT NULL,
                `grandTotal` REAL NOT NULL,
                `note` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchases_purchaseNumber` ON `purchases` (`purchaseNumber`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchases_purchaseDate` ON `purchases` (`purchaseDate`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchases_supplierName` ON `purchases` (`supplierName`)")

        // 4. Create purchase_items table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `purchase_items` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `purchaseId` INTEGER NOT NULL,
                `productId` INTEGER NOT NULL,
                `productNameSnapshot` TEXT NOT NULL,
                `barcodeSnapshot` TEXT NOT NULL,
                `unit` TEXT NOT NULL,
                `quantity` REAL NOT NULL,
                `purchaseRate` REAL NOT NULL,
                `gstRate` REAL NOT NULL,
                `lineDiscount` REAL NOT NULL,
                `lineTax` REAL NOT NULL,
                `lineTotal` REAL NOT NULL,
                FOREIGN KEY(`purchaseId`) REFERENCES `purchases`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_items_purchaseId` ON `purchase_items` (`purchaseId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_purchase_items_productId` ON `purchase_items` (`productId`)")

        // 5. Create stock_movements table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `stock_movements` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `productId` INTEGER NOT NULL,
                `quantity` REAL NOT NULL,
                `oldStock` REAL NOT NULL,
                `newStock` REAL NOT NULL,
                `operationType` TEXT NOT NULL,
                `referenceNumber` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL,
                `reason` TEXT NOT NULL,
                FOREIGN KEY(`productId`) REFERENCES `products`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_productId` ON `stock_movements` (`productId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_timestamp` ON `stock_movements` (`timestamp`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_stock_movements_operationType` ON `stock_movements` (`operationType`)")

        // 6. Ensure ledger_entries index on type
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_ledger_entries_type` ON `ledger_entries` (`type`)")
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val txCursor = db.query("PRAGMA table_info(`transactions`)")
        val txCols = mutableSetOf<String>()
        val nameIdx = txCursor.getColumnIndex("name")
        while (txCursor.moveToNext()) {
            if (nameIdx != -1) txCols.add(txCursor.getString(nameIdx))
        }
        txCursor.close()

        if (!txCols.contains("customerId")) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `customerId` INTEGER DEFAULT NULL")
        }
        if (!txCols.contains("cashReceived")) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `cashReceived` REAL NOT NULL DEFAULT 0.0")
        }
        if (!txCols.contains("changeDue")) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `changeDue` REAL NOT NULL DEFAULT 0.0")
        }
        if (!txCols.contains("paymentReference")) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `paymentReference` TEXT NOT NULL DEFAULT ''")
        }
        if (!txCols.contains("createdAt")) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `createdAt` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE `transactions` SET `createdAt` = `timestamp` WHERE `createdAt` = 0 OR `createdAt` IS NULL")
        }
        if (!txCols.contains("cancelledAt")) {
            db.execSQL("ALTER TABLE `transactions` ADD COLUMN `cancelledAt` INTEGER DEFAULT NULL")
        }

        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_customerId` ON `transactions` (`customerId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_createdAt` ON `transactions` (`createdAt`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_transactions_timestamp` ON `transactions` (`timestamp`)")
    }
}

val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val cursor = db.query("PRAGMA table_info(`sale_items`)")
        val cols = mutableSetOf<String>()
        val nameIdx = cursor.getColumnIndex("name")
        while (cursor.moveToNext()) {
            if (nameIdx != -1) cols.add(cursor.getString(nameIdx))
        }
        cursor.close()

        if (!cols.contains("allocatedDiscount")) {
            db.execSQL("ALTER TABLE `sale_items` ADD COLUMN `allocatedDiscount` REAL NOT NULL DEFAULT 0.0")
        }
    }
}

val DATABASE_CALLBACK = object : RoomDatabase.Callback() {
    override fun onCreate(db: SupportSQLiteDatabase) {
        super.onCreate(db)
        ensurePartialBarcodeIndex(db)
    }

    override fun onOpen(db: SupportSQLiteDatabase) {
        super.onOpen(db)
        ensurePartialBarcodeIndex(db)
    }

    private fun ensurePartialBarcodeIndex(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_products_barcode`")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`) WHERE `barcode` != ''")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_transactions_invoiceNumber` ON `transactions` (`invoiceNumber`)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_purchases_purchaseNumber` ON `purchases` (`purchaseNumber`)")
    }
}

@Database(
    entities = [
        ProductItem::class,
        SaleTransaction::class,
        ShopSettings::class,
        InvoiceSequence::class,
        SaleItemEntity::class,
        Customer::class,
        LedgerEntry::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        StockMovementEntity::class
    ],
    version = 9,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun transactionDao(): TransactionDao
    abstract fun shopSettingsDao(): ShopSettingsDao
    abstract fun invoiceSequenceDao(): InvoiceSequenceDao
    abstract fun saleItemDao(): SaleItemDao
    abstract fun customerDao(): CustomerDao
    abstract fun ledgerDao(): LedgerDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun purchaseItemDao(): PurchaseItemDao
    abstract fun stockMovementDao(): StockMovementDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kirana_database"
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
                    .addCallback(DATABASE_CALLBACK)
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun setTestDatabase(instance: AppDatabase?) {
            INSTANCE = instance
        }
    }
}
