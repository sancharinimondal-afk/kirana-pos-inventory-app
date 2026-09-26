package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Persisted snapshot of an individual line item associated with a completed sale transaction.
 * Retains historical product name, selling price (rate), purchase/cost price, GST rate,
 * quantity, line discount, and line tax snapshot at the exact time of the sale.
 * Future changes or deletions of products in inventory NEVER alter this historical record.
 */
@Entity(
    tableName = "sale_items",
    foreignKeys = [
        ForeignKey(
            entity = SaleTransaction::class,
            parentColumns = ["id"],
            childColumns = ["transactionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["transactionId"]),
        Index(value = ["productId"])
    ]
)
data class SaleItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val transactionId: Long,
    val productId: Long,
    val barcode: String = "",
    val productName: String,
    val unit: String = "Piece",
    val sellingPrice: Double,    // Price charged per unit (rate)
    val costPrice: Double = 0.0, // Purchase price / cost price per unit at sale time
    val mrp: Double = sellingPrice,
    val gstRate: Double = 0.0,   // GST percentage (e.g. 0.0, 5.0, 12.0, 18.0)
    val quantity: Double,
    val lineDiscount: Double = 0.0, // Direct item-level discount
    val allocatedDiscount: Double = 0.0, // Proportionately allocated bill-level discount
    val lineTax: Double = 0.0,   // Tax amount included in line
    val lineTotal: Double        // Net line total after discounts
) {
    val totalDiscount: Double
        get() = lineDiscount + allocatedDiscount
}
