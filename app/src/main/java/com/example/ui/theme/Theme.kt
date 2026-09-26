package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = KiranaPrimaryDark,
    onPrimary = KiranaOnPrimaryDark,
    primaryContainer = KiranaPrimaryContainerDark,
    onPrimaryContainer = KiranaOnPrimaryContainerDark,
    secondary = KiranaSecondaryDark,
    onSecondary = KiranaOnSecondaryDark,
    secondaryContainer = KiranaSecondaryContainerDark,
    onSecondaryContainer = KiranaOnSecondaryContainerDark,
    tertiary = KiranaTertiaryDark,
    onTertiary = KiranaOnTertiaryDark,
    error = KiranaErrorDark,
    onError = KiranaOnErrorDark,
    errorContainer = KiranaErrorContainerDark,
    onErrorContainer = KiranaOnErrorContainerDark,
    background = KiranaBackgroundDark,
    onBackground = KiranaOnBackgroundDark,
    surface = KiranaSurfaceDark,
    onSurface = KiranaOnSurfaceDark,
    surfaceVariant = KiranaSurfaceVariantDark,
    onSurfaceVariant = KiranaOnSurfaceVariantDark,
    outline = KiranaOutlineDark
  )

private val LightColorScheme =
  lightColorScheme(
    primary = KiranaPrimaryLight,
    onPrimary = KiranaOnPrimaryLight,
    primaryContainer = KiranaPrimaryContainerLight,
    onPrimaryContainer = KiranaOnPrimaryContainerLight,
    secondary = KiranaSecondaryLight,
    onSecondary = KiranaOnSecondaryLight,
    secondaryContainer = KiranaSecondaryContainerLight,
    onSecondaryContainer = KiranaOnSecondaryContainerLight,
    tertiary = KiranaTertiaryLight,
    onTertiary = KiranaOnTertiaryLight,
    error = KiranaErrorLight,
    onError = KiranaOnErrorLight,
    errorContainer = KiranaErrorContainerLight,
    onErrorContainer = KiranaOnErrorContainerLight,
    background = KiranaBackgroundLight,
    onBackground = KiranaOnBackgroundLight,
    surface = KiranaSurfaceLight,
    onSurface = KiranaOnSurfaceLight,
    surfaceVariant = KiranaSurfaceVariantLight,
    onSurfaceVariant = KiranaOnSurfaceVariantLight,
    outline = KiranaOutlineLight
  )

@Composable
fun MyApplicationTheme(
  // Default to false for high-contrast store POS readability, or system theme if explicitly passed
  darkTheme: Boolean = false,
  // Dynamic color is optional on Android 12+ (false by default for consistent Kirana brand identity)
  dynamicColor: Boolean = false,
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
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
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
    unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
    cursorColor = MaterialTheme.colorScheme.primary
)
