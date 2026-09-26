package com.example.ui.screens

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BillingSettingsManager
import com.example.data.model.CartItem
import com.example.data.model.SaleTransaction
import com.example.printer.ThermalPrinterManager
import com.example.security.SecureAuthManager
import com.example.ui.KiranaViewModel
import com.example.ui.theme.GroceryGreen
import com.example.ui.theme.GroceryNavy
import com.example.ui.theme.GroceryOrange

/**
 * Settings Screen (Phase 15).
 *
 * 8 Standard Sections:
 * 1. Shop Information (Shop Name, Owner, Phone, Address, GSTIN, UPI ID)
 * 2. Billing & Tax (GST, Invoice Prefix, Rounding, Default Payment Mode, Discount Settings)
 * 3. Printer (58mm, 80mm, Printer Selection, Test Print, Auto Print)
 * 4. Security (Change Password, Remember Me, Auto Lock, Logout)
 * 5. Backup & Restore
 * 6. Inventory
 * 7. Data/Demo
 * 8. About
 */
@Composable
fun SettingsScreen(
    viewModel: KiranaViewModel,
    onNavigateToBackup: () -> Unit = {},
    onOpenGitHubReleaseGuide: () -> Unit = {},
    onLockStore: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentSettings by viewModel.shopSettings.collectAsStateWithLifecycle()

    // 1. Shop Information State
    var shopName by remember(currentSettings) { mutableStateOf(currentSettings.shopName) }
    var ownerName by remember(currentSettings) { mutableStateOf(currentSettings.ownerName) }
    var phone by remember(currentSettings) { mutableStateOf(currentSettings.phone) }
    var address by remember(currentSettings) { mutableStateOf(currentSettings.address) }
    var gstin by remember(currentSettings) { mutableStateOf(currentSettings.gstin) }
    var upiId by remember(currentSettings) { mutableStateOf(currentSettings.upiId) }

    // 2. Billing & Tax State
    var gstEnabled by remember { mutableStateOf(BillingSettingsManager.isGstEnabled(context)) }
    var defaultGstRate by remember { mutableDoubleStateOf(BillingSettingsManager.getDefaultGstRate(context)) }
    var invoicePrefix by remember { mutableStateOf(BillingSettingsManager.getInvoicePrefix(context)) }
    var roundingEnabled by remember { mutableStateOf(BillingSettingsManager.isRoundingEnabled(context)) }
    var defaultPaymentMode by remember { mutableStateOf(BillingSettingsManager.getDefaultPaymentMode(context)) }
    var maxDiscountPercent by remember { mutableDoubleStateOf(BillingSettingsManager.getMaxDiscountPercent(context)) }
    var defaultDiscountPercent by remember { mutableDoubleStateOf(BillingSettingsManager.getDefaultDiscountPercent(context)) }

    // 3. Printer State
    var paperWidth by remember(currentSettings) { mutableStateOf(currentSettings.printerPaperWidth) }
    var selectedPrinterType by remember {
        mutableStateOf(context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE).getString("printer_connection", "Bluetooth Thermal") ?: "Bluetooth Thermal")
    }
    var autoPrintAfterSale by remember {
        mutableStateOf(context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE).getBoolean("auto_print_sale", true))
    }

    // 4. Security State
    var currentUsername by remember { mutableStateOf(SecureAuthManager.getUsername(context)) }
    var rememberMeEnabled by remember { mutableStateOf(SecureAuthManager.isRememberMeEnabled(context)) }
    var biometricEnabled by remember { mutableStateOf(SecureAuthManager.isBiometricEnabled(context)) }
    var autoLockTimer by remember { mutableIntStateOf(SecureAuthManager.getAutoLockTimer(context)) }

    // 6. Inventory State
    var lowStockThreshold by remember(currentSettings) { mutableDoubleStateOf(currentSettings.lowStockThresholdDefault) }
    var allowNegativeStock by remember { mutableStateOf(BillingSettingsManager.isNegativeStockAllowed(context)) }
    var barcodeAutoAdd by remember { mutableStateOf(BillingSettingsManager.isBarcodeAutoAdd(context)) }

    // Dialog States
    var showChangePinDialog by remember { mutableStateOf(false) }
    var showChangeUsernameDialog by remember { mutableStateOf(false) }
    var showLogoutConfirmDialog by remember { mutableStateOf(false) }
    var showClearCacheConfirm by remember { mutableStateOf(false) }
    var showLoadDemoDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8FAFC))
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
            .testTag("settings_screen"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // ==========================================
        // 1. SHOP INFORMATION SECTION
        // ==========================================
        SectionHeader(title = "1. SHOP INFORMATION", icon = Icons.Default.Store)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = shopName,
                    onValueChange = { shopName = it },
                    label = { Text("Shop Name") },
                    modifier = Modifier.fillMaxWidth().testTag("settings_shop_name"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    label = { Text("Owner") },
                    modifier = Modifier.fillMaxWidth().testTag("settings_owner_name"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Phone") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth().testTag("settings_phone"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address") },
                    modifier = Modifier.fillMaxWidth().testTag("settings_address"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = gstin,
                    onValueChange = { gstin = it.uppercase() },
                    label = { Text("GSTIN") },
                    placeholder = { Text("e.g. 07AAAAA0000A1Z5") },
                    modifier = Modifier.fillMaxWidth().testTag("settings_gstin"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = upiId,
                    onValueChange = { upiId = it.trim() },
                    label = { Text("UPI ID") },
                    placeholder = { Text("e.g. storename@upi") },
                    modifier = Modifier.fillMaxWidth().testTag("settings_upi_id"),
                    singleLine = true
                )

                Button(
                    onClick = {
                        val updated = currentSettings.copy(
                            shopName = shopName.trim(),
                            ownerName = ownerName.trim(),
                            phone = phone.trim(),
                            address = address.trim(),
                            gstin = gstin.trim(),
                            upiId = upiId.trim()
                        )
                        viewModel.updateShopSettings(updated) { success ->
                            if (success) {
                                Toast.makeText(context, "Shop information saved", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GroceryOrange),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("save_shop_info_btn")
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Save Shop Information", fontWeight = FontWeight.Bold)
                }
            }
        }

        // ==========================================
        // 2. BILLING & TAX SECTION
        // ==========================================
        SectionHeader(title = "2. BILLING & TAX", icon = Icons.Default.Receipt)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // GST Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("GST Billing", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = GroceryNavy)
                        Text("Enable GST calculation on taxable products", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                    Switch(
                        checked = gstEnabled,
                        onCheckedChange = {
                            gstEnabled = it
                            BillingSettingsManager.setGstEnabled(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = GroceryOrange),
                        modifier = Modifier.testTag("gst_billing_switch")
                    )
                }

                if (gstEnabled) {
                    Text("Default GST Rate:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GroceryNavy)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0.0, 5.0, 12.0, 18.0, 28.0).forEach { rate ->
                            val isSelected = defaultGstRate == rate
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    defaultGstRate = rate
                                    BillingSettingsManager.setDefaultGstRate(context, rate)
                                },
                                label = { Text("${rate.toInt()}%", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GroceryOrange,
                                    selectedLabelColor = Color.White
                                )
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Invoice Prefix
                OutlinedTextField(
                    value = invoicePrefix,
                    onValueChange = {
                        invoicePrefix = it
                        BillingSettingsManager.setInvoicePrefix(context, it)
                    },
                    label = { Text("Invoice Prefix") },
                    placeholder = { Text("e.g. INV- or BILL-") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("billing_invoice_prefix")
                )

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Rounding
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Round Off Total", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = GroceryNavy)
                        Text("Round final bill amount to nearest ₹1", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                    Switch(
                        checked = roundingEnabled,
                        onCheckedChange = {
                            roundingEnabled = it
                            BillingSettingsManager.setRoundingEnabled(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = GroceryOrange),
                        modifier = Modifier.testTag("rounding_switch")
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Default Payment Mode
                Text("Default Payment Mode:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = GroceryNavy)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("CASH", "UPI", "KHATA", "CARD").forEach { mode ->
                        val isSelected = defaultPaymentMode == mode
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                defaultPaymentMode = mode
                                BillingSettingsManager.setDefaultPaymentMode(context, mode)
                            },
                            label = { Text(mode, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GroceryNavy,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("payment_mode_${mode.lowercase()}")
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Discount Settings
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = maxDiscountPercent.toString(),
                        onValueChange = {
                            val parsed = it.toDoubleOrNull() ?: 0.0
                            maxDiscountPercent = parsed
                            BillingSettingsManager.setMaxDiscountPercent(context, parsed)
                        },
                        label = { Text("Max Discount %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("billing_max_discount")
                    )
                    OutlinedTextField(
                        value = defaultDiscountPercent.toString(),
                        onValueChange = {
                            val parsed = it.toDoubleOrNull() ?: 0.0
                            defaultDiscountPercent = parsed
                            BillingSettingsManager.setDefaultDiscountPercent(context, parsed)
                        },
                        label = { Text("Default Disc %") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.weight(1f).testTag("billing_default_discount")
                    )
                }
            }
        }

        // ==========================================
        // 3. PRINTER SECTION
        // ==========================================
        SectionHeader(title = "3. PRINTER", icon = Icons.Default.Print)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Paper Width selection: 58mm / 80mm
                Text("Thermal Paper Width:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GroceryNavy)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("58mm", "80mm").forEach { width ->
                        val isSelected = paperWidth == width
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                paperWidth = width
                                viewModel.updateShopSettings(currentSettings.copy(printerPaperWidth = width))
                            },
                            label = { Text(width, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GroceryOrange,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("printer_width_$width")
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Printer Selection
                Text("Printer Selection:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GroceryNavy)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Bluetooth Thermal", "System Print Service", "USB POS").forEach { pType ->
                        val isSelected = selectedPrinterType == pType
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                selectedPrinterType = pType
                                context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
                                    .edit().putString("printer_connection", pType).apply()
                            },
                            label = { Text(pType, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GroceryNavy,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("printer_type_${pType.lowercase().replace(" ", "_")}")
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Auto Print
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Print", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = GroceryNavy)
                        Text("Automatically print receipt immediately after sale", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                    Switch(
                        checked = autoPrintAfterSale,
                        onCheckedChange = {
                            autoPrintAfterSale = it
                            context.getSharedPreferences("settings_prefs", Context.MODE_PRIVATE)
                                .edit().putBoolean("auto_print_sale", it).apply()
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = GroceryOrange),
                        modifier = Modifier.testTag("auto_print_switch")
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Test Print Button
                OutlinedButton(
                    onClick = {
                        val dummyTx = SaleTransaction(
                            invoiceNumber = "TEST-1001",
                            customerName = "Walk-in Customer",
                            subtotal = 145.0,
                            grandTotal = 145.0,
                            paymentMode = "CASH",
                            itemsJson = "[]"
                        )
                        val dummyItems = listOf(
                            CartItem(1, "8901234567890", "Tata Salt 1kg", "kg", 28.0, 1.0, 30.0, 24.0),
                            CartItem(2, "8901234567891", "Fortune Oil 1L", "L", 117.0, 1.0, 130.0, 105.0)
                        )
                        ThermalPrinterManager.printViaSystemPrintManager(context, dummyTx, dummyItems, currentSettings)
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("test_print_btn")
                ) {
                    Icon(imageVector = Icons.Default.Print, contentDescription = null, tint = GroceryNavy, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Print ($paperWidth)", color = GroceryNavy, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ==========================================
        // 4. SECURITY SECTION
        // ==========================================
        SectionHeader(title = "4. SECURITY", icon = Icons.Default.Security)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Change Password button
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth().clickable { showChangePinDialog = true }.testTag("change_pin_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = GroceryNavy, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Change Password", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = GroceryNavy)
                        }
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(16.dp))
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Remember Me
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Remember Me", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = GroceryNavy)
                        Text("Stay logged in across app restarts", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                    Switch(
                        checked = rememberMeEnabled,
                        onCheckedChange = {
                            rememberMeEnabled = it
                            SecureAuthManager.setRememberMeEnabled(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = GroceryOrange),
                        modifier = Modifier.testTag("remember_me_switch")
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Auto Lock
                Text("Auto Lock Timer:", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GroceryNavy)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val timerOptions = listOf("Immediate" to 0, "1 min" to 1, "5 min" to 5, "15 min" to 15, "Never" to -1)
                    timerOptions.forEach { (label, minutes) ->
                        val isSelected = autoLockTimer == minutes
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                autoLockTimer = minutes
                                SecureAuthManager.setAutoLockTimer(context, minutes)
                            },
                            label = { Text(label, fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = GroceryNavy,
                                selectedLabelColor = Color.White
                            ),
                            modifier = Modifier.testTag("autolock_${minutes}m")
                        )
                    }
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                // Logout
                Button(
                    onClick = { showLogoutConfirmDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("logout_btn")
                ) {
                    Icon(imageVector = Icons.Default.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Logout", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }

        // ==========================================
        // 5. BACKUP & RESTORE SECTION
        // ==========================================
        SectionHeader(title = "5. BACKUP & RESTORE", icon = Icons.Default.CloudUpload)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Export and restore complete store database (products, sales, purchases, customer khata) locally without cloud upload.",
                    fontSize = 12.sp,
                    color = Color(0xFF64748B)
                )
                Button(
                    onClick = onNavigateToBackup,
                    colors = ButtonDefaults.buttonColors(containerColor = GroceryNavy),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("backup_shortcut_btn")
                ) {
                    Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Open Backup & Restore Center", fontWeight = FontWeight.Bold)
                }
            }
        }

        // ==========================================
        // 6. INVENTORY SECTION
        // ==========================================
        SectionHeader(title = "6. INVENTORY", icon = Icons.Default.Inventory)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = lowStockThreshold.toInt().toString(),
                    onValueChange = {
                        val parsed = it.toDoubleOrNull() ?: 5.0
                        lowStockThreshold = parsed
                        viewModel.updateShopSettings(currentSettings.copy(lowStockThresholdDefault = parsed))
                    },
                    label = { Text("Default Low Stock Alert Threshold (Units)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("inventory_low_stock_threshold")
                )

                HorizontalDivider(color = Color(0xFFF1F5F9))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Allow Negative Stock Sales", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = GroceryNavy)
                        Text("Permit billing items even when stock level reaches 0", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                    Switch(
                        checked = allowNegativeStock,
                        onCheckedChange = {
                            allowNegativeStock = it
                            BillingSettingsManager.setNegativeStockAllowed(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = GroceryOrange),
                        modifier = Modifier.testTag("negative_stock_switch")
                    )
                }

                HorizontalDivider(color = Color(0xFFF1F5F9))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto Add on Barcode Scan", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = GroceryNavy)
                        Text("Directly add item to cart on barcode match in POS", fontSize = 12.sp, color = Color(0xFF64748B))
                    }
                    Switch(
                        checked = barcodeAutoAdd,
                        onCheckedChange = {
                            barcodeAutoAdd = it
                            BillingSettingsManager.setBarcodeAutoAdd(context, it)
                        },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = GroceryOrange),
                        modifier = Modifier.testTag("barcode_auto_add_switch")
                    )
                }
            }
        }

        // ==========================================
        // 7. DATA / DEMO SECTION
        // ==========================================
        SectionHeader(title = "7. DATA/DEMO", icon = Icons.Default.CleaningServices)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Demo Catalog Data", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = GroceryNavy)
                        Text(
                            text = "Standard Indian grocery items (Atta, Dal, Oil, Salt, Sugar) for quick trial.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(
                            onClick = { showLoadDemoDialog = true },
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().testTag("settings_load_demo_products_btn")
                        ) {
                            Text("LOAD DEMO PRODUCTS", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }

                // Clear Cache
                OutlinedButton(
                    onClick = { showClearCacheConfirm = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("clear_cache_btn")
                ) {
                    Icon(imageVector = Icons.Default.CleaningServices, contentDescription = null, tint = Color(0xFF64748B), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Clear Temporary Cache (Safe)", color = Color(0xFF475569), fontWeight = FontWeight.SemiBold)
                }
            }
        }

        // ==========================================
        // 8. ABOUT SECTION
        // ==========================================
        SectionHeader(title = "8. ABOUT", icon = Icons.Default.Info)

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Application", fontSize = 13.sp, color = Color(0xFF64748B))
                    Text("Kirana Store POS & Inventory", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GroceryNavy)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Version", fontSize = 13.sp, color = Color(0xFF64748B))
                    Text("1.0.0", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GroceryNavy)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Architecture", fontSize = 13.sp, color = Color(0xFF64748B))
                    Text("100% Offline • Local Room DB", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = GroceryGreen)
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedButton(
                    onClick = onOpenGitHubReleaseGuide,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("github_release_guide_btn")
                ) {
                    Text("Release & Build Documentation", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }

    // Change Password Dialog
    if (showChangePinDialog) {
        ChangePinDialog(
            onDismiss = { showChangePinDialog = false },
            onPinChanged = {
                showChangePinDialog = false
                Toast.makeText(context, "Password updated successfully!", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // Logout Confirmation Dialog
    if (showLogoutConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutConfirmDialog = false },
            title = { Text("Logout & Lock Store?", fontWeight = FontWeight.Bold, color = GroceryNavy) },
            text = {
                Text("This will close your active session and require your PIN/password to re-enter your dashboard.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLogoutConfirmDialog = false
                        SecureAuthManager.logout(context)
                        onLockStore()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_logout_btn")
                ) {
                    Text("Logout", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLogoutConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Clear Cache Confirmation Dialog
    if (showClearCacheConfirm) {
        AlertDialog(
            onDismissRequest = { showClearCacheConfirm = false },
            title = { Text("Clear Temporary Cache?", fontWeight = FontWeight.Bold, color = GroceryNavy) },
            text = {
                Text("This will safely delete temporary generated print files without touching your products, sales history, or customer khata balances.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        try {
                            val cacheDir = context.cacheDir
                            val bytesFreed = cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                            cacheDir.deleteRecursively()
                            val kbFreed = bytesFreed / 1024
                            Toast.makeText(context, "Cache cleared ($kbFreed KB freed). Database intact.", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "Cache cleared.", Toast.LENGTH_SHORT).show()
                        }
                        showClearCacheConfirm = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GroceryOrange)
                ) {
                    Text("Clear Cache")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showClearCacheConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Load Demo Products Dialog
    if (showLoadDemoDialog) {
        AlertDialog(
            onDismissRequest = { showLoadDemoDialog = false },
            title = { Text("LOAD DEMO PRODUCTS", fontWeight = FontWeight.Bold, color = GroceryNavy) },
            text = {
                Text("Add standard Indian grocery items (Atta, Dal, Oil, Salt, Sugar) to your inventory?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLoadDemoDialog = false
                        viewModel.loadDemoCatalog()
                        Toast.makeText(context, "Demo products loaded into catalog", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GroceryNavy),
                    modifier = Modifier.testTag("settings_confirm_load_demo_btn")
                ) {
                    Text("LOAD DEMO PRODUCTS", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showLoadDemoDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SectionHeader(title: String, icon: ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = GroceryNavy, modifier = Modifier.size(18.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = GroceryNavy,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun ChangePinDialog(
    onDismiss: () -> Unit,
    onPinChanged: () -> Unit
) {
    val context = LocalContext.current
    var oldPin by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Change Password", fontWeight = FontWeight.Bold, color = GroceryNavy)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = oldPin,
                    onValueChange = { oldPin = it },
                    label = { Text("Current Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("current_pin_input")
                )

                OutlinedTextField(
                    value = newPin,
                    onValueChange = { newPin = it },
                    label = { Text("New Password (min 4 chars)") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("new_pin_input")
                )

                OutlinedTextField(
                    value = confirmPin,
                    onValueChange = { confirmPin = it },
                    label = { Text("Confirm New Password") },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("confirm_pin_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showPassword = !showPassword }) {
                        Icon(
                            imageVector = if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                            contentDescription = "Toggle password visibility"
                        )
                    }
                    Text("Show Password", fontSize = 12.sp, color = Color(0xFF64748B))
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newPin.length < 4) {
                        errorMessage = "New password must be at least 4 characters"
                        return@Button
                    }
                    if (newPin != confirmPin) {
                        errorMessage = "New password and confirmation do not match"
                        return@Button
                    }
                    val success = SecureAuthManager.changePassword(context, oldPin, newPin)
                    if (success) {
                        onPinChanged()
                    } else {
                        errorMessage = "Current password is incorrect"
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = GroceryOrange),
                modifier = Modifier.testTag("submit_pin_change_btn")
            ) {
                Text("Update Password")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
