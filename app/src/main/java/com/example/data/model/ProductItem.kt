package com.example.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "products",
    indices = [Index(value = ["barcode"], unique = true)]
)
data class ProductItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val barcode: String,
    val name: String,
    val bengaliName: String = "",
    val category: String,
    val unit: String = "Piece", // kg, g, Litre, ml, Packet, Piece, Box, Dozen
    val costPrice: Double,      // Buy / Wholesale price in INR (₹)
    val sellingPrice: Double,   // Retail selling price in INR (₹)
    val mrp: Double,            // Maximum Retail Price printed on pack
    val currentStock: Double,   // Available quantity
    val minStockAlert: Double = 5.0, // Alert threshold
    val gstRate: Double = 0.0,  // 0%, 5%, 12%, 18%
    val rackLocation: String = "",
    val isActive: Boolean = true,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    val isOutOfStock: Boolean
        get() = currentStock <= 0.0

    val isLowStock: Boolean
        get() = currentStock > 0.0 && currentStock <= minStockAlert

    val sku: String
        get() = if (barcode.isNotBlank()) barcode else "SKU-${id.toString().padStart(5, '0')}"

    val profitMarginPercent: Double
        get() = if (costPrice > 0) ((sellingPrice - costPrice) / costPrice) * 100 else 0.0

    val profitPerUnit: Double
        get() = sellingPrice - costPrice

    val totalStockValueCost: Double
        get() = currentStock * costPrice

    val totalStockValueMrp: Double
        get() = currentStock * mrp
}
