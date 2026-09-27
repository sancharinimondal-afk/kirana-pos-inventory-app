package com.example.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.backup.AutoBackupManager
import com.example.backup.AutoBackupScheduler
import com.example.backup.AutoBackupState
import com.example.backup.BackupManager
import com.example.backup.BackupValidationResult
import com.example.backup.ParsedBackupData
import com.example.billing.BillingCalculator
import com.example.billing.BillingItemInput
import com.example.billing.BillCalculationResult
import com.example.billing.DiscountType
import com.example.billing.ProfitCalculator
import com.example.data.DuplicateBarcodeException
import com.example.data.InsufficientCashException
import com.example.data.InsufficientStockException
import com.example.data.KhataCustomerRequiredException
import com.example.data.KiranaRepository
import com.example.data.PurchaseItemInput
import com.example.data.SampleKiranaData
import com.example.data.model.CartItem
import com.example.data.model.Customer
import com.example.data.model.CustomerWithBalance
import com.example.data.model.LedgerEntry
import com.example.data.model.ProductItem
import com.example.data.model.PurchaseEntity
import com.example.data.model.PurchaseItemEntity
import com.example.data.model.SaleItemEntity
import com.example.data.model.SaleTransaction
import com.example.data.model.ShopSettings
import com.example.data.model.StockMovementEntity
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.util.Calendar
import java.util.Locale

enum class StockFilterOption {
    ALL,
    LOW_STOCK,
    OUT_OF_STOCK,
    INACTIVE
}

