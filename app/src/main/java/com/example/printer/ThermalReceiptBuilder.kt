package com.example.printer

import com.example.billing.BillingCalculator
import com.example.billing.BillingItemInput
import com.example.billing.DiscountType
import com.example.data.model.CartItem
import com.example.data.model.SaleTransaction
import com.example.data.model.ShopSettings
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ThermalReceiptBuilder {

    private val ESC_INIT = byteArrayOf(0x1B, 0x40)
    private val ESC_ALIGN_LEFT = byteArrayOf(0x1B, 0x61, 0x00)
    private val ESC_ALIGN_CENTER = byteArrayOf(0x1B, 0x61, 0x01)
    private val ESC_ALIGN_RIGHT = byteArrayOf(0x1B, 0x61, 0x02)
    private val ESC_BOLD_ON = byteArrayOf(0x1B, 0x45, 0x01)
    private val ESC_BOLD_OFF = byteArrayOf(0x1B, 0x45, 0x00)
    private val ESC_DOUBLE_SIZE_ON = byteArrayOf(0x1D, 0x21, 0x11)
    private val ESC_DOUBLE_HEIGHT_ON = byteArrayOf(0x1D, 0x21, 0x01)
    private val ESC_NORMAL_SIZE = byteArrayOf(0x1D, 0x21, 0x00)
    private val ESC_FEED = byteArrayOf(0x0A)
    private val ESC_FEED_LINES = byteArrayOf(0x1B, 0x64, 0x03)
    private val ESC_PAPER_CUT = byteArrayOf(0x1D, 0x56, 0x41, 0x10)

    fun getLineWidth(paperWidth: String): Int {
        return if (paperWidth.equals("80mm", ignoreCase = true)) 48 else 32
    }

    /**
     * Formats a human-readable monospace plain text receipt for display, clipboard, or WhatsApp.
     * The paperWidth ("58mm" -> 32 cols, "80mm" -> 48 cols) directly shapes column layout and line wrapping.
     */
    fun formatPlainReceipt(
        transaction: SaleTransaction,
        items: List<CartItem>,
        settings: ShopSettings,
        paperWidth: String = settings.printerPaperWidth
    ): String {
        val width = getLineWidth(paperWidth)
        val sb = StringBuilder()
        val sepLine = "-".repeat(width)
        val dblLine = "=".repeat(width)

        val dateFormat = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.ENGLISH)
        val dateStr = dateFormat.format(Date(transaction.timestamp))

        // 1. Shop Header
        sb.append(centerText(settings.shopName.uppercase(), width)).append("\n")
        if (settings.tagline.isNotBlank()) {
            sb.append(centerText(settings.tagline, width)).append("\n")
        }
        sb.append(centerText(settings.address, width)).append("\n")
        if (settings.city.isNotBlank()) {
            sb.append(centerText(settings.city, width)).append("\n")
        }
        sb.append(centerText("Phone: ${settings.phone}", width)).append("\n")

        val gstinDisplay = if (settings.gstin.isNotBlank()) settings.gstin else "Unregistered"
        sb.append(centerText("GSTIN: $gstinDisplay", width)).append("\n")

        sb.append(dblLine).append("\n")

        // 2. Invoice Meta & Khata Customer
        sb.append(justifyText("Invoice: ${transaction.invoiceNumber}", "Mode: ${transaction.paymentMode}", width)).append("\n")
        sb.append(justifyText("Date: $dateStr", "", width)).append("\n")

        if (transaction.paymentMode.equals("KHATA", ignoreCase = true)) {
            val khataCust = transaction.customerName.ifBlank { "Registered Customer" }
            sb.append(justifyText("Khata Customer: $khataCust", "", width)).append("\n")
            if (transaction.customerPhone.isNotBlank()) {
                sb.append(justifyText("Customer Phone: ${transaction.customerPhone}", "", width)).append("\n")
            }
        } else if (transaction.customerName.isNotBlank() || transaction.customerPhone.isNotBlank()) {
            val cust = listOfNotNull(
                transaction.customerName.takeIf { it.isNotBlank() },
                transaction.customerPhone.takeIf { it.isNotBlank() }
            ).joinToString(" | ")
            sb.append(justifyText("Customer: $cust", "", width)).append("\n")
        }

        sb.append(sepLine).append("\n")

        // 3. Table Header (Different layout for 58mm vs 80mm)
        if (width >= 48) {
            // 80mm: 48 columns allows explicit discount column
            // ITEM (18) + QTY (5) + RATE (7) + DISC (7) + TOTAL (8) + 3 spaces = 48
            sb.append(String.format("%-18s %5s %7s %7s %8s", "ITEM", "QTY", "RATE", "DISC", "TOTAL")).append("\n")
        } else {
            // 58mm: 32 columns compact layout
            // ITEM (14) + QTY (4) + RATE (6) + TOTAL (6) = 30 + spaces = 32
            sb.append(String.format("%-14s %4s %6s %6s", "ITEM", "QTY", "RATE", "TOTAL")).append("\n")
        }
        sb.append(sepLine).append("\n")

        // 4. Calculate Bill Line Items
        val itemInputs = items.map { item ->
            BillingItemInput(
                productId = item.productId,
                barcode = item.barcode,
                name = item.name,
                unit = item.unit,
                quantity = item.quantity,
                unitPriceInclusive = item.rate,
                gstRatePercentage = item.gstRate,
                mrp = if (item.mrp < item.rate) item.rate else item.mrp,
                lineDiscount = item.lineDiscount
            )
        }
        val billResult = BillingCalculator.calculateBill(
            items = itemInputs,
            discountType = if (transaction.discount > 0.0) DiscountType.FixedAmount(transaction.discount) else null
        )

        for (item in items) {
            val itemTotal = BillingCalculator.roundMoney(item.rate * item.quantity - item.lineDiscount)
            val itemTotalStr = String.format(Locale.ENGLISH, "%.2f", itemTotal)
            val rateStr = String.format(Locale.ENGLISH, "%.2f", item.rate)
            val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format(Locale.ENGLISH, "%.2f", item.quantity)
            val discStr = String.format(Locale.ENGLISH, "%.2f", item.lineDiscount)

            if (width >= 48) {
                val nameTrimmed = if (item.name.length > 18) item.name.substring(0, 17) + "." else item.name
                sb.append(String.format("%-18s %5s %7s %7s %8s", nameTrimmed, qtyStr, rateStr, discStr, itemTotalStr)).append("\n")
            } else {
                val nameTrimmed = if (item.name.length > 14) item.name.substring(0, 13) + "." else item.name
                sb.append(String.format("%-14s %4s %6s %6s", nameTrimmed, qtyStr, rateStr, itemTotalStr)).append("\n")
                if (item.lineDiscount > 0.0) {
                    sb.append("  (Disc: -Rs.$discStr)\n")
                }
            }
        }

        sb.append(sepLine).append("\n")

        // 5. Totals & Tax Breakdown
        val totalQty = billResult.totalQuantity
        val qtySumStr = if (totalQty % 1.0 == 0.0) totalQty.toInt().toString() else String.format(Locale.ENGLISH, "%.2f", totalQty)
        sb.append(justifyText("Total Items / Qty:", "$qtySumStr ${if (totalQty == 1.0) "item" else "items"}", width)).append("\n")
        sb.append(justifyText("Subtotal:", "Rs. " + String.format(Locale.ENGLISH, "%.2f", transaction.subtotal), width)).append("\n")

        if (transaction.discount > 0.0) {
            sb.append(justifyText("Bill Discount:", "- Rs. " + String.format(Locale.ENGLISH, "%.2f", transaction.discount), width)).append("\n")
        }

        val displayGst = if (transaction.gstAmount > 0.0) transaction.gstAmount else billResult.gstAmount
        if (displayGst > 0.0) {
            sb.append(justifyText("GST Tax Included:", "Rs. " + String.format(Locale.ENGLISH, "%.2f", displayGst), width)).append("\n")
            if (width >= 48) {
                val halfGst = displayGst / 2.0
                sb.append(justifyText("  CGST (Half):", "Rs. " + String.format(Locale.ENGLISH, "%.2f", halfGst), width)).append("\n")
                sb.append(justifyText("  SGST (Half):", "Rs. " + String.format(Locale.ENGLISH, "%.2f", halfGst), width)).append("\n")
            }
        }

        sb.append(dblLine).append("\n")
        sb.append(justifyText("GRAND TOTAL:", "Rs. " + String.format(Locale.ENGLISH, "%.2f", transaction.grandTotal), width)).append("\n")
        sb.append(dblLine).append("\n")

        // 6. Payment Breakdown
        sb.append(justifyText("Payment Mode:", transaction.paymentMode, width)).append("\n")
        if (transaction.paymentMode.equals("CASH", ignoreCase = true) || transaction.cashReceived > 0.0) {
            sb.append(justifyText("Cash Received:", "Rs. " + String.format(Locale.ENGLISH, "%.2f", transaction.cashReceived), width)).append("\n")
            sb.append(justifyText("Change Returned:", "Rs. " + String.format(Locale.ENGLISH, "%.2f", transaction.changeDue), width)).append("\n")
        }
        if (transaction.paymentReference.isNotBlank()) {
            sb.append(justifyText("Ref / Txn ID:", transaction.paymentReference, width)).append("\n")
        }
        if (transaction.paymentMode.equals("KHATA", ignoreCase = true) && transaction.customerId != null) {
            sb.append(justifyText("Khata Account #:", transaction.customerId.toString(), width)).append("\n")
        }

        sb.append(sepLine).append("\n")

        // 7. Savings on MRP
        val displaySavings = billResult.totalSavings
        if (displaySavings > 0.0) {
            sb.append(centerText("* YOU SAVED Rs. ${String.format(Locale.ENGLISH, "%.2f", displaySavings)} ON MRP! *", width)).append("\n")
            sb.append(sepLine).append("\n")
        }

        // 8. Footer
        if (settings.upiId.isNotBlank()) {
            sb.append(centerText("UPI: ${settings.upiId}", width)).append("\n")
        }
        if (settings.termsNote.isNotBlank()) {
            sb.append(centerText(settings.termsNote, width)).append("\n")
        }
        sb.append(centerText(settings.receiptFooterNote, width)).append("\n")
        sb.append(centerText("*** Thank You! Visit Again ***", width)).append("\n")

        return sb.toString()
    }

    /**
     * Builds ESC/POS byte sequence for Bluetooth thermal POS printers.
     * The paperWidth ("58mm" -> 32 cols, "80mm" -> 48 cols) directly shapes column layout and line wrapping.
     */
    fun buildEscPosBytes(
        transaction: SaleTransaction,
        items: List<CartItem>,
        settings: ShopSettings,
        paperWidth: String = settings.printerPaperWidth
    ): ByteArray {
        val width = getLineWidth(paperWidth)
        val stream = ByteArrayOutputStream()

        fun write(bytes: ByteArray) = stream.write(bytes)
        fun writeText(text: String) = stream.write(text.toByteArray(Charsets.ISO_8859_1))
        fun writeLine(text: String) {
            writeText(text)
            write(ESC_FEED)
        }

        write(ESC_INIT)

        // Shop Header Center
        write(ESC_ALIGN_CENTER)
        write(ESC_BOLD_ON)
        write(ESC_DOUBLE_HEIGHT_ON)
        writeLine(settings.shopName.uppercase())
        write(ESC_NORMAL_SIZE)
        write(ESC_BOLD_OFF)

        if (settings.tagline.isNotBlank()) {
            writeLine(settings.tagline)
        }
        writeLine(settings.address)
        if (settings.city.isNotBlank()) {
            writeLine(settings.city)
        }
        writeLine("Phone: ${settings.phone}")

        val gstinDisplay = if (settings.gstin.isNotBlank()) settings.gstin else "Unregistered"
        writeLine("GSTIN: $gstinDisplay")

        val sepLine = "-".repeat(width)
        val dblLine = "=".repeat(width)

        writeLine(dblLine)

        // Invoice Meta & Khata Customer
        write(ESC_ALIGN_LEFT)
        val dateFormat = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.ENGLISH)
        val dateStr = dateFormat.format(Date(transaction.timestamp))
        writeLine(justifyText("Invoice: ${transaction.invoiceNumber}", "Mode: ${transaction.paymentMode}", width))
        writeLine("Date: $dateStr")

        if (transaction.paymentMode.equals("KHATA", ignoreCase = true)) {
            val khataCust = transaction.customerName.ifBlank { "Registered Customer" }
            writeLine(justifyText("Khata Customer: $khataCust", "", width))
            if (transaction.customerPhone.isNotBlank()) {
                writeLine(justifyText("Customer Phone: ${transaction.customerPhone}", "", width))
            }
        } else if (transaction.customerName.isNotBlank() || transaction.customerPhone.isNotBlank()) {
            val cust = listOfNotNull(
                transaction.customerName.takeIf { it.isNotBlank() },
                transaction.customerPhone.takeIf { it.isNotBlank() }
            ).joinToString(" | ")
            writeLine("Customer: $cust")
        }

        writeLine(sepLine)

        // Table Header
        write(ESC_BOLD_ON)
        if (width >= 48) {
            writeLine(String.format("%-18s %5s %7s %7s %8s", "ITEM", "QTY", "RATE", "DISC", "TOTAL"))
        } else {
            writeLine(String.format("%-14s %4s %6s %6s", "ITEM", "QTY", "RATE", "TOTAL"))
        }
        write(ESC_BOLD_OFF)
        writeLine(sepLine)

        // Items
        val itemInputs = items.map { item ->
            BillingItemInput(
                productId = item.productId,
                barcode = item.barcode,
                name = item.name,
                unit = item.unit,
                quantity = item.quantity,
                unitPriceInclusive = item.rate,
                gstRatePercentage = item.gstRate,
                mrp = if (item.mrp < item.rate) item.rate else item.mrp,
                lineDiscount = item.lineDiscount
            )
        }
        val billResult = BillingCalculator.calculateBill(
            items = itemInputs,
            discountType = if (transaction.discount > 0.0) DiscountType.FixedAmount(transaction.discount) else null
        )

        for (item in items) {
            val itemTotal = BillingCalculator.roundMoney(item.rate * item.quantity - item.lineDiscount)
            val itemTotalStr = String.format(Locale.ENGLISH, "%.2f", itemTotal)
            val rateStr = String.format(Locale.ENGLISH, "%.2f", item.rate)
            val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format(Locale.ENGLISH, "%.2f", item.quantity)
            val discStr = String.format(Locale.ENGLISH, "%.2f", item.lineDiscount)

            if (width >= 48) {
                val nameTrimmed = if (item.name.length > 18) item.name.substring(0, 17) + "." else item.name
                writeLine(String.format("%-18s %5s %7s %7s %8s", nameTrimmed, qtyStr, rateStr, discStr, itemTotalStr))
            } else {
                val nameTrimmed = if (item.name.length > 14) item.name.substring(0, 13) + "." else item.name
                writeLine(String.format("%-14s %4s %6s %6s", nameTrimmed, qtyStr, rateStr, itemTotalStr))
                if (item.lineDiscount > 0.0) {
                    writeLine("  (Disc: -Rs.$discStr)")
                }
            }
        }

        writeLine(sepLine)

        // Totals
        writeLine(justifyText("Subtotal:", "Rs. " + String.format(Locale.ENGLISH, "%.2f", transaction.subtotal), width))
        if (transaction.discount > 0.0) {
            writeLine(justifyText("Bill Discount:", "- Rs. " + String.format(Locale.ENGLISH, "%.2f", transaction.discount), width))
        }

        val displayGst = if (transaction.gstAmount > 0.0) transaction.gstAmount else billResult.gstAmount
        if (displayGst > 0.0) {
            writeLine(justifyText("GST Tax Included:", "Rs. " + String.format(Locale.ENGLISH, "%.2f", displayGst), width))
            if (width >= 48) {
                val halfGst = displayGst / 2.0
                writeLine(justifyText("  CGST (Half):", "Rs. " + String.format(Locale.ENGLISH, "%.2f", halfGst), width))
                writeLine(justifyText("  SGST (Half):", "Rs. " + String.format(Locale.ENGLISH, "%.2f", halfGst), width))
            }
        }

        writeLine(dblLine)

        // Grand Total Big
        write(ESC_ALIGN_LEFT)
        write(ESC_BOLD_ON)
        write(ESC_DOUBLE_HEIGHT_ON)
        writeLine(justifyText("TOTAL:", "Rs. " + String.format(Locale.ENGLISH, "%.2f", transaction.grandTotal), width))
        write(ESC_NORMAL_SIZE)
        write(ESC_BOLD_OFF)

        writeLine(dblLine)

        // Payment Info
        writeLine(justifyText("Payment Mode:", transaction.paymentMode, width))
        if (transaction.paymentMode.equals("CASH", ignoreCase = true) || transaction.cashReceived > 0.0) {
            writeLine(justifyText("Cash Received:", "Rs. " + String.format(Locale.ENGLISH, "%.2f", transaction.cashReceived), width))
            writeLine(justifyText("Change Returned:", "Rs. " + String.format(Locale.ENGLISH, "%.2f", transaction.changeDue), width))
        }
        if (transaction.paymentReference.isNotBlank()) {
            writeLine(justifyText("Ref / Txn ID:", transaction.paymentReference, width))
        }
        if (transaction.paymentMode.equals("KHATA", ignoreCase = true) && transaction.customerId != null) {
            writeLine(justifyText("Khata Acct #:", transaction.customerId.toString(), width))
        }
        writeLine(sepLine)

        val displaySavings = billResult.totalSavings
        if (displaySavings > 0.0) {
            write(ESC_ALIGN_CENTER)
            writeLine("* SAVINGS: Rs. ${String.format(Locale.ENGLISH, "%.2f", displaySavings)} *")
            writeLine(sepLine)
        }

        // Footer
        write(ESC_ALIGN_CENTER)
        if (settings.upiId.isNotBlank()) {
            writeLine("UPI: ${settings.upiId}")
        }
        if (settings.termsNote.isNotBlank()) {
            writeLine(settings.termsNote)
        }
        writeLine(settings.receiptFooterNote)
        writeLine("*** Thank You! Visit Again ***")

        // Feed & Paper cut
        write(ESC_FEED_LINES)
        write(ESC_PAPER_CUT)

        return stream.toByteArray()
    }

    private fun centerText(text: String, width: Int): String {
        if (text.length >= width) return text.take(width)
        val padding = (width - text.length) / 2
        return " ".repeat(padding) + text
    }

    private fun justifyText(left: String, right: String, width: Int): String {
        val totalLen = left.length + right.length
        if (totalLen >= width) {
            val availLeft = (width - right.length - 1).coerceAtLeast(1)
            return left.take(availLeft) + " " + right
        }
        val spaces = " ".repeat(width - totalLen)
        return left + spaces + right
    }
}
