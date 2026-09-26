package com.example.billing

import com.example.data.model.CartItem
import com.example.data.model.SaleTransaction
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * Exception thrown when any selling price exceeds Maximum Retail Price (MRP).
 * In Indian retail under the Legal Metrology Act, selling above MRP is illegal.
 * BillingCalculator strictly BLOCKS any operation where sellingPrice > MRP.
 */
class MrpViolationException(message: String) : IllegalArgumentException(message)

/**
 * Item specification passed into [BillingCalculator].
 */
data class BillingItemInput(
    val productId: Long = 0L,
    val barcode: String = "",
    val name: String = "",
    val unit: String = "Piece",
    val quantity: Double,
    val unitPriceInclusive: Double, // Price entered/stored per unit, GST-inclusive
    val gstRatePercentage: Double = 0.0, // e.g. 0.0, 5.0, 12.0, 18.0
    val mrp: Double = unitPriceInclusive,
    val lineDiscount: Double = 0.0, // Specific item-level direct discount
    val lineDiscountType: DiscountType? = null // Optional flexible line discount (Fixed or Percentage)
)

/**
 * Discount specification passed into [BillingCalculator].
 */
sealed class DiscountType {
    data class FixedAmount(val amount: Double) : DiscountType()
    data class Percentage(val percentage: Double) : DiscountType()
}

/**
 * Calculated line item output.
 * Uses authoritative BigDecimal calculations with consistent HALF_UP rounding.
 */
data class CalculatedItem(
    val productId: Long,
    val barcode: String,
    val name: String,
    val unit: String,
    val quantity: Double,
    val unitPriceInclusive: Double,
    val gstRatePercentage: Double,
    val mrp: Double,
    val lineGrossTotal: Double,        // quantity * unitPriceInclusive
    val lineDiscount: Double,          // Item-level direct discount
    val allocatedBillDiscount: Double, // Proportionately allocated bill-level discount
    val totalDiscount: Double,         // lineDiscount + allocatedBillDiscount
    val lineNetTotal: Double,          // lineGrossTotal - totalDiscount
    val taxableAmount: Double,         // Base price without GST: lineNetTotal / (1 + GST/100)
    val gstAmount: Double,             // GST tax included in lineNetTotal: lineNetTotal - taxableAmount
    val cgstAmount: Double,            // gstAmount / 2
    val sgstAmount: Double,            // gstAmount - cgstAmount (preserves exact sum)
    val savingsOnMrp: Double           // Savings on MRP + all line discounts
)

/**
 * Calculated summary output from [BillingCalculator].
 */
data class BillCalculationResult(
    val items: List<CalculatedItem>,
    val subtotal: Double,            // Sum of line gross totals (Rs.)
    val lineDiscountsTotal: Double,  // Sum of direct line discounts (Rs.)
    val billDiscountApplied: Double, // Validated bill-level discount (Rs.)
    val discountApplied: Double,     // Total discount = lineDiscountsTotal + billDiscountApplied
    val netSubtotal: Double,         // subtotal - discountApplied (before rounding)
    val taxableAmount: Double,       // Total taxable (base) amount after all discounts
    val gstAmount: Double,           // Total GST tax included
    val cgstAmount: Double,          // Total CGST (for intra-state display)
    val sgstAmount: Double,          // Total SGST (for intra-state display)
    val roundOff: Double,            // Difference between rounded grand total and netSubtotal
    val grandTotal: Double,          // Final payable amount (rounded to nearest rupee or exact 2 dec)
    val totalSavings: Double,        // Total savings on MRP across all items + all discounts
    val totalQuantity: Double,
    val cashReceived: Double,
    val changeDue: Double,
    val isCashSufficient: Boolean
)

/**
 * Sales summary calculation result for reports and analytics.
 */
data class SalesSummaryResult(
    val totalGrossSales: Double,
    val totalDiscount: Double,
    val totalNetSales: Double,
    val totalTaxableAmount: Double,
    val totalGstAmount: Double,
    val totalCgstAmount: Double,
    val totalSgstAmount: Double,
    val totalOrders: Int
)

