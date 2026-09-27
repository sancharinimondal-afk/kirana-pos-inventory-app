package com.example.data

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages Billing, Tax, and Inventory configuration preferences.
 */
object BillingSettingsManager {

    private const val PREFS_NAME = "kirana_billing_settings_prefs"

    // Billing & Tax Keys
    private const val KEY_GST_ENABLED = "billing_gst_enabled"
    private const val KEY_DEFAULT_GST_RATE = "billing_default_gst_rate"
    private const val KEY_INVOICE_PREFIX = "billing_invoice_prefix"
    private const val KEY_ROUNDING_ENABLED = "billing_rounding_enabled"
    private const val KEY_DEFAULT_PAYMENT_MODE = "billing_default_payment_mode"
    private const val KEY_MAX_DISCOUNT_PERCENT = "billing_max_discount_percent"
    private const val KEY_DEFAULT_DISCOUNT_PERCENT = "billing_default_discount_percent"

    // Inventory Keys
    private const val KEY_ALLOW_NEGATIVE_STOCK = "inventory_allow_negative_stock"
    private const val KEY_BARCODE_AUTO_ADD = "inventory_barcode_auto_add"

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // --- Billing & Tax ---

    fun isGstEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_GST_ENABLED, true)
    }

    fun setGstEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_GST_ENABLED, enabled).apply()
    }

    fun getDefaultGstRate(context: Context): Double {
        return getPrefs(context).getFloat(KEY_DEFAULT_GST_RATE, 5.0f).toDouble()
    }

    fun setDefaultGstRate(context: Context, rate: Double) {
        getPrefs(context).edit().putFloat(KEY_DEFAULT_GST_RATE, rate.toFloat()).apply()
    }

    fun getInvoicePrefix(context: Context): String {
        return getPrefs(context).getString(KEY_INVOICE_PREFIX, "INV-") ?: "INV-"
    }

    fun setInvoicePrefix(context: Context, prefix: String) {
        getPrefs(context).edit().putString(KEY_INVOICE_PREFIX, prefix.trim()).apply()
    }

    fun isRoundingEnabled(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ROUNDING_ENABLED, true)
    }

    fun setRoundingEnabled(context: Context, enabled: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ROUNDING_ENABLED, enabled).apply()
    }

    fun getDefaultPaymentMode(context: Context): String {
        return getPrefs(context).getString(KEY_DEFAULT_PAYMENT_MODE, "CASH") ?: "CASH"
    }

    fun setDefaultPaymentMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_DEFAULT_PAYMENT_MODE, mode.trim().uppercase()).apply()
    }

    fun getMaxDiscountPercent(context: Context): Double {
        return getPrefs(context).getFloat(KEY_MAX_DISCOUNT_PERCENT, 20.0f).toDouble()
    }

    fun setMaxDiscountPercent(context: Context, maxDiscount: Double) {
        getPrefs(context).edit().putFloat(KEY_MAX_DISCOUNT_PERCENT, maxDiscount.toFloat()).apply()
    }

    fun getDefaultDiscountPercent(context: Context): Double {
        return getPrefs(context).getFloat(KEY_DEFAULT_DISCOUNT_PERCENT, 0.0f).toDouble()
    }

    fun setDefaultDiscountPercent(context: Context, discount: Double) {
        getPrefs(context).edit().putFloat(KEY_DEFAULT_DISCOUNT_PERCENT, discount.toFloat()).apply()
    }

    // --- Inventory ---

    fun isNegativeStockAllowed(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_ALLOW_NEGATIVE_STOCK, false)
    }

    fun setNegativeStockAllowed(context: Context, allowed: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_ALLOW_NEGATIVE_STOCK, allowed).apply()
    }

    fun isBarcodeAutoAdd(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_BARCODE_AUTO_ADD, true)
    }

    fun setBarcodeAutoAdd(context: Context, autoAdd: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_BARCODE_AUTO_ADD, autoAdd).apply()
    }

    // --- Theme & Appearance ---
    private const val KEY_THEME_MODE = "app_theme_mode"
    private const val KEY_COLOR_PALETTE = "app_color_palette"

    fun getThemeMode(context: Context): String {
        return getPrefs(context).getString(KEY_THEME_MODE, "SYSTEM") ?: "SYSTEM"
    }

    fun setThemeMode(context: Context, mode: String) {
        getPrefs(context).edit().putString(KEY_THEME_MODE, mode.trim().uppercase()).apply()
    }

    fun getColorPalette(context: Context): String {
        return getPrefs(context).getString(KEY_COLOR_PALETTE, "EMERALD") ?: "EMERALD"
    }

    fun setColorPalette(context: Context, palette: String) {
        getPrefs(context).edit().putString(KEY_COLOR_PALETTE, palette.trim().uppercase()).apply()
    }
}
