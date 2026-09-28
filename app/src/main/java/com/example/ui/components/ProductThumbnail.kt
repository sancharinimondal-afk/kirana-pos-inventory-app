package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProductThumbnail(
    name: String,
    category: String,
    modifier: Modifier = Modifier,
    size: Dp = 54.dp
) {
    val lower = name.lowercase()
    val (primaryTint, secondaryTint, emoji) = when {
        lower.contains("rice") || lower.contains("chal") -> Triple(Color(0xFFF59E0B), Color(0xFFD97706), "🍚")
        lower.contains("atta") || lower.contains("flour") || lower.contains("wheat") -> Triple(Color(0xFFEAB308), Color(0xFFCA8A04), "🌾")
        lower.contains("sugar") || lower.contains("chini") -> Triple(Color(0xFF94A3B8), Color(0xFF64748B), "🧂")
        lower.contains("tea") || lower.contains("chai") || lower.contains("coffee") -> Triple(Color(0xFF10B981), Color(0xFF059669), "🍵")
        lower.contains("oil") || lower.contains("mustard") || lower.contains("ghee") -> Triple(Color(0xFFFBBF24), Color(0xFFD97706), "🌻")
        lower.contains("maggi") || lower.contains("noodle") -> Triple(Color(0xFFEF4444), Color(0xFFDC2626), "🍜")
        lower.contains("biscuit") || lower.contains("cookie") || lower.contains("parle") || lower.contains("good day") -> Triple(Color(0xFFF97316), Color(0xFFEA580C), "🍪")
        lower.contains("drink") || lower.contains("beverage") || lower.contains("cola") || lower.contains("juice") -> Triple(Color(0xFF0284C7), Color(0xFF0369A1), "🥤")
        lower.contains("soap") || lower.contains("surf") || lower.contains("dettol") || lower.contains("clean") || lower.contains("bath") -> Triple(Color(0xFF6366F1), Color(0xFF4F46E5), "🧼")
        lower.contains("dal") || lower.contains("pulse") -> Triple(Color(0xFFF59E0B), Color(0xFFB45309), "🥣")
        lower.contains("salt") || lower.contains("namak") -> Triple(Color(0xFF94A3B8), Color(0xFF475569), "🧂")
        lower.contains("masala") || lower.contains("spice") || lower.contains("chilli") -> Triple(Color(0xFFEA580C), Color(0xFFC2410C), "🌶️")
        category.contains("Snack", ignoreCase = true) || category.contains("Instant", ignoreCase = true) -> Triple(Color(0xFFF97316), Color(0xFFC2410C), "🍪")
        category.contains("Personal", ignoreCase = true) -> Triple(Color(0xFF8B5CF6), Color(0xFF6D28D9), "🧴")
        category.contains("Household", ignoreCase = true) || category.contains("Clean", ignoreCase = true) -> Triple(Color(0xFF06B6D4), Color(0xFF0891B2), "🧹")
        category.contains("Beverage", ignoreCase = true) || category.contains("Drink", ignoreCase = true) -> Triple(Color(0xFF0284C7), Color(0xFF0369A1), "🧃")
        category.contains("Dairy", ignoreCase = true) -> Triple(Color(0xFF38BDF8), Color(0xFF0284C7), "🥛")
        category.contains("Grain", ignoreCase = true) || category.contains("Flour", ignoreCase = true) -> Triple(Color(0xFFEAB308), Color(0xFFCA8A04), "🌾")
        else -> Triple(Color(0xFF10B981), Color(0xFF059669), "📦")
    }

    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = modifier
            .size(size)
            .shadow(elevation = 3.dp, shape = shape, clip = false)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        primaryTint.copy(alpha = 0.28f),
                        secondaryTint.copy(alpha = 0.12f)
                    )
                )
            )
            .border(
                BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        colors = listOf(
                            primaryTint.copy(alpha = 0.55f),
                            secondaryTint.copy(alpha = 0.25f)
                        )
                    )
                ),
                shape
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = emoji,
            fontSize = (size.value * 0.52).sp,
            textAlign = TextAlign.Center
        )
    }
}