/**
 * Central authoritative billing and calculation engine.
 * Single source of truth for POS billing, invoices, receipts, reports, and stored transactions.
 *
 * All money calculations use [BigDecimal] with 2 decimal scale and [RoundingMode.HALF_UP].
 *
 * Business Rules:
 * 1. Selling price is GST-inclusive.
 *    taxableAmount = grossAmount / (1 + GST/100)
 *    GST = grossAmount - taxableAmount
 *    CGST = GST / 2
 *    SGST = GST / 2
 * 2. Discounts:
 *    - Cannot be negative.
 *    - Percentage must be in [0, 100].
 *    - Cannot exceed subtotal.
 *    - Applied exactly once.
 *    - Bill-level discount is proportionally allocated to sale lines.
 * 3. MRP Rule:
 *    sellingPrice <= MRP. If sellingPrice > MRP, BLOCK the operation (throw MrpViolationException).
 * 4. Decimal quantities supported for kg, g, litre, ml.
 * 5. Duplicate cart items aggregated before stock validation.
 */
object BillingCalculator {

    const val CURRENCY_SCALE = 2
    const val QUANTITY_SCALE = 4
    val ROUNDING_MODE: RoundingMode = RoundingMode.HALF_UP

    private val BD_ZERO = BigDecimal.ZERO.setScale(CURRENCY_SCALE, ROUNDING_MODE)
    private val BD_HUNDRED = BigDecimal("100.00")
    private val BD_TWO = BigDecimal("2.00")

    /**
     * Authoritative money rounding helper (HALF_UP, 2 decimal places).
     */
    fun roundMoney(value: Double): Double {
        if (value.isNaN() || value.isInfinite()) return 0.0
        return BigDecimal.valueOf(value)
            .setScale(CURRENCY_SCALE, ROUNDING_MODE)
            .toDouble()
    }

    /**
     * Authoritative money rounding helper for BigDecimal.
     */
    fun roundMoney(value: BigDecimal): BigDecimal {
        return value.setScale(CURRENCY_SCALE, ROUNDING_MODE)
    }

    /**
     * Authoritative quantity rounding helper (up to 4 decimal places, trailing zeroes cleaned).
     */
    fun roundQuantity(value: Double): Double {
        if (value.isNaN() || value.isInfinite() || value <= 0.0) return 0.0
        return BigDecimal.valueOf(value)
            .setScale(QUANTITY_SCALE, ROUNDING_MODE)
            .stripTrailingZeros()
            .toDouble()
    }

    /**
     * Converts a rupee Double to paise (Long) for integer math if needed.
     */
    fun toPaise(rupees: Double): Long {
        if (rupees.isNaN() || rupees.isInfinite()) return 0L
        return BigDecimal.valueOf(rupees)
            .setScale(CURRENCY_SCALE, ROUNDING_MODE)
            .multiply(BigDecimal.valueOf(100))
            .setScale(0, ROUNDING_MODE)
            .toLong()
    }

    /**
     * Converts paise (Long) back to rupee Double rounded to 2 decimals.
     */
    fun toRupees(paise: Long): Double {
        return BigDecimal.valueOf(paise)
            .divide(BigDecimal.valueOf(100), CURRENCY_SCALE, ROUNDING_MODE)
            .toDouble()
    }

    /**
     * Normalizes and validates discount amount against applicable subtotal.
     * Enforces all discount rules:
     * - Cannot be negative.
     * - Percentage capped at 100%.
     * - Discount cannot exceed applicable subtotal.
     */
    fun validateAndCalculateDiscount(
        subtotal: Double,
        discountType: DiscountType?
    ): Double {
        if (subtotal <= 0.0 || discountType == null) return 0.0
        val subtotalBd = roundMoney(BigDecimal.valueOf(subtotal))
        return calculateDiscountBd(subtotalBd, discountType).toDouble()
    }

    private fun calculateDiscountBd(
        applicableSubtotal: BigDecimal,
        discountType: DiscountType?
    ): BigDecimal {
        if (applicableSubtotal.compareTo(BigDecimal.ZERO) <= 0 || discountType == null) {
            return BD_ZERO
        }

        return when (discountType) {
            is DiscountType.FixedAmount -> {
                val fixed = discountType.amount
                if (fixed <= 0.0 || fixed.isNaN() || fixed.isInfinite()) return BD_ZERO
                val fixedBd = roundMoney(BigDecimal.valueOf(fixed))
                fixedBd.min(applicableSubtotal).max(BD_ZERO)
            }
            is DiscountType.Percentage -> {
                val pct = discountType.percentage
                if (pct <= 0.0 || pct.isNaN() || pct.isInfinite()) return BD_ZERO
                val clampedPct = pct.coerceIn(0.0, 100.0)
                val pctBd = BigDecimal.valueOf(clampedPct)
                val discountAmount = applicableSubtotal
                    .multiply(pctBd)
                    .divide(BigDecimal("100"), 4, ROUNDING_MODE)
                roundMoney(discountAmount).min(applicableSubtotal).max(BD_ZERO)
            }
        }
    }

