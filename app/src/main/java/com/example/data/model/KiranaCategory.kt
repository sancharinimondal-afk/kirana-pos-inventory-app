package com.example.data.model

import androidx.compose.ui.graphics.Color

data class KiranaCategoryInfo(
    val id: String,
    val nameEn: String,
    val emoji: String,
    val tagColor: Color,
    val tagBgColor: Color
) {
    fun adaptiveBg(isDark: Boolean): Color {
        return if (isDark) {
            tagColor.copy(alpha = 0.16f)
        } else {
            tagBgColor
        }
    }

    fun adaptiveText(isDark: Boolean): Color {
        return if (isDark) {
            // Brighten slightly in dark theme for crisp contrast
            tagColor
        } else {
            tagColor
        }
    }

    fun adaptiveBorder(isDark: Boolean): Color {
        return if (isDark) {
            tagColor.copy(alpha = 0.35f)
        } else {
            tagColor.copy(alpha = 0.25f)
        }
    }
}

object KiranaCategories {
    val ALL_CATEGORIES = listOf(
        KiranaCategoryInfo("Atta & Flour", "Atta & Flour", "🌾", Color(0xFFF59E0B), Color(0xFFFEF3C7)),
        KiranaCategoryInfo("Rice & Grains", "Rice & Grains", "🍚", Color(0xFF10B981), Color(0xFFD1FAE5)),
        KiranaCategoryInfo("Pulses & Dal", "Pulses & Dal", "🫘", Color(0xFFD97706), Color(0xFFFDE68A)),
        KiranaCategoryInfo("Edible Oils & Ghee", "Edible Oils & Ghee", "🛢️", Color(0xFFEAB308), Color(0xFFFEF9C3)),
        KiranaCategoryInfo("Spices & Salt", "Spices & Salt", "🧂", Color(0xFFF97316), Color(0xFFFFEDD5)),
        KiranaCategoryInfo("Dairy & Bakery", "Dairy & Bakery", "🥛", Color(0xFF0284C7), Color(0xFFE0F2FE)),
        KiranaCategoryInfo("Snacks & Instant Food", "Snacks & Instant Food", "🍪", Color(0xFFF43F5E), Color(0xFFFFE4E6)),
        KiranaCategoryInfo("Beverages & Tea", "Beverages & Tea", "☕", Color(0xFFB45309), Color(0xFFFEF08A)),
        KiranaCategoryInfo("Household & Cleaning", "Household & Cleaning", "🧼", Color(0xFF0D9488), Color(0xFFCCFBF1)),
        KiranaCategoryInfo("Personal Care", "Personal Care", "🧴", Color(0xFF8B5CF6), Color(0xFFEDE9FE)),
        KiranaCategoryInfo("Pooja Essentials", "Pooja Essentials", "🪔", Color(0xFFEF4444), Color(0xFFFEE2E2)),
        KiranaCategoryInfo("General Kirana", "General Kirana", "📦", Color(0xFF6366F1), Color(0xFFEEF2FF))
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
            tagColor = Color(0xFF64748B),
            tagBgColor = Color(0xFFF1F5F9)
        )
    }
}

