package com.example.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import android.widget.Toast
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
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.OutputStreamWriter
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class ParsedBackupData(
    val backupVersion: Int,
    val creationTimestamp: Long,
    val exportDate: String,
    val settings: ShopSettings?,
    val products: List<ProductItem>,
    val transactions: List<SaleTransaction>,
    val saleItems: List<SaleItemEntity>,
    val invoiceSequences: List<InvoiceSequence>,
    val customers: List<Customer> = emptyList(),
    val ledger: List<LedgerEntry> = emptyList(),
    val purchases: List<PurchaseEntity> = emptyList(),
    val purchaseItems: List<PurchaseItemEntity> = emptyList(),
    val stockMovements: List<StockMovementEntity> = emptyList()
)

data class BackupValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val backupVersion: Int = 0,
    val databaseVersion: Int = 9,
    val exportDate: String = "",
    val creationTimestamp: Long = 0L,
    val fileSizeBytes: Long = 0L,
    val fileSizeFormatted: String = "",
    val shopName: String = "",
    val productCount: Int = 0,
    val salesCount: Int = 0,
    val saleItemsCount: Int = 0,
    val customersCount: Int = 0,
    val ledgerCount: Int = 0,
    val invoiceSequenceCount: Int = 0,
    val purchaseCount: Int = 0,
    val purchaseItemCount: Int = 0,
    val stockMovementCount: Int = 0,
    val isEncrypted: Boolean = false,
    val parsedData: ParsedBackupData? = null
)

object BackupCrypto {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val KDF_ALGORITHM = "PBKDF2WithHmacSHA256"
    private const val TAG_LENGTH_BITS = 128
    private const val IV_LENGTH_BYTES = 12
    private const val SALT_LENGTH_BYTES = 16
    private const val ITERATIONS = 10000
    private const val KEY_LENGTH_BITS = 256

    fun isEncrypted(jsonString: String): Boolean {
        val trimmed = jsonString.trim()
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false
        return try {
            val obj = JSONObject(trimmed)
            obj.optBoolean("encrypted", false) && obj.has("ciphertext") && obj.has("salt") && obj.has("iv")
        } catch (e: Exception) {
            false
        }
    }

    fun encrypt(plainText: String, password: String): String {
        require(password.isNotEmpty()) { "Encryption password cannot be empty" }
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH_BYTES).apply { random.nextBytes(this) }
        val iv = ByteArray(IV_LENGTH_BYTES).apply { random.nextBytes(this) }

        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

        val encryptedObj = JSONObject()
        encryptedObj.put("encrypted", true)
        encryptedObj.put("format", "KIRANA_ENCRYPTED_BACKUP")
        encryptedObj.put("algorithm", "AES-256-GCM")
        encryptedObj.put("kdf", KDF_ALGORITHM)
        encryptedObj.put("iterations", ITERATIONS)
        encryptedObj.put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
        encryptedObj.put("iv", Base64.encodeToString(iv, Base64.NO_WRAP))
        encryptedObj.put("ciphertext", Base64.encodeToString(cipherBytes, Base64.NO_WRAP))
        val now = System.currentTimeMillis()
        encryptedObj.put("creationTimestamp", now)
        encryptedObj.put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(now)))
        encryptedObj.put("databaseVersion", 9)
        encryptedObj.put("backupVersion", BackupManager.BACKUP_VERSION)

        return encryptedObj.toString(2)
    }

    fun decrypt(encryptedJson: String, password: String): String {
        require(password.isNotEmpty()) { "Decryption password cannot be empty" }
        val obj = JSONObject(encryptedJson)
        val salt = Base64.decode(obj.getString("salt"), Base64.DEFAULT)
        val iv = Base64.decode(obj.getString("iv"), Base64.DEFAULT)
        val ciphertext = Base64.decode(obj.getString("ciphertext"), Base64.DEFAULT)
        val iterations = obj.optInt("iterations", ITERATIONS)

        val factory = SecretKeyFactory.getInstance(KDF_ALGORITHM)
        val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_LENGTH_BITS)
        val secretKey = SecretKeySpec(factory.generateSecret(spec).encoded, "AES")

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, iv))
        val plainBytes = cipher.doFinal(ciphertext)
        return String(plainBytes, Charsets.UTF_8)
    }
}

object BackupManager {

    const val BACKUP_VERSION = 3

