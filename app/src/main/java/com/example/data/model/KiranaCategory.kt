package com.example.data.model

import androidx.compose.ui.graphics.Color

data class KiranaCategoryInfo(
    val id: String,
    val nameEn: String,
    val emoji: String,
    val tagColor: Color,
    val tagBgColor: Color
)

object KiranaCategories {
    val ALL_CATEGORIES = listOf(
        KiranaCategoryInfo("Atta & Flour", "Atta & Flour", "🌾", Color(0xFFD97706), Color(0xFFFEF3C7)),
        KiranaCategoryInfo("Rice & Grains", "Rice & Grains", "🍚", Color(0xFF374151), Color(0xFFF3F4F6)),
        KiranaCategoryInfo("Pulses & Dal", "Pulses & Dal", "🫘", Color(0xFFB45309), Color(0xFFFDE68A)),
        KiranaCategoryInfo("Edible Oils & Ghee", "Edible Oils & Ghee", "🛢️", Color(0xFFCA8A04), Color(0xFFFEF9C3)),
        KiranaCategoryInfo("Spices & Salt", "Spices & Salt", "🧂", Color(0xFFEA580C), Color(0xFFFFEDD5)),
        KiranaCategoryInfo("Dairy & Bakery", "Dairy & Bakery", "🥛", Color(0xFF0284C7), Color(0xFFE0F2FE)),
        KiranaCategoryInfo("Snacks & Instant Food", "Snacks & Instant Food", "🍪", Color(0xFFE11D48), Color(0xFFFFE4E6)),
        KiranaCategoryInfo("Beverages & Tea", "Beverages & Tea", "☕", Color(0xFF854D0E), Color(0xFFFEF08A)),
        KiranaCategoryInfo("Household & Cleaning", "Household & Cleaning", "🧼", Color(0xFF0D9488), Color(0xFFCCFBF1)),
        KiranaCategoryInfo("Personal Care", "Personal Care", "🧴", Color(0xFF7C3AED), Color(0xFFEDE9FE)),
        KiranaCategoryInfo("Pooja Essentials", "Pooja Essentials", "🪔", Color(0xFFDC2626), Color(0xFFFEE2E2)),
        KiranaCategoryInfo("General Kirana", "General Kirana", "📦", Color(0xFF475569), Color(0xFFF1F5F9))
    )

    fun getCategoryInfo(categoryName: String): KiranaCategoryInfo {
        val trimmed = categoryName.trim()
        return ALL_CATEGORIES.firstOrNull { 
            it.id.equals(trimmed, ignoreCase = true) || 
            it.nameEn.equals(trimmed, ignoreCase = true) ||
            trimmed.contains(it.nameEn, ignoreCase = true) ||
            it.nameEn.contains(trimmed, ignoreCase = true)
        } ?: KiranaCategoryInfo(
            id = trimmed,
            nameEn = trimmed,
            emoji = "🏷️",
            tagColor = Color(0xFF475569),
            tagBgColor = Color(0xFFF1F5F9)
        )
    }
}
