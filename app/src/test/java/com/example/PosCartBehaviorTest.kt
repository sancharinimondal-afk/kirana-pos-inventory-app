package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.DATABASE_CALLBACK
import com.example.data.KiranaRepository
import com.example.data.model.ProductItem
import com.example.ui.KiranaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PosCartBehaviorTest {

    private lateinit var db: AppDatabase
    private lateinit var repository: KiranaRepository
    private lateinit var viewModel: KiranaViewModel

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

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
    }

    @After
    fun tearDown() {
        viewModel.viewModelScope.coroutineContext.cancelChildren()
        db.close()
        Dispatchers.resetMain()
    }

    // 1. Add product: Valid product enters the cart successfully with accurate quantity and price
    @Test
    fun testAddProduct() = runTest {
        val product = ProductItem(
            name = "Fortune Sunlite Sunflower Oil 1L",
            barcode = "8906007280014",
            category = "Edible Oils",
            unit = "Litre",
            costPrice = 110.0,
            sellingPrice = 135.0,
            mrp = 150.0,
            currentStock = 10.0,
            minStockAlert = 3.0,
            isActive = true
        )
        val id = repository.insertProduct(product)
        val savedProduct = repository.getProductById(id)!!

        val added = viewModel.addToCart(savedProduct, 2.0)
        assertTrue("Product should be added to cart successfully", added)

        val cart = viewModel.cartItems.value
        assertEquals(1, cart.size)
        assertEquals(savedProduct.id, cart[0].productId)
        assertEquals("Fortune Sunlite Sunflower Oil 1L", cart[0].name)
        assertEquals(2.0, cart[0].quantity, 0.001)
        assertEquals(135.0, cart[0].rate, 0.001)
        assertEquals(270.0, cart[0].totalAmount, 0.001)
        assertEquals(30.0, cart[0].totalSavings, 0.001) // (150 - 135) * 2 = 30
    }

    // 2. Scan/add same product twice: Merges the quantities into a single cart entry
    @Test
    fun testScanAddSameProductTwice() = runTest {
        val product = ProductItem(
            name = "Tata Salt 1kg",
            barcode = "8901030383701",
            category = "Spices & Salt",
            unit = "Packet",
            costPrice = 20.0,
            sellingPrice = 28.0,
            mrp = 30.0,
            currentStock = 15.0,
            minStockAlert = 5.0,
            isActive = true
        )
        val id = repository.insertProduct(product)
        val savedProduct = repository.getProductById(id)!!

        val firstAdd = viewModel.addToCart(savedProduct, 2.0)
        assertTrue("First add should succeed", firstAdd)

        val secondAdd = viewModel.addToCart(savedProduct, 3.0)
        assertTrue("Second add of same product should succeed", secondAdd)

        val cart = viewModel.cartItems.value
        assertEquals("Cart should have only 1 merged line item", 1, cart.size)
        assertEquals(savedProduct.id, cart[0].productId)
        assertEquals(5.0, cart[0].quantity, 0.001)
        assertEquals(140.0, cart[0].totalAmount, 0.001)
    }

    // 3. Change quantity: Increases/decreases quantity, removing item if quantity drops to 0 or below
    @Test
    fun testChangeQuantity() = runTest {
        val product = ProductItem(
            name = "Madhur Pure & Hygienic Sugar 1kg",
            barcode = "8906014640016",
            category = "Sugar & Jaggery",
            unit = "kg",
            costPrice = 40.0,
            sellingPrice = 46.0,
            mrp = 50.0,
            currentStock = 10.0,
            minStockAlert = 2.0,
            isActive = true
        )
        val id = repository.insertProduct(product)
        val savedProduct = repository.getProductById(id)!!

        viewModel.addToCart(savedProduct, 2.0)
        assertEquals(2.0, viewModel.cartItems.value[0].quantity, 0.001)

        // Increase quantity to 4
        val increased = viewModel.updateCartItemQuantity(savedProduct.id, 4.0)
        assertTrue("Increasing valid quantity should succeed", increased)
        assertEquals(4.0, viewModel.cartItems.value[0].quantity, 0.001)

        // Decrease quantity to 1
        val decreased = viewModel.updateCartItemQuantity(savedProduct.id, 1.0)
        assertTrue("Decreasing valid quantity should succeed", decreased)
        assertEquals(1.0, viewModel.cartItems.value[0].quantity, 0.001)

        // Setting quantity to 0 should remove the item from cart
        val zeroResult = viewModel.updateCartItemQuantity(savedProduct.id, 0.0)
        assertTrue("Setting quantity to 0 removes the item", zeroResult)
        assertTrue("Cart should now be empty after setting quantity to 0", viewModel.cartItems.value.isEmpty())
    }

    // 4. Remove product: Deletes the line item from the cart
    @Test
    fun testRemoveProduct() = runTest {
        val p1 = ProductItem(
            name = "Parle-G Gold Biscuits 100g",
            barcode = "8901719101015",
            category = "Biscuits & Snacks",
            unit = "Packet",
            costPrice = 8.0,
            sellingPrice = 10.0,
            mrp = 10.0,
            currentStock = 20.0,
            isActive = true
        )
        val p2 = ProductItem(
            name = "Britannia Good Day Butter 120g",
            barcode = "8901063012345",
            category = "Biscuits & Snacks",
            unit = "Packet",
            costPrice = 25.0,
            sellingPrice = 30.0,
            mrp = 35.0,
            currentStock = 10.0,
            isActive = true
        )
        val id1 = repository.insertProduct(p1)
        val id2 = repository.insertProduct(p2)

        viewModel.addToCart(repository.getProductById(id1)!!, 2.0)
        viewModel.addToCart(repository.getProductById(id2)!!, 1.0)

        assertEquals(2, viewModel.cartItems.value.size)

        // Remove item 1
        viewModel.removeFromCart(id1)

        val cart = viewModel.cartItems.value
        assertEquals(1, cart.size)
        assertEquals(id2, cart[0].productId)
        assertEquals("Britannia Good Day Butter 120g", cart[0].name)
    }

    // 5. Invalid quantity: Quantity <= 0 must be rejected
    @Test
    fun testInvalidQuantity() = runTest {
        val product = ProductItem(
            name = "Aashirvaad Atta 5kg",
            barcode = "8901030383718",
            category = "Atta & Flour",
            unit = "kg",
            costPrice = 210.0,
            sellingPrice = 245.0,
            mrp = 260.0,
            currentStock = 20.0,
            isActive = true
        )
        val id = repository.insertProduct(product)
        val savedProduct = repository.getProductById(id)!!

        // Add with 0 quantity
        val zeroAdd = viewModel.addToCart(savedProduct, 0.0)
        assertFalse("Adding 0 quantity must be blocked", zeroAdd)
        assertTrue("Cart must remain empty", viewModel.cartItems.value.isEmpty())
        assertTrue(
            "Clear message must explain quantity error",
            viewModel.userMessage.value?.contains("Quantity must be greater than zero") == true
        )

        // Add with negative quantity
        val negAdd = viewModel.addToCart(savedProduct, -3.0)
        assertFalse("Adding negative quantity must be blocked", negAdd)
        assertTrue("Cart must remain empty", viewModel.cartItems.value.isEmpty())
    }

    // 6. Insufficient stock: Available = 2, Requested = 5 blocks the operation with clear message and prevents accidental negative stock
    @Test
    fun testInsufficientStock() = runTest {
        val product = ProductItem(
            name = "Amul Pure Ghee 1L Tin",
            barcode = "8901262010058",
            category = "Dairy & Ghee",
            unit = "Piece",
            costPrice = 520.0,
            sellingPrice = 610.0,
            mrp = 650.0,
            currentStock = 2.0, // Available = 2
            isActive = true
        )
        val id = repository.insertProduct(product)
        val savedProduct = repository.getProductById(id)!!

        // Attempt to request 5
        val result = viewModel.addToCart(savedProduct, 5.0)
        assertFalse("Operation must be blocked when requested exceeds available stock", result)
        assertTrue("Cart must remain empty to prevent accidental negative stock", viewModel.cartItems.value.isEmpty())

        val msg = viewModel.userMessage.value
        assertNotNull("User message must be displayed", msg)
        assertTrue("Message must indicate insufficient stock", msg!!.contains("Insufficient stock"))
        assertTrue("Message must mention available 2", msg.contains("Available: 2"))
        assertTrue("Message must mention requested 5", msg.contains("Requested: 5"))

        // Now test when adding 2 (which is available), then requesting 1 more (total 3 > 2)
        val firstAdd = viewModel.addToCart(savedProduct, 2.0)
        assertTrue("Adding available stock (2) should succeed", firstAdd)

        val secondAdd = viewModel.addToCart(savedProduct, 1.0)
        assertFalse("Adding beyond available stock on merged quantity must be blocked", secondAdd)
        assertEquals("Cart quantity must remain 2", 2.0, viewModel.cartItems.value[0].quantity, 0.001)
    }

    // 7. Empty cart: Complete sale/checkout validation fails gracefully on empty cart
    @Test
    fun testEmptyCart() = runTest {
        assertTrue(viewModel.cartItems.value.isEmpty())

        // Validate before checkout directly
        val isValid = viewModel.validateCartBeforeCheckout()
        assertFalse("Checkout validation must fail on empty cart", isValid)
        assertTrue(
            "User message must mention empty cart",
            viewModel.userMessage.value?.contains("Cart is empty") == true
        )

        // Complete sale with empty cart should not persist transaction
        viewModel.completeSale()
        assertEquals(0, repository.getTransactionCountDirect())
    }

    // 8. Inactive product: Cannot enter the cart or be billed
    @Test
    fun testInactiveProduct() = runTest {
        val activeProduct = ProductItem(
            name = "Maggi 2-Minute Noodles 70g",
            barcode = "8901058852449",
            category = "Noodles & Pasta",
            unit = "Packet",
            costPrice = 11.0,
            sellingPrice = 14.0,
            mrp = 14.0,
            currentStock = 50.0,
            isActive = true
        )
        val inactiveProduct = ProductItem(
            name = "Discontinued Biscuit Brand 100g",
            barcode = "8909999999999",
            category = "Biscuits & Snacks",
            unit = "Packet",
            costPrice = 10.0,
            sellingPrice = 12.0,
            mrp = 15.0,
            currentStock = 10.0,
            isActive = false // Inactive product
        )
        val activeId = repository.insertProduct(activeProduct)
        val inactiveId = repository.insertProduct(inactiveProduct)

        val savedInactive = repository.getProductById(inactiveId)!!
        val addInactive = viewModel.addToCart(savedInactive, 1.0)

        assertFalse("Inactive product must NOT enter the cart", addInactive)
        assertTrue("Cart must remain empty", viewModel.cartItems.value.isEmpty())
        assertTrue(
            "User message must explain product is inactive",
            viewModel.userMessage.value?.contains("is inactive") == true
        )

        // If product was added when active, but then deactivated before checkout:
        val savedActive = repository.getProductById(activeId)!!
        viewModel.addToCart(savedActive, 2.0)
        assertEquals(1, viewModel.cartItems.value.size)

        // Deactivate the product in inventory
        repository.updateProduct(savedActive.copy(isActive = false))

        val canCheckout = viewModel.validateCartBeforeCheckout()
        assertFalse("Validation must fail if an item in cart is deactivated before checkout", canCheckout)
        assertTrue(
            "User message must indicate product is inactive",
            viewModel.userMessage.value?.contains("is inactive") == true
        )
    }

    // 9. Non-existent product: Null or invalid product cannot enter cart
    @Test
    fun testNonExistentProduct() = runTest {
        val added = viewModel.addToCart(null, 1.0)
        assertFalse("Null product must not be added to cart", added)
        assertTrue("Cart must remain empty", viewModel.cartItems.value.isEmpty())
        assertTrue(
            "User message must explain product does not exist",
            viewModel.userMessage.value?.contains("Product does not exist") == true
        )
    }
}
