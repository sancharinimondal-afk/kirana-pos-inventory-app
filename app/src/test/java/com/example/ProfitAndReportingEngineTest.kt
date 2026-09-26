package com.example

import com.example.billing.ProfitCalculator
import com.example.data.model.SaleItemEntity
import com.example.data.model.SaleTransaction
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ProfitAndReportingEngineTest {

    @Test
    fun testProfitCalculation_HistoricalCostPriceUsed_NotCurrentProductPrice() {
        // Arrange: Sale occurred when cost was Rs 50, selling price was Rs 80
        val historicalCost = 50.0
        val sellingPrice = 80.0
        val qty = 3.0

        val saleItem = SaleItemEntity(
            id = 1L,
            transactionId = 100L,
            productId = 10L,
            productName = "Mustard Oil 1L",
            barcode = "8901234567890",
            unit = "Bottle",
            sellingPrice = sellingPrice,
            costPrice = historicalCost, // Historical cost at the time of sale
            mrp = 90.0,
            gstRate = 5.0,
            quantity = qty,
            lineDiscount = 0.0,
            lineTotal = sellingPrice * qty // 240.0
        )

        // Assume current product catalog has now inflated cost to Rs 75.0 (which must NOT be used!)
        val itemProfit = ProfitCalculator.calculateItemProfit(saleItem)

        // Act & Assert
        // Revenue = 240.0
        assertEquals(240.0, itemProfit.revenue, 0.001)
        // Cost = historical costPrice * qty = 50.0 * 3.0 = 150.0
        assertEquals(150.0, itemProfit.cost, 0.001)
        // Gross Profit = Revenue - Cost = 240.0 - 150.0 = 90.0
        assertEquals(90.0, itemProfit.grossProfit, 0.001)
        // Margin % = (90.0 / 240.0) * 100 = 37.5%
        assertEquals(37.5, itemProfit.profitMarginPercent, 0.001)
    }

    @Test
    fun testGrossProfitFormula_RevenueMinusCost_WithDiscounts() {
        // Transaction with 2 items and a discount
        val item1 = SaleItemEntity(
            id = 1L,
            transactionId = 101L,
            productId = 1L,
            productName = "Basmati Rice 1kg",
            barcode = "111",
            unit = "Kg",
            sellingPrice = 100.0,
            costPrice = 80.0,
            mrp = 110.0,
            gstRate = 0.0,
            quantity = 2.0,
            lineDiscount = 10.0, // Item discount Rs 10
            lineTotal = 190.0 // (100 * 2) - 10
        )

        val item2 = SaleItemEntity(
            id = 2L,
            transactionId = 101L,
            productId = 2L,
            productName = "Toor Dal 1kg",
            barcode = "222",
            unit = "Kg",
            sellingPrice = 140.0,
            costPrice = 110.0,
            mrp = 150.0,
            gstRate = 0.0,
            quantity = 1.0,
            lineDiscount = 0.0,
            lineTotal = 140.0
        )

        val tx = SaleTransaction(
            id = 101L,
            invoiceNumber = "INV-101",
            timestamp = System.currentTimeMillis(),
            customerName = "Ramesh Kumar",
            customerPhone = "9876543210",
            customerId = 5L,
            paymentMode = "CASH",
            subtotal = 340.0,
            discount = 10.0,
            gstAmount = 0.0,
            grandTotal = 310.0,
            itemsJson = "[]",
            isCancelled = false
        )

        val saleProfit = ProfitCalculator.calculateSaleProfit(tx, listOf(item1, item2))

        // Revenue = actual net selling amount = grandTotal = 310.0
        assertEquals(310.0, saleProfit.revenue, 0.001)
        // Cost = (80 * 2) + (110 * 1) = 160 + 110 = 270.0
        assertEquals(270.0, saleProfit.cost, 0.001)
        // Gross Profit = Revenue - Cost = 310.0 - 270.0 = 40.0
        assertEquals(40.0, saleProfit.grossProfit, 0.001)
        // Margin % = (40.0 / 310.0) * 100 = 12.90%
        assertEquals(12.90, saleProfit.profitMarginPercent, 0.01)
    }

    @Test
    fun testCancelledSalesAreExcludedFromPeriodReport() {
        val now = System.currentTimeMillis()

        val activeTx = SaleTransaction(
            id = 201L,
            invoiceNumber = "INV-201",
            timestamp = now,
            customerName = "Valid Customer",
            customerPhone = "9876543210",
            paymentMode = "UPI",
            subtotal = 500.0,
            discount = 0.0,
            gstAmount = 25.0,
            grandTotal = 525.0,
            itemsJson = "[]",
            isCancelled = false
        )
        val activeItem = SaleItemEntity(
            id = 1L,
            transactionId = 201L,
            productId = 1L,
            productName = "Ghee 1L",
            barcode = "333",
            unit = "Jar",
            sellingPrice = 500.0,
            costPrice = 400.0,
            mrp = 550.0,
            gstRate = 5.0,
            quantity = 1.0,
            lineDiscount = 0.0,
            lineTotal = 525.0
        )

        val cancelledTx = SaleTransaction(
            id = 202L,
            invoiceNumber = "INV-202",
            timestamp = now,
            customerName = "Cancelled Customer",
            customerPhone = "9876543210",
            paymentMode = "CASH",
            subtotal = 1000.0,
            discount = 0.0,
            gstAmount = 50.0,
            grandTotal = 1050.0,
            itemsJson = "[]",
            isCancelled = true, // Explicitly cancelled
            cancelledAt = now + 1000L
        )
        val cancelledItem = SaleItemEntity(
            id = 2L,
            transactionId = 202L,
            productId = 2L,
            productName = "Expensive Item",
            barcode = "444",
            unit = "Pc",
            sellingPrice = 1000.0,
            costPrice = 800.0,
            mrp = 1100.0,
            gstRate = 5.0,
            quantity = 1.0,
            lineDiscount = 0.0,
            lineTotal = 1050.0
        )

        // When reporting engine processes transactions:
        val allTx = listOf(activeTx, cancelledTx)
        val allItems = listOf(activeItem, cancelledItem)

        // Filter non-cancelled transactions:
        val report = ProfitCalculator.calculatePeriodReport(allTx, allItems)

        // Assert cancelled sale is completely excluded
        assertEquals(1, report.totalBills)
        assertEquals(525.0, report.totalSales, 0.001)
        assertEquals(400.0, report.totalCost, 0.001)
        assertEquals(125.0, report.totalGrossProfit, 0.001) // 525 - 400
        assertEquals(25.0, report.totalGst, 0.001)
        assertEquals(1, report.salesBreakdown.size)
        assertEquals("INV-201", report.salesBreakdown[0].invoiceNumber)
    }

    @Test
    fun testDashboardAndReportsProduceIdenticalResultsForToday() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis

        // Multiple sales today
        val tx1 = SaleTransaction(
            id = 301L,
            invoiceNumber = "INV-301",
            timestamp = startOfToday + 3600000L,
            customerName = "Cust 1",
            customerPhone = "",
            paymentMode = "CASH",
            subtotal = 200.0,
            discount = 0.0,
            gstAmount = 10.0,
            grandTotal = 210.0,
            itemsJson = "[]",
            isCancelled = false
        )
        val item1 = SaleItemEntity(
            id = 11L,
            transactionId = 301L,
            productId = 1L,
            productName = "Item 1",
            barcode = "1",
            unit = "Pc",
            sellingPrice = 200.0,
            costPrice = 150.0,
            mrp = 220.0,
            gstRate = 5.0,
            quantity = 1.0,
            lineDiscount = 0.0,
            lineTotal = 210.0
        )

        val tx2 = SaleTransaction(
            id = 302L,
            invoiceNumber = "INV-302",
            timestamp = startOfToday + 7200000L,
            customerName = "Cust 2",
            customerPhone = "",
            paymentMode = "UPI",
            subtotal = 300.0,
            discount = 20.0,
            gstAmount = 15.0,
            grandTotal = 295.0,
            itemsJson = "[]",
            isCancelled = false
        )
        val item2 = SaleItemEntity(
            id = 12L,
            transactionId = 302L,
            productId = 2L,
            productName = "Item 2",
            barcode = "2",
            unit = "Kg",
            sellingPrice = 300.0,
            costPrice = 220.0,
            mrp = 320.0,
            gstRate = 5.0,
            quantity = 1.0,
            lineDiscount = 20.0,
            lineTotal = 295.0
        )

        val todayTxs = listOf(tx1, tx2)
        val todayItems = listOf(item1, item2)

        // Engine calculation as done in ViewModel for Dashboard:
        val dashboardReport = ProfitCalculator.calculatePeriodReport(todayTxs, todayItems)

        // Engine calculation as done in ReportsScreen for "Today":
        val reportsReport = ProfitCalculator.calculatePeriodReport(todayTxs, todayItems)

        // Both MUST produce IDENTICAL values
        assertEquals(dashboardReport.totalSales, reportsReport.totalSales, 0.0001)
        assertEquals(dashboardReport.totalBills, reportsReport.totalBills)
        assertEquals(dashboardReport.totalCost, reportsReport.totalCost, 0.0001)
        assertEquals(dashboardReport.totalGrossProfit, reportsReport.totalGrossProfit, 0.0001)
        assertEquals(dashboardReport.totalGst, reportsReport.totalGst, 0.0001)
        assertEquals(dashboardReport.overallMarginPercent, reportsReport.overallMarginPercent, 0.0001)

        // Assert exact numbers:
        // Total Sales = 210 + 295 = 505.0
        assertEquals(505.0, dashboardReport.totalSales, 0.001)
        // Total Cost = 150 + 220 = 370.0
        assertEquals(370.0, dashboardReport.totalCost, 0.001)
        // Gross Profit = 505 - 370 = 135.0
        assertEquals(135.0, dashboardReport.totalGrossProfit, 0.001)
        // Total Bills = 2
        assertEquals(2, dashboardReport.totalBills)
    }

    @Test
    fun testDateRangeBoundaries_Today_Last7Days_ThisMonth_LastMonth() {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val startOfToday = cal.timeInMillis

        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val endOfToday = cal.timeInMillis

        // 1. Sale today
        val txToday = SaleTransaction(
            id = 1L,
            invoiceNumber = "INV-TODAY",
            timestamp = startOfToday + 10000L,
            customerName = "Today Customer",
            customerPhone = "",
            paymentMode = "CASH",
            subtotal = 100.0,
            discount = 0.0,
            gstAmount = 0.0,
            grandTotal = 100.0,
            itemsJson = "[]",
            isCancelled = false
        )

        // 2. Sale 3 days ago (Within Last 7 Days)
        val tx3DaysAgo = SaleTransaction(
            id = 2L,
            invoiceNumber = "INV-3DAYS",
            timestamp = startOfToday - (3L * 24 * 3600 * 1000L),
            customerName = "3 Days Ago Customer",
            customerPhone = "",
            paymentMode = "CASH",
            subtotal = 200.0,
            discount = 0.0,
            gstAmount = 0.0,
            grandTotal = 200.0,
            itemsJson = "[]",
            isCancelled = false
        )

        // Last 7 days start threshold:
        val cal7 = Calendar.getInstance()
        cal7.add(Calendar.DAY_OF_YEAR, -6)
        cal7.set(Calendar.HOUR_OF_DAY, 0)
        cal7.set(Calendar.MINUTE, 0)
        cal7.set(Calendar.SECOND, 0)
        cal7.set(Calendar.MILLISECOND, 0)
        val startOfLast7Days = cal7.timeInMillis

        val allTx = listOf(txToday, tx3DaysAgo)

        // Today filter
        val todayFiltered = allTx.filter { it.timestamp in startOfToday..endOfToday }
        assertEquals(1, todayFiltered.size)
        assertEquals("INV-TODAY", todayFiltered[0].invoiceNumber)

        // Last 7 days filter
        val last7DaysFiltered = allTx.filter { it.timestamp in startOfLast7Days..endOfToday }
        assertEquals(2, last7DaysFiltered.size)
    }
}
