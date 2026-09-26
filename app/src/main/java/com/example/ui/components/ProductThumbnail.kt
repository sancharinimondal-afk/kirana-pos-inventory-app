package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fastfood
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalGroceryStore
import androidx.compose.material.icons.filled.OilBarrel
import androidx.compose.material.icons.filled.RiceBowl
import androidx.compose.material.icons.filled.Sanitizer
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProductThumbnail(
    name: String,
    category: String,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp
) {
    val lower = name.lowercase()
    val (bgColor, emoji, iconColor) = when {
        lower.contains("rice") || lower.contains("chal") -> Triple(Color(0xFFFFFBEB), "🍚", Color(0xFFD97706))
        lower.contains("atta") || lower.contains("flour") || lower.contains("wheat") -> Triple(Color(0xFFFEF3C7), "🌾", Color(0xFFB45309))
        lower.contains("sugar") || lower.contains("chini") -> Triple(Color(0xFFF1F5F9), "🧂", Color(0xFF475569))
        lower.contains("tea") || lower.contains("chai") || lower.contains("coffee") -> Triple(Color(0xFFDCFCE7), "🍵", Color(0xFF15803D))
        lower.contains("oil") || lower.contains("mustard") || lower.contains("ghee") -> Triple(Color(0xFFFEF9C3), "🌻", Color(0xFFCA8A04))
        lower.contains("maggi") || lower.contains("noodle") -> Triple(Color(0xFFFEE2E2), "🍜", Color(0xFFDC2626))
        lower.contains("biscuit") || lower.contains("cookie") || lower.contains("parle") -> Triple(Color(0xFFFFEDD5), "🍪", Color(0xFFC2410C))
        lower.contains("drink") || lower.contains("beverage") || lower.contains("cola") || lower.contains("juice") -> Triple(Color(0xFFE0F2FE), "🥤", Color(0xFF0284C7))
        lower.contains("soap") || lower.contains("surf") || lower.contains("dettol") || lower.contains("clean") -> Triple(Color(0xFFE0E7FF), "🧼", Color(0xFF4338CA))
        lower.contains("dal") || lower.contains("pulse") -> Triple(Color(0xFFFEF3C7), "🥣", Color(0xFFD97706))
        lower.contains("salt") || lower.contains("namak") -> Triple(Color(0xFFF8FAFC), "🧂", Color(0xFF64748B))
        lower.contains("masala") || lower.contains("spice") -> Triple(Color(0xFFFFEDD5), "🌶️", Color(0xFFEA580C))
        category.contains("Food", ignoreCase = true) -> Triple(Color(0xFFFFF7ED), "📦", Color(0xFFEA580C))
        category.contains("Beverage", ignoreCase = true) -> Triple(Color(0xFFE0F2FE), "🧃", Color(0xFF0284C7))
        category.contains("Household", ignoreCase = true) -> Triple(Color(0xFFF3E8FF), "🧹", Color(0xFF7E22CE))
        else -> Triple(Color(0xFFFFE8DC), "🛒", Color(0xFFFF5E00))
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = (size.value * 0.48).sp
        )
    }
}
