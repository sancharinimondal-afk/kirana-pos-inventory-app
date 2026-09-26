package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a persistent purchase record from a wholesale vendor/supplier.
 * All purchase transactions must be saved atomically with their [PurchaseItemEntity] records.
 */
@Entity(
    tableName = "purchases",
    indices = [
        Index(value = ["purchaseNumber"], unique = true),
        Index(value = ["purchaseDate"]),
        Index(value = ["supplierName"])
    ]
)
data class PurchaseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val purchaseNumber: String,
    val supplierName: String,
    val supplierPhone: String = "",
    val purchaseDate: Long = System.currentTimeMillis(),
    val paymentMode: String = "Cash", // Cash, UPI, Bank Transfer, Credit
    val subtotal: Double,
    val discount: Double = 0.0,
    val tax: Double = 0.0,
    val grandTotal: Double,
    val note: String = "",
    val timestamp: Long = System.currentTimeMillis()
)