    /**
     * Validates that selling price does NOT exceed MRP for any item.
     * Throws [MrpViolationException] and blocks the operation if violated.
     */
    fun validateMrp(items: List<BillingItemInput>) {
        for (item in items) {
            val selling = roundMoney(item.unitPriceInclusive)
            val mrp = roundMoney(item.mrp)
            if (selling > mrp) {
                val nameDisplay = item.name.ifBlank { "Product ID ${item.productId}" }
                throw MrpViolationException(
                    "Selling price (₹$selling) cannot exceed MRP (₹$mrp) for '$nameDisplay'. Operation BLOCKED."
                )
            }
        }
    }

    /**
     * Aggregates duplicate items in cart by product identity before stock validation and billing.
     * Sums quantities and line discounts; preserves unit price, MRP, GST rate, unit, and name.
     */
    fun aggregateCartItems(items: List<CartItem>): List<CartItem> {
        if (items.size <= 1) return items

        val aggregated = mutableListOf<CartItem>()
        val grouped = items.groupBy {
            if (it.productId > 0L) {
                "ID_${it.productId}"
            } else if (it.barcode.isNotBlank()) {
                "BC_${it.barcode.trim()}"
            } else {
                "NAME_${it.name.trim().lowercase(Locale.ENGLISH)}"
            }
        }

        for ((_, group) in grouped) {
            if (group.size == 1) {
                aggregated.add(group.first())
            } else {
                val first = group.first()
                val totalQty = group.sumOf { it.quantity }
                val totalLineDiscount = group.sumOf { it.lineDiscount }
                aggregated.add(
                    first.copy(
                        quantity = roundQuantity(totalQty),
                        lineDiscount = roundMoney(totalLineDiscount)
                    )
                )
            }
        }
        return aggregated
    }

    /**
     * Aggregates duplicate billing inputs by product identity.
     */
    fun aggregateBillingItems(items: List<BillingItemInput>): List<BillingItemInput> {
        if (items.size <= 1) return items

        val aggregated = mutableListOf<BillingItemInput>()
        val grouped = items.groupBy {
            if (it.productId > 0L) {
                "ID_${it.productId}"
            } else if (it.barcode.isNotBlank()) {
                "BC_${it.barcode.trim()}"
            } else {
                "NAME_${it.name.trim().lowercase(Locale.ENGLISH)}"
            }
        }

        for ((_, group) in grouped) {
            if (group.size == 1) {
                aggregated.add(group.first())
            } else {
                val first = group.first()
                val totalQty = group.sumOf { it.quantity }
                val totalLineDiscount = group.sumOf { it.lineDiscount }
                aggregated.add(
                    first.copy(
                        quantity = roundQuantity(totalQty),
                        lineDiscount = roundMoney(totalLineDiscount)
                    )
                )
            }
        }
        return aggregated
    }

