package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Audit ledger for every change in product stock.
 *
 * Operation types:
 * - "PURCHASE" (+ stock from wholesale purchase)
 * - "SALE" (- stock from retail checkout)
 * - "ADJUSTMENT" (+/- stock from manual physical audit)
 * - "RETURN" (+/- stock from customer return or vendor return)
 * - "INITIAL" (initial stock on product registration)
 */
@Entity(
    tableName = "stock_movements",
    foreignKeys = [
        ForeignKey(
            entity = ProductItem::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["productId"]),
        Index(value = ["timestamp"]),
        Index(value = ["operationType"])
    ]
)
data class StockMovementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val productId: Long,
    val quantity: Double,        // Quantity changed (always positive; direction determined by operationType)
    val oldStock: Double,        // Stock before operation
    val newStock: Double,        // Stock after operation
    val operationType: String,   // PURCHASE, SALE, ADJUSTMENT, RETURN, INITIAL
    val referenceNumber: String, // invoiceNumber, purchaseNumber, auditRef
    val timestamp: Long = System.currentTimeMillis(),
    val reason: String = ""
) {
    companion object {
        const val OP_INITIAL = "INITIAL"
        const val OP_PURCHASE = "PURCHASE"
        const val OP_SALE = "SALE"
        const val OP_SALE_CANCEL = "SALE_CANCEL"
        const val OP_ADJUSTMENT = "ADJUSTMENT"
        const val OP_CUSTOMER_RETURN = "CUSTOMER_RETURN"
        const val OP_SUPPLIER_RETURN = "SUPPLIER_RETURN"

        // Backwards compatibility alias
        const val OP_RETURN = "CUSTOMER_RETURN"

        val ALL_OPERATION_TYPES = listOf(
            OP_INITIAL,
            OP_PURCHASE,
            OP_SALE,
            OP_SALE_CANCEL,
            OP_ADJUSTMENT,
            OP_CUSTOMER_RETURN,
            OP_SUPPLIER_RETURN
        )
    }
}
