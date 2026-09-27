package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// ==========================================
// Modern Retail Theme Palettes
// ==========================================

// 1. Fresh Emerald Retail (Default - Clean, modern, trustworthy)
val EmeraldPrimary = Color(0xFF0F766E)
val EmeraldPrimaryDark = Color(0xFF2DD4BF)
val EmeraldContainer = Color(0xFFCCFBF1)
val EmeraldOnContainer = Color(0xFF134E4A)
val EmeraldContainerDark = Color(0xFF115E59)
val EmeraldOnContainerDark = Color(0xFFCCFBF1)

// 2. Classic Kirana (Warm Terracotta / Saffron - Indian retail warm vibe without harsh neon)
val SaffronPrimary = Color(0xFFC2410C)
val SaffronPrimaryDark = Color(0xFFFB923C)
val SaffronContainer = Color(0xFFFFEDD5)
val SaffronOnContainer = Color(0xFF7C2D12)
val SaffronContainerDark = Color(0xFF9A3412)
val SaffronOnContainerDark = Color(0xFFFFEDD5)

// 3. Royal Sapphire (Deep Tech Navy / Blue - FinTech POS style)
val SapphirePrimary = Color(0xFF1D4ED8)
val SapphirePrimaryDark = Color(0xFF60A5FA)
val SapphireContainer = Color(0xFFDBEAFE)
val SapphireOnContainer = Color(0xFF1E3A8A)
val SapphireContainerDark = Color(0xFF1E40AF)
val SapphireOnContainerDark = Color(0xFFDBEAFE)

// 4. Modern Violet (Royal Purple - Boutique / Fashion / Premium grocery)
val VioletPrimary = Color(0xFF6D28D9)
val VioletPrimaryDark = Color(0xFFA78BFA)
val VioletContainer = Color(0xFFEDE9FE)
val VioletOnContainer = Color(0xFF4C1D95)
val VioletContainerDark = Color(0xFF5B21B6)
val VioletOnContainerDark = Color(0xFFEDE9FE)

// ==========================================
// Light Theme Backgrounds & Neutrals
// ==========================================
val KiranaBackgroundLight = Color(0xFFF8FAFC) // Crisp, modern, clean slate 50
val KiranaOnBackgroundLight = Color(0xFF0F172A) // Deep slate 900
val KiranaSurfaceLight = Color(0xFFFFFFFF) // Pure elevated white
val KiranaOnSurfaceLight = Color(0xFF0F172A)
val KiranaSurfaceVariantLight = Color(0xFFF1F5F9) // Slate 100
val KiranaOnSurfaceVariantLight = Color(0xFF475569) // Slate 600
val KiranaOutlineLight = Color(0xFFCBD5E1) // Slate 300
val KiranaOutlineVariantLight = Color(0xFFE2E8F0) // Slate 200

// ==========================================
// Dark Theme Backgrounds & Neutrals
// ==========================================
val KiranaBackgroundDark = Color(0xFF0F172A) // Clean, modern dark slate 900
val KiranaOnBackgroundDark = Color(0xFFF8FAFC) // Slate 50 crisp high-contrast
val KiranaSurfaceDark = Color(0xFF1E293B) // Elevated card surface Slate 800
val KiranaOnSurfaceDark = Color(0xFFF8FAFC) // Pure readable text Slate 50
val KiranaSurfaceVariantDark = Color(0xFF334155) // Slate 700
val KiranaOnSurfaceVariantDark = Color(0xFFCBD5E1) // Slate 300
val KiranaOutlineDark = Color(0xFF475569) // Slate 600
val KiranaOutlineVariantDark = Color(0xFF334155) // Slate 700

// Shared Secondary & Feedback
val KiranaSecondaryLight = Color(0xFF0284C7)
val KiranaOnSecondaryLight = Color(0xFFFFFFFF)
val KiranaSecondaryContainerLight = Color(0xFFE0F2FE)
val KiranaOnSecondaryContainerLight = Color(0xFF0369A1)

val KiranaSecondaryDark = Color(0xFF38BDF8)
val KiranaOnSecondaryDark = Color(0xFF082F49)
val KiranaSecondaryContainerDark = Color(0xFF0369A1)
val KiranaOnSecondaryContainerDark = Color(0xFFE0F2FE)

val KiranaErrorLight = Color(0xFFDC2626)
val KiranaOnErrorLight = Color(0xFFFFFFFF)
val KiranaErrorContainerLight = Color(0xFFFEE2E2)
val KiranaOnErrorContainerLight = Color(0xFF991B1B)

val KiranaErrorDark = Color(0xFFEF4444)
val KiranaOnErrorDark = Color(0xFFFFFFFF)
val KiranaErrorContainerDark = Color(0xFF7F1D1D)
val KiranaOnErrorContainerDark = Color(0xFFFEE2E2)

// Backwards-compatible aliases
val KiranaPrimaryLight = EmeraldPrimary
val KiranaOnPrimaryLight = Color(0xFFFFFFFF)
val KiranaPrimaryContainerLight = EmeraldContainer
val KiranaOnPrimaryContainerLight = EmeraldOnContainer

val KiranaPrimaryDark = EmeraldPrimaryDark
val KiranaOnPrimaryDark = Color(0xFF042F2E)
val KiranaPrimaryContainerDark = EmeraldContainerDark
val KiranaOnPrimaryContainerDark = EmeraldOnContainerDark

val KiranaTertiaryLight = Color(0xFFD97706)
val KiranaOnTertiaryLight = Color(0xFFFFFFFF)
val KiranaTertiaryContainerLight = Color(0xFFFEF3C7)
val KiranaOnTertiaryContainerLight = Color(0xFF78350F)

val KiranaTertiaryDark = Color(0xFFFBBF24)
val KiranaOnTertiaryDark = Color(0xFF451A03)
val KiranaTertiaryContainerDark = Color(0xFF92400E)
val KiranaOnTertiaryContainerDark = Color(0xFFFEF3C7)

// Grocery Shop Accent Colors
val GroceryOrange = EmeraldPrimary // Replaced harsh neon orange with elegant primary
val GroceryOrangeDark = Color(0xFF0F766E)
val GroceryOrangeLight = Color(0xFF2DD4BF)
val GroceryNavy = Color(0xFF0B132B)
val GroceryNavyDark = Color(0xFF060B18)
val GroceryGreen = Color(0xFF059669)

// Metric Card Colors
val MetricProductsGreen = Color(0xFF059669)
val MetricStockBlue = Color(0xFF0284C7)
val MetricSalesOrange = Color(0xFFD97706)
val MetricCustomersPurple = Color(0xFF7C3AED)
val MetricCyan = Color(0xFF0D9488)
val MetricRed = Color(0xFFDC2626)

// Custom semantic badge colors
val LowStockAlertColor = Color(0xFFD97706)
val LowStockAlertContainer = Color(0xFFFEF3C7)
val OutOfStockAlertColor = Color(0xFFDC2626)
val OutOfStockAlertContainer = Color(0xFFFEE2E2)
val InStockColor = Color(0xFF059669)
val InStockContainer = Color(0xFFD1FAE5)
val UpiPurple = Color(0xFF7C3AED)
val WhatsAppGreen = Color(0xFF25D366)
