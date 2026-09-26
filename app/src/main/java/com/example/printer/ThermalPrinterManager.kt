package com.example.printer

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.data.model.CartItem
import com.example.data.model.SaleTransaction
import com.example.data.model.ShopSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.util.UUID

object ThermalPrinterManager {

    // Standard SPP UUID for Bluetooth POS Thermal Printers
    val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private const val PREFS_NAME = "printer_config_prefs"
    private const val KEY_SAVED_PRINTER_MAC = "saved_printer_mac"
    private const val KEY_SAVED_PRINTER_NAME = "saved_printer_name"

    fun getBluetoothAdapter(context: Context): BluetoothAdapter? {
        return try {
            val manager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            manager?.adapter ?: BluetoothAdapter.getDefaultAdapter()
        } catch (_: Exception) {
            null
        }
    }

    fun isBluetoothSupported(context: Context): Boolean {
        return getBluetoothAdapter(context) != null
    }

    fun isBluetoothEnabled(context: Context): Boolean {
        return getBluetoothAdapter(context)?.isEnabled == true
    }

    fun getRequiredBluetoothPermissions(): Array<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                android.Manifest.permission.BLUETOOTH_CONNECT,
                android.Manifest.permission.BLUETOOTH_SCAN
            )
        } else {
            arrayOf(
                android.Manifest.permission.BLUETOOTH,
                android.Manifest.permission.BLUETOOTH_ADMIN
            )
        }
    }

    fun hasBluetoothPermissions(context: Context): Boolean {
        val perms = getRequiredBluetoothPermissions()
        return perms.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Shows all paired Bluetooth devices without any unconditional exclusion filters.
     * Every paired device can be selected by the user.
     */
    @SuppressLint("MissingPermission")
    fun getPairedBluetoothDevices(context: Context): List<BluetoothDevice> {
        return try {
            val adapter = getBluetoothAdapter(context) ?: return emptyList()
            if (!adapter.isEnabled) return emptyList()
            adapter.bondedDevices?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Backwards-compatible alias for getPairedBluetoothDevices
     */
    @SuppressLint("MissingPermission")
    fun getPairedBluetoothPrinters(context: Context): List<BluetoothDevice> = getPairedBluetoothDevices(context)

    /**
     * Non-blocking heuristic indicator to badge likely POS / thermal printers in the UI,
     * while NEVER hiding or excluding any other paired Bluetooth device.
     */
    @SuppressLint("MissingPermission")
    fun isLikelyPrinter(device: BluetoothDevice): Boolean {
        return try {
            val name = (device.name ?: "").lowercase()
            val majorClass = device.bluetoothClass?.majorDeviceClass ?: 0
            majorClass == 1536 || // BluetoothClass.Device.Major.IMAGING
                    name.contains("pos") ||
                    name.contains("print") ||
                    name.contains("thermal") ||
                    name.contains("rpp") ||
                    name.contains("mpt") ||
                    name.contains("bt") ||
                    name.contains("receipt")
        } catch (_: Exception) {
            false
        }
    }

    // -------------------------------------------------------------
    // PRINTER PERSISTENCE
    // -------------------------------------------------------------
    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveSelectedPrinter(context: Context, address: String, name: String) {
        getPrefs(context).edit()
            .putString(KEY_SAVED_PRINTER_MAC, address.trim())
            .putString(KEY_SAVED_PRINTER_NAME, name.trim())
            .apply()
    }

    fun getSavedPrinterAddress(context: Context): String? {
        val mac = getPrefs(context).getString(KEY_SAVED_PRINTER_MAC, null)
        return if (mac.isNullOrBlank()) null else mac
    }

    fun getSavedPrinterName(context: Context): String? {
        val name = getPrefs(context).getString(KEY_SAVED_PRINTER_NAME, null)
        return if (name.isNullOrBlank()) null else name
    }

    fun clearSavedPrinter(context: Context) {
        getPrefs(context).edit()
            .remove(KEY_SAVED_PRINTER_MAC)
            .remove(KEY_SAVED_PRINTER_NAME)
            .apply()
    }

    // -------------------------------------------------------------
    // CONNECTION TESTING & PRINTING
    // -------------------------------------------------------------
    @SuppressLint("MissingPermission")
    suspend fun testPrinterConnection(
        context: Context,
        device: BluetoothDevice
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions(context)) {
            return@withContext Result.failure(SecurityException("Bluetooth permission not granted. Please allow Bluetooth permission."))
        }
        var socket: BluetoothSocket? = null
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            val deviceName = device.name ?: device.address
            Result.success("Connected successfully to $deviceName! Printer is online.")
        } catch (e: Exception) {
            Result.failure(Exception("Could not connect to ${device.name ?: device.address}: ${e.localizedMessage ?: "Printer offline or unreachable"}"))
        } finally {
            try {
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun printToBluetoothPrinter(
        context: Context,
        device: BluetoothDevice,
        data: ByteArray
    ): Result<String> = withContext(Dispatchers.IO) {
        if (!hasBluetoothPermissions(context)) {
            return@withContext Result.failure(SecurityException("Bluetooth permission not granted. Please allow Bluetooth permission."))
        }
        var socket: BluetoothSocket? = null
        var outStream: OutputStream? = null
        try {
            socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
            socket.connect()
            outStream = socket.outputStream
            outStream.write(data)
            outStream.flush()
            delay(250) // Allow printer hardware buffer time to process cut & feed
            val deviceName = device.name ?: device.address
            Result.success("Printed successfully to $deviceName")
        } catch (e: Exception) {
            Result.failure(Exception("Bluetooth print failed: ${e.localizedMessage ?: "Communication error with printer"}"))
        } finally {
            try {
                outStream?.close()
                socket?.close()
            } catch (_: Exception) {}
        }
    }

    // -------------------------------------------------------------
    // SYSTEM PRINT MANAGER (58mm & 80mm Custom Roll Media)
    // -------------------------------------------------------------
    fun printViaSystemPrintManager(
        context: Context,
        transaction: SaleTransaction,
        items: List<CartItem>,
        settings: ShopSettings,
        paperWidth: String = settings.printerPaperWidth
    ) {
        try {
            val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                ?: run {
                    Toast.makeText(context, "System print service is not available on this device", Toast.LENGTH_SHORT).show()
                    return
                }

            val is80mm = paperWidth.equals("80mm", ignoreCase = true)
            val receiptText = ThermalReceiptBuilder.formatPlainReceipt(transaction, items, settings, paperWidth)

            // Dynamic thermal paper dimensions in mils (1 inch = 1000 mils, 1 mm = 39.37 mils)
            // 58mm roll = 2283 mils. 80mm roll = 3150 mils. Length simulates continuous thermal receipt paper.
            val mediaSize = if (is80mm) {
                PrintAttributes.MediaSize(
                    "THERMAL_80MM",
                    "Thermal 80mm Roll",
                    3150,
                    11811 // 300mm length
                )
            } else {
                PrintAttributes.MediaSize(
                    "THERMAL_58MM",
                    "Thermal 58mm Roll",
                    2283,
                    7874 // 200mm length
                )
            }

            val cssWidth = if (is80mm) "72mm" else "48mm"
            val fontSize = if (is80mm) "12px" else "10.5px"
            val pageSize = if (is80mm) "80mm auto" else "58mm auto"

            val html = """
                <!DOCTYPE html>
                <html>
                <head>
                    <meta charset="utf-8">
                    <title>Invoice #${transaction.invoiceNumber}</title>
                    <style>
                        @page {
                            size: $pageSize;
                            margin: 0mm;
                        }
                        body {
                            font-family: 'Courier New', Courier, monospace;
                            font-size: $fontSize;
                            line-height: 1.35;
                            width: $cssWidth;
                            margin: 0 auto;
                            padding: 4mm 2mm;
                            color: #000;
                            white-space: pre-wrap;
                            background: #fff;
                        }
                    </style>
                </head>
                <body>$receiptText</body>
                </html>
            """.trimIndent()

            val webView = WebView(context)
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    val printAdapter = webView.createPrintDocumentAdapter("Thermal_Bill_${transaction.invoiceNumber}")
                    val printAttributes = PrintAttributes.Builder()
                        .setMediaSize(mediaSize)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()
                    printManager.print("Thermal_Bill_${transaction.invoiceNumber}", printAdapter, printAttributes)
                }
            }
            webView.loadDataWithBaseURL(null, html, "text/html", "UTF-8", null)
        } catch (e: Exception) {
            Toast.makeText(context, "Printing error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    // -------------------------------------------------------------
    // WHATSAPP & SYSTEM SHARE
    // -------------------------------------------------------------
    fun shareReceiptViaWhatsApp(
        context: Context,
        transaction: SaleTransaction,
        items: List<CartItem>,
        settings: ShopSettings,
        paperWidth: String = settings.printerPaperWidth
    ) {
        try {
            val receiptText = ThermalReceiptBuilder.formatPlainReceipt(transaction, items, settings, paperWidth)
            val shareText = "🧾 *INVOICE FROM ${settings.shopName.uppercase()}*\n\n```\n$receiptText\n```"

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Bill ${transaction.invoiceNumber} - ${settings.shopName}")
                putExtra(Intent.EXTRA_TEXT, shareText)
            }

            // Target phone number directly if available
            val phone = transaction.customerPhone.filter { it.isDigit() }
            if (phone.isNotBlank()) {
                val formattedPhone = if (phone.length == 10) "91$phone" else phone
                val whatsappIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse("https://api.whatsapp.com/send?phone=$formattedPhone&text=" + Uri.encode(shareText))
                }
                try {
                    context.startActivity(whatsappIntent)
                    return
                } catch (_: Exception) {
                    // Fallback to standard chooser
                }
            }

            val chooser = Intent.createChooser(intent, "Share Thermal Bill")
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            Toast.makeText(context, "Share error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
