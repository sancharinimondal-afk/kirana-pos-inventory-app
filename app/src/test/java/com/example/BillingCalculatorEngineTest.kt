package com.example

import com.example.billing.BillCalculationResult
import com.example.billing.BillingCalculator
import com.example.billing.BillingItemInput
import com.example.billing.DiscountType
import com.example.billing.MrpViolationException
import com.example.data.model.CartItem
import com.example.data.model.SaleTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class BillingCalculatorEngineTest {

    // =========================================================================
    // 1. GST CALCULATIONS
    // Formula for GST-inclusive price:
    // taxableAmount = grossAmount / (1 + GST/100)
    // GST = grossAmount - taxableAmount
    // CGST = GST / 2
    // SGST = GST / 2
    // Consistent rounding
    // =========================================================================

    @Test
    fun testGstInclusiveCalculation() {
        // Product: Selling price ₹105 (inclusive of 5% GST)
        // taxable = 105 / 1.05 = 100.00
        // GST = 105 - 100 = 5.00
        // CGST = 2.50, SGST = 2.50
        val items = listOf(
            BillingItemInput(
                productId = 1L,
                name = "Packaged Mustard Oil 1L",
                quantity = 1.0,
                unitPriceInclusive = 105.00,
                gstRatePercentage = 5.0,
                mrp = 110.00
            )
        )

        val result = BillingCalculator.calculateBill(items)
        assertEquals("Subtotal must be 105.00", 105.00, result.subtotal, 0.001)
        assertEquals("Taxable base must be 100.00", 100.00, result.taxableAmount, 0.001)
        assertEquals("GST must be 5.00", 5.00, result.gstAmount, 0.001)
        assertEquals("CGST must be 2.50", 2.50, result.cgstAmount, 0.001)
        assertEquals("SGST must be 2.50", 2.50, result.sgstAmount, 0.001)
        assertEquals("CGST + SGST must exactly equal total GST", result.gstAmount, result.cgstAmount + result.sgstAmount, 0.001)
        assertEquals("Grand total must be 105.00", 105.00, result.grandTotal, 0.001)
    }

    @Test
    fun testGstRates12And18Percent() {
        // Item 1: ₹112 inclusive of 12% GST -> Taxable = 100.00, GST = 12.00, CGST = 6.00, SGST = 6.00
        // Item 2: ₹118 inclusive of 18% GST -> Taxable = 100.00, GST = 18.00, CGST = 9.00, SGST = 9.00
        val items = listOf(
            BillingItemInput(
                productId = 1L,
                name = "Processed Cheese",
                quantity = 1.0,
                unitPriceInclusive = 112.00,
                gstRatePercentage = 12.0,
                mrp = 120.00
            ),
            BillingItemInput(
                productId = 2L,
                name = "Washing Powder",
                quantity = 1.0,
                unitPriceInclusive = 118.00,
                gstRatePercentage = 18.0,
                mrp = 125.00
            )
        )

        val result = BillingCalculator.calculateBill(items)
        assertEquals(230.00, result.subtotal, 0.001)
        assertEquals(200.00, result.taxableAmount, 0.001)
        assertEquals(30.00, result.gstAmount, 0.001)
        assertEquals(15.00, result.cgstAmount, 0.001)
        assertEquals(15.00, result.sgstAmount, 0.001)
        assertEquals(result.gstAmount, result.cgstAmount + result.sgstAmount, 0.001)
        assertEquals(230.00, result.grandTotal, 0.001)
    }

    @Test
    fun testZeroGstProduct() {
        val items = listOf(
            BillingItemInput(
                productId = 1L,
                name = "Fresh Cow Milk 1L",
                quantity = 2.0,
                unitPriceInclusive = 60.0,
                gstRatePercentage = 0.0,
                mrp = 65.0
            )
        )

        val result = BillingCalculator.calculateBill(items)
        assertEquals(120.00, result.subtotal, 0.001)
        assertEquals(0.0, result.gstAmount, 0.001)
        assertEquals(120.00, result.taxableAmount, 0.001)
        assertEquals(120.00, result.grandTotal, 0.001)
        assertEquals(10.00, result.totalSavings, 0.001)
    }

    // =========================================================================
    // 2. DISCOUNT RULES & IMPLEMENTATIONS
    // - Percentage discount (0-100%)
    // - Fixed discount
    // - Line discount
    // - Bill-level discount
    // - Cannot be negative
    // - Cannot exceed subtotal
    // - Must be applied exactly once
    // - Bill-level discount must be proportionally allocated to sale lines
    // =========================================================================

    @Test
    fun testDiscountValidationRules() {
        val items = listOf(
            BillingItemInput(
                productId = 1L,
                name = "Basmati Rice 5kg",
                quantity = 1.0,
                unitPriceInclusive = 500.00,
                gstRatePercentage = 5.0,
                mrp = 550.00
            )
        )

        // Negative fixed discount clamped to 0.0
        val negativeFixedResult = BillingCalculator.calculateBill(
            items = items,
            discountType = DiscountType.FixedAmount(-50.00)
        )
        assertEquals(0.0, negativeFixedResult.discountApplied, 0.001)
        assertEquals(500.00, negativeFixedResult.grandTotal, 0.001)

        // Negative percentage discount clamped to 0.0
        val negativePctResult = BillingCalculator.calculateBill(
            items = items,
            discountType = DiscountType.Percentage(-15.0)
        )
        assertEquals(0.0, negativePctResult.discountApplied, 0.001)
        assertEquals(500.00, negativePctResult.grandTotal, 0.001)

        // Fixed discount exceeding subtotal capped at subtotal
        val excessiveFixedResult = BillingCalculator.calculateBill(
            items = items,
            discountType = DiscountType.FixedAmount(650.00)
        )
        assertEquals(500.00, excessiveFixedResult.discountApplied, 0.001)
        assertEquals(0.0, excessiveFixedResult.grandTotal, 0.001)

        // Percentage discount exceeding 100% capped at 100%
        val excessivePctResult = BillingCalculator.calculateBill(
            items = items,
            discountType = DiscountType.Percentage(150.0)
        )
        assertEquals(500.00, excessivePctResult.discountApplied, 0.001)
        assertEquals(0.0, excessivePctResult.grandTotal, 0.001)

        // Valid fixed discount
        val validFixedResult = BillingCalculator.calculateBill(
            items = items,
            discountType = DiscountType.FixedAmount(50.00)
        )
        assertEquals(50.00, validFixedResult.discountApplied, 0.001)
        assertEquals(450.00, validFixedResult.grandTotal, 0.001)

        // Valid percentage discount
        val validPctResult = BillingCalculator.calculateBill(
            items = items,
            discountType = DiscountType.Percentage(10.0)
        )
        assertEquals(50.00, validPctResult.discountApplied, 0.001)
        assertEquals(450.00, validPctResult.grandTotal, 0.001)
    }

    @Test
    fun testLineDiscountAndBillLevelDiscountCombination() {
        // Item 1: gross 200, line discount 20 -> net before bill disc = 180
        // Item 2: gross 100, line discount 0  -> net before bill disc = 100
        // Total before bill discount = 280
        // Bill discount: fixed 28.00 (10% overall)
        // Proportional allocation:
        // Item 1 weight: 180 / 280 * 28 = 18.00
        // Item 2 weight: 100 / 280 * 28 = 10.00
        val items = listOf(
            BillingItemInput(
                productId = 1L,
                name = "Item 1",
                quantity = 2.0,
                unitPriceInclusive = 100.00,
                mrp = 110.00,
                lineDiscount = 20.00
            ),
            BillingItemInput(
                productId = 2L,
                name = "Item 2",
                quantity = 1.0,
                unitPriceInclusive = 100.00,
                mrp = 100.00,
                lineDiscount = 0.0
            )
        )

        val result = BillingCalculator.calculateBill(
            items = items,
            discountType = DiscountType.FixedAmount(28.00)
        )

        assertEquals("Subtotal must be 300.00", 300.00, result.subtotal, 0.001)
        assertEquals("Line discounts total must be 20.00", 20.00, result.lineDiscountsTotal, 0.001)
        assertEquals("Bill discount applied must be 28.00", 28.00, result.billDiscountApplied, 0.001)
        assertEquals("Total discount applied must be 48.00", 48.00, result.discountApplied, 0.001)
        assertEquals("Net grand total must be 252.00", 252.00, result.grandTotal, 0.001)

        // Check line 1 allocation
        val line1 = result.items[0]
        assertEquals(20.00, line1.lineDiscount, 0.001)
        assertEquals(18.00, line1.allocatedBillDiscount, 0.001)
        assertEquals(38.00, line1.totalDiscount, 0.001)
        assertEquals(162.00, line1.lineNetTotal, 0.001)

        // Check line 2 allocation
        val line2 = result.items[1]
        assertEquals(0.0, line2.lineDiscount, 0.001)
        assertEquals(10.00, line2.allocatedBillDiscount, 0.001)
        assertEquals(10.00, line2.totalDiscount, 0.001)
        assertEquals(90.00, line2.lineNetTotal, 0.001)

        // Sum of allocated bill discounts must exactly equal bill discount
        assertEquals(
            result.billDiscountApplied,
            line1.allocatedBillDiscount + line2.allocatedBillDiscount,
            0.001
        )
    }

    // =========================================================================
    // 3. MRP RULE
    // sellingPrice <= MRP
    // If sellingPrice > MRP: BLOCK the operation.
    // =========================================================================

    @Test
    fun testMrpViolationBlocksOperation() {
        val invalidItems = listOf(
            BillingItemInput(
                productId = 1L,
                name = "Amul Butter 500g",
                quantity = 1.0,
                unitPriceInclusive = 300.00, // selling price ₹300
                mrp = 280.00 // MRP ₹280 -> VIOLATION
            )
        )

        val exception = assertThrows(MrpViolationException::class.java) {
            BillingCalculator.calculateBill(invalidItems)
        }
        assertTrue("Exception must state selling price exceeds MRP", exception.message!!.contains("exceed MRP"))
    }

    @Test
    fun testSellingPriceEqualsMrpAllowed() {
        val validItems = listOf(
            BillingItemInput(
                productId = 1L,
                name = "Parle-G Biscuit",
                quantity = 5.0,
                unitPriceInclusive = 10.00,
                mrp = 10.00
            )
        )

        val result = BillingCalculator.calculateBill(validItems)
        assertEquals(50.00, result.subtotal, 0.001)
        assertEquals(50.00, result.grandTotal, 0.001)
    }

    // =========================================================================
    // 4. DECIMAL QUANTITIES
    // kg, g, litre, ml
    // =========================================================================

    @Test
    fun testDecimalQuantitiesForKgAndLitre() {
        // 1.750 kg Rice @ ₹60/kg = 105.00
        // 0.500 litre Oil @ ₹150/litre = 75.00
        val items = listOf(
            BillingItemInput(
                productId = 1L,
                name = "Loose Basmati Rice",
                unit = "kg",
                quantity = 1.750,
                unitPriceInclusive = 60.00,
                mrp = 65.00
            ),
            BillingItemInput(
                productId = 2L,
                name = "Loose Sunflower Oil",
                unit = "litre",
                quantity = 0.500,
                unitPriceInclusive = 150.00,
                mrp = 160.00
            )
        )

        val result = BillingCalculator.calculateBill(items)
        assertEquals("Subtotal: 105 + 75 = 180.00", 180.00, result.subtotal, 0.001)
        assertEquals("Total quantity: 1.75 + 0.5 = 2.25", 2.25, result.totalQuantity, 0.001)
        assertEquals(180.00, result.grandTotal, 0.001)
    }

    @Test
    fun testUnitConversionForGramAndMl() {
        // 500 g converted to kg is 0.5 kg
        val kg = BillingCalculator.convertUnit(500.0, "g", "kg")
        assertEquals(0.5, kg, 0.001)

        // 250 ml converted to litre is 0.25 litre
        val litre = BillingCalculator.convertUnit(250.0, "ml", "litre")
        assertEquals(0.25, litre, 0.001)

        // 1.5 kg converted to grams is 1500 g
        val grams = BillingCalculator.convertUnit(1.5, "kg", "g")
        assertEquals(1500.0, grams, 0.001)
    }

    // =========================================================================
    // 5. DUPLICATE CART ITEMS AGGREGATION
    // Aggregate duplicate products before stock validation
    // =========================================================================

    @Test
    fun testAggregateDuplicateCartItems() {
        val cartItems = listOf(
            CartItem(
                productId = 10L,
                barcode = "890123",
                name = "Aashirvaad Atta 5kg",
                unit = "kg",
                rate = 200.0,
                quantity = 2.0,
                mrp = 220.0,
                lineDiscount = 5.0
            ),
            CartItem(
                productId = 20L,
                barcode = "890456",
                name = "Tata Salt 1kg",
                unit = "Packet",
                rate = 25.0,
                quantity = 1.0,
                mrp = 28.0,
                lineDiscount = 0.0
            ),
            CartItem(
                productId = 10L, // Duplicate product
                barcode = "890123",
                name = "Aashirvaad Atta 5kg",
                unit = "kg",
                rate = 200.0,
                quantity = 3.0,
                mrp = 220.0,
                lineDiscount = 10.0
            )
        )

        val aggregated = BillingCalculator.aggregateCartItems(cartItems)
        assertEquals("Must aggregate down to 2 distinct products", 2, aggregated.size)

        val atta = aggregated.first { it.productId == 10L }
        assertEquals("Quantity must be 2.0 + 3.0 = 5.0", 5.0, atta.quantity, 0.001)
        assertEquals("Line discount must be 5.0 + 10.0 = 15.0", 15.0, atta.lineDiscount, 0.001)
        assertEquals(200.0, atta.rate, 0.001)
        assertEquals(220.0, atta.mrp, 0.001)

        val salt = aggregated.first { it.productId == 20L }
        assertEquals(1.0, salt.quantity, 0.001)
    }

    @Test
    fun testCalculateBillWithAutoAggregateDuplicates() {
        val items = listOf(
            BillingItemInput(
                productId = 1L,
                name = "Milk",
                quantity = 1.0,
                unitPriceInclusive = 30.0,
                mrp = 32.0
            ),
            BillingItemInput(
                productId = 1L, // Duplicate
                name = "Milk",
                quantity = 2.0,
                unitPriceInclusive = 30.0,
                mrp = 32.0
            )
        )

        val result = BillingCalculator.calculateBill(items, autoAggregateDuplicates = true)
        assertEquals(1, result.items.size)
        assertEquals(3.0, result.items[0].quantity, 0.001)
        assertEquals(90.00, result.subtotal, 0.001)
        assertEquals(90.00, result.grandTotal, 0.001)
    }

    // =========================================================================
    // 6. MULTIPLE ITEMS & ROUNDING CONSISTENCY
    // =========================================================================

    @Test
    fun testMultipleItemsConsistentRounding() {
        val items = listOf(
            // 1.35 kg Sugar @ 43.50 = 58.725 -> rounds to 58.73
            BillingItemInput(
                productId = 1L,
                name = "Madhur Sugar (Loose)",
                quantity = 1.35,
                unitPriceInclusive = 43.50,
                gstRatePercentage = 5.0,
                mrp = 48.00
            ),
            // 2.65 kg Atta @ 38.25 = 101.3625 -> rounds to 101.36
            BillingItemInput(
                productId = 2L,
                name = "Wheat Atta (Loose)",
                quantity = 2.65,
                unitPriceInclusive = 38.25,
                gstRatePercentage = 0.0,
                mrp = 42.00
            )
        )

        val result = BillingCalculator.calculateBill(items)
        // 58.73 + 101.36 = 160.09
        assertEquals(160.09, result.subtotal, 0.001)
        assertEquals(160.09, result.grandTotal, 0.001)

        // Rupee rounding test
        val roundedResult = BillingCalculator.calculateBill(items, enableRupeeRounding = true)
        assertEquals(160.00, roundedResult.grandTotal, 0.001)
        assertEquals(-0.09, roundedResult.roundOff, 0.001)
    }

    // =========================================================================
    // 7. CASH RECEIVED & CHANGE
    // =========================================================================

    @Test
    fun testCashReceivedAndChange() {
        val items = listOf(
            BillingItemInput(
                productId = 1L,
                name = "Tata Tea Gold 500g",
                quantity = 1.0,
                unitPriceInclusive = 340.00,
                gstRatePercentage = 5.0,
                mrp = 360.00
            )
        )

        // Exact cash
        val exactCash = BillingCalculator.calculateBill(items, cashReceived = 340.00)
        assertTrue(exactCash.isCashSufficient)
        assertEquals(0.0, exactCash.changeDue, 0.001)

        // Change due
        val changeCash = BillingCalculator.calculateBill(items, cashReceived = 500.00)
        assertTrue(changeCash.isCashSufficient)
        assertEquals(160.00, changeCash.changeDue, 0.001)

        // Short payment
        val shortCash = BillingCalculator.calculateBill(items, cashReceived = 300.00)
        assertFalse(shortCash.isCashSufficient)
        assertEquals(0.0, shortCash.changeDue, 0.001)
    }

    // =========================================================================
    // 8. SALES SUMMARY REPORTS CALCULATION ENGINE
    // POS, invoices, receipts, reports and stored transactions must use the same engine
    // =========================================================================

    @Test
    fun testSalesSummaryMatchesCalculationEngine() {
        val transactions = listOf(
            SaleTransaction(
                id = 1,
                invoiceNumber = "INV-001",
                subtotal = 500.0,
                discount = 50.0,
                gstAmount = 21.43,
                grandTotal = 450.0,
                itemsJson = "[]"
            ),
            SaleTransaction(
                id = 2,
                invoiceNumber = "INV-002",
                subtotal = 300.0,
                discount = 0.0,
                gstAmount = 14.29,
                grandTotal = 300.0,
                itemsJson = "[]"
            ),
            SaleTransaction(
                id = 3,
                invoiceNumber = "INV-003",
                subtotal = 1000.0,
                discount = 100.0,
                gstAmount = 50.0,
                grandTotal = 900.0,
                itemsJson = "[]",
                isCancelled = true // cancelled transaction must be excluded
            )
        )

        val summary = BillingCalculator.calculateSalesSummary(transactions)
        assertEquals(2, summary.totalOrders)
        assertEquals(800.00, summary.totalGrossSales, 0.001)
        assertEquals(50.00, summary.totalDiscount, 0.001)
        assertEquals(750.00, summary.totalNetSales, 0.001)
        assertEquals(35.72, summary.totalGstAmount, 0.001)
        assertEquals(17.86, summary.totalCgstAmount, 0.001)
        assertEquals(17.86, summary.totalSgstAmount, 0.001)
        assertEquals(summary.totalGstAmount, summary.totalCgstAmount + summary.totalSgstAmount, 0.001)
    }
}