    /**
     * Core Authoritative Billing Engine calculation method.
     * Computes line items, subtotal, discounts (line & bill-level), taxable base amounts,
     * GST breakdown (CGST & SGST), grand total, round-off, and cash change.
     *
     * @param items List of line item inputs.
     * @param discountType Optional bill-level discount (FixedAmount or Percentage).
     * @param cashReceived Cash amount handed by customer (if cash transaction).
     * @param enableRupeeRounding If true, rounds grandTotal to nearest whole rupee with roundOff.
     * @param autoAggregateDuplicates If true, aggregates duplicate items before calculating.
     *
     * @throws MrpViolationException if any item has sellingPrice > MRP.
     */
    fun calculateBill(
        items: List<BillingItemInput>,
        discountType: DiscountType? = null,
        cashReceived: Double = 0.0,
        enableRupeeRounding: Boolean = false,
        autoAggregateDuplicates: Boolean = false
    ): BillCalculationResult {
        val workingItems = if (autoAggregateDuplicates) aggregateBillingItems(items) else items

        if (workingItems.isEmpty()) {
            val safeCash = if (cashReceived > 0.0) roundMoney(cashReceived) else 0.0
            return BillCalculationResult(
                items = emptyList(),
                subtotal = 0.0,
                lineDiscountsTotal = 0.0,
                billDiscountApplied = 0.0,
                discountApplied = 0.0,
                netSubtotal = 0.0,
                taxableAmount = 0.0,
                gstAmount = 0.0,
                cgstAmount = 0.0,
                sgstAmount = 0.0,
                roundOff = 0.0,
                grandTotal = 0.0,
                totalSavings = 0.0,
                totalQuantity = 0.0,
                cashReceived = safeCash,
                changeDue = safeCash,
                isCashSufficient = true
            )
        }

        // STEP 1: Strictly enforce MRP Rule (BLOCK operation if sellingPrice > MRP)
        validateMrp(workingItems)

        // STEP 2: Compute line gross and line-level discounts
        val lineGrossBdList = mutableListOf<BigDecimal>()
        val lineDiscBdList = mutableListOf<BigDecimal>()
        val lineNetAfterLineDiscBdList = mutableListOf<BigDecimal>()
        val mrpSavingsBdList = mutableListOf<BigDecimal>()
        var totalQuantity = 0.0

        for (item in workingItems) {
            val qty = if (item.quantity > 0.0) item.quantity else 0.0
            totalQuantity += qty

            val qtyBd = BigDecimal.valueOf(qty)
            val rateBd = BigDecimal.valueOf(item.unitPriceInclusive)
            val mrpBd = BigDecimal.valueOf(item.mrp)

            val lineGrossBd = roundMoney(qtyBd.multiply(rateBd))
            lineGrossBdList.add(lineGrossBd)

            // Direct line discount
            val directLineDiscBd = when {
                item.lineDiscountType != null -> {
                    calculateDiscountBd(lineGrossBd, item.lineDiscountType)
                }
                item.lineDiscount > 0.0 -> {
                    roundMoney(BigDecimal.valueOf(item.lineDiscount)).min(lineGrossBd).max(BD_ZERO)
                }
                else -> BD_ZERO
            }
            lineDiscBdList.add(directLineDiscBd)

            val lineNetAfterLineDiscBd = lineGrossBd.subtract(directLineDiscBd).max(BD_ZERO)
            lineNetAfterLineDiscBdList.add(lineNetAfterLineDiscBd)

            // Savings on MRP per line
            val mrpGrossBd = roundMoney(qtyBd.multiply(mrpBd))
            val savingsFromMrpBd = mrpGrossBd.subtract(lineGrossBd).max(BD_ZERO)
            mrpSavingsBdList.add(savingsFromMrpBd)
        }

        // STEP 3: Subtotal and Bill-Level Discount calculation
        val subtotalBd = lineGrossBdList.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
        val lineDiscountsTotalBd = lineDiscBdList.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }

        // The base available for bill-level discount is the sum of items after their line discounts
        val subtotalAfterLineDiscountsBd = lineNetAfterLineDiscBdList.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
        val billDiscountAppliedBd = calculateDiscountBd(subtotalAfterLineDiscountsBd, discountType)

        // STEP 4: Proportionally allocate bill-level discount to sale lines
        val allocatedBillDiscounts = mutableListOf<BigDecimal>()
        if (billDiscountAppliedBd.compareTo(BigDecimal.ZERO) > 0 && subtotalAfterLineDiscountsBd.compareTo(BigDecimal.ZERO) > 0) {
            var accumulatedAllocated = BigDecimal.ZERO
            for (i in 0 until workingItems.size - 1) {
                val weight = lineNetAfterLineDiscBdList[i]
                val share = roundMoney(
                    billDiscountAppliedBd.multiply(weight).divide(subtotalAfterLineDiscountsBd, 6, ROUNDING_MODE)
                )
                allocatedBillDiscounts.add(share)
                accumulatedAllocated = accumulatedAllocated.add(share)
            }
            // The last item absorbs any fractional remainder to ensure total matches exactly
            val lastShare = billDiscountAppliedBd.subtract(accumulatedAllocated).max(BD_ZERO)
            allocatedBillDiscounts.add(lastShare)
        } else {
            for (i in workingItems.indices) {
                allocatedBillDiscounts.add(BD_ZERO)
            }
        }

