package com.example.billing

import java.util.Locale

/**
 * Centralized Purchase Calculator for wholesale inventory procurement.
 *
 * CALCULATION MODEL: STRICTLY GST-EXCLUSIVE
 * Wholesale purchase rates are quoted exclusive of Goods and Services Tax (GST).
 *
 * Core Formula:
 * For each item:
 *   grossAmount = quantity × purchaseRate
 *   taxableAmount = grossAmount - lineDiscount - allocatedOverallDiscount
 *   gst = taxableAmount × (gstRate / 100)
 *   lineTotal = taxableAmount + gst
 *
 * Grand Total:
 *   grandTotal = totalTaxableAmount + totalGst
 *
 * Configurable Markup Model:
 * New products created through purchase do NOT receive arbitrary prices.
 * The selling price is either explicitly specified by the user or calculated
 * using a configurable markup percentage over the cost price.
 */
object PurchaseCalculator {

    const val MODEL_GST_EXCLUSIVE = "GST_EXCLUSIVE"

    /**
     * Default configurable markup percentage applied when user does not specify a selling price.
     * e.g. 20.0 = 20% markup over cost price.
     */
    var defaultMarkupPercentage: Double = 20.0

    /**
     * Default MRP margin percentage over selling price.
     */
    var defaultMrpMarginPercentage: Double = 10.0

    data class ItemInput(
        val quantity: Double,
        val purchaseRate: Double,
        val gstRate: Double = 0.0,
        val lineDiscount: Double = 0.0
    )

    data class ItemResult(
        val quantity: Double,
        val purchaseRate: Double,
        val grossAmount: Double,
        val lineDiscount: Double,
        val allocatedOverallDiscount: Double,
        val taxableAmount: Double,
        val gstRate: Double,
        val gstAmount: Double,
        val lineTotal: Double
    )

    data class PurchaseCalculationResult(
        val calculationModel: String = MODEL_GST_EXCLUSIVE,
        val items: List<ItemResult>,
        val grossSubtotal: Double,
        val totalLineDiscounts: Double,
        val overallDiscount: Double,
        val totalDiscount: Double,
        val totalTaxableAmount: Double,
        val totalGst: Double,
        val grandTotal: Double
    )

    /**
     * Calculate a single line item under the GST-exclusive model.
     *
     * taxableAmount = (quantity × purchaseRate) - discount
     * GST = taxableAmount × GST rate / 100
     * lineTotal = taxableAmount + GST
     */
    fun calculateSingleItem(
        quantity: Double,
        purchaseRate: Double,
        gstRate: Double = 0.0,
        lineDiscount: Double = 0.0,
        allocatedOverallDiscount: Double = 0.0
    ): ItemResult {
        require(quantity > 0.0) { "Quantity must be greater than 0" }
        require(!quantity.isNaN() && !quantity.isInfinite()) { "Invalid quantity" }
        require(purchaseRate >= 0.0) { "Purchase rate cannot be negative" }
        require(!purchaseRate.isNaN() && !purchaseRate.isInfinite()) { "Invalid purchase rate" }
        require(gstRate >= 0.0) { "GST rate cannot be negative" }
        require(lineDiscount >= 0.0) { "Line discount cannot be negative" }
        require(allocatedOverallDiscount >= 0.0) { "Allocated discount cannot be negative" }

        val gross = BillingCalculator.roundMoney(quantity * purchaseRate)
        val totalItemDiscount = BillingCalculator.roundMoney(lineDiscount + allocatedOverallDiscount)
        val taxable = BillingCalculator.roundMoney((gross - totalItemDiscount).coerceAtLeast(0.0))
        val gst = if (gstRate > 0.0) BillingCalculator.roundMoney(taxable * gstRate / 100.0) else 0.0
        val lineTotal = BillingCalculator.roundMoney(taxable + gst)

        return ItemResult(
            quantity = quantity,
            purchaseRate = purchaseRate,
            grossAmount = gross,
            lineDiscount = lineDiscount,
            allocatedOverallDiscount = allocatedOverallDiscount,
            taxableAmount = taxable,
            gstRate = gstRate,
            gstAmount = gst,
            lineTotal = lineTotal
        )
    }

