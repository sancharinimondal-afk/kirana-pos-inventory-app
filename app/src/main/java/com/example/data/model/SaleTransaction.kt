package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["invoiceNumber"], unique = true),
        Index(value = ["customerId"]),
        Index(value = ["createdAt"]),
        Index(value = ["timestamp"])
    ]
)
data class SaleTransaction(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val invoiceNumber: String,
    val timestamp: Long = System.currentTimeMillis(),
    val customerName: String = "",
    val customerPhone: String = "",
    val paymentMode: String = "CASH", // CASH, UPI, KHATA, CARD
    val subtotal: Double,
    val discount: Double = 0.0,
    val gstAmount: Double = 0.0,
    val grandTotal: Double,
    val itemsJson: String, // Serialized CartItem list for backward compatibility
    val isCancelled: Boolean = false,
    val cancellationReason: String = "",
    val customerId: Long? = null,
    val cashReceived: Double = 0.0,
    val changeDue: Double = 0.0,
    val paymentReference: String = "",
    val createdAt: Long = timestamp,
    val cancelledAt: Long? = null
)

data class CartItem(
    val productId: Long,
    val barcode: String,
    val name: String,
    val unit: String,
    val rate: Double,
    val quantity: Double,
    val mrp: Double,
    val costPrice: Double = 0.0,
    val gstRate: Double = 0.0,
    val lineDiscount: Double = 0.0
) {
    val lineGross: Double
        get() = rate * quantity

    val totalAmount: Double
        get() = (rate * quantity - lineDiscount).coerceAtLeast(0.0)

    val totalSavings: Double
        get() = (if (mrp > rate) (mrp - rate) * quantity else 0.0) + lineDiscount
}
