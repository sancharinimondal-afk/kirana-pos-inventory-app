package com.example.ui.theme

import android.os.Build
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private data class PaletteColors(
    val primary: Color,
    val primaryDark: Color,
    val onPrimaryDark: Color,
    val container: Color,
    val onContainer: Color,
    val containerDark: Color,
    val onContainerDark: Color
)

fun getKiranaColorScheme(darkTheme: Boolean, palette: String): ColorScheme {
    val p = when (palette.uppercase()) {
        "CLASSIC_KIRANA" -> PaletteColors(
            primary = SaffronPrimary,
            primaryDark = SaffronPrimaryDark,
            onPrimaryDark = Color(0xFF431B05),
            container = SaffronContainer,
            onContainer = SaffronOnContainer,
            containerDark = SaffronContainerDark,
            onContainerDark = SaffronOnContainerDark
        )
        "SAPPHIRE" -> PaletteColors(
            primary = SapphirePrimary,
            primaryDark = SapphirePrimaryDark,
            onPrimaryDark = Color(0xFF082F49),
            container = SapphireContainer,
            onContainer = SapphireOnContainer,
            containerDark = SapphireContainerDark,
            onContainerDark = SapphireOnContainerDark
        )
        "VIOLET" -> PaletteColors(
            primary = VioletPrimary,
            primaryDark = VioletPrimaryDark,
            onPrimaryDark = Color(0xFF2E1065),
            container = VioletContainer,
            onContainer = VioletOnContainer,
            containerDark = VioletContainerDark,
            onContainerDark = VioletOnContainerDark
        )
        else -> PaletteColors( // Default: Emerald Retail
            primary = EmeraldPrimary,
            primaryDark = EmeraldPrimaryDark,
            onPrimaryDark = Color(0xFF003735),
            container = EmeraldContainer,
            onContainer = EmeraldOnContainer,
            containerDark = EmeraldContainerDark,
            onContainerDark = EmeraldOnContainerDark
        )
    }

    return if (darkTheme) {
        darkColorScheme(
            primary = p.primaryDark,
            onPrimary = p.onPrimaryDark,
            primaryContainer = p.containerDark,
            onPrimaryContainer = p.onContainerDark,
            secondary = KiranaSecondaryDark,
            onSecondary = Color(0xFF082F49),
            secondaryContainer = KiranaSecondaryContainerDark,
            onSecondaryContainer = Color.White,
            tertiary = KiranaTertiaryDark,
            onTertiary = Color(0xFF451A03),
            background = KiranaBackgroundDark,
            onBackground = Color(0xFFF8FAFC),
            surface = KiranaSurfaceDark,
            onSurface = Color(0xFFF8FAFC),
            surfaceVariant = KiranaSurfaceVariantDark,
            onSurfaceVariant = Color(0xFFCBD5E1),
            outline = KiranaOutlineDark,
            outlineVariant = KiranaOutlineVariantDark,
            error = KiranaErrorDark,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = p.primary,
            onPrimary = Color.White,
            primaryContainer = p.container,
            onPrimaryContainer = p.onContainer,
            secondary = KiranaSecondaryLight,
            onSecondary = Color.White,
            secondaryContainer = KiranaSecondaryContainerLight,
            onSecondaryContainer = KiranaOnSecondaryContainerLight,
            tertiary = KiranaTertiaryLight,
            onTertiary = Color.White,
            background = KiranaBackgroundLight,
            onBackground = Color(0xFF0F172A),
            surface = KiranaSurfaceLight,
            onSurface = Color(0xFF0F172A),
            surfaceVariant = KiranaSurfaceVariantLight,
            onSurfaceVariant = Color(0xFF334155),
            outline = KiranaOutlineLight,
            outlineVariant = KiranaOutlineVariantLight,
            error = KiranaErrorLight,
            onError = Color.White
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    palette: String = "EMERALD",
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> getKiranaColorScheme(darkTheme, palette)
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}

@Composable
fun kiranaTextFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedContainerColor = MaterialTheme.colorScheme.surface,
    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
    cursorColor = MaterialTheme.colorScheme.primary
)