        // STEP 5: Calculate line net totals and GST distribution (selling price is GST-inclusive)
        val calculatedItems = mutableListOf<CalculatedItem>()
        var totalTaxableBd = BigDecimal.ZERO
        var totalGstBd = BigDecimal.ZERO
        var totalCgstBd = BigDecimal.ZERO
        var totalSgstBd = BigDecimal.ZERO

        for (i in workingItems.indices) {
            val item = workingItems[i]
            val lineGrossBd = lineGrossBdList[i]
            val lineDiscBd = lineDiscBdList[i]
            val allocatedBillDiscBd = allocatedBillDiscounts[i]
            val totalItemDiscBd = lineDiscBd.add(allocatedBillDiscBd)
            val lineNetTotalBd = lineGrossBd.subtract(totalItemDiscBd).max(BD_ZERO)

            val gstRate = if (item.gstRatePercentage > 0.0) item.gstRatePercentage else 0.0
            val (taxableBd, gstBd, cgstBd, sgstBd) = if (gstRate > 0.0 && lineNetTotalBd.compareTo(BigDecimal.ZERO) > 0) {
                val factor = BigDecimal.ONE.add(
                    BigDecimal.valueOf(gstRate).divide(BD_HUNDRED, 6, ROUNDING_MODE)
                )
                val taxable = roundMoney(lineNetTotalBd.divide(factor, 4, ROUNDING_MODE))
                val gst = lineNetTotalBd.subtract(taxable)
                val cgst = roundMoney(gst.divide(BD_TWO, 4, ROUNDING_MODE))
                val sgst = gst.subtract(cgst) // guarantees cgst + sgst == gst
                listOf(taxable, gst, cgst, sgst)
            } else {
                listOf(lineNetTotalBd, BD_ZERO, BD_ZERO, BD_ZERO)
            }

            totalTaxableBd = totalTaxableBd.add(taxableBd)
            totalGstBd = totalGstBd.add(gstBd)
            totalCgstBd = totalCgstBd.add(cgstBd)
            totalSgstBd = totalSgstBd.add(sgstBd)

            calculatedItems.add(
                CalculatedItem(
                    productId = item.productId,
                    barcode = item.barcode,
                    name = item.name,
                    unit = item.unit,
                    quantity = item.quantity,
                    unitPriceInclusive = item.unitPriceInclusive,
                    gstRatePercentage = gstRate,
                    mrp = item.mrp,
                    lineGrossTotal = lineGrossBd.toDouble(),
                    lineDiscount = lineDiscBd.toDouble(),
                    allocatedBillDiscount = allocatedBillDiscBd.toDouble(),
                    totalDiscount = totalItemDiscBd.toDouble(),
                    lineNetTotal = lineNetTotalBd.toDouble(),
                    taxableAmount = taxableBd.toDouble(),
                    gstAmount = gstBd.toDouble(),
                    cgstAmount = cgstBd.toDouble(),
                    sgstAmount = sgstBd.toDouble(),
                    savingsOnMrp = mrpSavingsBdList[i].add(totalItemDiscBd).toDouble()
                )
            )
        }

        // STEP 6: Summary totals, rounding, and cash calculation
        val totalDiscountAppliedBd = lineDiscountsTotalBd.add(billDiscountAppliedBd)
        val netSubtotalBd = subtotalBd.subtract(totalDiscountAppliedBd).max(BD_ZERO)

        val (finalGrandTotalBd, roundOffBd) = if (enableRupeeRounding) {
            val roundedRupees = netSubtotalBd.setScale(0, ROUNDING_MODE).setScale(CURRENCY_SCALE, ROUNDING_MODE)
            val diff = roundedRupees.subtract(netSubtotalBd)
            Pair(roundedRupees, diff)
        } else {
            Pair(netSubtotalBd, BD_ZERO)
        }

        val validatedCashBd = if (cashReceived >= 0.0) roundMoney(BigDecimal.valueOf(cashReceived)) else BD_ZERO
        val isCashSufficient = validatedCashBd.compareTo(finalGrandTotalBd) >= 0
        val changeDueBd = if (isCashSufficient) {
            validatedCashBd.subtract(finalGrandTotalBd)
        } else {
            BD_ZERO
        }

        val totalSavingsOnMrpBd = mrpSavingsBdList.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
        val grandTotalSavingsBd = totalSavingsOnMrpBd.add(totalDiscountAppliedBd)

