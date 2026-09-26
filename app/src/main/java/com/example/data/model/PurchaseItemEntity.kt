package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Historical snapshot of an individual line item in a purchase transaction.
 * Foreign-keyed to [PurchaseEntity]. On purchase deletion, cascade deletes line items.
 */
@Entity(
    tableName = "purchase_items",
    foreignKeys = [
        ForeignKey(
            entity = PurchaseEntity::class,
            parentColumns = ["id"],
            childColumns = ["purchaseId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["purchaseId"]),
        Index(value = ["productId"])
    ]
)
data class PurchaseItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val purchaseId: Long,
    val productId: Long,
    val productNameSnapshot: String,
    val barcodeSnapshot: String = "",
    val unit: String = "Piece",
    val quantity: Double,
    val purchaseRate: Double, // Cost price per unit at purchase time
    val gstRate: Double = 0.0,
    val lineDiscount: Double = 0.0,
    val lineTax: Double = 0.0,
    val lineTotal: Double
)
