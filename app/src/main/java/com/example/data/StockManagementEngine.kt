package com.example.data

import androidx.room.withTransaction
import com.example.data.dao.ProductDao
import com.example.data.dao.StockMovementDao
import com.example.data.model.CartItem
import com.example.data.model.ProductItem
import com.example.data.model.PurchaseItemEntity
import com.example.data.model.SaleItemEntity
import com.example.data.model.StockMovementEntity

/**
 * Centralized Stock Management Engine for the Kirana Store.
 *
 * Rules:
 * 1. All inventory stock mutations MUST go through this engine.
 * 2. Stock must never become negative unless allowNegativeStock is true.
 * 3. Product stock is validated fresh from Room inside the database transaction.
 *    Never trust values supplied solely by the UI.
 * 4. Sale: oldStock - soldQuantity = newStock
 * 5. Purchase: oldStock + purchaseQuantity = newStock
 * 6. Cancelled sale: oldStock + cancelledQuantity = newStock
 * 7. Every stock change creates a StockMovement record.
 * 8. Editing product information must NOT silently change stock.
 * 9. Stock adjustment must have an explicit reason.
 * 10. Do not create a stock movement when oldStock == newStock.
 * 11. All stock operations execute atomically inside Room transactions.
 */
class StockManagementEngine(
    private val database: AppDatabase,
    private val productDao: ProductDao,
    private val stockMovementDao: StockMovementDao,
    var allowNegativeStock: Boolean = false
) {

    /**
     * Increase stock manually or via inward stock adjustment.
     * Atomic inside Room transaction.
     */
    suspend fun increaseStock(
        productId: Long,
        quantity: Double,
        referenceNumber: String = "",
        reason: String = "Manual stock increase"
    ): ProductItem {
        require(quantity > 0.0) { "Stock increase quantity must be greater than zero (was $quantity)" }
        require(!quantity.isNaN() && !quantity.isInfinite()) { "Invalid quantity" }

        return database.withTransaction {
            val product = productDao.getProductById(productId)
                ?: throw IllegalArgumentException("Product not found with id: $productId")
            val oldStock = product.currentStock
            val newStock = oldStock + quantity

            // Rule 10: Do not create a stock movement when oldStock == newStock
            if (oldStock == newStock) {
                return@withTransaction product
            }

            val now = System.currentTimeMillis()
            productDao.updateStock(productId, newStock, now)

            stockMovementDao.insertMovement(
                StockMovementEntity(
                    productId = productId,
                    quantity = quantity,
                    oldStock = oldStock,
                    newStock = newStock,
                    operationType = StockMovementEntity.OP_ADJUSTMENT,
                    referenceNumber = referenceNumber.ifBlank { "INC-$productId" },
                    timestamp = now,
                    reason = reason.trim()
                )
            )

            product.copy(currentStock = newStock, lastUpdated = now)
        }
    }

    /**
     * Decrease stock manually or via outward stock reduction.
     * Atomic inside Room transaction.
     */
    suspend fun decreaseStock(
        productId: Long,
        quantity: Double,
        referenceNumber: String = "",
        reason: String = "Manual stock decrease",
        allowNegative: Boolean = allowNegativeStock
    ): ProductItem {
        require(quantity > 0.0) { "Stock decrease quantity must be greater than zero (was $quantity)" }
        require(!quantity.isNaN() && !quantity.isInfinite()) { "Invalid quantity" }

        return database.withTransaction {
            val product = productDao.getProductById(productId)
                ?: throw IllegalArgumentException("Product not found with id: $productId")
            val oldStock = product.currentStock
            val targetStock = oldStock - quantity

            // Rule 1 & 2: Re-validate inside transaction
            if (targetStock < 0.0 && !allowNegative) {
                throw InsufficientStockException(
                    "Insufficient stock for '${product.name}'. Available: $oldStock, Requested deduction: $quantity"
                )
            }

            // Rule 10: Do not create a stock movement when oldStock == newStock
            if (oldStock == targetStock) {
                return@withTransaction product
            }

            val now = System.currentTimeMillis()
            productDao.updateStock(productId, targetStock, now)

            stockMovementDao.insertMovement(
                StockMovementEntity(
                    productId = productId,
                    quantity = quantity,
                    oldStock = oldStock,
                    newStock = targetStock,
                    operationType = StockMovementEntity.OP_ADJUSTMENT,
                    referenceNumber = referenceNumber.ifBlank { "DEC-$productId" },
                    timestamp = now,
                    reason = reason.trim()
                )
            )

            product.copy(currentStock = targetStock, lastUpdated = now)
        }
    }

    /**
     * Explicit stock adjustment to an absolute count with a mandatory reason.
     * Atomic inside Room transaction.
     */
    suspend fun adjustStock(
        productId: Long,
        newStock: Double,
        reason: String,
        referenceNumber: String = "",
        allowNegative: Boolean = allowNegativeStock
    ): ProductItem {
        require(!newStock.isNaN() && !newStock.isInfinite()) { "Stock cannot be NaN or Infinite" }
        // Rule 9: Stock adjustment must have a reason
        require(reason.isNotBlank()) { "Stock adjustment reason must be provided" }

        return database.withTransaction {
            val product = productDao.getProductById(productId)
                ?: throw IllegalArgumentException("Product not found with id: $productId")
            val oldStock = product.currentStock

            // Rule 1 & 2: Re-validate inside transaction
            if (newStock < 0.0 && !allowNegative) {
                throw InsufficientStockException("Cannot set negative stock ($newStock) when negative stock is not allowed")
            }

            // Rule 10: Do not create a stock movement when oldStock == newStock
            if (oldStock == newStock) {
                return@withTransaction product
            }

            val diff = kotlin.math.abs(newStock - oldStock)
            val now = System.currentTimeMillis()
            productDao.updateStock(productId, newStock, now)

            stockMovementDao.insertMovement(
                StockMovementEntity(
                    productId = productId,
                    quantity = diff,
                    oldStock = oldStock,
                    newStock = newStock,
                    operationType = StockMovementEntity.OP_ADJUSTMENT,
                    referenceNumber = referenceNumber.ifBlank { "ADJ-$productId" },
                    timestamp = now,
                    reason = reason.trim()
                )
            )

            product.copy(currentStock = newStock, lastUpdated = now)
        }
    }

    /**
     * Set initial stock during product creation/registration.
     * Atomic inside Room transaction.
     */
    suspend fun setInitialStock(
        productId: Long,
        initialStock: Double,
        allowNegative: Boolean = allowNegativeStock
    ): ProductItem {
        require(!initialStock.isNaN() && !initialStock.isInfinite()) { "Initial stock cannot be NaN or Infinite" }

        return database.withTransaction {
            val product = productDao.getProductById(productId)
                ?: throw IllegalArgumentException("Product not found with id: $productId")
            val oldStock = product.currentStock
            val newStock = initialStock

            // Rule 1 & 2: Re-validate inside transaction
            if (newStock < 0.0 && !allowNegative) {
                throw InsufficientStockException("Initial stock cannot be negative ($initialStock)")
            }

            // Rule 10: Do not create a stock movement when oldStock == newStock
            if (oldStock == newStock && oldStock == 0.0) {
                return@withTransaction product
            }

            val now = System.currentTimeMillis()
            productDao.updateStock(productId, newStock, now)

            stockMovementDao.insertMovement(
                StockMovementEntity(
                    productId = productId,
                    quantity = newStock,
                    oldStock = oldStock,
                    newStock = newStock,
                    operationType = StockMovementEntity.OP_INITIAL,
                    referenceNumber = "INIT-$productId",
                    timestamp = now,
                    reason = "Initial inventory registration"
                )
            )

            product.copy(currentStock = newStock, lastUpdated = now)
        }
    }

    /**
     * Deduct stock for completed retail sales.
     * Rule 4: oldStock - soldQuantity = newStock
     * Re-validates all products atomically inside Room transaction before deducting.
     */
    suspend fun processSaleStock(
        items: List<CartItem>,
        invoiceNumber: String,
        allowNegative: Boolean = allowNegativeStock
    ) {
        if (items.isEmpty()) return

        database.withTransaction {
            // Pass 1: Validation inside transaction against fresh DB records
            for (item in items) {
                val product = productDao.getProductById(item.productId)
                    ?: throw IllegalArgumentException("Product not found with id: ${item.productId}")
                if (!product.isActive) {
                    throw IllegalStateException("Cannot sell inactive product '${product.name}' (ID: ${product.id})")
                }
                val oldStock = product.currentStock
                val newStock = oldStock - item.quantity
                if (newStock < 0.0 && !allowNegative) {
                    throw InsufficientStockException(
                        "Insufficient stock for '${product.name}' during sale. Available: $oldStock, Requested: ${item.quantity}"
                    )
                }
            }

            // Pass 2: Deductions & audit records
            val now = System.currentTimeMillis()
            for (item in items) {
                val product = productDao.getProductById(item.productId)!!
                val oldStock = product.currentStock
                val soldQuantity = item.quantity
                val newStock = oldStock - soldQuantity

                // Rule 10: Do not create movement when oldStock == newStock
                if (oldStock != newStock) {
                    productDao.updateStock(product.id, newStock, now)
                    stockMovementDao.insertMovement(
                        StockMovementEntity(
                            productId = product.id,
                            quantity = soldQuantity,
                            oldStock = oldStock,
                            newStock = newStock,
                            operationType = StockMovementEntity.OP_SALE,
                            referenceNumber = invoiceNumber,
                            timestamp = now,
                            reason = "Sale invoice: $invoiceNumber"
                        )
                    )
                }
            }
        }
    }

    /**
     * Inward stock for wholesale purchases.
     * Rule 5: oldStock + purchaseQuantity = newStock
     * Atomic inside Room transaction.
     */
    suspend fun processPurchaseStock(
        items: List<PurchaseItemEntity>,
        invoiceNumber: String
    ) {
        if (items.isEmpty()) return

        database.withTransaction {
            val now = System.currentTimeMillis()
            for (item in items) {
                if (item.productId <= 0) continue
                val product = productDao.getProductById(item.productId) ?: continue
                val oldStock = product.currentStock
                val purchaseQuantity = item.quantity
                val newStock = oldStock + purchaseQuantity

                // Rule 10: Do not create movement when oldStock == newStock
                if (oldStock != newStock) {
                    productDao.updateStock(product.id, newStock, now)
                    stockMovementDao.insertMovement(
                        StockMovementEntity(
                            productId = product.id,
                            quantity = purchaseQuantity,
                            oldStock = oldStock,
                            newStock = newStock,
                            operationType = StockMovementEntity.OP_PURCHASE,
                            referenceNumber = invoiceNumber,
                            timestamp = now,
                            reason = "Purchase invoice: $invoiceNumber"
                        )
                    )
                }
            }
        }
    }

    /**
     * Restore stock upon sale cancellation.
     * Rule 6: Cancelled sale: oldStock + cancelledQuantity = newStock
     * Atomic inside Room transaction.
     */
    suspend fun reverseSaleStock(
        saleItems: List<SaleItemEntity>,
        invoiceNumber: String,
        reason: String = "Sale cancelled"
    ) {
        if (saleItems.isEmpty()) return

        database.withTransaction {
            val now = System.currentTimeMillis()
            for (item in saleItems) {
                if (item.productId <= 0) continue
                val product = productDao.getProductById(item.productId) ?: continue
                val oldStock = product.currentStock
                val cancelledQuantity = item.quantity
                val newStock = oldStock + cancelledQuantity

                // Rule 10: Do not create movement when oldStock == newStock
                if (oldStock != newStock) {
                    productDao.updateStock(product.id, newStock, now)
                    stockMovementDao.insertMovement(
                        StockMovementEntity(
                            productId = product.id,
                            quantity = cancelledQuantity,
                            oldStock = oldStock,
                            newStock = newStock,
                            operationType = StockMovementEntity.OP_SALE_CANCEL,
                            referenceNumber = invoiceNumber,
                            timestamp = now,
                            reason = "Sale cancelled ($invoiceNumber): ${reason.trim()}"
                        )
                    )
                }
            }
        }
    }

    /**
     * Record customer return into inventory.
     * Atomic inside Room transaction.
     */
    suspend fun processCustomerReturn(
        productId: Long,
        quantity: Double,
        referenceNumber: String,
        reason: String = "Customer return"
    ): ProductItem {
        require(quantity > 0.0) { "Customer return quantity must be greater than zero" }

        return database.withTransaction {
            val product = productDao.getProductById(productId)
                ?: throw IllegalArgumentException("Product not found with id: $productId")
            val oldStock = product.currentStock
            val newStock = oldStock + quantity

            if (oldStock == newStock) {
                return@withTransaction product
            }

            val now = System.currentTimeMillis()
            productDao.updateStock(productId, newStock, now)

            stockMovementDao.insertMovement(
                StockMovementEntity(
                    productId = productId,
                    quantity = quantity,
                    oldStock = oldStock,
                    newStock = newStock,
                    operationType = StockMovementEntity.OP_CUSTOMER_RETURN,
                    referenceNumber = referenceNumber,
                    timestamp = now,
                    reason = reason.trim()
                )
            )

            product.copy(currentStock = newStock, lastUpdated = now)
        }
    }

    /**
     * Record supplier return out of inventory.
     * Atomic inside Room transaction.
     */
    suspend fun processSupplierReturn(
        productId: Long,
        quantity: Double,
        referenceNumber: String,
        reason: String = "Supplier return",
        allowNegative: Boolean = allowNegativeStock
    ): ProductItem {
        require(quantity > 0.0) { "Supplier return quantity must be greater than zero" }

        return database.withTransaction {
            val product = productDao.getProductById(productId)
                ?: throw IllegalArgumentException("Product not found with id: $productId")
            val oldStock = product.currentStock
            val newStock = oldStock - quantity

            if (newStock < 0.0 && !allowNegative) {
                throw InsufficientStockException(
                    "Insufficient stock for return to supplier. Available: $oldStock, Requested: $quantity"
                )
            }

            if (oldStock == newStock) {
                return@withTransaction product
            }

            val now = System.currentTimeMillis()
            productDao.updateStock(productId, newStock, now)

            stockMovementDao.insertMovement(
                StockMovementEntity(
                    productId = productId,
                    quantity = quantity,
                    oldStock = oldStock,
                    newStock = newStock,
                    operationType = StockMovementEntity.OP_SUPPLIER_RETURN,
                    referenceNumber = referenceNumber,
                    timestamp = now,
                    reason = reason.trim()
                )
            )

            product.copy(currentStock = newStock, lastUpdated = now)
        }
    }
}
