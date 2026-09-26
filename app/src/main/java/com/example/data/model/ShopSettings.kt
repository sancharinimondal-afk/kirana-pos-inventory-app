package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "shop_settings")
data class ShopSettings(
    @PrimaryKey val id: Int = 1,
    val shopName: String = "Shree Ganesh Kirana & General Store",
    val tagline: String = "Quality Ration & Daily Essentials at Best Price",
    val ownerName: String = "Rajesh Kumar Gupta",
    val phone: String = "+91 98765 43210",
    val address: String = "Shop No. 12, Main Bazaar, Near Old Clock Tower",
    val city: String = "Delhi - 110006",
    val gstin: String = "07AAAAA0000A1Z5",
    val upiId: String = "shreeganeshkirana@upi",
    val printerPaperWidth: String = "58mm", // 58mm or 80mm
    val receiptFooterNote: String = "Thank You! Visit Again",
    val termsNote: String = "Goods once sold can be returned within 2 days with bill.",
    val lowStockThresholdDefault: Double = 5.0
)
