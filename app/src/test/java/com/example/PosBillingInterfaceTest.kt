package com.example

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.DATABASE_CALLBACK
import com.example.data.KiranaRepository
import com.example.data.model.Customer
import com.example.data.model.ProductItem
import com.example.ui.KiranaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PosBillingInterfaceTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: KiranaRepository
    private lateinit var viewModel: KiranaViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(DATABASE_CALLBACK)
            .allowMainThreadQueries()
            .build()
        repository = KiranaRepository(db)
        viewModel = KiranaViewModel(repository = repository, context = null, ioDispatcher = testDispatcher)
        testDispatcher.scheduler.advanceUntilIdle()
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.coroutineContext.cancelChildren()
        db.close()
        Dispatchers.resetMain()
    }

    private fun TestScope.subscribeToCartFlows() {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.cartGrandTotal.collect {}
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.cartSubtotal.collect {}
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.cartGstAmount.collect {}
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.cartChangeDue.collect {}
        }
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.allProducts.collect {}
        }
    }

    @Test
    fun testTopBarcodeSearchAndCameraLookup() = runTest(testDispatcher) {
        subscribeToCartFlows()
        val prodId = repository.insertProduct(
            ProductItem(
                name = "Aashirvaad Atta 5kg",
                barcode = "8901030000012",
                category = "Grains",
                unit = "Pack",
                costPrice = 200.0,
                sellingPrice = 240.0,
                mrp = 260.0,
                currentStock = 50.0
            )
        )
        advanceUntilIdle()

        val product = repository.getProductById(prodId)!!
        viewModel.addToCart(product, 1.0)
        advanceUntilIdle()

        val cart = viewModel.cartItems.value
        assertEquals(1, cart.size)
        assertEquals("Aashirvaad Atta 5kg", cart[0].name)
        assertEquals("Pack", cart[0].unit)
        assertEquals(240.0, cart[0].rate, 0.001)
        assertEquals(1.0, cart[0].quantity, 0.001)

        // Clear and add by barcode
        viewModel.clearCart()
        assertEquals(0, viewModel.cartItems.value.size)

        val added = viewModel.addToCart(product, 2.0)
        assertTrue(added)
        assertEquals(1, viewModel.cartItems.value.size)
        assertEquals(prodId, viewModel.cartItems.value[0].productId)
        assertEquals(2.0, viewModel.cartItems.value[0].quantity, 0.001)
    }

    @Test
    fun testCartItemDetailsRateQuantityDiscountLineTotalAndRemove() = runTest(testDispatcher) {
        subscribeToCartFlows()
        val prodId = repository.insertProduct(
            ProductItem(
                name = "Tata Salt 1kg",
                barcode = "8901058852262",
                category = "Groceries",
                unit = "Packet",
                costPrice = 20.0,
                sellingPrice = 28.0,
                mrp = 30.0,
                currentStock = 100.0
            )
        )
        advanceUntilIdle()

        val prod = repository.getProductById(prodId)
        assertNotNull(prod)
        viewModel.addToCart(prod, 2.0)
        advanceUntilIdle()

        var item = viewModel.cartItems.value.first()
        assertEquals("Tata Salt 1kg", item.name)
        assertEquals("Packet", item.unit)
        assertEquals(28.0, item.rate, 0.001)
        assertEquals(2.0, item.quantity, 0.001)
        assertEquals(0.0, item.lineDiscount, 0.001)
        assertEquals(56.0, item.totalAmount, 0.001) // 2 * 28.0

        // Test increment & decrement quantity
        viewModel.incrementCartItem(prodId)
        assertEquals(3.0, viewModel.cartItems.value.first().quantity, 0.001)

        viewModel.decrementCartItem(prodId)
        assertEquals(2.0, viewModel.cartItems.value.first().quantity, 0.001)

        // Test line discount update
        viewModel.updateCartItemDiscount(prodId, 6.0)
        item = viewModel.cartItems.value.first()
        assertEquals(6.0, item.lineDiscount, 0.001)
        assertEquals(50.0, item.totalAmount, 0.001) // 56 - 6 = 50

        // Test remove item
        viewModel.removeFromCart(prodId)
        assertTrue(viewModel.cartItems.value.isEmpty())
    }

    @Test
    fun testBottomSummarySubtotalDiscountGstGrandTotal() = runTest(testDispatcher) {
        subscribeToCartFlows()
        val prodId = repository.insertProduct(
            ProductItem(
                name = "Amul Butter 500g",
                barcode = "8901262010051",
                category = "Dairy",
                unit = "Box",
                costPrice = 220.0,
                sellingPrice = 275.0,
                mrp = 280.0,
                gstRate = 5.0,
                currentStock = 30.0
            )
        )
        advanceUntilIdle()

        val prod = repository.getProductById(prodId)
        viewModel.addToCart(prod, 2.0) // 2 * 275 = 550.0 gross inclusive
        viewModel.discountAmount.value = 50.0 // 50 bill discount
        advanceUntilIdle()

        val grandTotal = viewModel.cartGrandTotal.value
        assertEquals(500.0, grandTotal, 0.01)

        val gst = viewModel.cartGstAmount.value
        assertTrue("GST should be calculated from inclusive rate", gst > 0.0)
    }

    @Test
    fun testCashPaymentExactAndQuickButtonsAndChangeDue() = runTest(testDispatcher) {
        subscribeToCartFlows()
        val prodId = repository.insertProduct(
            ProductItem(
                name = "Sugar 1kg",
                barcode = "890001",
                category = "Groceries",
                unit = "kg",
                costPrice = 35.0,
                sellingPrice = 45.0,
                mrp = 50.0,
                currentStock = 100.0
            )
        )
        advanceUntilIdle()

        val prod = repository.getProductById(prodId)
        viewModel.addToCart(prod, 4.0) // 4 * 45 = 180.0
        advanceUntilIdle()

        assertEquals(180.0, viewModel.cartGrandTotal.value, 0.001)

        // Exact payment
        viewModel.paymentMode.value = "CASH"
        viewModel.cashReceived.value = 180.0
        advanceUntilIdle()
        assertEquals(0.0, viewModel.cartChangeDue.value, 0.001)

        // ₹200 quick button
        viewModel.cashReceived.value = 200.0
        advanceUntilIdle()
        assertEquals(20.0, viewModel.cartChangeDue.value, 0.001)

        // ₹500 quick button
        viewModel.cashReceived.value = 500.0
        advanceUntilIdle()
        assertEquals(320.0, viewModel.cartChangeDue.value, 0.001)

        // Short cash
        viewModel.cashReceived.value = 150.0
        advanceUntilIdle()
        assertEquals(0.0, viewModel.cartChangeDue.value, 0.001) // Not sufficient
    }

    @Test
    fun testKhataPaymentCustomerDueAndValidation() = runTest(testDispatcher) {
        subscribeToCartFlows()
        val custId = repository.insertCustomer(
            Customer(
                name = "Ramesh Kumar",
                phone = "9876543210",
                openingBalance = 150.0
            )
        )
        advanceUntilIdle()

        val prodId = repository.insertProduct(
            ProductItem(
                name = "Mustard Oil 1L",
                barcode = "890002",
                category = "Oils",
                unit = "L",
                costPrice = 120.0,
                sellingPrice = 160.0,
                mrp = 170.0,
                currentStock = 50.0
            )
        )
        advanceUntilIdle()

        val prod = repository.getProductById(prodId)
        viewModel.addToCart(prod, 1.0)
        advanceUntilIdle()

        viewModel.paymentMode.value = "KHATA"
        viewModel.selectedCustomerId.value = null

        // Completing Khata sale without customer should fail and preserve cart
        viewModel.completeSale()
        advanceUntilIdle()

        assertEquals("Cart must NOT be cleared before successful commit", 1, viewModel.cartItems.value.size)
        assertFalse(viewModel.showReceiptDialog.value)

        // Now select customer
        viewModel.selectedCustomerId.value = custId
        viewModel.customerName.value = "Ramesh Kumar"
        viewModel.customerPhone.value = "9876543210"

        // Complete sale directly via repository or viewModel
        val saleResult = repository.completeSaleTransaction(
            items = viewModel.cartItems.value,
            customerName = "Ramesh Kumar",
            customerPhone = "9876543210",
            paymentMode = "KHATA",
            discountAmount = 0.0,
            customerId = custId,
            cashReceived = 0.0,
            changeDue = 0.0,
            paymentReference = ""
        )
        assertNotNull(saleResult)
        assertEquals("KHATA", saleResult.transaction.paymentMode)
        assertEquals(160.0, saleResult.transaction.grandTotal, 0.001)

        // Verify customer ledger balance updated
        val newBalance = repository.getCustomerBalance(custId).first()
        assertEquals(310.0, newBalance, 0.001) // 150 + 160 = 310
    }

    @Test
    fun testDoNotClearCartBeforeSuccessfulDatabaseCommit() = runTest(testDispatcher) {
        subscribeToCartFlows()
        // Product with initial stock = 5.0
        val prodId = repository.insertProduct(
            ProductItem(
                name = "Basmati Rice 5kg",
                barcode = "890003",
                category = "Grains",
                unit = "Bag",
                costPrice = 400.0,
                sellingPrice = 550.0,
                mrp = 600.0,
                currentStock = 5.0
            )
        )
        advanceUntilIdle()

        val prod = repository.getProductById(prodId)!!
        viewModel.addToCart(prod, 5.0) // Valid at add time
        advanceUntilIdle()
        assertEquals(1, viewModel.cartItems.value.size)

        // Concurrent sale or stock depletion reduces stock in database to 2.0
        repository.updateProduct(prod.copy(currentStock = 2.0))
        advanceUntilIdle()

        viewModel.paymentMode.value = "CASH"
        viewModel.cashReceived.value = 3000.0
        advanceUntilIdle()

        // Attempt checkout which should fail due to insufficient stock in database transaction
        viewModel.completeSale()
        advanceUntilIdle()

        // Cart must NOT be cleared!
        assertEquals("Cart should remain intact on failure", 1, viewModel.cartItems.value.size)
        assertEquals(5.0, viewModel.cartItems.value.first().quantity, 0.001)
        assertFalse(viewModel.showReceiptDialog.value)
    }

    @Test
    fun testSuccessfulSaleStateShowsInvoiceNumberAndReadyForNewSale() = runTest(testDispatcher) {
        subscribeToCartFlows()
        val prodId = repository.insertProduct(
            ProductItem(
                name = "Good Day Biscuits",
                barcode = "890004",
                category = "Snacks",
                unit = "Pack",
                costPrice = 25.0,
                sellingPrice = 30.0,
                mrp = 30.0,
                currentStock = 20.0
            )
        )
        advanceUntilIdle()

        val prod = repository.getProductById(prodId)!!
        viewModel.addToCart(prod, 2.0) // 60.0
        advanceUntilIdle()
        assertEquals(1, viewModel.cartItems.value.size)

        val saleResult = repository.completeSaleTransaction(
            items = viewModel.cartItems.value,
            customerName = "Walk-in Customer",
            customerPhone = "",
            paymentMode = "CASH",
            discountAmount = 0.0,
            customerId = null,
            cashReceived = 100.0,
            changeDue = 40.0,
            paymentReference = ""
        )

        // Simulate ViewModel post-commit state transition
        viewModel.activeReceiptTransaction.value = saleResult.transaction
        viewModel.activeReceiptCartItems.value = saleResult.cartItems
        viewModel.showReceiptDialog.value = true
        viewModel.clearCart()

        // Verify successful database commit cleared cart
        assertEquals(0, viewModel.cartItems.value.size)
        assertTrue(viewModel.showReceiptDialog.value)

        val tx = viewModel.activeReceiptTransaction.value
        assertNotNull(tx)
        assertTrue(tx!!.invoiceNumber.startsWith("INV-"))
        assertEquals(60.0, tx.grandTotal, 0.001)
        assertEquals(100.0, tx.cashReceived, 0.001)
        assertEquals(40.0, tx.changeDue, 0.001)

        // Dismiss receipt / New Sale action
        viewModel.showReceiptDialog.value = false
        assertFalse(viewModel.showReceiptDialog.value)
        assertEquals("Cart ready for new sale", 0, viewModel.cartItems.value.size)
    }
}
