package com.example.ui.components

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsBluetooth
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.CartItem
import com.example.data.model.SaleTransaction
import com.example.data.model.ShopSettings
import com.example.printer.ThermalPrinterManager
import com.example.printer.ThermalReceiptBuilder
import com.example.ui.theme.WhatsAppGreen
import kotlinx.coroutines.launch
import java.util.Locale

@SuppressLint("MissingPermission")
@Composable
fun ThermalReceiptDialog(
    transaction: SaleTransaction,
    items: List<CartItem>,
    settings: ShopSettings,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current

    // Paper width selection: 58mm (32 chars) vs 80mm (48 chars)
    var selectedPaperWidth by remember { mutableStateOf(settings.printerPaperWidth) }

    val receiptText = remember(transaction, items, settings, selectedPaperWidth) {
        ThermalReceiptBuilder.formatPlainReceipt(transaction, items, settings, selectedPaperWidth)
    }

    // Bluetooth states
    var hasBtPermissions by remember { mutableStateOf(ThermalPrinterManager.hasBluetoothPermissions(context)) }
    var isBtSupported by remember { mutableStateOf(ThermalPrinterManager.isBluetoothSupported(context)) }
    var isBtEnabled by remember { mutableStateOf(ThermalPrinterManager.isBluetoothEnabled(context)) }
    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var selectedDevice by remember { mutableStateOf<BluetoothDevice?>(null) }
    var printerDropdownExpanded by remember { mutableStateOf(false) }

    // Printing & Connection test feedback states
    var isTestingConnection by remember { mutableStateOf(false) }
    var connectionTestResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) } // isSuccess to message
    var isPrinting by remember { mutableStateOf(false) }
    var printResult by remember { mutableStateOf<Pair<Boolean, String>?>(null) } // isSuccess to message

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissionsMap ->
        hasBtPermissions = permissionsMap.values.all { it }
        if (hasBtPermissions) {
            isBtEnabled = ThermalPrinterManager.isBluetoothEnabled(context)
            pairedDevices = ThermalPrinterManager.getPairedBluetoothDevices(context)
        }
    }

    // Enable Bluetooth intent launcher
    val enableBtLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        isBtEnabled = ThermalPrinterManager.isBluetoothEnabled(context)
        if (isBtEnabled && hasBtPermissions) {
            pairedDevices = ThermalPrinterManager.getPairedBluetoothDevices(context)
        }
    }

    // Refresh paired devices and restore saved printer
    fun refreshDevices() {
        if (!hasBtPermissions) {
            permissionLauncher.launch(ThermalPrinterManager.getRequiredBluetoothPermissions())
            return
        }
        isBtEnabled = ThermalPrinterManager.isBluetoothEnabled(context)
        if (!isBtEnabled) {
            enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }

        pairedDevices = ThermalPrinterManager.getPairedBluetoothDevices(context)
        val savedMac = ThermalPrinterManager.getSavedPrinterAddress(context)
        if (savedMac != null) {
            selectedDevice = pairedDevices.firstOrNull { it.address.equals(savedMac, ignoreCase = true) }
        }
        if (selectedDevice == null && pairedDevices.isNotEmpty()) {
            // Prefer likely printer or fallback to first paired device
            selectedDevice = pairedDevices.firstOrNull { ThermalPrinterManager.isLikelyPrinter(it) } ?: pairedDevices.first()
        }
    }

    LaunchedEffect(Unit) {
        refreshDevices()
    }

    fun selectPrinter(device: BluetoothDevice) {
        selectedDevice = device
        printerDropdownExpanded = false
        connectionTestResult = null
        printResult = null
        ThermalPrinterManager.saveSelectedPrinter(context, device.address, device.name ?: device.address)
    }

    fun testPrinterConnection() {
        val device = selectedDevice ?: return
        coroutineScope.launch {
            isTestingConnection = true
            connectionTestResult = null
            val result = ThermalPrinterManager.testPrinterConnection(context, device)
            isTestingConnection = false
            if (result.isSuccess) {
                connectionTestResult = Pair(true, result.getOrNull() ?: "Printer is reachable and online")
            } else {
                connectionTestResult = Pair(false, result.exceptionOrNull()?.message ?: "Connection test failed")
            }
        }
    }

    fun printBluetooth() {
        val device = selectedDevice
        if (device == null) {
            Toast.makeText(context, "Please select a paired printer first", Toast.LENGTH_SHORT).show()
            return
        }
        coroutineScope.launch {
            isPrinting = true
            printResult = null
            val escPosData = ThermalReceiptBuilder.buildEscPosBytes(transaction, items, settings, selectedPaperWidth)
            val result = ThermalPrinterManager.printToBluetoothPrinter(context, device, escPosData)
            isPrinting = false
            if (result.isSuccess) {
                printResult = Pair(true, result.getOrNull() ?: "Printed successfully!")
                Toast.makeText(context, "Printed successfully to ${device.name ?: device.address}", Toast.LENGTH_SHORT).show()
            } else {
                val errMsg = result.exceptionOrNull()?.message ?: "Unknown printer error"
                printResult = Pair(false, errMsg)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .testTag("thermal_receipt_dialog"),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Success Banner Header
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                            .testTag("sale_success_banner"),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFDCFCE7)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = "Saved Successfully",
                                        tint = Color(0xFF16A34A),
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Saved Successfully",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.5.sp,
                                        color = Color(0xFF15803D),
                                        modifier = Modifier.testTag("sale_saved_successfully_text")
                                    )
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Invoice Number: ${transaction.invoiceNumber}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = Color(0xFF1E293B),
                                    modifier = Modifier.testTag("sale_invoice_number_text")
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Grand Total: ₹${String.format(Locale.ENGLISH, "%.2f", transaction.grandTotal)} • Mode: ${transaction.paymentMode}",
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF475569)
                                )
                            }

                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = Color(0xFF15803D),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                    // Middle Scrollable Area: Receipt layout, preview card, and Bluetooth controls
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        // 2. Paper Size Selector (58mm vs 80mm)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Receipt Layout:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("58mm", "80mm").forEach { width ->
                            val isSelected = selectedPaperWidth.equals(width, ignoreCase = true)
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    selectedPaperWidth = width
                                    connectionTestResult = null
                                    printResult = null
                                },
                                label = {
                                    Text(
                                        text = "$width (${if (width == "58mm") "32 cols" else "48 cols"})",
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = Color.White
                                ),
                                modifier = Modifier.testTag("receipt_paper_size_$width")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. Realistic Thermal Paper Simulation Container (Responsive to 58mm vs 80mm)
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val rollWidth = if (selectedPaperWidth == "80mm") 380.dp else 290.dp
                    Card(
                        modifier = Modifier
                            .widthIn(max = rollWidth)
                            .fillMaxWidth()
                            .border(1.dp, Color(0xFFD6D3D1), RoundedCornerShape(8.dp)),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFDF7)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = "- - - - - [ THERMAL ROLL $selectedPaperWidth ] - - - - -",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = receiptText,
                                fontFamily = FontFamily.Monospace,
                                fontSize = if (selectedPaperWidth == "80mm") 10.sp else 11.sp,
                                lineHeight = if (selectedPaperWidth == "80mm") 13.5.sp else 14.5.sp,
                                color = Color(0xFF1C1917),
                                modifier = Modifier.testTag("thermal_receipt_preview_text")
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "- - - - - - - - - - [ TEAR ] - - - - - - - - - -",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                color = Color.Gray,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // 4. Bluetooth Printer Workflow Section
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("bluetooth_printer_section"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bluetooth,
                                    contentDescription = "Bluetooth",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Bluetooth Thermal Printer",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.5.sp
                                )
                            }

                            IconButton(
                                onClick = { refreshDevices() },
                                modifier = Modifier.size(32.dp).testTag("refresh_printers_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Refresh Printers",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        // Bluetooth Checks & Permissions
                        if (!hasBtPermissions) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF3C7)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Bluetooth permission required to connect to printer", fontSize = 11.5.sp, color = Color(0xFF92400E), modifier = Modifier.weight(1f))
                                    Button(
                                        onClick = { permissionLauncher.launch(ThermalPrinterManager.getRequiredBluetoothPermissions()) },
                                        shape = RoundedCornerShape(6.dp),
                                        contentPadding = ButtonDefaults.TextButtonContentPadding,
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Grant", fontSize = 11.sp)
                                    }
                                }
                            }
                        } else if (!isBtEnabled) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.SettingsBluetooth, contentDescription = null, tint = Color(0xFF2563EB), modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Bluetooth is turned off", fontSize = 12.sp, color = Color(0xFF1E40AF), modifier = Modifier.weight(1f))
                                    Button(
                                        onClick = { enableBtLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Turn On", fontSize = 11.sp)
                                    }
                                }
                            }
                        } else {
                            // Printer selector dropdown showing compatible paired devices
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { printerDropdownExpanded = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("select_printer_dropdown_btn"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(horizontalAlignment = Alignment.Start, modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = selectedDevice?.name ?: "Tap to Select Paired Printer",
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            if (selectedDevice != null) {
                                                Text(
                                                    text = "${selectedDevice!!.address} • Saved",
                                                    fontSize = 11.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }
                                        Icon(
                                            imageVector = Icons.Default.SettingsBluetooth,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }

                                DropdownMenu(
                                    expanded = printerDropdownExpanded,
                                    onDismissRequest = { printerDropdownExpanded = false }
                                ) {
                                    if (pairedDevices.isEmpty()) {
                                        DropdownMenuItem(
                                            text = {
                                                Text("No paired devices found. Pair your printer in Android Bluetooth Settings.", fontSize = 12.sp)
                                            },
                                            onClick = { printerDropdownExpanded = false }
                                        )
                                    } else {
                                        pairedDevices.forEach { device ->
                                            val isPrinter = ThermalPrinterManager.isLikelyPrinter(device)
                                            val isCurrent = selectedDevice?.address == device.address
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Column {
                                                            Text(
                                                                text = device.name ?: "Unnamed Device",
                                                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                                                fontSize = 13.sp
                                                            )
                                                            Text(
                                                                text = device.address + if (isPrinter) " • [POS Printer]" else "",
                                                                fontSize = 10.sp,
                                                                color = if (isPrinter) Color(0xFF16A34A) else Color.Gray
                                                            )
                                                        }
                                                        if (isCurrent) {
                                                            Icon(Icons.Default.Check, contentDescription = "Selected", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                        }
                                                    }
                                                },
                                                onClick = { selectPrinter(device) }
                                            )
                                        }
                                    }
                                }
                            }

                            // Connection Test & Print Actions
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Test connection button
                                OutlinedButton(
                                    onClick = { testPrinterConnection() },
                                    enabled = selectedDevice != null && !isTestingConnection && !isPrinting,
                                    modifier = Modifier
                                        .weight(0.45f)
                                        .height(44.dp)
                                        .testTag("test_printer_connection_btn"),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    if (isTestingConnection) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    } else {
                                        Icon(Icons.Default.BluetoothConnected, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Test", fontSize = 12.sp)
                                    }
                                }

                                // Print to Bluetooth button
                                Button(
                                    onClick = { printBluetooth() },
                                    enabled = selectedDevice != null && !isPrinting && !isTestingConnection,
                                    modifier = Modifier
                                        .weight(0.55f)
                                        .height(44.dp)
                                        .testTag("bluetooth_print_button"),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    if (isPrinting) {
                                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Printing...", fontSize = 12.sp)
                                    } else {
                                        Icon(Icons.Default.Print, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Print ($selectedPaperWidth)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }

                            // Connection test feedback message
                            AnimatedVisibility(visible = connectionTestResult != null) {
                                val (success, message) = connectionTestResult ?: return@AnimatedVisibility
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (success) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 6.dp)
                                        .testTag("connection_test_feedback")
                                ) {
                                    Row(
                                        modifier = Modifier.padding(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                            contentDescription = null,
                                            tint = if (success) Color(0xFF16A34A) else Color(0xFFDC2626),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = message,
                                            fontSize = 11.sp,
                                            color = if (success) Color(0xFF15803D) else Color(0xFFB91C1C)
                                        )
                                    }
                                }
                            }

                            // Print result feedback message (Sale = Saved, Printer = Failed)
                            AnimatedVisibility(visible = printResult != null) {
                                val (success, message) = printResult ?: return@AnimatedVisibility
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (success) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                        .testTag("print_status_banner")
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (success) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                                                contentDescription = null,
                                                tint = if (success) Color(0xFF16A34A) else Color(0xFFDC2626),
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (success) "Printed Successfully" else "Printer Failed",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.5.sp,
                                                color = if (success) Color(0xFF15803D) else Color(0xFFB91C1C)
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = if (success) message else "$message\n• Sale is safely saved as Invoice #${transaction.invoiceNumber} in the database and will not be lost. You can retry printing, change printer, or use System Print/Share.",
                                            fontSize = 11.sp,
                                            color = if (success) Color(0xFF15803D) else Color(0xFF7F1D1D)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))

                // 5 & 6. Sticky Bottom Footer: ALWAYS VISIBLE ABOVE MOBILE KEYS
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(
                            text = "Alternative Print & Share:",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    ThermalPrinterManager.printViaSystemPrintManager(
                                        context = context,
                                        transaction = transaction,
                                        items = items,
                                        settings = settings,
                                        paperWidth = selectedPaperWidth
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sale_success_print_btn"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Print, contentDescription = "System Print", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Print", fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    ThermalPrinterManager.shareReceiptViaWhatsApp(
                                        context = context,
                                        transaction = transaction,
                                        items = items,
                                        settings = settings,
                                        paperWidth = selectedPaperWidth
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("sale_success_share_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(15.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("WhatsApp", fontSize = 12.sp)
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            OutlinedButton(
                                onClick = {
                                    clipboardManager.setText(AnnotatedString(receiptText))
                                    Toast.makeText(context, "Receipt text copied to clipboard!", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier
                                    .weight(0.7f)
                                    .testTag("copy_receipt_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Copy", fontSize = 11.5.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // 6. New Sale Primary Action Button
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("sale_success_new_sale_btn"),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddCircleOutline,
                                contentDescription = "New Sale",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "New Sale",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}
}