    /**
     * Centralized calculation for an entire purchase order under the GST-exclusive model.
     * Allocates overall discount proportionally across items so that GST is accurately computed on taxable values.
     */
    fun calculatePurchase(
        items: List<ItemInput>,
        overallDiscount: Double = 0.0
    ): PurchaseCalculationResult {
        require(items.isNotEmpty()) { "Purchase items cannot be empty" }
        require(overallDiscount >= 0.0) { "Overall discount cannot be negative" }

        val itemGrossList = items.map { (it.quantity * it.purchaseRate) - it.lineDiscount }
        val grossSubtotal = BillingCalculator.roundMoney(items.sumOf { it.quantity * it.purchaseRate })
        val totalLineDiscounts = BillingCalculator.roundMoney(items.sumOf { it.lineDiscount })
        val totalBaseTaxable = BillingCalculator.roundMoney(itemGrossList.sumOf { it.coerceAtLeast(0.0) })

        val allocatedDiscounts = if (overallDiscount > 0.0 && totalBaseTaxable > 0.0) {
            var distributedSoFar = 0.0
            items.mapIndexed { index, item ->
                if (index == items.size - 1) {
                    // Last item absorbs any rounding remainder
                    BillingCalculator.roundMoney((overallDiscount - distributedSoFar).coerceAtLeast(0.0))
                } else {
                    val base = ((item.quantity * item.purchaseRate) - item.lineDiscount).coerceAtLeast(0.0)
                    val share = BillingCalculator.roundMoney(overallDiscount * (base / totalBaseTaxable))
                    distributedSoFar += share
                    share
                }
            }
        } else {
            List(items.size) { 0.0 }
        }

        val calculatedItems = items.mapIndexed { index, input ->
            calculateSingleItem(
                quantity = input.quantity,
                purchaseRate = input.purchaseRate,
                gstRate = input.gstRate,
                lineDiscount = input.lineDiscount,
                allocatedOverallDiscount = allocatedDiscounts[index]
            )
        }

        val totalTaxable = BillingCalculator.roundMoney(calculatedItems.sumOf { it.taxableAmount })
        val totalGst = BillingCalculator.roundMoney(calculatedItems.sumOf { it.gstAmount })
        val grandTotal = BillingCalculator.roundMoney(totalTaxable + totalGst)
        val totalDiscount = BillingCalculator.roundMoney(totalLineDiscounts + overallDiscount)

        return PurchaseCalculationResult(
            calculationModel = MODEL_GST_EXCLUSIVE,
            items = calculatedItems,
            grossSubtotal = grossSubtotal,
            totalLineDiscounts = totalLineDiscounts,
            overallDiscount = overallDiscount,
            totalDiscount = totalDiscount,
            totalTaxableAmount = totalTaxable,
            totalGst = totalGst,
            grandTotal = grandTotal
        )
    }

    /**
     * Resolves the selling price for a new product created during purchase.
     * Uses explicit user selling price if provided (> 0.0);
     * Otherwise applies the configurable markup percentage setting over cost price.
     * Never uses arbitrary fixed hardcoded multipliers like 1.15.
     */
    fun resolveSellingPrice(
        costPrice: Double,
        userSellingPrice: Double? = null,
        markupPercent: Double = defaultMarkupPercentage
    ): Double {
        require(costPrice >= 0.0) { "Cost price cannot be negative" }
        if (userSellingPrice != null && userSellingPrice > 0.0) {
            return BillingCalculator.roundMoney(userSellingPrice)
        }
        require(markupPercent >= 0.0) { "Markup percentage cannot be negative" }
        return BillingCalculator.roundMoney(costPrice * (1.0 + (markupPercent / 100.0)))
    }

    /**
     * Resolves the Maximum Retail Price (MRP) for a new product created during purchase.
     * Uses explicit user MRP if provided and valid (>= selling price);
     * Otherwise computes a reasonable MRP margin over the selling price.
     */
    fun resolveMrp(
        sellingPrice: Double,
        userMrp: Double? = null,
        marginPercent: Double = defaultMrpMarginPercentage
    ): Double {
        require(sellingPrice >= 0.0) { "Selling price cannot be negative" }
        if (userMrp != null && userMrp >= sellingPrice) {
            return BillingCalculator.roundMoney(userMrp)
        }
        val computed = BillingCalculator.roundMoney(sellingPrice * (1.0 + (marginPercent / 100.0)))
        return maxOf(sellingPrice, computed)
    }
}