class KiranaViewModel(
    private val repository: KiranaRepository,
    private val context: Context? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    // Auto-backup state
    private val _autoBackupState = MutableStateFlow(AutoBackupState())
    val autoBackupState: StateFlow<AutoBackupState> = _autoBackupState.asStateFlow()

    // Shop settings
    private val _shopSettings = MutableStateFlow(ShopSettings())
    val shopSettings: StateFlow<ShopSettings> = _shopSettings.asStateFlow()

    // App Theme & Appearance State
    private val _themeMode = MutableStateFlow(context?.let { com.example.data.BillingSettingsManager.getThemeMode(it) } ?: "SYSTEM")
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _colorPalette = MutableStateFlow(context?.let { com.example.data.BillingSettingsManager.getColorPalette(it) } ?: "EMERALD")
    val colorPalette: StateFlow<String> = _colorPalette.asStateFlow()

    fun setThemeMode(mode: String) {
        _themeMode.value = mode
        context?.let { com.example.data.BillingSettingsManager.setThemeMode(it, mode) }
    }

    fun setColorPalette(palette: String) {
        _colorPalette.value = palette
        context?.let { com.example.data.BillingSettingsManager.setColorPalette(it, palette) }
    }

    init {
        refreshAutoBackupState()
        // Demo products must NEVER automatically enter real inventory.
        // Catalog starts empty until user adds products or explicitly taps 'LOAD DEMO PRODUCTS'.
        viewModelScope.launch(ioDispatcher) {
            repository.shopSettings.collect { settings ->
                if (settings != null) {
                    _shopSettings.value = settings
                } else {
                    val defaultSettings = ShopSettings()
                    repository.saveShopSettings(defaultSettings)
                    _shopSettings.value = defaultSettings
                }
            }
        }
    }

    fun loadSampleProducts() = viewModelScope.launch(ioDispatcher) {
        try {
            repository.insertAllProducts(com.example.data.SampleKiranaData.sampleProducts)
            userMessage.value = "Loaded standard Kirana products"
        } catch (e: Exception) {
            userMessage.value = "Failed to load products: ${e.localizedMessage}"
        }
    }

    fun updateShopSettings(newSettings: ShopSettings, onComplete: ((Boolean) -> Unit)? = null) = viewModelScope.launch(ioDispatcher) {
        try {
            repository.saveShopSettings(newSettings.copy(id = 1))
            _shopSettings.value = newSettings.copy(id = 1)
            userMessage.value = "Shop settings saved successfully"
            onComplete?.invoke(true)
        } catch (e: Exception) {
            userMessage.value = "Failed to save settings: ${e.localizedMessage}"
            onComplete?.invoke(false)
        }
    }

    // Products State
    val allProducts: StateFlow<List<ProductItem>> = repository.allProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockProducts: StateFlow<List<ProductItem>> = repository.lowStockProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val outOfStockProducts: StateFlow<List<ProductItem>> = repository.outOfStockProducts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val productCount: StateFlow<Int> = repository.productCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val lowStockCount: StateFlow<Int> = repository.lowStockCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val outOfStockCount: StateFlow<Int> = repository.outOfStockCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val inactiveProducts: StateFlow<List<ProductItem>> = allProducts.map { list ->
        list.filter { !it.isActive }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val inactiveCount: StateFlow<Int> = inactiveProducts.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCostValue: StateFlow<Double?> = repository.totalCostValue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalMrpValue: StateFlow<Double?> = repository.totalMrpValue
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Filter & Search state for Inventory
    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("All")
    val stockFilter = MutableStateFlow(StockFilterOption.ALL)

    val filteredProducts: StateFlow<List<ProductItem>> = combine(
        allProducts,
        searchQuery,
        selectedCategory,
        stockFilter
    ) { products, query, category, filter ->
        products.filter { product ->
            val matchesQuery = query.isBlank() ||
                product.name.contains(query, ignoreCase = true) ||
                product.bengaliName.contains(query, ignoreCase = true) ||
                product.barcode.contains(query, ignoreCase = true) ||
                product.sku.contains(query, ignoreCase = true) ||
                product.rackLocation.contains(query, ignoreCase = true) ||
                product.category.contains(query, ignoreCase = true)

            val matchesCategory = category == "All" || category == "All Categories" ||
                product.category.equals(category, ignoreCase = true) ||
                product.category.contains(category, ignoreCase = true) ||
                category.contains(product.category, ignoreCase = true)

            val matchesFilter = when (filter) {
                StockFilterOption.ALL -> product.isActive
                StockFilterOption.LOW_STOCK -> product.isActive && product.isLowStock
                StockFilterOption.OUT_OF_STOCK -> product.isActive && product.isOutOfStock
                StockFilterOption.INACTIVE -> !product.isActive
            }

            matchesQuery && matchesCategory && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Sales & Transactions
    val allTransactions: StateFlow<List<SaleTransaction>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val startOfToday: Long
        get() {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.timeInMillis
        }

    val allSaleItems: StateFlow<List<SaleItemEntity>> = repository.allSaleItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayTransactions: StateFlow<List<SaleTransaction>> = repository.getTodayTransactions(startOfToday)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todaySaleItems: StateFlow<List<SaleItemEntity>> = repository.observeSaleItemsInPeriod(startOfToday, Long.MAX_VALUE)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Authoritative Today's Period Report calculated via ProfitCalculator (excluding cancelled sales)
    val todayReport: StateFlow<ProfitCalculator.PeriodReport> = combine(todayTransactions, todaySaleItems) { txs, items ->
        ProfitCalculator.calculatePeriodReport(txs, items)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProfitCalculator.emptyReport())

    val todaySalesTotal: StateFlow<Double?> = todayReport.map { it.totalSales }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todaySalesCount: StateFlow<Int> = todayReport.map { it.totalBills }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val todayCost: StateFlow<Double> = todayReport.map { it.totalCost }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todayGrossProfit: StateFlow<Double> = todayReport.map { it.totalGrossProfit }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val todayProfit: StateFlow<Double> = todayGrossProfit

    val totalLifetimeSales: StateFlow<Double?> = repository.totalLifetimeSales
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val totalLifetimeTransactionsCount: StateFlow<Int> = repository.totalLifetimeTransactionsCount
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Purchases, Movements & Customers State
    val allPurchases: StateFlow<List<PurchaseEntity>> = repository.allPurchases
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allStockMovements: StateFlow<List<StockMovementEntity>> = repository.allStockMovements
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCustomers: StateFlow<List<Customer>> = repository.allCustomers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allCustomersWithBalance: StateFlow<List<CustomerWithBalance>> = repository.allCustomersWithBalance
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalKhataOutstanding: StateFlow<Double> = repository.totalKhataOutstanding
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // POS Cart State
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val cartItems: StateFlow<List<CartItem>> = _cartItems.asStateFlow()

    // POS Customer & Payment details
    val customerName = MutableStateFlow("")
    val customerPhone = MutableStateFlow("")
    val selectedCustomerId = MutableStateFlow<Long?>(null)
    val paymentMode = MutableStateFlow("CASH") // CASH, UPI, KHATA, CARD
    val paymentReference = MutableStateFlow("")
    val discountAmount = MutableStateFlow(0.0)
    val cashReceived = MutableStateFlow(0.0)

    // Central Billing Calculation Engine State
    val cartBillResult: StateFlow<BillCalculationResult> = combine(
        _cartItems,
        discountAmount,
        cashReceived
    ) { items, discount, cash ->
        val billInputs = items.map { item ->
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
        val discountType = if (discount > 0.0) DiscountType.FixedAmount(discount) else null
        BillingCalculator.calculateBill(
            items = billInputs,
            discountType = discountType,
            cashReceived = cash,
            autoAggregateDuplicates = true
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        BillCalculationResult(
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
            cashReceived = 0.0,
            changeDue = 0.0,
            isCashSufficient = true
        )
    )

    val cartSubtotal: StateFlow<Double> = cartBillResult.map { it.subtotal }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val cartTaxableAmount: StateFlow<Double> = cartBillResult.map { it.taxableAmount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val cartGstAmount: StateFlow<Double> = cartBillResult.map { it.gstAmount }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val cartGrandTotal: StateFlow<Double> = cartBillResult.map { it.grandTotal }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val cartTotalSavings: StateFlow<Double> = cartBillResult.map { it.totalSavings }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val cartChangeDue: StateFlow<Double> = cartBillResult.map { it.changeDue }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    val activeReceiptTransaction = MutableStateFlow<SaleTransaction?>(null)
    val activeReceiptCartItems = MutableStateFlow<List<CartItem>>(emptyList())
    val showReceiptDialog = MutableStateFlow(false)

    // Feedback Toast / Snackbar message
    val userMessage = MutableStateFlow<String?>(null)

    // Restore Flow State
    val pendingRestoreValidation = MutableStateFlow<BackupValidationResult?>(null)
    val isRestoring = MutableStateFlow(false)

    fun clearUserMessage() {
        userMessage.value = null
    }

    // POS Cart Actions
    fun addToCart(product: ProductItem?, qty: Double = 1.0): Boolean {
        if (product == null) {
            userMessage.value = "Cannot add to cart: Product does not exist"
            return false
        }

        if (!product.isActive) {
            userMessage.value = "Cannot add to cart: '${product.name}' is inactive"
            return false
        }

        if (product.sellingPrice > product.mrp) {
            userMessage.value = "Cannot add to cart: Selling price (₹${product.sellingPrice}) exceeds MRP (₹${product.mrp}). Operation BLOCKED."
            return false
        }

        if (qty <= 0.0) {
            userMessage.value = "Quantity must be greater than zero"
            return false
        }

        val current = _cartItems.value.toMutableList()
        val existingIndex = current.indexOfFirst { it.productId == product.id }
        val currentCartQty = if (existingIndex >= 0) current[existingIndex].quantity else 0.0
        val targetTotalQty = currentCartQty + qty

        val availableStock = product.currentStock
        if (targetTotalQty > availableStock) {
            val availStr = if (availableStock % 1.0 == 0.0) availableStock.toInt().toString() else String.format(Locale.ENGLISH, "%.2f", availableStock)
            val reqStr = if (targetTotalQty % 1.0 == 0.0) targetTotalQty.toInt().toString() else String.format(Locale.ENGLISH, "%.2f", targetTotalQty)
            userMessage.value = "Insufficient stock for '${product.name}'. Available: $availStr ${product.unit}, Requested: $reqStr ${product.unit}"
            return false
        }

        if (existingIndex >= 0) {
            val item = current[existingIndex]
            current[existingIndex] = item.copy(quantity = targetTotalQty)
        } else {
            current.add(
                CartItem(
                    productId = product.id,
                    barcode = product.barcode,
                    name = product.name,
                    unit = product.unit,
                    rate = product.sellingPrice,
                    quantity = qty,
                    mrp = product.mrp,
                    costPrice = product.costPrice,
                    gstRate = product.gstRate
                )
            )
        }
        _cartItems.value = current
        userMessage.value = "Added '${product.name}' to cart"
        return true
    }

    fun addByBarcode(barcode: String, onNotFound: () -> Unit) {
        val trimmed = barcode.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val product = repository.getProductByBarcode(trimmed)
            if (product != null) {
                launch(Dispatchers.Main) {
                    addToCart(product, 1.0)
                }
            } else {
                launch(Dispatchers.Main) {
                    userMessage.value = "Barcode '$trimmed' not found in inventory"
                    onNotFound()
                }
            }
        }
    }

    fun handleScannedBarcodeInPos(
        query: String,
        onNavigateToCatalog: ((String) -> Unit)? = null,
        onNotFound: ((String) -> Unit)? = null
    ) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return

        viewModelScope.launch(ioDispatcher) {
            // 1. Direct barcode match
            val productByBarcode = repository.getProductByBarcode(trimmed)
            if (productByBarcode != null) {
                withContext(Dispatchers.Main) {
                    addToCart(productByBarcode, 1.0)
                }
                return@launch
            }

            // 2. Exact match on name, barcode, or SKU
            val all = repository.getAllProductsDirect()
            val exactMatch = all.firstOrNull {
                it.name.equals(trimmed, ignoreCase = true) ||
                it.barcode.equals(trimmed, ignoreCase = true) ||
                it.sku.equals(trimmed, ignoreCase = true)
            }
            if (exactMatch != null) {
                withContext(Dispatchers.Main) {
                    addToCart(exactMatch, 1.0)
                }
                return@launch
            }

            // 3. Partial match on name or bengali name
            val matches = all.filter {
                it.name.contains(trimmed, ignoreCase = true) ||
                it.bengaliName.contains(trimmed, ignoreCase = true) ||
                it.barcode.contains(trimmed, ignoreCase = true)
            }

            if (matches.size == 1) {
                withContext(Dispatchers.Main) {
                    addToCart(matches.first(), 1.0)
                }
            } else if (matches.size > 1) {
                withContext(Dispatchers.Main) {
                    userMessage.value = "Found ${matches.size} items matching '$trimmed'"
                    onNavigateToCatalog?.invoke(trimmed)
                }
            } else {
                withContext(Dispatchers.Main) {
                    userMessage.value = "Item '$trimmed' not found in inventory"
                    onNotFound?.invoke(trimmed)
                }
            }
        }
    }

    fun updateCartQuantity(productId: Long, newQty: Double): Boolean {
        if (newQty <= 0.0) {
            removeFromCart(productId)
            return true
        }

        val product = allProducts.value.firstOrNull { it.id == productId }
        if (product != null && newQty > product.currentStock) {
            val availStr = if (product.currentStock % 1.0 == 0.0) product.currentStock.toInt().toString() else String.format(Locale.ENGLISH, "%.2f", product.currentStock)
            val reqStr = if (newQty % 1.0 == 0.0) newQty.toInt().toString() else String.format(Locale.ENGLISH, "%.2f", newQty)
            userMessage.value = "Insufficient stock for '${product.name}'. Available: $availStr ${product.unit}, Requested: $reqStr ${product.unit}"
            return false
        }

        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.productId == productId }
        if (index >= 0) {
            current[index] = current[index].copy(quantity = newQty)
            _cartItems.value = current
            return true
        }
        return false
    }

    fun updateCartItemQuantity(productId: Long, newQty: Double): Boolean {
        return updateCartQuantity(productId, newQty)
    }

    fun incrementCartItem(productId: Long, step: Double = 1.0) {
        val item = _cartItems.value.firstOrNull { it.productId == productId } ?: return
        updateCartQuantity(productId, item.quantity + step)
    }

    fun decrementCartItem(productId: Long, step: Double = 1.0) {
        val item = _cartItems.value.firstOrNull { it.productId == productId } ?: return
        val newQty = item.quantity - step
        if (newQty <= 0.0) {
            removeFromCart(productId)
        } else {
            updateCartQuantity(productId, newQty)
        }
    }

    fun updateCartItemDiscount(productId: Long, discount: Double) {
        _cartItems.value = _cartItems.value.map { item ->
            if (item.productId == productId) {
                item.copy(lineDiscount = discount.coerceAtLeast(0.0))
            } else {
                item
            }
        }
    }

    fun updateCartItemRate(productId: Long, newRate: Double) {
        if (newRate < 0.0) return
        _cartItems.value = _cartItems.value.map { item ->
            if (item.productId == productId) {
                item.copy(rate = newRate)
            } else {
                item
            }
        }
    }

    fun removeFromCart(productId: Long) {
        _cartItems.value = _cartItems.value.filter { it.productId != productId }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
        customerName.value = ""
        customerPhone.value = ""
        selectedCustomerId.value = null
        paymentReference.value = ""
        discountAmount.value = 0.0
        cashReceived.value = 0.0
        paymentMode.value = "CASH"
    }

    suspend fun validateCartBeforeCheckout(): Boolean {
        val items = _cartItems.value
        if (items.isEmpty()) {
            userMessage.value = "Cart is empty. Add products before checkout."
            return false
        }

        for (item in items) {
            if (item.quantity <= 0.0) {
                userMessage.value = "Invalid quantity (${item.quantity}) for '${item.name}'"
                return false
            }

            val product = repository.getProductById(item.productId)
            if (product == null) {
                userMessage.value = "Product '${item.name}' no longer exists in inventory"
                return false
            }

            if (!product.isActive) {
                userMessage.value = "Product '${product.name}' is inactive and cannot be billed"
                return false
            }

            if (item.quantity > product.currentStock) {
                val availStr = if (product.currentStock % 1.0 == 0.0) product.currentStock.toInt().toString() else String.format(Locale.ENGLISH, "%.2f", product.currentStock)
                val reqStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format(Locale.ENGLISH, "%.2f", item.quantity)
                userMessage.value = "Insufficient stock for '${product.name}'. Available: $availStr ${product.unit}, Requested: $reqStr ${product.unit}"
                return false
            }
        }
        return true
    }

    /**
     * Completes a sale using ONE atomic Room database transaction.
     * Flow:
     * Validate cart -> validate stock -> calculate totals -> generate unique invoice ->
     * save transaction -> save sale items snapshot -> deduct stock -> COMMIT.
     * If ANY operation fails: ROLLBACK EVERYTHING. Never allow partial stock deduction.
     * Printing is NOT part of this transaction and failure to print does not affect saved sale.
     */
    fun completeSale() {
        val items = _cartItems.value
        if (items.isEmpty()) {
            userMessage.value = "Cart is empty. Add products before checkout."
            return
        }

        viewModelScope.launch(ioDispatcher) {
            try {
                val currentCash = cashReceived.value
                val itemInputs = items.map { item ->
                    BillingItemInput(
                        productId = item.productId,
                        barcode = item.barcode,
                        name = item.name,
                        unit = item.unit,
                        quantity = item.quantity,
                        unitPriceInclusive = item.rate,
                        gstRatePercentage = item.gstRate,
                        mrp = item.mrp,
                        lineDiscount = item.lineDiscount
                    )
                }
                val disc = discountAmount.value
                val discountType = if (disc > 0.0) DiscountType.FixedAmount(disc) else null
                val billResult = BillingCalculator.calculateBill(
                    items = itemInputs,
                    discountType = discountType,
                    cashReceived = currentCash
                )
                val currentGrandTotal = billResult.grandTotal

                // Phase 5 CASH rule: cashReceived >= grandTotal
                if (paymentMode.value.equals("CASH", ignoreCase = true) && currentCash < currentGrandTotal) {
                    userMessage.value = "Cash received (₹${String.format(Locale.ENGLISH, "%.2f", currentCash)}) is less than total (₹${String.format(Locale.ENGLISH, "%.2f", currentGrandTotal)}). Checkout BLOCKED."
                    return@launch
                }

                // Phase 5 KHATA rule: Require exact customerId
                if (paymentMode.value.equals("KHATA", ignoreCase = true) && (selectedCustomerId.value == null || selectedCustomerId.value!! <= 0L)) {
                    userMessage.value = "Khata sale requires an exact registered customer. Checkout BLOCKED."
                    return@launch
                }

                val change = billResult.changeDue

                val result = repository.completeSaleTransaction(
                    items = items,
                    customerName = customerName.value,
                    customerPhone = customerPhone.value,
                    paymentMode = paymentMode.value,
                    discountAmount = discountAmount.value,
                    customerId = selectedCustomerId.value,
                    cashReceived = currentCash,
                    changeDue = change,
                    paymentReference = paymentReference.value
                )

                // Transaction is safely committed in Room! Now prepare receipt preview
                // Note: Printing is NOT part of the database transaction
                activeReceiptTransaction.value = result.transaction
                activeReceiptCartItems.value = result.cartItems
                showReceiptDialog.value = true

                // Clear current cart ONLY after successful transaction commit
                _cartItems.value = emptyList()
                customerName.value = ""
                customerPhone.value = ""
                selectedCustomerId.value = null
                paymentReference.value = ""
                discountAmount.value = 0.0
                cashReceived.value = 0.0
                paymentMode.value = "CASH"

                userMessage.value = "Bill #${result.transaction.invoiceNumber} generated successfully! Stock updated."
                scheduleAutoBackupOnDatabaseChange()
            } catch (e: InsufficientStockException) {
                userMessage.value = e.message ?: "Insufficient stock. Sale aborted."
            } catch (e: InsufficientCashException) {
                userMessage.value = e.message ?: "Insufficient cash. Checkout BLOCKED."
            } catch (e: KhataCustomerRequiredException) {
                userMessage.value = e.message ?: "Registered customer required for Khata."
            } catch (e: Exception) {
                userMessage.value = "Failed to complete sale: ${e.message}"
            }
        }
    }

    // Product Inventory Actions
    fun saveProduct(product: ProductItem, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (product.id == 0L) {
                    repository.insertProduct(product)
                    userMessage.value = "Product '${product.name}' added to inventory"
                } else {
                    repository.updateProduct(product)
                    userMessage.value = "Product '${product.name}' updated"
                }
                scheduleAutoBackupOnDatabaseChange()
                onComplete?.invoke(true, null)
            } catch (e: DuplicateBarcodeException) {
                val error = e.message ?: "Duplicate barcode rejected"
                userMessage.value = error
                onComplete?.invoke(false, error)
            } catch (e: InsufficientStockException) {
                val error = e.message ?: "Stock validation error"
                userMessage.value = error
                onComplete?.invoke(false, error)
            } catch (e: Exception) {
                val error = e.message ?: "Failed to save product"
                userMessage.value = error
                onComplete?.invoke(false, error)
            }
        }
    }

    fun quickUpdateStock(productId: Long, deltaStock: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (deltaStock > 0) {
                    repository.increaseStock(productId, deltaStock)
                    userMessage.value = "Added +$deltaStock stock"
                    scheduleAutoBackupOnDatabaseChange()
                } else if (deltaStock < 0) {
                    repository.decreaseStock(productId, -deltaStock)
                    userMessage.value = "Decreased ${-deltaStock} stock"
                    scheduleAutoBackupOnDatabaseChange()
                }
            } catch (e: InsufficientStockException) {
                userMessage.value = e.message ?: "Cannot decrease stock below zero"
            } catch (e: Exception) {
                userMessage.value = "Failed to update stock: ${e.message}"
            }
        }
    }

    fun quickAdjustStock(productId: Long, deltaStock: Double) {
        quickUpdateStock(productId, deltaStock)
    }

    fun performStockAdjustment(
        productId: Long,
        adjustmentType: com.example.ui.components.StockAdjustmentType,
        quantity: Double,
        reason: String,
        reference: String,
        onComplete: ((Boolean, String) -> Unit)? = null
    ) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val prod = repository.getProductById(productId)
                    ?: throw IllegalArgumentException("Product not found with ID $productId")
                val finalReason = reason.ifBlank { "Manual stock adjustment" }
                val finalRef = reference.ifBlank { "ADJ-" + System.currentTimeMillis().toString().takeLast(6) }

                when (adjustmentType) {
                    com.example.ui.components.StockAdjustmentType.ADD -> {
                        require(quantity > 0.0) { "Quantity to add must be greater than 0" }
                        repository.increaseStock(productId, quantity, finalRef, finalReason)
                        userMessage.value = "Stock for '${prod.name}' increased by $quantity"
                    }
                    com.example.ui.components.StockAdjustmentType.REMOVE -> {
                        require(quantity > 0.0) { "Quantity to remove must be greater than 0" }
                        repository.decreaseStock(productId, quantity, finalRef, finalReason)
                        userMessage.value = "Stock for '${prod.name}' decreased by $quantity"
                    }
                    com.example.ui.components.StockAdjustmentType.SET_EXACT -> {
                        require(quantity >= 0.0) { "Exact stock quantity cannot be negative" }
                        repository.adjustStock(productId, quantity, finalReason, finalRef)
                        userMessage.value = "Stock for '${prod.name}' set to $quantity"
                    }
                }
                scheduleAutoBackupOnDatabaseChange()
                onComplete?.invoke(true, "Stock adjusted successfully")
            } catch (e: Exception) {
                userMessage.value = "Failed to adjust stock: ${e.message}"
                onComplete?.invoke(false, e.message ?: "Failed to adjust stock")
            }
        }
    }

    fun setDirectStock(productId: Long, newStock: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.updateStock(productId, newStock)
                userMessage.value = "Stock updated to $newStock"
                scheduleAutoBackupOnDatabaseChange()
            } catch (e: InsufficientStockException) {
                userMessage.value = e.message ?: "Negative stock not allowed"
            } catch (e: Exception) {
                userMessage.value = "Failed to set stock: ${e.message}"
            }
        }
    }

    fun deleteProduct(product: ProductItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val wasDeactivated = repository.deleteProduct(product)
                if (wasDeactivated) {
                    userMessage.value = "Product '${product.name}' deactivated to preserve sales and purchase history"
                } else {
                    userMessage.value = "Product '${product.name}' removed"
                }
                scheduleAutoBackupOnDatabaseChange()
            } catch (e: Exception) {
                userMessage.value = "Failed to delete: ${e.message}"
            }
        }
    }

    fun deactivateProduct(product: ProductItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.deactivateProduct(product.id)
                userMessage.value = "Product '${product.name}' deactivated"
                scheduleAutoBackupOnDatabaseChange()
            } catch (e: Exception) {
                userMessage.value = "Failed to deactivate: ${e.message}"
            }
        }
    }

    fun reactivateProduct(product: ProductItem) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                repository.reactivateProduct(product.id)
                userMessage.value = "Product '${product.name}' reactivated"
                scheduleAutoBackupOnDatabaseChange()
            } catch (e: Exception) {
                userMessage.value = "Failed to reactivate: ${e.message}"
            }
        }
    }

    /**
     * Explicitly loads demo data upon manual user request (Settings/Diagnostics).
     * Marked and identified as demo data. Never invoked automatically.
     */
    fun loadDemoCatalog() {
        viewModelScope.launch(Dispatchers.IO) {
            val sampleProducts = SampleKiranaData.sampleProducts
            repository.insertAllProducts(sampleProducts)
            userMessage.value = "Loaded ${sampleProducts.size} demo grocery products!"
        }
    }

    /**
     * Explicitly removes only the demo products from inventory without wiping real store items.
     */
    fun removeDemoCatalog() {
        viewModelScope.launch(Dispatchers.IO) {
            val sampleBarcodes = SampleKiranaData.sampleProducts.map { it.barcode }.toSet()
            val allProds = repository.getAllProductsDirect()
            val demoProds = allProds.filter { it.barcode in sampleBarcodes }
            for (p in demoProds) {
                repository.deleteProduct(p)
            }
            userMessage.value = "Removed ${demoProds.size} demo items from catalog"
        }
    }

    // Data Export & Backup Actions
    suspend fun generateBackupJson(): String {
        val products = repository.getAllProductsDirect()
        val transactions = repository.getAllTransactionsDirect()
        val saleItems = repository.getAllSaleItemsDirect()
        val sequences = repository.getAllSequencesDirect()
        val settings = repository.getShopSettingsSync()
        val customers = repository.getAllCustomersDirect()
        val ledger = repository.getAllLedgerEntriesDirect()
        val purchases = repository.getAllPurchasesDirect()
        val purchaseItems = repository.getAllPurchaseItemsDirect()
        val stockMovements = repository.getAllStockMovementsDirect()
        return BackupManager.buildBackupJsonString(
            products = products,
            transactions = transactions,
            saleItems = saleItems,
            settings = settings,
            invoiceSequences = sequences,
            customers = customers,
            ledger = ledger,
            purchases = purchases,
            purchaseItems = purchaseItems,
            stockMovements = stockMovements
        )
    }

    fun exportBackup(context: Context, password: String? = null, onResult: ((File?) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val products = repository.getAllProductsDirect()
                val transactions = repository.getAllTransactionsDirect()
                val saleItems = repository.getAllSaleItemsDirect()
                val sequences = repository.getAllSequencesDirect()
                val settings = repository.getShopSettingsSync() ?: ShopSettings()
                val customers = repository.getAllCustomersDirect()
                val ledger = repository.getAllLedgerEntriesDirect()
                val purchases = repository.getAllPurchasesDirect()
                val purchaseItems = repository.getAllPurchaseItemsDirect()
                val stockMovements = repository.getAllStockMovementsDirect()

                val file = BackupManager.exportBackupToJson(
                    context = context,
                    products = products,
                    transactions = transactions,
                    settings = settings,
                    saleItems = saleItems,
                    invoiceSequences = sequences,
                    customers = customers,
                    ledger = ledger,
                    purchases = purchases,
                    purchaseItems = purchaseItems,
                    stockMovements = stockMovements,
                    password = password
                )
                if (file != null) {
                    userMessage.value = "Backup created: ${file.name}"
                } else {
                    userMessage.value = "Failed to create backup file"
                }
                onResult?.invoke(file)
            } catch (e: Exception) {
                userMessage.value = "Export error: ${e.localizedMessage}"
                onResult?.invoke(null)
            }
        }
    }

    fun exportBackupToJson(context: Context, password: String? = null, onResult: ((File?) -> Unit)? = null) {
        exportBackup(context, password, onResult)
    }

    fun exportBackupToSafUri(context: Context, uri: Uri, onResult: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val json = generateBackupJson()
                val success = BackupManager.writeBackupToUri(context, uri, json)
                if (success) {
                    userMessage.value = "Backup exported successfully to selected file!"
                } else {
                    userMessage.value = "Failed to write backup to selected destination."
                }
                onResult?.invoke(success)
            } catch (e: Exception) {
                userMessage.value = "Export error: ${e.localizedMessage}"
                onResult?.invoke(false)
            }
        }
    }

    fun exportInventoryCsv(context: Context, onResult: ((File?) -> Unit)? = null): File? {
        val products = allProducts.value
        val file = BackupManager.exportInventoryToCsv(context, products)
        if (file != null) {
            userMessage.value = "Inventory CSV saved: ${file.name}"
        } else {
            userMessage.value = "Failed to export CSV"
        }
        onResult?.invoke(file)
        return file
    }

    fun exportSalesCsv(context: Context, onResult: ((File?) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val sales = repository.getAllTransactionsDirect()
                val saleItems = repository.getAllSaleItemsDirect()
                val file = BackupManager.exportSalesToCsv(context, sales, saleItems)
                if (file != null) {
                    userMessage.value = "Sales report exported: ${file.name}"
                } else {
                    userMessage.value = "Failed to export sales CSV"
                }
                onResult?.invoke(file)
            } catch (e: Exception) {
                userMessage.value = "Export error: ${e.localizedMessage}"
                onResult?.invoke(null)
            }
        }
    }

    fun exportCsv(context: Context): File? {
        return exportInventoryCsv(context)
    }

    fun validateBackupContent(jsonString: String): BackupValidationResult {
        val validation = BackupManager.validateBackup(jsonString)
        if (validation.isValid) {
            pendingRestoreValidation.value = validation
        } else {
            userMessage.value = "Validation failed: ${validation.errorMessage}"
        }
        return validation
    }

    fun validateEncryptedBackupContent(jsonString: String, password: String): BackupValidationResult {
        val validation = BackupManager.decryptAndValidateBackup(jsonString, password)
        if (validation.isValid) {
            pendingRestoreValidation.value = validation
        } else {
            userMessage.value = "Validation failed: ${validation.errorMessage}"
        }
        return validation
    }

    fun validateBackupFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val content = BackupManager.readBackupFromUri(context, uri)
            if (content.isNullOrBlank()) {
                userMessage.value = "Could not read backup file from selected location"
                return@launch
            }
            validateBackupContent(content)
        }
    }

    fun dismissRestoreConfirmation() {
        pendingRestoreValidation.value = null
    }

    fun confirmAndExecuteRestore(onComplete: ((Boolean, String) -> Unit)? = null) {
        val validation = pendingRestoreValidation.value
        val data = validation?.parsedData
        if (data == null) {
            userMessage.value = "No validated backup data available to restore"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            isRestoring.value = true
            try {
                val result = repository.restoreFullBackup(data)
                userMessage.value = result.message
                pendingRestoreValidation.value = null
                onComplete?.invoke(true, result.message)
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "Unknown restore failure"
                userMessage.value = "Restore failed: $err. Database unchanged."
                pendingRestoreValidation.value = null
                onComplete?.invoke(false, err)
            } finally {
                isRestoring.value = false
            }
        }
    }

    fun restoreBackup(jsonString: String, password: String? = null, onComplete: ((Boolean, String) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            isRestoring.value = true
            try {
                val result = repository.restoreFromBackupJson(jsonString, password)
                userMessage.value = result.message
                onComplete?.invoke(true, result.message)
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "Unknown restore error"
                userMessage.value = "Restore failed: $err. Database unchanged."
                onComplete?.invoke(false, err)
            } finally {
                isRestoring.value = false
            }
        }
    }

    fun importBackup(context: Context, jsonString: String) {
        validateBackupContent(jsonString)
    }

    fun openPastTransactionReceipt(transaction: SaleTransaction) {
        viewModelScope.launch(Dispatchers.IO) {
            // First check if structured SaleItemEntities exist in Room for this transaction
            val saleEntities = repository.getSaleItemsByTransactionId(transaction.id)
            val items = if (saleEntities.isNotEmpty()) {
                saleEntities.map { entity ->
                    CartItem(
                        productId = entity.productId,
                        barcode = entity.barcode,
                        name = entity.productName,
                        unit = entity.unit,
                        rate = entity.sellingPrice,
                        quantity = entity.quantity,
                        mrp = entity.mrp,
                        costPrice = entity.costPrice,
                        gstRate = entity.gstRate
                    )
                }
            } else {
                // Fallback to itemsJson for legacy/imported transactions
                val parsed = mutableListOf<CartItem>()
                try {
                    val array = JSONArray(transaction.itemsJson)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        parsed.add(
                            CartItem(
                                productId = obj.optLong("productId", 0L),
                                barcode = obj.optString("barcode", ""),
                                name = obj.optString("name", "Item"),
                                unit = obj.optString("unit", "Piece"),
                                rate = obj.optDouble("rate", 0.0),
                                quantity = obj.optDouble("quantity", 1.0),
                                mrp = obj.optDouble("mrp", 0.0),
                                costPrice = obj.optDouble("costPrice", 0.0),
                                gstRate = obj.optDouble("gstRate", 0.0)
                            )
                        )
                    }
                } catch (_: Exception) {}
                parsed
            }

            activeReceiptTransaction.value = transaction
            activeReceiptCartItems.value = items
            showReceiptDialog.value = true
        }
    }

    // Purchase Actions
    fun completePurchase(
        items: List<PurchaseItemInput>,
        supplierName: String,
        supplierPhone: String = "",
        paymentMode: String = "Cash",
        discount: Double = 0.0,
        note: String = "",
        configurableMarkupPercentage: Double? = null,
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch(ioDispatcher) {
            try {
                val result = repository.completePurchaseTransaction(
                    items = items,
                    supplierName = supplierName,
                    supplierPhone = supplierPhone,
                    paymentMode = paymentMode,
                    discount = discount,
                    note = note,
                    configurableMarkupPercentage = configurableMarkupPercentage
                )
                userMessage.value = "Purchase #${result.purchase.purchaseNumber} recorded! Stock increased."
                scheduleAutoBackupOnDatabaseChange()
                onComplete?.invoke(true, null)
            } catch (e: Exception) {
                val error = e.localizedMessage ?: "Failed to save purchase"
                userMessage.value = error
                onComplete?.invoke(false, error)
            }
        }
    }

    fun cancelSale(saleId: Long, reason: String = "Cancelled by merchant") {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.cancelSaleTransaction(saleId, reason)
                userMessage.value = "Sale #$saleId marked as cancelled"
                scheduleAutoBackupOnDatabaseChange()
            } catch (e: Exception) {
                userMessage.value = "Failed to cancel sale: ${e.message}"
            }
        }
    }

    fun addCustomer(customer: Customer, onComplete: ((Boolean, String?) -> Unit)? = null) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.insertCustomer(customer)
                userMessage.value = "Customer '${customer.name}' registered"
                scheduleAutoBackupOnDatabaseChange()
                onComplete?.invoke(true, null)
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "Failed to add customer"
                userMessage.value = err
                onComplete?.invoke(false, err)
            }
        }
    }

    fun addKhataPayment(
        customerId: Long,
        amount: Double,
        reference: String = "",
        note: String = "Payment received",
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.addKhataPayment(customerId, amount, reference, note)
                userMessage.value = "Payment of ₹$amount recorded"
                scheduleAutoBackupOnDatabaseChange()
                onComplete?.invoke(true, null)
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "Failed to record payment"
                userMessage.value = err
                onComplete?.invoke(false, err)
            }
        }
    }

    fun addKhataCredit(
        customerId: Long,
        amount: Double,
        reference: String = "",
        note: String = "Credit sale adjustment",
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.addKhataAdjustment(customerId, amount, isDebit = true, reference, note)
                userMessage.value = "Credit of ₹$amount added to customer account"
                scheduleAutoBackupOnDatabaseChange()
                onComplete?.invoke(true, null)
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "Failed to add credit"
                userMessage.value = err
                onComplete?.invoke(false, err)
            }
        }
    }

    fun addKhataAdjustment(
        customerId: Long,
        amount: Double,
        isDebit: Boolean,
        reference: String = "",
        note: String = "",
        onComplete: ((Boolean, String?) -> Unit)? = null
    ) {
        viewModelScope.launch(ioDispatcher) {
            try {
                repository.addKhataAdjustment(customerId, amount, isDebit, reference, note)
                val typeDesc = if (isDebit) "Debit (+Due)" else "Credit (-Due)"
                userMessage.value = "$typeDesc adjustment of ₹$amount recorded"
                scheduleAutoBackupOnDatabaseChange()
                onComplete?.invoke(true, null)
            } catch (e: Exception) {
                val err = e.localizedMessage ?: "Failed to record adjustment"
                userMessage.value = err
                onComplete?.invoke(false, err)
            }
        }
    }

    fun getCustomerEntries(customerId: Long): kotlinx.coroutines.flow.Flow<List<LedgerEntry>> =
        repository.getCustomerEntries(customerId)

    fun getCustomerBalance(customerId: Long): kotlinx.coroutines.flow.Flow<Double> =
        repository.getCustomerBalance(customerId)

    // Auto-Backup Management Methods
    fun refreshAutoBackupState() {
        context?.let { ctx ->
            viewModelScope.launch(Dispatchers.IO) {
                val state = AutoBackupManager.getAutoBackupState(ctx)
                _autoBackupState.value = state
            }
        }
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        context?.let { ctx ->
            AutoBackupManager.setAutoBackupEnabled(ctx, enabled)
            if (enabled) {
                AutoBackupScheduler.scheduleDailyBackup(ctx)
            } else {
                AutoBackupScheduler.cancelScheduledWork(ctx)
            }
            refreshAutoBackupState()
        }
    }

    fun triggerAutoBackupNow() {
        context?.let { ctx ->
            viewModelScope.launch(Dispatchers.IO) {
                val result = AutoBackupManager.performAutoBackup(ctx, repository, force = true)
                refreshAutoBackupState()
                withContext(Dispatchers.Main) {
                    if (result.success) {
                        userMessage.value = "Auto-backup created: ${result.file?.name} (${result.productsCount} items, ${result.salesCount} sales)"
                    } else {
                        userMessage.value = "Auto-backup failed: ${result.errorMessage}"
                    }
                }
            }
        }
    }

    fun restoreFromAutoBackupFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val content = file.readText(Charsets.UTF_8)
                withContext(Dispatchers.Main) {
                    validateBackupContent(content)
                }
            } catch (e: Exception) {
                userMessage.value = "Failed to read auto-backup: ${e.localizedMessage}"
            }
        }
    }

    private fun scheduleAutoBackupOnDatabaseChange() {
        context?.let { ctx ->
            AutoBackupScheduler.scheduleBackupAfterDatabaseChange(ctx)
            refreshAutoBackupState()
        }
    }
}

// Simple helper alias
fun MutableFlowString(initial: String) = MutableStateFlow(initial)
