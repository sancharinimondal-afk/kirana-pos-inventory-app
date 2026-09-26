package com.example.data

import androidx.room.withTransaction
import com.example.backup.BackupManager
import com.example.backup.ParsedBackupData
import com.example.billing.BillCalculationResult
import com.example.billing.BillingCalculator
import com.example.billing.BillingItemInput
import com.example.billing.DiscountType
import com.example.billing.MrpViolationException
import com.example.billing.PurchaseCalculator
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
import com.example.data.model.CartItem
import com.example.data.model.Customer
import com.example.data.model.CustomerWithBalance
import com.example.data.model.InvoiceSequence
import com.example.data.model.LedgerEntry
import com.example.data.model.ProductItem
import com.example.data.model.PurchaseEntity
import com.example.data.model.PurchaseItemEntity
import com.example.data.model.SaleItemEntity
import com.example.data.model.SaleTransaction
import com.example.data.model.ShopSettings
import com.example.data.model.StockMovementEntity
import kotlinx.coroutines.flow.Flow
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InsufficientStockException(message: String) : IllegalStateException(message)
class DuplicateBarcodeException(message: String) : IllegalArgumentException(message)
class InsufficientCashException(message: String) : IllegalArgumentException(message)
class KhataCustomerRequiredException(message: String) : IllegalArgumentException(message)

/**
 * Line item input for wholesale inventory purchases.
 */
data class PurchaseItemInput(
    val productId: Long = 0L,
    val productName: String,
    val barcode: String = "",
    val unit: String = "Piece",
    val quantity: Double,
    val purchaseRate: Double,
    val gstRate: Double = 0.0,
    val lineDiscount: Double = 0.0,
    val updateProductCostPrice: Boolean = false,
    val sellingPrice: Double? = null,
    val mrp: Double? = null,
    val category: String = "General"
)

/**
 * Result data holder for a completed atomic purchase transaction.
 */
data class CompletedPurchaseResult(
    val purchase: PurchaseEntity,
    val items: List<PurchaseItemEntity>
)

/**
 * Result data holder for a completed atomic checkout transaction.
 */
data class CompletedSaleResult(
    val transaction: SaleTransaction,
    val saleItems: List<SaleItemEntity>,
    val cartItems: List<CartItem>,
    val calculationResult: BillCalculationResult
)

/**
 * Result data holder for an atomic backup restore operation.
 */
data class RestoreResult(
    val success: Boolean,
    val productsRestored: Int,
    val salesRestored: Int,
    val saleItemsRestored: Int,
    val customersRestored: Int,
    val ledgerEntriesRestored: Int,
    val purchasesRestored: Int,
    val purchaseItemsRestored: Int,
    val stockMovementsRestored: Int,
    val sequencesRestored: Int,
    val settingsRestored: Boolean,
    val message: String
)

