package com.example.billing

import com.example.data.model.SaleItemEntity
import com.example.data.model.SaleTransaction
import org.json.JSONArray
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Authoritative Profit and Reporting Engine for Kirana POS.
 *
 * Implements real, deterministic retail profit calculations:
 * - Revenue = actual net selling amount (grandTotal after all line & bill discounts)
 * - Cost = historical costPrice at the time of sale × quantity sold
 * - Gross Profit = Revenue - Cost
 * - Strictly excludes cancelled and refunded sales from all performance metrics.
 * - Uses the cost price stored at the time of sale in SaleItemEntity or CartItem snapshot;
 *   NEVER uses the current inventory catalog cost price for past transactions.
 * - Accounts for GST treatment, item-level discounts, and bill-level discounts.
 * - Single source of truth shared identically across Dashboard and Business Reports.
 */
object ProfitCalculator {

    data class SaleProfitBreakdown(
        val transactionId: Long,
        val invoiceNumber: String,
        val timestamp: Long,
        val isCancelled: Boolean,
        val revenue: Double, // Actual net selling amount after discounts (grandTotal)
        val cost: Double, // Historical cost price at sale time * quantity
        val grossProfit: Double, // Revenue - Cost
        val gstAmount: Double, // GST tax collected
        val taxableRevenue: Double, // Revenue - gstAmount
        val totalDiscount: Double, // Discounts applied
        val profitMarginPercent: Double, // (grossProfit / revenue) * 100
        val paymentMode: String,
        val customerName: String,
        val itemsCount: Int
    )

    data class ItemProfitBreakdown(
        val itemId: Long,
        val productId: Long,
        val productName: String,
        val quantity: Double,
        val unit: String,
        val unitSellingPrice: Double,
        val historicalCostPrice: Double,
        val lineDiscount: Double,
        val allocatedDiscount: Double,
        val totalDiscount: Double,
        val lineTax: Double,
        val revenue: Double, // Actual net line total
        val cost: Double, // historicalCostPrice * quantity
        val grossProfit: Double, // revenue - cost
        val profitMarginPercent: Double
    )

    data class PaymentBreakdown(
        val cashSales: Double = 0.0,
        val upiSales: Double = 0.0,
        val khataSales: Double = 0.0,
        val cardSales: Double = 0.0
    )

    data class PeriodReport(
        val totalSales: Double, // Total Revenue (net of discounts)
        val totalBills: Int, // Total completed non-cancelled bills
        val totalCost: Double, // Total historical cost of goods sold (COGS)
        val totalGrossProfit: Double, // Total Sales - Total Cost
        val totalGst: Double, // Total GST collected
        val totalDiscount: Double, // Total discounts provided
        val taxableRevenue: Double, // Net taxable turnover (sales - GST)
        val averageBillValue: Double, // totalSales / totalBills
        val overallMarginPercent: Double, // (totalGrossProfit / totalSales) * 100
        val paymentBreakdown: PaymentBreakdown,
        val salesBreakdown: List<SaleProfitBreakdown> = emptyList()
    )

    /**
     * Calculates profit breakdown for a single item using its historical cost price snapshot.
     */
    fun calculateItemProfit(item: SaleItemEntity): ItemProfitBreakdown {
        val totalDisc = BillingCalculator.roundMoney(item.lineDiscount + item.allocatedDiscount)
        val revenue = BillingCalculator.roundMoney(item.lineTotal)
        val cost = BillingCalculator.roundMoney(item.costPrice * item.quantity)
        val grossProfit = BillingCalculator.roundMoney(revenue - cost)
        val margin = if (revenue > 0.0) {
            BillingCalculator.roundMoney((grossProfit / revenue) * 100.0)
        } else {
            0.0
        }

        return ItemProfitBreakdown(
            itemId = item.id,
            productId = item.productId,
            productName = item.productName,
            quantity = item.quantity,
            unit = item.unit,
            unitSellingPrice = item.sellingPrice,
            historicalCostPrice = item.costPrice,
            lineDiscount = item.lineDiscount,
            allocatedDiscount = item.allocatedDiscount,
            totalDiscount = totalDisc,
            lineTax = item.lineTax,
            revenue = revenue,
            cost = cost,
            grossProfit = grossProfit,
            profitMarginPercent = margin
        )
    }

    /**
     * Calculates profit breakdown for a single sale transaction.
     * Uses the historical cost price stored in [items] or falls back to snapshot inside [transaction.itemsJson].
     * NEVER uses the current product catalog cost price.
     */
    fun calculateSaleProfit(
        transaction: SaleTransaction,
        items: List<SaleItemEntity> = emptyList()
    ): SaleProfitBreakdown {
        val isCancelled = transaction.isCancelled || transaction.cancelledAt != null

        // Historical cost of items sold
        val totalCost = if (items.isNotEmpty()) {
            items.sumOf { BillingCalculator.roundMoney(it.costPrice * it.quantity) }
        } else {
            parseHistoricalCostFromItemsJson(transaction.itemsJson)
        }

        val roundedCost = BillingCalculator.roundMoney(totalCost)
        val revenue = BillingCalculator.roundMoney(transaction.grandTotal)
        val grossProfit = BillingCalculator.roundMoney(revenue - roundedCost)
        val gstAmount = BillingCalculator.roundMoney(transaction.gstAmount)
        val taxableRevenue = BillingCalculator.roundMoney((revenue - gstAmount).coerceAtLeast(0.0))
        val totalDiscount = BillingCalculator.roundMoney(transaction.discount)

        val margin = if (revenue > 0.0) {
            BillingCalculator.roundMoney((grossProfit / revenue) * 100.0)
        } else {
            0.0
        }

        val itemsCount = if (items.isNotEmpty()) items.size else parseItemCountFromItemsJson(transaction.itemsJson)

        return SaleProfitBreakdown(
            transactionId = transaction.id,
            invoiceNumber = transaction.invoiceNumber,
            timestamp = transaction.timestamp,
            isCancelled = isCancelled,
            revenue = revenue,
            cost = roundedCost,
            grossProfit = grossProfit,
            gstAmount = gstAmount,
            taxableRevenue = taxableRevenue,
            totalDiscount = totalDiscount,
            profitMarginPercent = margin,
            paymentMode = transaction.paymentMode,
            customerName = transaction.customerName,
            itemsCount = itemsCount
        )
    }