    /**
     * Builds the complete, versioned JSON backup containing all persistent application data.
     * Guaranteed read-only with respect to the database.
     * Preserves exact database IDs and relationships for all persistent tables.
     */
    fun buildBackupJsonString(
        products: List<ProductItem>,
        transactions: List<SaleTransaction>,
        saleItems: List<SaleItemEntity>,
        settings: ShopSettings? = null,
        invoiceSequences: List<InvoiceSequence> = emptyList(),
        customers: List<Customer> = emptyList(),
        ledger: List<LedgerEntry> = emptyList(),
        purchases: List<PurchaseEntity> = emptyList(),
        purchaseItems: List<PurchaseItemEntity> = emptyList(),
        stockMovements: List<StockMovementEntity> = emptyList()
    ): String {
        val root = JSONObject()
        root.put("backupVersion", BACKUP_VERSION)
        root.put("version", BACKUP_VERSION) // backwards compatibility
        val now = System.currentTimeMillis()
        root.put("creationTimestamp", now)
        root.put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(now)))
        root.put("appVersion", "1.0.0")
        root.put("databaseVersion", 9)
        root.put("app", "Kirana Inventory Management System")

        // Metadata summary counts
        val metadataObj = JSONObject().apply {
            put("backupVersion", BACKUP_VERSION)
            put("creationTimestamp", now)
            put("exportDate", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(now)))
            put("appVersion", "1.0.0")
            put("databaseVersion", 9)
            put("productsCount", products.size)
            put("salesCount", transactions.size)
            put("saleItemsCount", saleItems.size)
            put("invoiceSequencesCount", invoiceSequences.size)
            put("customersCount", customers.size)
            put("ledgerEntriesCount", ledger.size)
            put("purchasesCount", purchases.size)
            put("purchaseItemsCount", purchaseItems.size)
            put("stockMovementsCount", stockMovements.size)
        }
        root.put("metadata", metadataObj)

        // Shop Settings
        val settingsObj = JSONObject().apply {
            if (settings != null) {
                put("id", settings.id)
                put("shopName", settings.shopName)
                put("tagline", settings.tagline)
                put("ownerName", settings.ownerName)
                put("phone", settings.phone)
                put("address", settings.address)
                put("city", settings.city)
                put("gstin", settings.gstin)
                put("upiId", settings.upiId)
                put("printerPaperWidth", settings.printerPaperWidth)
                put("receiptFooterNote", settings.receiptFooterNote)
                put("termsNote", settings.termsNote)
                put("lowStockThresholdDefault", settings.lowStockThresholdDefault)
            }
        }
        root.put("shopSettings", settingsObj)
        root.put("settings", settingsObj) // backwards compatibility

        // Products
        val productsArray = JSONArray()
        for (p in products) {
            val pObj = JSONObject().apply {
                put("id", p.id)
                put("barcode", p.barcode)
                put("name", p.name)
                put("bengaliName", p.bengaliName)
                put("category", p.category)
                put("unit", p.unit)
                put("costPrice", p.costPrice)
                put("sellingPrice", p.sellingPrice)
                put("mrp", p.mrp)
                put("currentStock", p.currentStock)
                put("minStockAlert", p.minStockAlert)
                put("gstRate", p.gstRate)
                put("rackLocation", p.rackLocation)
                put("isActive", p.isActive)
                put("lastUpdated", p.lastUpdated)
            }
            productsArray.put(pObj)
        }
        root.put("products", productsArray)

        // Sales / Transactions
        val transArray = JSONArray()
        for (t in transactions) {
            val tObj = JSONObject().apply {
                put("id", t.id)
                put("invoiceNumber", t.invoiceNumber)
                put("timestamp", t.timestamp)
                put("customerName", t.customerName)
                put("customerPhone", t.customerPhone)
                put("paymentMode", t.paymentMode)
                put("subtotal", t.subtotal)
                put("discount", t.discount)
                put("gstAmount", t.gstAmount)
                put("grandTotal", t.grandTotal)
                put("itemsJson", t.itemsJson)
                put("isCancelled", t.isCancelled)
                put("cancellationReason", t.cancellationReason)
                if (t.customerId != null) put("customerId", t.customerId)
                put("cashReceived", t.cashReceived)
                put("changeDue", t.changeDue)
                put("paymentReference", t.paymentReference)
                put("createdAt", t.createdAt)
                if (t.cancelledAt != null) put("cancelledAt", t.cancelledAt)
            }
            transArray.put(tObj)
        }
        root.put("sales", transArray)
        root.put("transactions", transArray) // backwards compatibility

        // Sale items (individual snapshot line items)
        val saleItemsArray = JSONArray()
        for (si in saleItems) {
            val siObj = JSONObject().apply {
                put("id", si.id)
                put("transactionId", si.transactionId)
                put("productId", si.productId)
                put("barcode", si.barcode)
                put("productName", si.productName)
                put("unit", si.unit)
                put("quantity", si.quantity)
                put("sellingPrice", si.sellingPrice)
                put("mrp", si.mrp)
                put("costPrice", si.costPrice)
                put("gstRate", si.gstRate)
                put("lineDiscount", si.lineDiscount)
                put("allocatedDiscount", si.allocatedDiscount)
                put("lineTax", si.lineTax)
                put("lineTotal", si.lineTotal)
            }
            saleItemsArray.put(siObj)
        }
        root.put("saleItems", saleItemsArray)

        // Customers (Real database records only, never reconstructed)
        val customersArray = JSONArray()
        for (c in customers) {
            val cObj = JSONObject().apply {
                put("id", c.id)
                put("name", c.name)
                put("phone", c.phone)
                put("address", c.address)
                put("openingBalance", c.openingBalance)
                put("createdAt", c.createdAt)
                put("updatedAt", c.updatedAt)
                put("active", c.active)
            }
            customersArray.put(cObj)
        }
        root.put("customers", customersArray)

        // Ledger (Real database records only, never reconstructed)
        val ledgerArray = JSONArray()
        for (l in ledger) {
            val lObj = JSONObject().apply {
                put("id", l.id)
                put("customerId", l.customerId)
                put("date", l.date)
                put("type", l.type)
                put("amount", l.amount)
                put("reference", l.reference)
                put("note", l.note)
            }
            ledgerArray.put(lObj)
        }
        root.put("ledgerEntries", ledgerArray)
        root.put("ledger", ledgerArray) // backwards compatibility

        // Purchases
        val purchasesArray = JSONArray()
        for (pur in purchases) {
            val purObj = JSONObject().apply {
                put("id", pur.id)
                put("purchaseNumber", pur.purchaseNumber)
                put("supplierName", pur.supplierName)
                put("supplierPhone", pur.supplierPhone)
                put("purchaseDate", pur.purchaseDate)
                put("paymentMode", pur.paymentMode)
                put("subtotal", pur.subtotal)
                put("discount", pur.discount)
                put("tax", pur.tax)
                put("grandTotal", pur.grandTotal)
                put("note", pur.note)
                put("timestamp", pur.timestamp)
            }
            purchasesArray.put(purObj)
        }
        root.put("purchases", purchasesArray)

        // Purchase Items
        val purchaseItemsArray = JSONArray()
        for (pi in purchaseItems) {
            val piObj = JSONObject().apply {
                put("id", pi.id)
                put("purchaseId", pi.purchaseId)
                put("productId", pi.productId)
                put("productNameSnapshot", pi.productNameSnapshot)
                put("barcodeSnapshot", pi.barcodeSnapshot)
                put("unit", pi.unit)
                put("quantity", pi.quantity)
                put("purchaseRate", pi.purchaseRate)
                put("gstRate", pi.gstRate)
                put("lineDiscount", pi.lineDiscount)
                put("lineTax", pi.lineTax)
                put("lineTotal", pi.lineTotal)
            }
            purchaseItemsArray.put(piObj)
        }
        root.put("purchaseItems", purchaseItemsArray)

        // Stock Movements
        val movementsArray = JSONArray()
        for (sm in stockMovements) {
            val smObj = JSONObject().apply {
                put("id", sm.id)
                put("productId", sm.productId)
                put("quantity", sm.quantity)
                put("oldStock", sm.oldStock)
                put("newStock", sm.newStock)
                put("operationType", sm.operationType)
                put("referenceNumber", sm.referenceNumber)
                put("timestamp", sm.timestamp)
                put("reason", sm.reason)
            }
            movementsArray.put(smObj)
        }
        root.put("stockMovements", movementsArray)

        // Invoice sequence
        val sequencesArray = JSONArray()
        for (seq in invoiceSequences) {
            val sObj = JSONObject().apply {
                put("prefix", seq.prefix)
                put("lastSequenceNumber", seq.lastSequenceNumber)
            }
            sequencesArray.put(sObj)
        }
        root.put("invoiceSequences", sequencesArray)
        root.put("invoiceSequence", sequencesArray) // backwards compatibility

        return root.toString(2)
    }

    /**
     * Writes backup JSON to an Android Storage Access Framework (SAF) URI.
     */
    fun writeBackupToUri(context: Context, uri: Uri, jsonContent: String): Boolean {
        return try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                OutputStreamWriter(outputStream, Charsets.UTF_8).use { writer ->
                    writer.write(jsonContent)
                    writer.flush()
                }
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Reads and returns UTF-8 content from an Android Storage Access Framework (SAF) URI.
     */
    fun readBackupFromUri(context: Context, uri: Uri): String? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun formatFileSize(bytes: Long): String {
        return when {
            bytes <= 0L -> "0 B"
            bytes < 1024L -> "$bytes B"
            bytes < 1024L * 1024L -> String.format(Locale.ENGLISH, "%.1f KB", bytes / 1024.0)
            else -> String.format(Locale.ENGLISH, "%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    fun isEncryptedBackup(jsonString: String): Boolean = BackupCrypto.isEncrypted(jsonString)

    fun encryptBackup(plainJson: String, password: String): String = BackupCrypto.encrypt(plainJson, password)

    fun decryptBackup(encryptedJson: String, password: String): String = BackupCrypto.decrypt(encryptedJson, password)

    fun decryptAndValidateBackup(encryptedJson: String, password: String, fileSizeBytes: Long = 0L): BackupValidationResult {
        return try {
            val decrypted = BackupCrypto.decrypt(encryptedJson, password)
            validateBackup(decrypted, fileSizeBytes)
        } catch (e: Exception) {
            val totalBytes = if (fileSizeBytes > 0) fileSizeBytes else encryptedJson.toByteArray(Charsets.UTF_8).size.toLong()
            BackupValidationResult(
                isValid = false,
                isEncrypted = true,
                fileSizeBytes = totalBytes,
                fileSizeFormatted = formatFileSize(totalBytes),
                errorMessage = "Incorrect password or corrupted encrypted backup"
            )
        }
    }

    /**
     * Comprehensive validation of a backup file string before any database operation.
     */
    fun validateBackup(jsonString: String, fileSizeBytes: Long = 0L): BackupValidationResult {
        if (jsonString.isBlank()) {
            return BackupValidationResult(isValid = false, errorMessage = "Backup file is empty")
        }

        // Check if file is encrypted with password
        if (BackupCrypto.isEncrypted(jsonString)) {
            val root = try { JSONObject(jsonString) } catch (e: Exception) { null }
            val estBytes = if (fileSizeBytes > 0) fileSizeBytes else jsonString.toByteArray(Charsets.UTF_8).size.toLong()
            val exportDateStr = root?.optString("exportDate", "")?.ifBlank {
                val ts = root.optLong("creationTimestamp", 0L)
                if (ts > 0) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(ts)) else ""
            } ?: ""
            return BackupValidationResult(
                isValid = true,
                isEncrypted = true,
                backupVersion = root?.optInt("backupVersion", BACKUP_VERSION) ?: BACKUP_VERSION,
                databaseVersion = root?.optInt("databaseVersion", 9) ?: 9,
                exportDate = exportDateStr,
                creationTimestamp = root?.optLong("creationTimestamp", 0L) ?: 0L,
                fileSizeBytes = estBytes,
                fileSizeFormatted = formatFileSize(estBytes),
                errorMessage = null
            )
        }

        val root = try {
            JSONObject(jsonString)
        } catch (e: Exception) {
            return BackupValidationResult(isValid = false, errorMessage = "Malformed JSON syntax: ${e.message}")
        }

        // Validate backup version
        val version = if (root.has("backupVersion")) {
            root.optInt("backupVersion", -1)
        } else if (root.has("version")) {
            root.optInt("version", -1)
        } else {
            -1
        }
        if (version < 1 || version > BACKUP_VERSION) {
            return BackupValidationResult(isValid = false, errorMessage = "Invalid or unsupported backup version: $version (Supported: 1..$BACKUP_VERSION)")
        }

        // Validate required fields
        if (!root.has("products") || root.optJSONArray("products") == null) {
            return BackupValidationResult(isValid = false, errorMessage = "Missing required 'products' section in backup")
        }

        // Validate product entries
        val productsArray = root.optJSONArray("products") ?: JSONArray()
        val seenBarcodes = mutableSetOf<String>()
        val productIds = mutableSetOf<Long>()
        for (i in 0 until productsArray.length()) {
            val pObj = productsArray.optJSONObject(i)
                ?: return BackupValidationResult(isValid = false, errorMessage = "Invalid product structure at index $i")
            val name = pObj.optString("name", "").trim()
            if (name.isEmpty()) {
                return BackupValidationResult(isValid = false, errorMessage = "Product at index $i is missing required 'name'")
            }
            val cp = pObj.optDouble("costPrice", 0.0)
            if (cp < 0.0 || cp.isNaN() || cp.isInfinite()) {
                return BackupValidationResult(isValid = false, errorMessage = "Product '$name' has negative or invalid cost price: $cp")
            }
            val sp = pObj.optDouble("sellingPrice", 0.0)
            if (sp < 0.0 || sp.isNaN() || sp.isInfinite()) {
                return BackupValidationResult(isValid = false, errorMessage = "Product '$name' has negative selling price")
            }
            val mrp = pObj.optDouble("mrp", 0.0)
            if (mrp < 0.0 || mrp.isNaN() || mrp.isInfinite()) {
                return BackupValidationResult(isValid = false, errorMessage = "Product '$name' has negative MRP")
            }
            val gst = pObj.optDouble("gstRate", 0.0)
            if (gst < 0.0 || gst > 100.0 || gst.isNaN() || gst.isInfinite()) {
                return BackupValidationResult(isValid = false, errorMessage = "Product '$name' has invalid GST rate: $gst")
            }
            val stock = pObj.optDouble("currentStock", 0.0)
            if (stock.isNaN() || stock.isInfinite()) {
                return BackupValidationResult(isValid = false, errorMessage = "Product '$name' has invalid current stock")
            }
            val pId = pObj.optLong("id", 0L)
            if (pId > 0) {
                if (productIds.contains(pId)) {
                    return BackupValidationResult(isValid = false, errorMessage = "Duplicate product ID $pId found in backup")
                }
                productIds.add(pId)
            }
            val barcode = pObj.optString("barcode", "").trim()
            if (barcode.isNotEmpty()) {
                if (seenBarcodes.contains(barcode)) {
                    return BackupValidationResult(isValid = false, errorMessage = "Duplicate barcode '$barcode' found in products")
                }
                seenBarcodes.add(barcode)
            }
        }

        // Validate sales entries
        val salesArray = root.optJSONArray("sales") ?: root.optJSONArray("transactions") ?: JSONArray()
        val seenInvoices = mutableSetOf<String>()
        val saleIds = mutableSetOf<Long>()
        for (i in 0 until salesArray.length()) {
            val sObj = salesArray.optJSONObject(i)
                ?: return BackupValidationResult(isValid = false, errorMessage = "Invalid sale structure at index $i")
            val invoiceNo = sObj.optString("invoiceNumber", "").trim()
            if (invoiceNo.isEmpty()) {
                return BackupValidationResult(isValid = false, errorMessage = "Sale at index $i is missing required 'invoiceNumber'")
            }
            if (seenInvoices.contains(invoiceNo)) {
                return BackupValidationResult(isValid = false, errorMessage = "Duplicate invoice number '$invoiceNo' found in sales")
            }
            seenInvoices.add(invoiceNo)
            val sId = sObj.optLong("id", 0L)
            if (sId > 0) {
                if (saleIds.contains(sId)) {
                    return BackupValidationResult(isValid = false, errorMessage = "Duplicate sale transaction ID $sId found in backup")
                }
                saleIds.add(sId)
            }
            val grandTotal = sObj.optDouble("grandTotal", 0.0)
            if (grandTotal < 0.0 || grandTotal.isNaN() || grandTotal.isInfinite()) {
                return BackupValidationResult(isValid = false, errorMessage = "Sale '$invoiceNo' has invalid grand total: $grandTotal")
            }
        }

        // Validate sale items entries (referential integrity)
        val saleItemsArray = root.optJSONArray("saleItems") ?: JSONArray()
        for (i in 0 until saleItemsArray.length()) {
            val siObj = saleItemsArray.optJSONObject(i)
                ?: return BackupValidationResult(isValid = false, errorMessage = "Invalid sale item structure at index $i")
            val txId = siObj.optLong("transactionId", 0L)
            if (saleIds.isNotEmpty() && !saleIds.contains(txId)) {
                return BackupValidationResult(isValid = false, errorMessage = "Sale item at index $i refers to non-existent transactionId: $txId")
            }
            val qty = siObj.optDouble("quantity", 0.0)
            if (qty <= 0.0 || qty.isNaN() || qty.isInfinite()) {
                return BackupValidationResult(isValid = false, errorMessage = "Sale item at index $i has invalid quantity: $qty")
            }
            val sp = siObj.optDouble("sellingPrice", 0.0)
            if (sp < 0.0 || sp.isNaN() || sp.isInfinite()) {
                return BackupValidationResult(isValid = false, errorMessage = "Sale item at index $i has invalid selling price: $sp")
            }
        }

        // Validate customers entries
        val customersArray = root.optJSONArray("customers") ?: JSONArray()
        val customerIds = mutableSetOf<Long>()
        val seenPhones = mutableSetOf<String>()
        for (i in 0 until customersArray.length()) {
            val cObj = customersArray.optJSONObject(i)
                ?: return BackupValidationResult(isValid = false, errorMessage = "Invalid customer structure at index $i")
            val name = cObj.optString("name", "").trim()
            if (name.isEmpty()) {
                return BackupValidationResult(isValid = false, errorMessage = "Customer at index $i is missing required 'name'")
            }
            val cId = cObj.optLong("id", 0L)
            if (cId > 0) {
                if (customerIds.contains(cId)) {
                    return BackupValidationResult(isValid = false, errorMessage = "Duplicate customer ID $cId found in backup")
                }
                customerIds.add(cId)
            }
            val phone = cObj.optString("phone", "").trim()
            if (phone.isNotEmpty()) {
                if (seenPhones.contains(phone)) {
                    return BackupValidationResult(isValid = false, errorMessage = "Duplicate phone '$phone' found in customers")
                }
                seenPhones.add(phone)
            }
        }

        // Validate ledger entries (referential integrity)
        val ledgerArray = root.optJSONArray("ledgerEntries") ?: root.optJSONArray("ledger") ?: JSONArray()
        for (i in 0 until ledgerArray.length()) {
            val lObj = ledgerArray.optJSONObject(i)
                ?: return BackupValidationResult(isValid = false, errorMessage = "Invalid ledger entry structure at index $i")
            val cId = lObj.optLong("customerId", 0L)
            if (customerIds.isNotEmpty() && !customerIds.contains(cId)) {
                return BackupValidationResult(isValid = false, errorMessage = "Ledger entry at index $i refers to non-existent customerId: $cId")
            }
            val amount = lObj.optDouble("amount", 0.0)
            if (amount < 0.0 || amount.isNaN() || amount.isInfinite()) {
                return BackupValidationResult(isValid = false, errorMessage = "Ledger entry at index $i has invalid amount: $amount")
            }
        }

        // Validate purchases entries
        val purchasesArray = root.optJSONArray("purchases") ?: JSONArray()
        val purchaseIds = mutableSetOf<Long>()
        val seenPurNumbers = mutableSetOf<String>()
        for (i in 0 until purchasesArray.length()) {
            val purObj = purchasesArray.optJSONObject(i)
                ?: return BackupValidationResult(isValid = false, errorMessage = "Invalid purchase structure at index $i")
            val purNum = purObj.optString("purchaseNumber", "").trim()
            if (purNum.isEmpty()) {
                return BackupValidationResult(isValid = false, errorMessage = "Purchase at index $i is missing required 'purchaseNumber'")
            }
            if (seenPurNumbers.contains(purNum)) {
                return BackupValidationResult(isValid = false, errorMessage = "Duplicate purchase number '$purNum' found in purchases")
            }
            seenPurNumbers.add(purNum)
            val purId = purObj.optLong("id", 0L)
            if (purId > 0) {
                if (purchaseIds.contains(purId)) {
                    return BackupValidationResult(isValid = false, errorMessage = "Duplicate purchase ID $purId found in backup")
                }
                purchaseIds.add(purId)
            }
        }

        // Validate purchase items entries (referential integrity)
        val purchaseItemsArray = root.optJSONArray("purchaseItems") ?: JSONArray()
        for (i in 0 until purchaseItemsArray.length()) {
            val piObj = purchaseItemsArray.optJSONObject(i)
                ?: return BackupValidationResult(isValid = false, errorMessage = "Invalid purchase item structure at index $i")
            val pId = piObj.optLong("purchaseId", 0L)
            if (purchaseIds.isNotEmpty() && !purchaseIds.contains(pId)) {
                return BackupValidationResult(isValid = false, errorMessage = "Purchase item at index $i refers to non-existent purchaseId: $pId")
            }
            val qty = piObj.optDouble("quantity", 0.0)
            if (qty <= 0.0 || qty.isNaN() || qty.isInfinite()) {
                return BackupValidationResult(isValid = false, errorMessage = "Purchase item at index $i has invalid quantity: $qty")
            }
        }

        val parsed = parseFullBackupInternal(root, version)
        val totalBytes = if (fileSizeBytes > 0) fileSizeBytes else jsonString.toByteArray(Charsets.UTF_8).size.toLong()
        val dbVer = root.optInt("databaseVersion", root.optJSONObject("metadata")?.optInt("databaseVersion", 9) ?: 9)
        val exportDateStr = root.optString("exportDate", "").ifBlank {
            val ts = root.optLong("creationTimestamp", root.optLong("exportTimestamp", 0L))
            if (ts > 0) SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH).format(Date(ts)) else "Unknown"
        }

        return BackupValidationResult(
            isValid = true,
            backupVersion = version,
            databaseVersion = dbVer,
            exportDate = exportDateStr,
            creationTimestamp = root.optLong("creationTimestamp", root.optLong("exportTimestamp", 0L)),
            fileSizeBytes = totalBytes,
            fileSizeFormatted = formatFileSize(totalBytes),
            shopName = parsed.settings?.shopName ?: "",
            productCount = parsed.products.size,
            salesCount = parsed.transactions.size,
            saleItemsCount = parsed.saleItems.size,
            customersCount = parsed.customers.size,
            ledgerCount = parsed.ledger.size,
            invoiceSequenceCount = parsed.invoiceSequences.size,
            purchaseCount = parsed.purchases.size,
            purchaseItemCount = parsed.purchaseItems.size,
            stockMovementCount = parsed.stockMovements.size,
            isEncrypted = false,
            parsedData = parsed
        )
    }

    fun parseFullBackup(jsonString: String): ParsedBackupData {
        val root = JSONObject(jsonString)
        val version = if (root.has("backupVersion")) root.optInt("backupVersion", 1) else root.optInt("version", 1)
        return parseFullBackupInternal(root, version)
    }

    private fun parseFullBackupInternal(root: JSONObject, version: Int): ParsedBackupData {
        var settings: ShopSettings? = null
        val sObj = root.optJSONObject("shopSettings") ?: root.optJSONObject("settings")
        if (sObj != null) {
            settings = ShopSettings(
                id = sObj.optInt("id", 1),
                shopName = sObj.optString("shopName", "Shree Ganesh Kirana"),
                tagline = sObj.optString("tagline", "Quality Ration & Daily Essentials at Best Price"),
                ownerName = sObj.optString("ownerName", ""),
                phone = sObj.optString("phone", ""),
                address = sObj.optString("address", ""),
                city = sObj.optString("city", ""),
                gstin = sObj.optString("gstin", ""),
                upiId = sObj.optString("upiId", ""),
                printerPaperWidth = sObj.optString("printerPaperWidth", "58mm"),
                receiptFooterNote = sObj.optString("receiptFooterNote", "Thank You! Visit Again"),
                termsNote = sObj.optString("termsNote", "Goods once sold can be returned within 2 days with bill."),
                lowStockThresholdDefault = sObj.optDouble("lowStockThresholdDefault", 5.0)
            )
        }

        val products = mutableListOf<ProductItem>()
        val pArray = root.optJSONArray("products")
        if (pArray != null) {
            for (i in 0 until pArray.length()) {
                val obj = pArray.getJSONObject(i)
                products.add(
                    ProductItem(
                        id = obj.optLong("id", 0L),
                        barcode = obj.optString("barcode", ""),
                        name = obj.getString("name"),
                        bengaliName = obj.optString("bengaliName", obj.optString("hindiName", "")),
                        category = obj.optString("category", "General"),
                        unit = obj.optString("unit", "Piece"),
                        costPrice = obj.optDouble("costPrice", 0.0),
                        sellingPrice = obj.optDouble("sellingPrice", 0.0),
                        mrp = obj.optDouble("mrp", 0.0),
                        currentStock = obj.optDouble("currentStock", 0.0),
                        minStockAlert = obj.optDouble("minStockAlert", 5.0),
                        gstRate = obj.optDouble("gstRate", 0.0),
                        rackLocation = obj.optString("rackLocation", ""),
                        isActive = obj.optBoolean("isActive", true),
                        lastUpdated = obj.optLong("lastUpdated", System.currentTimeMillis())
                    )
                )
            }
        }

        val transactions = mutableListOf<SaleTransaction>()
        val tArray = root.optJSONArray("sales") ?: root.optJSONArray("transactions")
        if (tArray != null) {
            for (i in 0 until tArray.length()) {
                val obj = tArray.getJSONObject(i)
                transactions.add(
                    SaleTransaction(
                        id = obj.optLong("id", 0L),
                        invoiceNumber = obj.getString("invoiceNumber"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        customerName = obj.optString("customerName", ""),
                        customerPhone = obj.optString("customerPhone", ""),
                        paymentMode = obj.optString("paymentMode", "CASH"),
                        subtotal = obj.optDouble("subtotal", 0.0),
                        discount = obj.optDouble("discount", 0.0),
                        gstAmount = obj.optDouble("gstAmount", 0.0),
                        grandTotal = obj.optDouble("grandTotal", 0.0),
                        itemsJson = obj.optString("itemsJson", "[]"),
                        isCancelled = obj.optBoolean("isCancelled", false),
                        cancellationReason = obj.optString("cancellationReason", ""),
                        customerId = if (obj.has("customerId") && !obj.isNull("customerId")) obj.optLong("customerId") else null,
                        cashReceived = obj.optDouble("cashReceived", 0.0),
                        changeDue = obj.optDouble("changeDue", 0.0),
                        paymentReference = obj.optString("paymentReference", ""),
                        createdAt = obj.optLong("createdAt", obj.optLong("timestamp", System.currentTimeMillis())),
                        cancelledAt = if (obj.has("cancelledAt") && !obj.isNull("cancelledAt")) obj.optLong("cancelledAt") else null
                    )
                )
            }
        }

        val saleItems = mutableListOf<SaleItemEntity>()
        val siArray = root.optJSONArray("saleItems")
        if (siArray != null) {
            for (i in 0 until siArray.length()) {
                val obj = siArray.getJSONObject(i)
                saleItems.add(
                    SaleItemEntity(
                        id = obj.optLong("id", 0L),
                        transactionId = obj.getLong("transactionId"),
                        productId = obj.optLong("productId", 0L),
                        barcode = obj.optString("barcode", ""),
                        productName = obj.optString("productName", ""),
                        unit = obj.optString("unit", "Piece"),
                        sellingPrice = obj.optDouble("sellingPrice", 0.0),
                        costPrice = obj.optDouble("costPrice", 0.0),
                        mrp = obj.optDouble("mrp", 0.0),
                        gstRate = obj.optDouble("gstRate", 0.0),
                        quantity = obj.optDouble("quantity", 1.0),
                        lineDiscount = obj.optDouble("lineDiscount", 0.0),
                        allocatedDiscount = obj.optDouble("allocatedDiscount", 0.0),
                        lineTax = obj.optDouble("lineTax", 0.0),
                        lineTotal = obj.optDouble("lineTotal", 0.0)
                    )
                )
            }
        }

        val sequences = mutableListOf<InvoiceSequence>()
        val seqArray = root.optJSONArray("invoiceSequences") ?: root.optJSONArray("invoiceSequence")
        if (seqArray != null) {
            for (i in 0 until seqArray.length()) {
                val obj = seqArray.getJSONObject(i)
                sequences.add(
                    InvoiceSequence(
                        prefix = obj.getString("prefix"),
                        lastSequenceNumber = obj.getLong("lastSequenceNumber")
                    )
                )
            }
        }

        val customers = mutableListOf<Customer>()
        val custArray = root.optJSONArray("customers")
        if (custArray != null) {
            for (i in 0 until custArray.length()) {
                val obj = custArray.getJSONObject(i)
                customers.add(
                    Customer(
                        id = obj.optLong("id", 0L),
                        name = obj.optString("name", "Customer"),
                        phone = obj.optString("phone", ""),
                        address = obj.optString("address", ""),
                        openingBalance = obj.optDouble("openingBalance", 0.0),
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = obj.optLong("updatedAt", System.currentTimeMillis()),
                        active = obj.optBoolean("active", true)
                    )
                )
            }
        }

        val ledger = mutableListOf<LedgerEntry>()
        val ledArray = root.optJSONArray("ledgerEntries") ?: root.optJSONArray("ledger")
        if (ledArray != null) {
            for (i in 0 until ledArray.length()) {
                val obj = ledArray.getJSONObject(i)
                val customerId = obj.optLong("customerId", 0L)
                ledger.add(
                    LedgerEntry(
                        id = obj.optLong("id", 0L),
                        customerId = customerId,
                        date = obj.optLong("date", obj.optLong("timestamp", System.currentTimeMillis())),
                        type = obj.optString("type", LedgerEntry.TYPE_CREDIT_SALE),
                        amount = obj.optDouble("amount", 0.0),
                        reference = obj.optString("reference", obj.optString("invoiceNumber", "")),
                        note = obj.optString("note", "")
                    )
                )
            }
        }

        val purchases = mutableListOf<PurchaseEntity>()
        val purArray = root.optJSONArray("purchases")
        if (purArray != null) {
            for (i in 0 until purArray.length()) {
                val obj = purArray.getJSONObject(i)
                purchases.add(
                    PurchaseEntity(
                        id = obj.optLong("id", 0L),
                        purchaseNumber = obj.optString("purchaseNumber", ""),
                        supplierName = obj.optString("supplierName", ""),
                        supplierPhone = obj.optString("supplierPhone", ""),
                        purchaseDate = obj.optLong("purchaseDate", System.currentTimeMillis()),
                        paymentMode = obj.optString("paymentMode", "Cash"),
                        subtotal = obj.optDouble("subtotal", 0.0),
                        discount = obj.optDouble("discount", 0.0),
                        tax = obj.optDouble("tax", 0.0),
                        grandTotal = obj.optDouble("grandTotal", 0.0),
                        note = obj.optString("note", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
        }

        val purchaseItems = mutableListOf<PurchaseItemEntity>()
        val piArray = root.optJSONArray("purchaseItems")
        if (piArray != null) {
            for (i in 0 until piArray.length()) {
                val obj = piArray.getJSONObject(i)
                purchaseItems.add(
                    PurchaseItemEntity(
                        id = obj.optLong("id", 0L),
                        purchaseId = obj.optLong("purchaseId", 0L),
                        productId = obj.optLong("productId", 0L),
                        productNameSnapshot = obj.optString("productNameSnapshot", ""),
                        barcodeSnapshot = obj.optString("barcodeSnapshot", ""),
                        unit = obj.optString("unit", "Piece"),
                        quantity = obj.optDouble("quantity", 0.0),
                        purchaseRate = obj.optDouble("purchaseRate", 0.0),
                        gstRate = obj.optDouble("gstRate", 0.0),
                        lineDiscount = obj.optDouble("lineDiscount", 0.0),
                        lineTax = obj.optDouble("lineTax", 0.0),
                        lineTotal = obj.optDouble("lineTotal", 0.0)
                    )
                )
            }
        }

        val stockMovements = mutableListOf<StockMovementEntity>()
        val smArray = root.optJSONArray("stockMovements")
        if (smArray != null) {
            for (i in 0 until smArray.length()) {
                val obj = smArray.getJSONObject(i)
                stockMovements.add(
                    StockMovementEntity(
                        id = obj.optLong("id", 0L),
                        productId = obj.optLong("productId", 0L),
                        quantity = obj.optDouble("quantity", 0.0),
                        oldStock = obj.optDouble("oldStock", 0.0),
                        newStock = obj.optDouble("newStock", 0.0),
                        operationType = obj.optString("operationType", "ADJUSTMENT"),
                        referenceNumber = obj.optString("referenceNumber", ""),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        reason = obj.optString("reason", "")
                    )
                )
            }
        }

        return ParsedBackupData(
            backupVersion = version,
            creationTimestamp = root.optLong("creationTimestamp", 0L),
            exportDate = root.optString("exportDate", ""),
            settings = settings,
            products = products,
            transactions = transactions,
            saleItems = saleItems,
            invoiceSequences = sequences,
            customers = customers,
            ledger = ledger,
            purchases = purchases,
            purchaseItems = purchaseItems,
            stockMovements = stockMovements
        )
    }

    fun parseBackupJson(jsonString: String): Triple<List<ProductItem>, List<SaleTransaction>, ShopSettings?> {
        val parsed = parseFullBackup(jsonString)
        return Triple(parsed.products, parsed.transactions, parsed.settings)
    }

    fun shareBackupFile(context: Context, file: File, title: String = "Share Kirana Backup") {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = if (file.name.endsWith(".csv")) "text/csv" else "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val chooser = Intent.createChooser(intent, title)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (_: Exception) {
            try {
                val text = file.readText()
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                    putExtra(Intent.EXTRA_SUBJECT, file.name)
                }
                context.startActivity(Intent.createChooser(intent, title))
            } catch (ex: Exception) {
                Toast.makeText(context, "Error sharing file: ${ex.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun generateWholesaleReorderList(
        lowStockItems: List<ProductItem>,
        settings: ShopSettings
    ): String {
        val sb = StringBuilder()
        val dateStr = SimpleDateFormat("dd-MM-yyyy", Locale.ENGLISH).format(Date())
        sb.append("📦 *WHOLESALE REORDER LIST - ${settings.shopName.uppercase()}*\n")
        sb.append("📅 Date: $dateStr\n")
        sb.append("📞 Contact: ${settings.phone}\n")
        sb.append("------------------------------------\n")
        sb.append("Please supply the following grocery items:\n\n")

        for ((index, item) in lowStockItems.withIndex()) {
            val needed = (item.minStockAlert * 3 - item.currentStock).coerceAtLeast(item.minStockAlert)
            val current = if (item.currentStock % 1.0 == 0.0) item.currentStock.toInt().toString() else item.currentStock.toString()
            val neededStr = if (needed % 1.0 == 0.0) needed.toInt().toString() else String.format(Locale.ENGLISH, "%.1f", needed)
            sb.append("${index + 1}. *${item.name}* (${item.unit})\n")
            if (item.bengaliName.isNotBlank()) {
                sb.append("   ${item.bengaliName}\n")
            }
            sb.append("   • Current: $current ${item.unit} | *Order Qty: $neededStr ${item.unit}*\n")
        }

        sb.append("\n------------------------------------\n")
        sb.append("Total Items: ${lowStockItems.size}\n")
        sb.append("Urgent delivery required. Thank you!")
        return sb.toString()
    }

    fun exportBackupToJson(
        context: Context,
        products: List<ProductItem>,
        transactions: List<SaleTransaction>,
        settings: ShopSettings?,
        saleItems: List<SaleItemEntity> = emptyList(),
        invoiceSequences: List<InvoiceSequence> = emptyList(),
        customers: List<Customer> = emptyList(),
        ledger: List<LedgerEntry> = emptyList(),
        purchases: List<PurchaseEntity> = emptyList(),
        purchaseItems: List<PurchaseItemEntity> = emptyList(),
        stockMovements: List<StockMovementEntity> = emptyList(),
        password: String? = null
    ): File? {
        val json = buildBackupJsonString(
            products = products,
            transactions = transactions,
            saleItems = saleItems,
            settings = settings,
            invoiceSequences = invoiceSequences,
            customers = customers,
            ledger = ledger,
            purchases = purchases,
            purchaseItems = purchaseItems,
            stockMovements = stockMovements
        )
        val contentToWrite = if (!password.isNullOrBlank()) {
            BackupCrypto.encrypt(json, password)
        } else {
            json
        }
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val fileName = if (!password.isNullOrBlank()) "kirana_backup_enc_$timeStamp.json" else "kirana_backup_$timeStamp.json"
        val dir = File(context.filesDir, "backups").apply { if (!exists()) mkdirs() }
        val file = File(dir, fileName)
        return try {
            file.writeText(contentToWrite, Charsets.UTF_8)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun escapeCsv(field: Any?): String {
        if (field == null) return ""
        val str = field.toString()
        val containsSpecial = str.contains(',') || str.contains('"') || str.contains('\n') || str.contains('\r')
        return if (containsSpecial) {
            "\"" + str.replace("\"", "\"\"") + "\""
        } else {
            str
        }
    }

    fun exportInventoryToCsv(context: Context, products: List<ProductItem>): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val fileName = "kirana_inventory_$timeStamp.csv"
        val dir = File(context.filesDir, "exports").apply { if (!exists()) mkdirs() }
        val file = File(dir, fileName)
        return try {
            val sb = StringBuilder()
            sb.append("ID,Name,Bengali Name,Barcode,Category,Unit,Cost Price,Selling Price,MRP,Current Stock,Min Alert,GST Rate %,Rack Location\n")
            for (p in products) {
                sb.append("${p.id},")
                    .append("${escapeCsv(p.name)},")
                    .append("${escapeCsv(p.bengaliName)},")
                    .append("${escapeCsv(p.barcode)},")
                    .append("${escapeCsv(p.category)},")
                    .append("${escapeCsv(p.unit)},")
                    .append("${p.costPrice},")
                    .append("${p.sellingPrice},")
                    .append("${p.mrp},")
                    .append("${p.currentStock},")
                    .append("${p.minStockAlert},")
                    .append("${p.gstRate},")
                    .append("${escapeCsv(p.rackLocation)}\n")
            }
            file.writeText(sb.toString(), Charsets.UTF_8)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportSalesToCsv(
        context: Context,
        sales: List<SaleTransaction>,
        saleItems: List<SaleItemEntity>
    ): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val fileName = "kirana_sales_$timeStamp.csv"
        val dir = File(context.filesDir, "exports").apply { if (!exists()) mkdirs() }
        val file = File(dir, fileName)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        val itemsByTx = saleItems.groupBy { it.transactionId }
        return try {
            val sb = StringBuilder()
            sb.append("Invoice Number,Date,Customer Name,Customer Phone,Payment Mode,Subtotal,Discount,GST Amount,Grand Total,Status,Item Name,Barcode,Unit,Quantity,Selling Price,Cost Price,MRP,GST Rate %,Line Total,Line Cost,Line Gross Profit\n")
            for (s in sales) {
                val dateStr = dateFormat.format(Date(s.timestamp))
                val items = itemsByTx[s.id] ?: emptyList()
                val status = if (s.isCancelled) "CANCELLED" else "COMPLETED"
                if (items.isEmpty()) {
                    val cost = com.example.billing.ProfitCalculator.parseHistoricalCostFromItemsJson(s.itemsJson)
                    val profit = if (s.isCancelled) 0.0 else com.example.billing.BillingCalculator.roundMoney(s.grandTotal - cost)
                    sb.append("${escapeCsv(s.invoiceNumber)},")
                        .append("${escapeCsv(dateStr)},")
                        .append("${escapeCsv(s.customerName)},")
                        .append("${escapeCsv(s.customerPhone)},")
                        .append("${escapeCsv(s.paymentMode)},")
                        .append("${s.subtotal},")
                        .append("${s.discount},")
                        .append("${s.gstAmount},")
                        .append("${s.grandTotal},")
                        .append("$status,,,,,,,,,")
                        .append("$cost,$profit\n")
                } else {
                    for (item in items) {
                        val lineCost = com.example.billing.BillingCalculator.roundMoney(item.costPrice * item.quantity)
                        val lineProfit = if (s.isCancelled) 0.0 else com.example.billing.BillingCalculator.roundMoney(item.lineTotal - lineCost)
                        sb.append("${escapeCsv(s.invoiceNumber)},")
                            .append("${escapeCsv(dateStr)},")
                            .append("${escapeCsv(s.customerName)},")
                            .append("${escapeCsv(s.customerPhone)},")
                            .append("${escapeCsv(s.paymentMode)},")
                            .append("${s.subtotal},")
                            .append("${s.discount},")
                            .append("${s.gstAmount},")
                            .append("${s.grandTotal},")
                            .append("$status,")
                            .append("${escapeCsv(item.productName)},")
                            .append("${escapeCsv(item.barcode)},")
                            .append("${escapeCsv(item.unit)},")
                            .append("${item.quantity},")
                            .append("${item.sellingPrice},")
                            .append("${item.costPrice},")
                            .append("${item.mrp},")
                            .append("${item.gstRate},")
                            .append("${item.lineTotal},")
                            .append("$lineCost,$lineProfit\n")
                    }
                }
            }
            file.writeText(sb.toString(), Charsets.UTF_8)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportPurchasesToCsv(
        context: Context,
        purchases: List<PurchaseEntity>,
        purchaseItems: List<PurchaseItemEntity> = emptyList()
    ): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val fileName = "kirana_purchases_$timeStamp.csv"
        val dir = File(context.filesDir, "exports").apply { if (!exists()) mkdirs() }
        val file = File(dir, fileName)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        val itemsByPurchase = purchaseItems.groupBy { it.purchaseId }
        return try {
            val sb = StringBuilder()
            sb.append("Purchase Number,Date,Supplier Name,Supplier Phone,Payment Mode,Subtotal,Discount,Tax,Grand Total,Note,Item Name,Barcode,Unit,Quantity,Purchase Rate,GST Rate %,Line Total\n")
            for (p in purchases) {
                val dateStr = dateFormat.format(Date(p.timestamp))
                val items = itemsByPurchase[p.id] ?: emptyList()
                if (items.isEmpty()) {
                    sb.append("${escapeCsv(p.purchaseNumber)},")
                        .append("${escapeCsv(dateStr)},")
                        .append("${escapeCsv(p.supplierName)},")
                        .append("${escapeCsv(p.supplierPhone)},")
                        .append("${escapeCsv(p.paymentMode)},")
                        .append("${p.subtotal},")
                        .append("${p.discount},")
                        .append("${p.tax},")
                        .append("${p.grandTotal},")
                        .append("${escapeCsv(p.note)},,,,,,, \n")
                } else {
                    for (item in items) {
                        sb.append("${escapeCsv(p.purchaseNumber)},")
                            .append("${escapeCsv(dateStr)},")
                            .append("${escapeCsv(p.supplierName)},")
                            .append("${escapeCsv(p.supplierPhone)},")
                            .append("${escapeCsv(p.paymentMode)},")
                            .append("${p.subtotal},")
                            .append("${p.discount},")
                            .append("${p.tax},")
                            .append("${p.grandTotal},")
                            .append("${escapeCsv(p.note)},")
                            .append("${escapeCsv(item.productNameSnapshot)},")
                            .append("${escapeCsv(item.barcodeSnapshot)},")
                            .append("${escapeCsv(item.unit)},")
                            .append("${item.quantity},")
                            .append("${item.purchaseRate},")
                            .append("${item.gstRate},")
                            .append("${item.lineTotal}\n")
                    }
                }
            }
            file.writeText(sb.toString(), Charsets.UTF_8)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportCustomersToCsv(
        context: Context,
        customers: List<Customer>,
        balances: Map<Long, Double> = emptyMap()
    ): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val fileName = "kirana_customers_$timeStamp.csv"
        val dir = File(context.filesDir, "exports").apply { if (!exists()) mkdirs() }
        val file = File(dir, fileName)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        return try {
            val sb = StringBuilder()
            sb.append("Customer ID,Name,Phone,Address,Opening Balance,Current Khata Balance,Status,Created Date\n")
            for (c in customers) {
                val bal = balances[c.id] ?: c.openingBalance
                val dateStr = dateFormat.format(Date(c.createdAt))
                val status = if (c.active) "ACTIVE" else "INACTIVE"
                sb.append("${c.id},")
                    .append("${escapeCsv(c.name)},")
                    .append("${escapeCsv(c.phone)},")
                    .append("${escapeCsv(c.address)},")
                    .append("${c.openingBalance},")
                    .append("$bal,")
                    .append("$status,")
                    .append("${escapeCsv(dateStr)}\n")
            }
            file.writeText(sb.toString(), Charsets.UTF_8)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun exportLedgerToCsv(
        context: Context,
        ledger: List<LedgerEntry>,
        customers: List<Customer> = emptyList()
    ): File? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val fileName = "kirana_ledger_$timeStamp.csv"
        val dir = File(context.filesDir, "exports").apply { if (!exists()) mkdirs() }
        val file = File(dir, fileName)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ENGLISH)
        val custMap = customers.associateBy { it.id }
        return try {
            val sb = StringBuilder()
            sb.append("Entry ID,Date,Customer ID,Customer Name,Customer Phone,Type,Amount,Reference,Note\n")
            for (entry in ledger) {
                val cust = custMap[entry.customerId]
                val dateStr = dateFormat.format(Date(entry.date))
                sb.append("${entry.id},")
                    .append("${escapeCsv(dateStr)},")
                    .append("${entry.customerId},")
                    .append("${escapeCsv(cust?.name ?: "")},")
                    .append("${escapeCsv(cust?.phone ?: "")},")
                    .append("${escapeCsv(entry.type)},")
                    .append("${entry.amount},")
                    .append("${escapeCsv(entry.reference)},")
                    .append("${escapeCsv(entry.note)}\n")
            }
            file.writeText(sb.toString(), Charsets.UTF_8)
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