class KiranaRepository(
    private val database: AppDatabase,
    private val productDao: ProductDao = database.productDao(),
    private val transactionDao: TransactionDao = database.transactionDao(),
    private val shopSettingsDao: ShopSettingsDao = database.shopSettingsDao(),
    private val invoiceSequenceDao: InvoiceSequenceDao = database.invoiceSequenceDao(),
    private val saleItemDao: SaleItemDao = database.saleItemDao(),
    private val customerDao: CustomerDao = database.customerDao(),
    private val ledgerDao: LedgerDao = database.ledgerDao(),
    private val purchaseDao: PurchaseDao = database.purchaseDao(),
    private val purchaseItemDao: PurchaseItemDao = database.purchaseItemDao(),
    private val stockMovementDao: StockMovementDao = database.stockMovementDao()
) {
    // Centralized Stock Management Engine
    val stockEngine: StockManagementEngine = StockManagementEngine(database, productDao, stockMovementDao, false)

    // Explicit setting to allow/disallow negative stock (default: strictly disallow)
    var allowNegativeStock: Boolean = false
        set(value) {
            field = value
            stockEngine.allowNegativeStock = value
        }

    // ==========================================
    // 1. PRODUCTS & INVENTORY
    // ==========================================
    val allProducts: Flow<List<ProductItem>> = productDao.getAllProducts()
    val lowStockProducts: Flow<List<ProductItem>> = productDao.getLowStockProducts()
    val outOfStockProducts: Flow<List<ProductItem>> = productDao.getOutOfStockProducts()
    val productCount: Flow<Int> = productDao.getProductCount()
    val lowStockCount: Flow<Int> = productDao.getLowStockCount()
    val outOfStockCount: Flow<Int> = productDao.getOutOfStockCount()
    val totalCostValue: Flow<Double?> = productDao.getTotalInventoryCostValue()
    val totalMrpValue: Flow<Double?> = productDao.getTotalInventoryMrpValue()

    suspend fun getProductCountDirect(): Int = productDao.getProductCountDirect()
    suspend fun getAllProductsDirect(): List<ProductItem> = productDao.getAllProductsDirect()
    suspend fun getActiveProductsDirect(): List<ProductItem> = productDao.getActiveProductsDirect()
    suspend fun getProductById(id: Long): ProductItem? = productDao.getProductById(id)

    suspend fun getProductByBarcode(barcode: String): ProductItem? {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return null
        return productDao.getProductByBarcode(trimmed)
    }

    fun observeProductByBarcode(barcode: String): Flow<ProductItem?> {
        val trimmed = barcode.trim()
        return productDao.observeProductByBarcode(trimmed)
    }

    suspend fun insertProduct(product: ProductItem): Long {
        require(product.name.isNotBlank()) { "Product name is required" }
        require(!product.costPrice.isNaN() && product.costPrice >= 0.0) { "Cost price cannot be negative (${product.costPrice})" }
        require(!product.sellingPrice.isNaN() && product.sellingPrice > 0.0) { "Selling price must be greater than 0 (${product.sellingPrice})" }
        require(!product.mrp.isNaN() && product.mrp > 0.0) { "MRP must be greater than 0 (${product.mrp})" }
        if (product.sellingPrice > product.mrp) {
            throw MrpViolationException("Selling price (₹${product.sellingPrice}) cannot exceed MRP (₹${product.mrp}) for '${product.name}'. Operation BLOCKED.")
        }
        require(!product.minStockAlert.isNaN() && product.minStockAlert >= 0.0) { "Minimum stock alert cannot be negative (${product.minStockAlert})" }
        require(!product.gstRate.isNaN() && product.gstRate >= 0.0) { "GST rate cannot be negative (${product.gstRate})" }

        if (product.currentStock < 0.0 && !allowNegativeStock) {
            throw InsufficientStockException("Opening stock cannot be negative (${product.currentStock})")
        }

        val cleanBarcode = product.barcode.trim()
        if (cleanBarcode.isNotEmpty()) {
            val allExisting = productDao.getAllProductsDirect()
            val duplicate = allExisting.firstOrNull { 
                it.id != product.id && (it.barcode.equals(cleanBarcode, ignoreCase = true) || it.sku.equals(cleanBarcode, ignoreCase = true))
            }
            if (duplicate != null) {
                throw DuplicateBarcodeException("Barcode/SKU '$cleanBarcode' already exists on '${duplicate.name}'")
            }
        }

        val normalized = product.copy(
            barcode = cleanBarcode,
            name = product.name.trim(),
            bengaliName = product.bengaliName.trim(),
            rackLocation = product.rackLocation.trim(),
            currentStock = 0.0,
            lastUpdated = System.currentTimeMillis()
        )
        val id = productDao.insertProduct(normalized)
        if (product.currentStock != 0.0) {
            stockEngine.setInitialStock(id, product.currentStock, allowNegativeStock)
        }
        return id
    }

    suspend fun insertAllProducts(products: List<ProductItem>) {
        for (product in products) {
            insertProduct(product)
        }
    }

    suspend fun updateProduct(product: ProductItem) {
        require(product.id > 0) { "Product ID must be valid for update" }
        require(product.name.isNotBlank()) { "Product name is required" }
        require(!product.costPrice.isNaN() && product.costPrice >= 0.0) { "Cost price cannot be negative (${product.costPrice})" }
        require(!product.sellingPrice.isNaN() && product.sellingPrice > 0.0) { "Selling price must be greater than 0 (${product.sellingPrice})" }
        require(!product.mrp.isNaN() && product.mrp > 0.0) { "MRP must be greater than 0 (${product.mrp})" }
        if (product.sellingPrice > product.mrp) {
            throw MrpViolationException("Selling price (₹${product.sellingPrice}) cannot exceed MRP (₹${product.mrp}) for '${product.name}'. Operation BLOCKED.")
        }
        require(!product.minStockAlert.isNaN() && product.minStockAlert >= 0.0) { "Minimum stock alert cannot be negative (${product.minStockAlert})" }
        require(!product.gstRate.isNaN() && product.gstRate >= 0.0) { "GST rate cannot be negative (${product.gstRate})" }

        val cleanBarcode = product.barcode.trim()
        if (cleanBarcode.isNotEmpty()) {
            val allExisting = productDao.getAllProductsDirect()
            val duplicate = allExisting.firstOrNull { 
                it.id != product.id && (it.barcode.equals(cleanBarcode, ignoreCase = true) || it.sku.equals(cleanBarcode, ignoreCase = true))
            }
            if (duplicate != null) {
                throw DuplicateBarcodeException("Barcode/SKU '$cleanBarcode' already exists on '${duplicate.name}'")
            }
        }

        val existing = productDao.getProductById(product.id)
            ?: throw IllegalArgumentException("Product not found with id: ${product.id}")

        // Rule 8: Editing product information must NOT silently change stock.
        // Stock changes must explicitly go through the centralized StockManagementEngine.
        val normalized = product.copy(
            barcode = cleanBarcode,
            name = product.name.trim(),
            bengaliName = product.bengaliName.trim(),
            rackLocation = product.rackLocation.trim(),
            currentStock = existing.currentStock,
            isActive = product.isActive,
            lastUpdated = System.currentTimeMillis()
        )
        productDao.updateProduct(normalized)
    }

    suspend fun hasProductHistory(productId: Long): Boolean {
        val saleCount = saleItemDao.getSaleCountForProduct(productId)
        val purchaseCount = purchaseItemDao.getPurchaseCountForProduct(productId)
        return saleCount > 0 || purchaseCount > 0
    }

    suspend fun deactivateProduct(id: Long) = productDao.deactivateProduct(id)
    suspend fun reactivateProduct(id: Long) = productDao.reactivateProduct(id)

    suspend fun deleteProduct(product: ProductItem): Boolean = deleteProductById(product.id)

    suspend fun deleteProductById(id: Long): Boolean {
        return if (hasProductHistory(id)) {
            // NEVER destroy historical records: preserve history with isActive = false
            productDao.deactivateProduct(id)
            true // indicates deactivated
        } else {
            productDao.deleteProductById(id)
            false // indicates physically deleted because no history references it
        }
    }

    suspend fun deleteAllProducts() = productDao.deleteAllProducts()

    // ==========================================
    // 2. CENTRALIZED AUDITED STOCK OPERATIONS
    // ==========================================
    suspend fun recordStockMovement(
        productId: Long,
        quantity: Double,
        oldStock: Double,
        newStock: Double,
        operationType: String,
        referenceNumber: String,
        reason: String = "",
        timestamp: Long = System.currentTimeMillis()
    ): Long {
        require(!quantity.isNaN() && !quantity.isInfinite()) { "Quantity cannot be NaN or Infinite" }
        require(!oldStock.isNaN() && !oldStock.isInfinite()) { "Old stock cannot be NaN or Infinite" }
        require(!newStock.isNaN() && !newStock.isInfinite()) { "New stock cannot be NaN or Infinite" }
        val movement = StockMovementEntity(
            id = 0,
            productId = productId,
            quantity = kotlin.math.abs(quantity),
            oldStock = oldStock,
            newStock = newStock,
            operationType = operationType,
            referenceNumber = referenceNumber,
            timestamp = timestamp,
            reason = reason
        )
        return stockMovementDao.insertMovement(movement)
    }

    val allStockMovements: Flow<List<StockMovementEntity>> = stockMovementDao.getAllMovements()
    fun getProductStockMovements(productId: Long): Flow<List<StockMovementEntity>> = stockMovementDao.getMovementsForProduct(productId)
    suspend fun getAllStockMovementsDirect(): List<StockMovementEntity> = stockMovementDao.getAllMovementsDirect()
    suspend fun getStockMovementsForProductDirect(productId: Long): List<StockMovementEntity> = stockMovementDao.getMovementsForProductDirect(productId)

    suspend fun increaseStock(
        productId: Long,
        quantity: Double,
        referenceNumber: String = "",
        reason: String = "Manual stock increase"
    ): ProductItem = stockEngine.increaseStock(productId, quantity, referenceNumber, reason)

    suspend fun decreaseStock(
        productId: Long,
        quantity: Double,
        referenceNumber: String = "",
        reason: String = "Manual stock decrease",
        allowNegative: Boolean = allowNegativeStock
    ): ProductItem = stockEngine.decreaseStock(productId, quantity, referenceNumber, reason, allowNegative)

    suspend fun adjustStock(
        productId: Long,
        newStock: Double,
        reason: String,
        referenceNumber: String = "",
        allowNegative: Boolean = allowNegativeStock
    ): ProductItem = stockEngine.adjustStock(productId, newStock, reason, referenceNumber, allowNegative)

    suspend fun setInitialStock(
        productId: Long,
        initialStock: Double,
        allowNegative: Boolean = allowNegativeStock
    ): ProductItem = stockEngine.setInitialStock(productId, initialStock, allowNegative)

    suspend fun updateStock(productId: Long, newStock: Double, allowNegative: Boolean = allowNegativeStock): ProductItem {
        return adjustStock(productId, newStock, reason = "Stock level updated", allowNegative = allowNegative)
    }

    suspend fun deductStock(productId: Long, qtySold: Double, allowNegative: Boolean = allowNegativeStock): ProductItem {
        return decreaseStock(productId, qtySold, allowNegative = allowNegative)
    }

    suspend fun processSaleStock(
        items: List<CartItem>,
        invoiceNumber: String,
        allowNegative: Boolean = allowNegativeStock
    ) = stockEngine.processSaleStock(items, invoiceNumber, allowNegative)

    suspend fun processPurchaseStock(
        items: List<PurchaseItemEntity>,
        invoiceNumber: String
    ) = stockEngine.processPurchaseStock(items, invoiceNumber)

    suspend fun reverseSaleStock(
        saleItems: List<SaleItemEntity>,
        invoiceNumber: String,
        reason: String = "Sale cancelled"
    ) = stockEngine.reverseSaleStock(saleItems, invoiceNumber, reason)

    suspend fun recordCustomerReturn(
        productId: Long,
        quantity: Double,
        referenceNumber: String,
        reason: String = "Customer return"
    ): ProductItem = stockEngine.processCustomerReturn(productId, quantity, referenceNumber, reason)

    suspend fun recordSupplierReturn(
        productId: Long,
        quantity: Double,
        referenceNumber: String,
        reason: String = "Supplier return",
        allowNegative: Boolean = allowNegativeStock
    ): ProductItem = stockEngine.processSupplierReturn(productId, quantity, referenceNumber, reason, allowNegative)

    // ==========================================
    // 3. PERSISTENT PURCHASE SYSTEM
    // ==========================================
    val allPurchases: Flow<List<PurchaseEntity>> = purchaseDao.getAllPurchases()
    val totalPurchasesAmount: Flow<Double?> = purchaseDao.getTotalPurchasesAmount()
    val purchaseCount: Flow<Int> = purchaseDao.getPurchaseCount()

    suspend fun getAllPurchasesDirect(): List<PurchaseEntity> = purchaseDao.getAllPurchasesDirect()
    suspend fun getPurchaseById(id: Long): PurchaseEntity? = purchaseDao.getPurchaseById(id)
    suspend fun getPurchaseByNumber(num: String): PurchaseEntity? = purchaseDao.getPurchaseByNumber(num)
    suspend fun getAllPurchaseItemsDirect(): List<PurchaseItemEntity> = purchaseItemDao.getAllPurchaseItemsDirect()
    suspend fun getPurchaseItemsByPurchaseId(purchaseId: Long): List<PurchaseItemEntity> =
        purchaseItemDao.getPurchaseItemsByPurchaseId(purchaseId)
    fun observePurchaseItemsByPurchaseId(purchaseId: Long): Flow<List<PurchaseItemEntity>> =
        purchaseItemDao.observePurchaseItemsByPurchaseId(purchaseId)

    suspend fun getNextPurchaseNumber(year: String? = null): String {
        val y = year ?: SimpleDateFormat("yyyy", Locale.ENGLISH).format(Date())
        val prefix = "PUR-$y"
        val recordedSeq = invoiceSequenceDao.getLastSequence(prefix) ?: 0L
        val nextSeq = recordedSeq + 1L
        invoiceSequenceDao.saveSequence(InvoiceSequence(prefix = prefix, lastSequenceNumber = nextSeq))
        return "PUR-$y-${String.format(Locale.ENGLISH, "%06d", nextSeq)}"
    }

    /**
     * Executes atomic purchase completion.
     *
     * Flow:
     * 1. Validate purchase inputs and items
     * 2. Validate quantity > 0 and rate >= 0
     * 3. Compute item lines, taxes, and grand totals
     * 4. Save PurchaseEntity
     * 5. Save PurchaseItemEntity records
     * 6. Increase inventory stock
     * 7. Optionally update cost price for products
     * 8. Record StockMovementEntity for each item
     * 9. COMMIT
     *
     * If ANY step fails: ROLLBACK EVERYTHING.
     */
    suspend fun completePurchaseTransaction(
        items: List<PurchaseItemInput>,
        supplierName: String,
        supplierPhone: String = "",
        purchaseDate: Long = System.currentTimeMillis(),
        paymentMode: String = "Cash",
        discount: Double = 0.0,
        note: String = "",
        forcedPurchaseNumber: String? = null,
        configurableMarkupPercentage: Double? = null
    ): CompletedPurchaseResult {
        require(supplierName.isNotBlank()) { "Supplier name cannot be blank" }
        require(items.isNotEmpty()) { "Purchase items list cannot be empty" }

        for (item in items) {
            require(item.quantity > 0.0) { "Quantity must be greater than 0 for '${item.productName}'" }
            require(!item.quantity.isNaN() && !item.quantity.isInfinite()) { "Invalid quantity for '${item.productName}'" }
            require(item.purchaseRate >= 0.0) { "Purchase rate cannot be negative for '${item.productName}'" }
            require(!item.purchaseRate.isNaN() && !item.purchaseRate.isInfinite()) { "Invalid purchase rate for '${item.productName}'" }
            require(item.lineDiscount >= 0.0) { "Discount cannot be negative for '${item.productName}'" }
            require(item.gstRate >= 0.0) { "GST rate cannot be negative for '${item.productName}'" }
        }

        return database.withTransaction {
            val purchaseNo = forcedPurchaseNumber ?: getNextPurchaseNumber()

            // Verify purchase number uniqueness
            val existing = purchaseDao.getPurchaseByNumber(purchaseNo)
            if (existing != null) {
                throw IllegalArgumentException("Purchase number '$purchaseNo' already exists")
            }

            // Centralized calculation under GST-Exclusive purchase model
            val calcInputs = items.map {
                PurchaseCalculator.ItemInput(
                    quantity = it.quantity,
                    purchaseRate = it.purchaseRate,
                    gstRate = it.gstRate,
                    lineDiscount = it.lineDiscount
                )
            }
            val calcResult = PurchaseCalculator.calculatePurchase(calcInputs, overallDiscount = discount)

            val purchaseEntity = PurchaseEntity(
                id = 0,
                purchaseNumber = purchaseNo,
                supplierName = supplierName.trim(),
                supplierPhone = supplierPhone.trim(),
                purchaseDate = purchaseDate,
                paymentMode = paymentMode,
                subtotal = calcResult.grossSubtotal,
                discount = calcResult.totalDiscount,
                tax = calcResult.totalGst,
                grandTotal = calcResult.grandTotal,
                note = note.trim(),
                timestamp = System.currentTimeMillis()
            )
            val purchaseId = purchaseDao.insertPurchase(purchaseEntity)
            val persistedPurchase = purchaseEntity.copy(id = purchaseId)

            val markup = configurableMarkupPercentage ?: PurchaseCalculator.defaultMarkupPercentage
            val savedItems = mutableListOf<PurchaseItemEntity>()

            for ((index, item) in items.withIndex()) {
                val itemCalc = calcResult.items[index]

                // Match or locate product in database
                var targetProduct: ProductItem? = null
                if (item.productId > 0) {
                    targetProduct = productDao.getProductById(item.productId)
                }
                if (targetProduct == null && item.barcode.isNotBlank()) {
                    targetProduct = productDao.getProductByBarcode(item.barcode.trim())
                }
                if (targetProduct == null) {
                    // Try exact name match
                    val all = productDao.getAllProductsDirect()
                    targetProduct = all.firstOrNull { it.name.equals(item.productName.trim(), ignoreCase = true) }
                }

                val finalProductId: Long
                if (targetProduct != null) {
                    finalProductId = targetProduct.id
                    val updatedCost = if (item.updateProductCostPrice && item.purchaseRate > 0.0) item.purchaseRate else targetProduct.costPrice
                    val updatedSellingPrice = if (item.sellingPrice != null && item.sellingPrice > 0.0) item.sellingPrice else targetProduct.sellingPrice
                    val updatedMrp = if (item.mrp != null && item.mrp >= updatedSellingPrice) item.mrp else targetProduct.mrp

                    if (updatedCost != targetProduct.costPrice || updatedSellingPrice != targetProduct.sellingPrice || updatedMrp != targetProduct.mrp) {
                        productDao.updateProduct(
                            targetProduct.copy(
                                costPrice = updatedCost,
                                sellingPrice = updatedSellingPrice,
                                mrp = updatedMrp,
                                lastUpdated = System.currentTimeMillis()
                            )
                        )
                    }
                } else {
                    // New product creation: selling price determined via explicit user input OR configurable markup
                    val resolvedSellingPrice = PurchaseCalculator.resolveSellingPrice(
                        costPrice = item.purchaseRate,
                        userSellingPrice = item.sellingPrice,
                        markupPercent = markup
                    )
                    val resolvedMrp = PurchaseCalculator.resolveMrp(
                        sellingPrice = resolvedSellingPrice,
                        userMrp = item.mrp
                    )

                    val newProd = ProductItem(
                        id = 0,
                        barcode = item.barcode.trim(),
                        name = item.productName.trim(),
                        category = item.category.ifBlank { "General" },
                        unit = item.unit.ifBlank { "Piece" },
                        costPrice = item.purchaseRate,
                        sellingPrice = resolvedSellingPrice,
                        mrp = resolvedMrp,
                        currentStock = 0.0,
                        minStockAlert = 5.0,
                        gstRate = item.gstRate,
                        lastUpdated = System.currentTimeMillis()
                    )
                    finalProductId = productDao.insertProduct(newProd)
                }

                val purchaseItemEntity = PurchaseItemEntity(
                    id = 0,
                    purchaseId = purchaseId,
                    productId = finalProductId,
                    productNameSnapshot = item.productName.trim(),
                    barcodeSnapshot = item.barcode.trim(),
                    unit = item.unit,
                    quantity = item.quantity,
                    purchaseRate = item.purchaseRate,
                    gstRate = item.gstRate,
                    lineDiscount = BillingCalculator.roundMoney(item.lineDiscount + itemCalc.allocatedOverallDiscount),
                    lineTax = itemCalc.gstAmount,
                    lineTotal = itemCalc.lineTotal
                )
                val itemId = purchaseItemDao.insertPurchaseItem(purchaseItemEntity)
                savedItems.add(purchaseItemEntity.copy(id = itemId))
            }

            // Inward purchase stock and record stock movements via Centralized Stock Management Engine
            stockEngine.processPurchaseStock(savedItems, purchaseNo)

            CompletedPurchaseResult(
                purchase = persistedPurchase,
                items = savedItems
            )
        }
    }

    suspend fun getPurchaseCountDirect(): Int = purchaseDao.getPurchaseCountDirect()
    suspend fun deleteAllPurchases() = purchaseDao.deleteAllPurchases()
    suspend fun insertLedgerEntry(entry: LedgerEntry): Long = ledgerDao.insertLedgerEntry(entry)

    // ==========================================
    // 4. TRANSACTIONS & SALES / POS LOGIC
    // ==========================================
    val allTransactions: Flow<List<SaleTransaction>> = transactionDao.getAllTransactions()
    fun getTodayTransactions(startOfDay: Long): Flow<List<SaleTransaction>> = transactionDao.getTodayTransactions(startOfDay)
    fun getTodaySalesTotal(startOfDay: Long): Flow<Double?> = transactionDao.getTodaySalesTotal(startOfDay)
    fun getTodaySalesCount(startOfDay: Long): Flow<Int> = transactionDao.getTodaySalesCount(startOfDay)
    val totalLifetimeSales: Flow<Double?> = transactionDao.getTotalLifetimeSales()
    val totalLifetimeTransactionsCount: Flow<Int> = transactionDao.getTotalLifetimeTransactionsCount()
    suspend fun getTransactionCountDirect(): Int = transactionDao.getTransactionCountDirect()
    suspend fun getAllTransactionsDirect(): List<SaleTransaction> = transactionDao.getAllTransactionsDirect()
    suspend fun getTransactionById(id: Long): SaleTransaction? = transactionDao.getTransactionById(id)
    suspend fun getTransactionByInvoiceNumber(inv: String): SaleTransaction? = transactionDao.getTransactionByInvoiceNumber(inv)

    suspend fun insertTransaction(transaction: SaleTransaction): Long = transactionDao.insertTransaction(transaction)
    suspend fun insertAllTransactions(transactions: List<SaleTransaction>) = transactionDao.insertAllTransactions(transactions)
    suspend fun deleteTransactionById(id: Long) = transactionDao.deleteTransactionById(id)
    suspend fun deleteAllTransactions() = transactionDao.deleteAllTransactions()

    suspend fun cancelSaleTransaction(id: Long, reason: String = "Sale cancelled by user"): SaleTransaction? {
        return database.withTransaction {
            // Step 1: Find SaleTransaction
            val tx = transactionDao.getTransactionById(id) ?: return@withTransaction null
            if (tx.isCancelled) return@withTransaction tx
            val cancelTimestamp = System.currentTimeMillis()

            // Step 2: Read stored customerId
            val storedCustomerId = tx.customerId

            // Step 3 & 4: If Khata/Credit sale (or stored customerId present), find original ledger entry & create reversal
            val isKhataSale = tx.paymentMode.equals("KHATA", ignoreCase = true) ||
                    tx.paymentMode.equals("CREDIT", ignoreCase = true) ||
                    (storedCustomerId != null && storedCustomerId > 0L)

            if (isKhataSale && storedCustomerId != null && storedCustomerId > 0L) {
                // Step 3: Find original ledger entry
                val originalEntry = ledgerDao.findCreditSaleEntry(storedCustomerId, tx.invoiceNumber)

                // Step 4: Create reversal
                val reversalAmount = originalEntry?.amount ?: tx.grandTotal
                val reversalEntry = LedgerEntry(
                    id = 0,
                    customerId = storedCustomerId,
                    date = cancelTimestamp,
                    type = LedgerEntry.TYPE_CREDIT_ADJUSTMENT,
                    amount = BillingCalculator.roundMoney(reversalAmount),
                    reference = "CANCEL-${tx.invoiceNumber}",
                    note = "Reversal for cancelled invoice ${tx.invoiceNumber}: ${reason.trim()}"
                )
                ledgerDao.insertLedgerEntry(reversalEntry)
            }

            // Step 5: Restore stock
            val items = saleItemDao.getSaleItemsByTransactionId(id)

            // Step 6: Create SALE_CANCEL stock movement
            stockEngine.reverseSaleStock(items, tx.invoiceNumber, reason)

            // Step 7: Mark sale cancelled
            transactionDao.cancelTransaction(id, reason, cancelTimestamp)

            tx.copy(isCancelled = true, cancellationReason = reason, cancelledAt = cancelTimestamp)
        }
    }

    // Sale Items (Historical Snapshots)
    val allSaleItems: Flow<List<SaleItemEntity>> = saleItemDao.getAllSaleItems()
    suspend fun getAllSaleItemsDirect(): List<SaleItemEntity> = saleItemDao.getAllSaleItemsDirect()
    suspend fun getSaleItemsByTransactionId(txId: Long): List<SaleItemEntity> =
        saleItemDao.getSaleItemsByTransactionId(txId)
    fun observeSaleItemsByTransactionId(txId: Long): Flow<List<SaleItemEntity>> =
        saleItemDao.observeSaleItemsByTransactionId(txId)
    fun observeSaleItemsInPeriod(startTime: Long, endTime: Long): Flow<List<SaleItemEntity>> =
        saleItemDao.observeSaleItemsInPeriod(startTime, endTime)

    // Invoice Sequences
    suspend fun getAllSequencesDirect(): List<InvoiceSequence> = invoiceSequenceDao.getAllSequencesDirect()

    // Shop Settings
    val shopSettings: Flow<ShopSettings?> = shopSettingsDao.getSettings()
    suspend fun getShopSettingsSync(): ShopSettings? = shopSettingsDao.getSettingsSync()
    suspend fun saveShopSettings(settings: ShopSettings) = shopSettingsDao.insertOrUpdate(settings)

    /**
     * Generates a safe, concurrency-proof, yearly sequential invoice number.
     * Format: INV-YYYY-XXXXXX (e.g. INV-2026-000001).
     * Guaranteed to never duplicate or collide even if sequence table was out of sync.
     */
    suspend fun getNextInvoiceNumber(year: String? = null): String {
        val y = year ?: SimpleDateFormat("yyyy", Locale.ENGLISH).format(Date())
        val recordedSeq = invoiceSequenceDao.getLastSequence(y) ?: 0L

        // Double-check against transactions table to prevent collision with historical records
        val pattern = "INV-$y-%"
        val existingInvoices = transactionDao.getInvoiceNumbersMatching(pattern)
        var maxTxSeq = 0L
        for (inv in existingInvoices) {
            val parts = inv.split("-")
            if (parts.size == 3) {
                val num = parts[2].toLongOrNull() ?: 0L
                if (num > maxTxSeq) maxTxSeq = num
            }
        }

        val safeCurrentSeq = maxOf(recordedSeq, maxTxSeq)
        val nextSeq = safeCurrentSeq + 1L
        invoiceSequenceDao.saveSequence(InvoiceSequence(prefix = y, lastSequenceNumber = nextSeq))
        return "INV-$y-${String.format(Locale.ENGLISH, "%06d", nextSeq)}"
    }

    /**
     * Executes atomic sale completion, invoice generation, stock deduction, and Khata recording.
     *
     * All database operations happen inside ONE Room database transaction.
     * Flow:
     * 1. Validate cart (non-empty, positive quantities)
     * 2. Validate stock against current database state
     * 3. Calculate bill via single source of truth BillingCalculator
     * 4. Generate unique invoice number
     * 5. Save SaleTransaction record
     * 6. Save SaleItemEntity historical snapshots
     * 7. Deduct stock & log StockMovementEntity
     * 8. If paymentMode is KHATA: atomically link/create Customer and save LedgerEntry
     * 9. COMMIT
     */
    suspend fun completeSaleTransaction(
        items: List<CartItem>,
        customerName: String = "",
        customerPhone: String = "",
        paymentMode: String = "CASH",
        discountAmount: Double = 0.0,
        forcedInvoiceNumber: String? = null,
        customerId: Long? = null,
        cashReceived: Double? = null,
        changeDue: Double? = null,
        paymentReference: String = ""
    ): CompletedSaleResult {
        if (items.isEmpty()) {
            throw IllegalArgumentException("Cart is empty. Add products before checkout.")
        }
        for (item in items) {
            if (item.quantity <= 0.0 || item.quantity.isNaN() || item.quantity.isInfinite()) {
                throw IllegalArgumentException("Invalid quantity (${item.quantity}) for '${item.name}'")
            }
        }

        // Phase 4: Aggregate duplicate products in the cart before stock validation
        val validatedItems = BillingCalculator.aggregateCartItems(items)

        // Phase 4: Strictly enforce MRP rule: sellingPrice <= MRP (BLOCK operation if violated)
        for (item in validatedItems) {
            if (item.rate > item.mrp) {
                throw MrpViolationException(
                    "Selling price (₹${item.rate}) cannot exceed MRP (₹${item.mrp}) for '${item.name}'. Operation BLOCKED."
                )
            }
        }

        return database.withTransaction {
            // Step 2: Validate stock against aggregated quantities
            for (item in validatedItems) {
                val product = productDao.getProductById(item.productId)
                    ?: throw IllegalArgumentException("Product '${item.name}' no longer exists in inventory")

                if (!product.isActive) {
                    throw IllegalStateException("Product '${product.name}' is inactive and cannot be billed")
                }

                if (product.currentStock < item.quantity && !allowNegativeStock) {
                    throw InsufficientStockException(
                        "Insufficient stock for '${product.name}'. Available: ${product.currentStock} ${product.unit}, Requested: ${item.quantity} ${product.unit}"
                    )
                }
            }

            // Step 3: Calculate totals with central BillingCalculator
            val billInputs = validatedItems.map { item ->
                BillingItemInput(
                    productId = item.productId,
                    barcode = item.barcode,
                    name = item.name,
                    unit = item.unit,
                    quantity = item.quantity,
                    unitPriceInclusive = item.rate,
                    gstRatePercentage = item.gstRate,
                    mrp = item.mrp,
                    lineDiscount = item.lineDiscount
                )
            }
            val discountType = if (discountAmount > 0.0) DiscountType.FixedAmount(discountAmount) else null
            val calculationResult = BillingCalculator.calculateBill(
                items = billInputs,
                discountType = discountType,
                cashReceived = cashReceived ?: 0.0
            )

            // Step 4: Invoice generation & uniqueness check
            val invoiceNo: String
            if (forcedInvoiceNumber != null) {
                val existing = transactionDao.getTransactionByInvoiceNumber(forcedInvoiceNumber)
                if (existing != null) {
                    throw IllegalArgumentException("Invoice number '$forcedInvoiceNumber' already exists")
                }
                invoiceNo = forcedInvoiceNumber
            } else {
                invoiceNo = getNextInvoiceNumber()
            }

            // Step 5: Validate Payment and Customer Rules
            val timeStamp = System.currentTimeMillis()

            // Phase 5 CASH Rule:
            // cashReceived >= grandTotal
            // If cashReceived < grandTotal: BLOCK checkout.
            // If sufficient: changeDue = cashReceived - grandTotal
            // Save: cashReceived, changeDue
            val actualCashReceived: Double
            val actualChangeDue: Double
            if (paymentMode.equals("CASH", ignoreCase = true)) {
                val received = cashReceived ?: calculationResult.grandTotal
                if (received < calculationResult.grandTotal) {
                    throw InsufficientCashException(
                        "Cash received (₹${String.format(Locale.ENGLISH, "%.2f", received)}) is less than grand total (₹${String.format(Locale.ENGLISH, "%.2f", calculationResult.grandTotal)}). Checkout BLOCKED."
                    )
                }
                actualCashReceived = received
                actualChangeDue = BillingCalculator.roundMoney(received - calculationResult.grandTotal)
            } else {
                actualCashReceived = cashReceived ?: 0.0
                actualChangeDue = 0.0
            }

            // Phase 5 KHATA Rule:
            // Require exact customerId.
            // Do not identify Khata customers only by name or phone.
            // A Khata sale must store customerId in SaleTransaction.
            val targetCustomerId: Long?
            val cleanCustomerName: String
            val cleanCustomerPhone: String

            if (paymentMode.equals("KHATA", ignoreCase = true)) {
                if (customerId == null || customerId <= 0) {
                    throw KhataCustomerRequiredException(
                        "Khata sale requires an exact registered customer ID. Khata customers cannot be identified only by name or phone. Checkout BLOCKED."
                    )
                }
                val registeredCustomer = customerDao.getCustomerById(customerId)
                    ?: throw KhataCustomerRequiredException(
                        "Customer with ID $customerId does not exist. Cannot record Khata sale. Checkout BLOCKED."
                    )
                if (!registeredCustomer.active) {
                    throw IllegalStateException("Customer '${registeredCustomer.name}' is inactive and cannot be billed on Khata credit.")
                }
                targetCustomerId = registeredCustomer.id
                cleanCustomerName = registeredCustomer.name
                cleanCustomerPhone = registeredCustomer.phone
            } else {
                targetCustomerId = if (customerId != null && customerId > 0) customerId else null
                cleanCustomerName = customerName.trim()
                cleanCustomerPhone = customerPhone.trim()
            }

            val jsonArray = JSONArray()
            for (item in validatedItems) {
                val obj = org.json.JSONObject().apply {
                    put("productId", item.productId)
                    put("barcode", item.barcode)
                    put("name", item.name)
                    put("unit", item.unit)
                    put("rate", item.rate)
                    put("quantity", item.quantity)
                    put("mrp", item.mrp)
                    put("costPrice", item.costPrice)
                    put("gstRate", item.gstRate)
                    put("lineDiscount", item.lineDiscount)
                }
                jsonArray.put(obj)
            }

            val saleTx = SaleTransaction(
                id = 0,
                invoiceNumber = invoiceNo,
                timestamp = timeStamp,
                customerName = cleanCustomerName,
                customerPhone = cleanCustomerPhone,
                paymentMode = paymentMode.uppercase(),
                subtotal = calculationResult.subtotal,
                discount = calculationResult.discountApplied,
                gstAmount = calculationResult.gstAmount,
                grandTotal = calculationResult.grandTotal,
                itemsJson = jsonArray.toString(),
                isCancelled = false,
                cancellationReason = "",
                customerId = targetCustomerId,
                cashReceived = actualCashReceived,
                changeDue = actualChangeDue,
                paymentReference = paymentReference.trim(),
                createdAt = timeStamp,
                cancelledAt = null
            )
            val txId = transactionDao.insertTransaction(saleTx)
            val persistedTx = saleTx.copy(id = txId)

            // Step 6: Save historical sale items snapshots with discount information
            val saleEntities = mutableListOf<SaleItemEntity>()
            for (lineItem in calculationResult.items) {
                val cartItem = validatedItems.first { it.productId == lineItem.productId }
                val freshProduct = productDao.getProductById(cartItem.productId)
                val snapshotCostPrice = freshProduct?.costPrice ?: cartItem.costPrice

                val entity = SaleItemEntity(
                    transactionId = txId,
                    productId = cartItem.productId,
                    barcode = lineItem.barcode.ifBlank { cartItem.barcode },
                    productName = lineItem.name.ifBlank { cartItem.name },
                    unit = lineItem.unit.ifBlank { cartItem.unit },
                    sellingPrice = lineItem.unitPriceInclusive,
                    costPrice = snapshotCostPrice,
                    mrp = lineItem.mrp,
                    gstRate = lineItem.gstRatePercentage,
                    quantity = lineItem.quantity,
                    lineDiscount = lineItem.lineDiscount,
                    allocatedDiscount = lineItem.allocatedBillDiscount,
                    lineTax = lineItem.gstAmount,
                    lineTotal = lineItem.lineNetTotal
                )
                val itemId = saleItemDao.insertSaleItem(entity)
                saleEntities.add(entity.copy(id = itemId))
            }

            // Step 7: Deduct stock & log stock movements via Centralized Stock Management Engine
            stockEngine.processSaleStock(validatedItems, invoiceNo, allowNegativeStock)

            // Step 8: Atomic Khata Entry if payment mode is KHATA
            if (paymentMode.equals("KHATA", ignoreCase = true) && targetCustomerId != null) {
                val ledgerEntry = LedgerEntry(
                    id = 0,
                    customerId = targetCustomerId,
                    date = timeStamp,
                    type = LedgerEntry.TYPE_CREDIT_SALE,
                    amount = calculationResult.grandTotal,
                    reference = invoiceNo,
                    note = "Credit sale (${validatedItems.size} items)"
                )
                ledgerDao.insertLedgerEntry(ledgerEntry)
            }

            CompletedSaleResult(
                transaction = persistedTx,
                saleItems = saleEntities,
                cartItems = items,
                calculationResult = calculationResult
            )
        }
    }

    // ==========================================
    // 5. CUSTOMER & KHATA (LEDGER) ARCHITECTURE
    // ==========================================
    val allCustomers: Flow<List<Customer>> = customerDao.getAllCustomers()
    val allCustomersWithBalance: Flow<List<CustomerWithBalance>> = customerDao.getAllCustomersWithBalance()
    val totalKhataOutstanding: Flow<Double> = ledgerDao.getTotalKhataOutstanding()

    suspend fun getAllCustomersDirect(): List<Customer> = customerDao.getAllCustomersDirect()
    suspend fun getAllCustomersWithBalanceDirect(): List<CustomerWithBalance> = customerDao.getAllCustomersWithBalanceDirect()
    suspend fun getCustomerById(id: Long): Customer? = customerDao.getCustomerById(id)
    suspend fun getCustomerByPhone(phone: String): Customer? = customerDao.getCustomerByPhone(phone)
    fun getCustomerWithBalance(id: Long): Flow<CustomerWithBalance?> = customerDao.getCustomerWithBalance(id)
    suspend fun getCustomerWithBalanceDirect(id: Long): CustomerWithBalance? = customerDao.getCustomerWithBalanceDirect(id)

    /**
     * Inserts a customer and creates exactly ONE OPENING_BALANCE ledger entry if openingBalance > 0.
     * Prevents duplicate opening balance entries.
     */
    suspend fun insertCustomer(customer: Customer): Long {
        require(customer.name.isNotBlank()) { "Customer name cannot be blank" }
        val cleanPhone = customer.phone.trim()
        if (cleanPhone.isNotEmpty()) {
            val existing = customerDao.getCustomerByPhone(cleanPhone)
            if (existing != null && existing.id != customer.id) {
                throw IllegalArgumentException("Customer with phone '$cleanPhone' already exists (${existing.name})")
            }
        }
        return database.withTransaction {
            val id = customerDao.insertCustomer(customer.copy(phone = cleanPhone, name = customer.name.trim()))
            if (customer.openingBalance > 0.0) {
                // Prevent duplicate opening entries
                val existingOpening = ledgerDao.getOpeningBalanceEntry(id)
                if (existingOpening == null) {
                    val entry = LedgerEntry(
                        id = 0,
                        customerId = id,
                        date = System.currentTimeMillis(),
                        type = LedgerEntry.TYPE_OPENING_BALANCE,
                        amount = BillingCalculator.roundMoney(customer.openingBalance),
                        reference = "OPENING",
                        note = "Initial opening balance"
                    )
                    ledgerDao.insertLedgerEntry(entry)
                }
            }
            id
        }
    }

    /**
     * Creates an OPENING_BALANCE ledger entry only if one does not already exist.
     * Prevents duplicate opening entries.
     */
    suspend fun createOpeningBalanceIfNotExists(customerId: Long, amount: Double): LedgerEntry? {
        if (amount <= 0.0) return null
        return database.withTransaction {
            val existing = ledgerDao.getOpeningBalanceEntry(customerId)
            if (existing != null) {
                return@withTransaction existing // Duplicate prevented
            }
            val entry = LedgerEntry(
                id = 0,
                customerId = customerId,
                date = System.currentTimeMillis(),
                type = LedgerEntry.TYPE_OPENING_BALANCE,
                amount = BillingCalculator.roundMoney(amount),
                reference = "OPENING",
                note = "Initial opening balance"
            )
            val id = ledgerDao.insertLedgerEntry(entry)
            entry.copy(id = id)
        }
    }

    /**
     * Sets or updates customer opening balance, maintaining exactly one OPENING_BALANCE entry.
     */
    suspend fun setCustomerOpeningBalance(customerId: Long, amount: Double): LedgerEntry? {
        require(amount >= 0.0) { "Opening balance cannot be negative" }
        val customer = customerDao.getCustomerById(customerId)
            ?: throw IllegalArgumentException("Customer not found with id: $customerId")
        return database.withTransaction {
            val existing = ledgerDao.getOpeningBalanceEntry(customerId)
            if (existing != null) {
                val updated = existing.copy(amount = BillingCalculator.roundMoney(amount))
                ledgerDao.insertLedgerEntry(updated)
                customerDao.updateCustomer(customer.copy(openingBalance = amount))
                updated
            } else if (amount > 0.0) {
                val entry = LedgerEntry(
                    id = 0,
                    customerId = customerId,
                    date = System.currentTimeMillis(),
                    type = LedgerEntry.TYPE_OPENING_BALANCE,
                    amount = BillingCalculator.roundMoney(amount),
                    reference = "OPENING",
                    note = "Initial opening balance"
                )
                val id = ledgerDao.insertLedgerEntry(entry)
                customerDao.updateCustomer(customer.copy(openingBalance = amount))
                entry.copy(id = id)
            } else {
                customerDao.updateCustomer(customer.copy(openingBalance = 0.0))
                null
            }
        }
    }

    suspend fun updateCustomer(customer: Customer) {
        require(customer.id > 0) { "Customer ID must be valid" }
        require(customer.name.isNotBlank()) { "Customer name cannot be blank" }
        customerDao.updateCustomer(customer.copy(updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteCustomer(id: Long) = customerDao.deleteCustomerById(id)
    suspend fun deleteAllCustomers() = customerDao.deleteAllCustomers()
    suspend fun getCustomerCountDirect(): Int = customerDao.getCustomerCountDirect()

    fun getCustomerBalance(customerId: Long): Flow<Double> = ledgerDao.getCustomerBalance(customerId)
    suspend fun getCustomerBalanceDirect(customerId: Long): Double = ledgerDao.getCustomerBalanceDirect(customerId)
    fun getCustomerEntries(customerId: Long): Flow<List<LedgerEntry>> = ledgerDao.getEntriesByCustomerId(customerId)
    suspend fun getAllLedgerEntriesDirect(): List<LedgerEntry> = ledgerDao.getAllLedgerEntriesDirect()

    suspend fun addKhataPayment(
        customerId: Long,
        amount: Double,
        reference: String = "",
        note: String = "Payment received"
    ): LedgerEntry {
        require(amount > 0.0) { "Payment amount must be greater than zero (was $amount)" }
        val customer = customerDao.getCustomerById(customerId)
            ?: throw IllegalArgumentException("Customer not found with id: $customerId")
        val entry = LedgerEntry(
            id = 0,
            customerId = customer.id,
            date = System.currentTimeMillis(),
            type = LedgerEntry.TYPE_PAYMENT_RECEIVED,
            amount = BillingCalculator.roundMoney(amount),
            reference = reference.trim(),
            note = note.trim()
        )
        val entryId = ledgerDao.insertLedgerEntry(entry)
        return entry.copy(id = entryId)
    }

    suspend fun addKhataAdjustment(
        customerId: Long,
        amount: Double,
        isDebit: Boolean,
        reference: String = "",
        note: String = ""
    ): LedgerEntry {
        require(amount > 0.0) { "Adjustment amount must be greater than zero (was $amount)" }
        val customer = customerDao.getCustomerById(customerId)
            ?: throw IllegalArgumentException("Customer not found with id: $customerId")
        val type = if (isDebit) LedgerEntry.TYPE_DEBIT_ADJUSTMENT else LedgerEntry.TYPE_CREDIT_ADJUSTMENT
        val entry = LedgerEntry(
            id = 0,
            customerId = customer.id,
            date = System.currentTimeMillis(),
            type = type,
            amount = BillingCalculator.roundMoney(amount),
            reference = reference.trim(),
            note = note.trim()
        )
        val entryId = ledgerDao.insertLedgerEntry(entry)
        return entry.copy(id = entryId)
    }

    // ==========================================
    // 6. ATOMIC FULL BACKUP RESTORE
    // ==========================================
    suspend fun restoreFullBackup(data: ParsedBackupData): RestoreResult {
        return database.withTransaction {
            // Step 1: Clear existing tables in referential integrity order
            stockMovementDao.deleteAllMovements()
            purchaseItemDao.deleteAllPurchaseItems()
            purchaseDao.deleteAllPurchases()
            ledgerDao.deleteAllLedgerEntries()
            customerDao.deleteAllCustomers()
            saleItemDao.deleteAllSaleItems()
            transactionDao.deleteAllTransactions()
            productDao.deleteAllProducts()
            invoiceSequenceDao.deleteAllSequences()

            // Step 2: Restore products while preserving IDs and recording mappings
            val oldToNewProductIdMap = mutableMapOf<Long, Long>()
            for (product in data.products) {
                val oldId = product.id
                val targetProduct = if (oldId > 0) product else product.copy(id = 0)
                val newId = try {
                    productDao.insertProduct(targetProduct)
                } catch (e: Exception) {
                    productDao.insertProduct(targetProduct.copy(id = 0))
                }
                if (oldId > 0) {
                    oldToNewProductIdMap[oldId] = newId
                } else {
                    oldToNewProductIdMap[newId] = newId
                }
            }

            // Step 3: Restore sales transactions
            val oldToNewTxIdMap = mutableMapOf<Long, Long>()
            for (tx in data.transactions) {
                val oldTxId = tx.id
                val updatedItemsJson = updateItemsJsonProductIds(tx.itemsJson, oldToNewProductIdMap)
                val targetTx = (if (oldTxId > 0) tx else tx.copy(id = 0)).copy(itemsJson = updatedItemsJson)
                val newTxId = try {
                    transactionDao.insertTransaction(targetTx)
                } catch (e: Exception) {
                    transactionDao.insertTransaction(targetTx.copy(id = 0))
                }
                if (oldTxId > 0) {
                    oldToNewTxIdMap[oldTxId] = newTxId
                } else {
                    oldToNewTxIdMap[newTxId] = newTxId
                }
            }

            // Step 4: Restore sale items
            var saleItemsCount = 0
            if (data.saleItems.isNotEmpty()) {
                for (item in data.saleItems) {
                    val mappedTxId = oldToNewTxIdMap[item.transactionId] ?: item.transactionId
                    val mappedProductId = oldToNewProductIdMap[item.productId] ?: item.productId
                    val targetItem = (if (item.id > 0) item else item.copy(id = 0)).copy(
                        transactionId = mappedTxId,
                        productId = mappedProductId
                    )
                    try {
                        saleItemDao.insertSaleItem(targetItem)
                    } catch (e: Exception) {
                        saleItemDao.insertSaleItem(targetItem.copy(id = 0))
                    }
                    saleItemsCount++
                }
            } else {
                for (tx in data.transactions) {
                    val newTxId = oldToNewTxIdMap[tx.id] ?: tx.id
                    val reconstructed = reconstructSaleItemsFromItemsJson(tx.itemsJson, newTxId, oldToNewProductIdMap)
                    for (item in reconstructed) {
                        saleItemDao.insertSaleItem(item)
                        saleItemsCount++
                    }
                }
            }

            // Step 5: Restore customers & mappings
            val oldToNewCustomerIdMap = mutableMapOf<Long, Long>()
            for (cust in data.customers) {
                val oldId = cust.id
                val targetCust = if (oldId > 0) cust else cust.copy(id = 0)
                val newId = try {
                    customerDao.insertCustomer(targetCust)
                } catch (e: Exception) {
                    customerDao.insertCustomer(targetCust.copy(id = 0))
                }
                if (oldId > 0) {
                    oldToNewCustomerIdMap[oldId] = newId
                } else {
                    oldToNewCustomerIdMap[newId] = newId
                }
            }

            // Step 6: Restore ledger entries
            for (ledgerEntry in data.ledger) {
                val mappedCustId = oldToNewCustomerIdMap[ledgerEntry.customerId] ?: ledgerEntry.customerId
                if (mappedCustId > 0) {
                    val targetEntry = (if (ledgerEntry.id > 0) ledgerEntry else ledgerEntry.copy(id = 0))
                        .copy(customerId = mappedCustId)
                    try {
                        ledgerDao.insertLedgerEntry(targetEntry)
                    } catch (e: Exception) {
                        ledgerDao.insertLedgerEntry(targetEntry.copy(id = 0))
                    }
                }
            }

            // Step 7: Restore purchases & purchase items
            val oldToNewPurchaseIdMap = mutableMapOf<Long, Long>()
            for (pur in data.purchases) {
                val oldId = pur.id
                val targetPur = if (oldId > 0) pur else pur.copy(id = 0)
                val newId = try {
                    purchaseDao.insertPurchase(targetPur)
                } catch (e: Exception) {
                    purchaseDao.insertPurchase(targetPur.copy(id = 0))
                }
                if (oldId > 0) {
                    oldToNewPurchaseIdMap[oldId] = newId
                } else {
                    oldToNewPurchaseIdMap[newId] = newId
                }
            }

            for (pi in data.purchaseItems) {
                val mappedPurId = oldToNewPurchaseIdMap[pi.purchaseId] ?: pi.purchaseId
                val mappedProdId = oldToNewProductIdMap[pi.productId] ?: pi.productId
                if (mappedPurId > 0) {
                    val targetPi = (if (pi.id > 0) pi else pi.copy(id = 0)).copy(
                        purchaseId = mappedPurId,
                        productId = mappedProdId
                    )
                    try {
                        purchaseItemDao.insertPurchaseItem(targetPi)
                    } catch (e: Exception) {
                        purchaseItemDao.insertPurchaseItem(targetPi.copy(id = 0))
                    }
                }
            }

            // Step 8: Restore stock movements
            for (sm in data.stockMovements) {
                val mappedProdId = oldToNewProductIdMap[sm.productId] ?: sm.productId
                if (mappedProdId > 0) {
                    val targetSm = (if (sm.id > 0) sm else sm.copy(id = 0)).copy(productId = mappedProdId)
                    try {
                        stockMovementDao.insertMovement(targetSm)
                    } catch (e: Exception) {
                        stockMovementDao.insertMovement(targetSm.copy(id = 0))
                    }
                }
            }

            // Step 9: Restore invoice sequences
            var seqCount = 0
            if (data.invoiceSequences.isNotEmpty()) {
                for (seq in data.invoiceSequences) {
                    invoiceSequenceDao.saveSequence(seq)
                    seqCount++
                }
            } else {
                val derivedSequences = deriveInvoiceSequences(data.transactions)
                for (seq in derivedSequences) {
                    invoiceSequenceDao.saveSequence(seq)
                    seqCount++
                }
                seqCount = derivedSequences.size
            }

            // Step 10: Restore shop settings
            if (data.settings != null) {
                shopSettingsDao.insertOrUpdate(data.settings.copy(id = 1))
            }

            // Step 11: Post-restore verification
            val finalProductCount = productDao.getProductCountDirect()
            if (finalProductCount != data.products.size) {
                throw IllegalStateException("Post-restore verification failed: expected ${data.products.size} products but found $finalProductCount")
            }
            val finalTxCount = transactionDao.getTransactionCountDirect()
            if (finalTxCount != data.transactions.size) {
                throw IllegalStateException("Post-restore verification failed: expected ${data.transactions.size} sales but found $finalTxCount")
            }
            val finalCustCount = customerDao.getCustomerCountDirect()
            if (finalCustCount != data.customers.size) {
                throw IllegalStateException("Post-restore verification failed: expected ${data.customers.size} customers but found $finalCustCount")
            }
            val finalPurchaseCount = purchaseDao.getPurchaseCountDirect()
            if (finalPurchaseCount != data.purchases.size) {
                throw IllegalStateException("Post-restore verification failed: expected ${data.purchases.size} purchases but found $finalPurchaseCount")
            }

            RestoreResult(
                success = true,
                productsRestored = data.products.size,
                salesRestored = data.transactions.size,
                saleItemsRestored = saleItemsCount,
                customersRestored = data.customers.size,
                ledgerEntriesRestored = data.ledger.size,
                purchasesRestored = data.purchases.size,
                purchaseItemsRestored = data.purchaseItems.size,
                stockMovementsRestored = data.stockMovements.size,
                sequencesRestored = seqCount,
                settingsRestored = data.settings != null,
                message = "Successfully restored database state (${data.products.size} products, ${data.transactions.size} sales, ${data.purchases.size} purchases)"
            )
        }
    }

    suspend fun restoreFromBackupJson(jsonString: String, password: String? = null): RestoreResult {
        val plainJson = if (com.example.backup.BackupCrypto.isEncrypted(jsonString)) {
            require(!password.isNullOrBlank()) { "Password is required to restore an encrypted backup" }
            try {
                com.example.backup.BackupCrypto.decrypt(jsonString, password)
            } catch (e: Exception) {
                throw IllegalArgumentException("Decryption failed: Incorrect password or corrupted encrypted backup")
            }
        } else {
            jsonString
        }
        val validation = BackupManager.validateBackup(plainJson)
        if (!validation.isValid || validation.parsedData == null) {
            throw IllegalArgumentException("Restore validation failed: ${validation.errorMessage ?: "Unknown validation error"}")
        }
        return restoreFullBackup(validation.parsedData)
    }

    private fun updateItemsJsonProductIds(itemsJson: String, map: Map<Long, Long>): String {
        if (itemsJson.isBlank() || itemsJson == "[]" || map.isEmpty()) return itemsJson
        return try {
            val array = JSONArray(itemsJson)
            val updated = JSONArray()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val oldPid = obj.optLong("productId", 0L)
                if (oldPid > 0 && map.containsKey(oldPid)) {
                    obj.put("productId", map[oldPid]!!)
                }
                updated.put(obj)
            }
            updated.toString()
        } catch (e: Exception) {
            itemsJson
        }
    }

    private fun reconstructSaleItemsFromItemsJson(
        itemsJson: String,
        txId: Long,
        productMap: Map<Long, Long>
    ): List<SaleItemEntity> {
        if (itemsJson.isBlank() || itemsJson == "[]") return emptyList()
        val list = mutableListOf<SaleItemEntity>()
        try {
            val array = JSONArray(itemsJson)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val oldPid = obj.optLong("productId", 0L)
                val mappedPid = productMap[oldPid] ?: oldPid
                val rate = obj.optDouble("rate", 0.0)
                val qty = obj.optDouble("quantity", 1.0)
                list.add(
                    SaleItemEntity(
                        id = 0,
                        transactionId = txId,
                        productId = mappedPid,
                        barcode = obj.optString("barcode", ""),
                        productName = obj.optString("name", "Item"),
                        unit = obj.optString("unit", "Piece"),
                        sellingPrice = rate,
                        costPrice = obj.optDouble("costPrice", 0.0),
                        mrp = obj.optDouble("mrp", rate),
                        gstRate = obj.optDouble("gstRate", 0.0),
                        quantity = qty,
                        lineDiscount = 0.0,
                        lineTax = 0.0,
                        lineTotal = rate * qty
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }

    private fun deriveInvoiceSequences(transactions: List<SaleTransaction>): List<InvoiceSequence> {
        val maxMap = mutableMapOf<String, Long>()
        val regex = Regex("INV-(\\d{4})-(\\d+)")
        for (tx in transactions) {
            val match = regex.find(tx.invoiceNumber)
            if (match != null) {
                val prefix = match.groupValues[1]
                val seq = match.groupValues[2].toLongOrNull() ?: 0L
                val current = maxMap[prefix] ?: 0L
                if (seq > current) maxMap[prefix] = seq
            }
        }
        return maxMap.map { InvoiceSequence(prefix = it.key, lastSequenceNumber = it.value) }
    }
}