    /**
     * Computes the aggregated period report for a given set of transactions and sale item snapshots.
     * Guaranteed to exclude cancelled sales.
     *
     * Shared calculation engine for both Dashboard and Reports Screen.
     */
    fun calculatePeriodReport(
        transactions: List<SaleTransaction>,
        allSaleItems: List<SaleItemEntity> = emptyList()
    ): PeriodReport {
        // Exclude cancelled sales
        val activeTransactions = transactions.filter { !it.isCancelled && it.cancelledAt == null }
        if (activeTransactions.isEmpty()) {
            return emptyReport()
        }

        val itemsByTx = allSaleItems.groupBy { it.transactionId }

        var totalSales = 0.0
        var totalCost = 0.0
        var totalGst = 0.0
        var totalDiscount = 0.0
        var cashSales = 0.0
        var upiSales = 0.0
        var khataSales = 0.0
        var cardSales = 0.0

        val breakdowns = mutableListOf<SaleProfitBreakdown>()

        for (tx in activeTransactions) {
            val txItems = itemsByTx[tx.id] ?: emptyList()
            val breakdown = calculateSaleProfit(tx, txItems)
            breakdowns.add(breakdown)

            totalSales += breakdown.revenue
            totalCost += breakdown.cost
            totalGst += breakdown.gstAmount
            totalDiscount += breakdown.totalDiscount

            when (tx.paymentMode.uppercase().trim()) {
                "CASH" -> cashSales += breakdown.revenue
                "UPI" -> upiSales += breakdown.revenue
                "KHATA" -> khataSales += breakdown.revenue
                else -> cardSales += breakdown.revenue
            }
        }

        val roundedTotalSales = BillingCalculator.roundMoney(totalSales)
        val roundedTotalCost = BillingCalculator.roundMoney(totalCost)
        val roundedTotalGrossProfit = BillingCalculator.roundMoney(roundedTotalSales - roundedTotalCost)
        val roundedTotalGst = BillingCalculator.roundMoney(totalGst)
        val roundedTotalDiscount = BillingCalculator.roundMoney(totalDiscount)
        val taxableRevenue = BillingCalculator.roundMoney((roundedTotalSales - roundedTotalGst).coerceAtLeast(0.0))

        val count = activeTransactions.size
        val avgBill = if (count > 0) BillingCalculator.roundMoney(roundedTotalSales / count) else 0.0
        val overallMargin = if (roundedTotalSales > 0.0) {
            BillingCalculator.roundMoney((roundedTotalGrossProfit / roundedTotalSales) * 100.0)
        } else {
            0.0
        }

        return PeriodReport(
            totalSales = roundedTotalSales,
            totalBills = count,
            totalCost = roundedTotalCost,
            totalGrossProfit = roundedTotalGrossProfit,
            totalGst = roundedTotalGst,
            totalDiscount = roundedTotalDiscount,
            taxableRevenue = taxableRevenue,
            averageBillValue = avgBill,
            overallMarginPercent = overallMargin,
            paymentBreakdown = PaymentBreakdown(
                cashSales = BillingCalculator.roundMoney(cashSales),
                upiSales = BillingCalculator.roundMoney(upiSales),
                khataSales = BillingCalculator.roundMoney(khataSales),
                cardSales = BillingCalculator.roundMoney(cardSales)
            ),
            salesBreakdown = breakdowns
        )
    }

    /**
     * Parses historical costPrice snapshot from [itemsJson] when SaleItemEntity rows
     * are not available (e.g. legacy data).
     * Extracts `costPrice` stored at the exact time of the sale.
     */
    fun parseHistoricalCostFromItemsJson(itemsJson: String): Double {
        if (itemsJson.isBlank()) return 0.0
        return try {
            val array = JSONArray(itemsJson)
            var sum = 0.0
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val cost = obj.optDouble("costPrice", 0.0)
                val qty = obj.optDouble("quantity", 1.0)
                sum += (cost * qty)
            }
            BillingCalculator.roundMoney(sum)
        } catch (_: Exception) {
            0.0
        }
    }

    private fun parseItemCountFromItemsJson(itemsJson: String): Int {
        if (itemsJson.isBlank()) return 0
        return try {
            JSONArray(itemsJson).length()
        } catch (_: Exception) {
            0
        }
    }

    fun emptyReport(): PeriodReport {
        return PeriodReport(
            totalSales = 0.0,
            totalBills = 0,
            totalCost = 0.0,
            totalGrossProfit = 0.0,
            totalGst = 0.0,
            totalDiscount = 0.0,
            taxableRevenue = 0.0,
            averageBillValue = 0.0,
            overallMarginPercent = 0.0,
            paymentBreakdown = PaymentBreakdown(),
            salesBreakdown = emptyList()
        )
    }
}