        return BillCalculationResult(
            items = calculatedItems,
            subtotal = subtotalBd.toDouble(),
            lineDiscountsTotal = lineDiscountsTotalBd.toDouble(),
            billDiscountApplied = billDiscountAppliedBd.toDouble(),
            discountApplied = totalDiscountAppliedBd.toDouble(),
            netSubtotal = netSubtotalBd.toDouble(),
            taxableAmount = totalTaxableBd.toDouble(),
            gstAmount = totalGstBd.toDouble(),
            cgstAmount = totalCgstBd.toDouble(),
            sgstAmount = totalSgstBd.toDouble(),
            roundOff = roundOffBd.toDouble(),
            grandTotal = finalGrandTotalBd.toDouble(),
            totalSavings = grandTotalSavingsBd.toDouble(),
            totalQuantity = totalQuantity,
            cashReceived = validatedCashBd.toDouble(),
            changeDue = changeDueBd.toDouble(),
            isCashSufficient = isCashSufficient
        )
    }

    /**
     * Quick helper for change calculation on a known grand total.
     */
    fun calculateChange(grandTotal: Double, cashReceived: Double): Double {
        if (cashReceived < grandTotal) return 0.0
        return roundMoney(cashReceived - grandTotal)
    }

    /**
     * Central summary engine for reports and sales analytics.
     * Ensures reports, POS, and accounting compute figures using identical formulas.
     */
    fun calculateSalesSummary(transactions: List<SaleTransaction>): SalesSummaryResult {
        var grossBd = BigDecimal.ZERO
        var discountBd = BigDecimal.ZERO
        var netBd = BigDecimal.ZERO
        var gstBd = BigDecimal.ZERO

        for (tx in transactions) {
            if (tx.isCancelled) continue
            grossBd = grossBd.add(BigDecimal.valueOf(tx.subtotal))
            discountBd = discountBd.add(BigDecimal.valueOf(tx.discount))
            netBd = netBd.add(BigDecimal.valueOf(tx.grandTotal))
            gstBd = gstBd.add(BigDecimal.valueOf(tx.gstAmount))
        }

        grossBd = roundMoney(grossBd)
        discountBd = roundMoney(discountBd)
        netBd = roundMoney(netBd)
        gstBd = roundMoney(gstBd)
        val taxableBd = netBd.subtract(gstBd).max(BD_ZERO)
        val cgstBd = roundMoney(gstBd.divide(BD_TWO, 4, ROUNDING_MODE))
        val sgstBd = gstBd.subtract(cgstBd)

        return SalesSummaryResult(
            totalGrossSales = grossBd.toDouble(),
            totalDiscount = discountBd.toDouble(),
            totalNetSales = netBd.toDouble(),
            totalTaxableAmount = taxableBd.toDouble(),
            totalGstAmount = gstBd.toDouble(),
            totalCgstAmount = cgstBd.toDouble(),
            totalSgstAmount = sgstBd.toDouble(),
            totalOrders = transactions.count { !it.isCancelled }
        )
    }

    /**
     * Unit conversion support for decimal quantities (kg, g, litre, ml).
     */
    fun convertUnit(quantity: Double, fromUnit: String, toUnit: String): Double {
        val from = fromUnit.trim().lowercase(Locale.ENGLISH)
        val to = toUnit.trim().lowercase(Locale.ENGLISH)
        if (from == to) return quantity

        return when {
            (from == "g" || from == "gram" || from == "grams" || from == "gm") &&
                    (to == "kg" || to == "kilogram" || to == "kgs") ->
                BigDecimal.valueOf(quantity).divide(BigDecimal("1000"), 4, ROUNDING_MODE).toDouble()

            (from == "kg" || from == "kilogram" || from == "kgs") &&
                    (to == "g" || to == "gram" || to == "grams" || to == "gm") ->
                BigDecimal.valueOf(quantity).multiply(BigDecimal("1000")).toDouble()

            (from == "ml" || from == "millilitre" || from == "milliliter") &&
                    (to == "l" || to == "litre" || to == "liter" || to == "ltr") ->
                BigDecimal.valueOf(quantity).divide(BigDecimal("1000"), 4, ROUNDING_MODE).toDouble()

            (from == "l" || from == "litre" || from == "liter" || from == "ltr") &&
                    (to == "ml" || to == "millilitre" || to == "milliliter") ->
                BigDecimal.valueOf(quantity).multiply(BigDecimal("1000")).toDouble()

            else -> quantity
        }
    }
}
