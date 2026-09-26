package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CartItem
import com.example.data.model.CustomerWithBalance
import com.example.data.model.ProductItem
import com.example.ui.KiranaViewModel
import com.example.ui.theme.GroceryOrange
import com.example.ui.theme.UpiPurple
import java.util.Locale

@Composable
fun PosBillingScreen(
    viewModel: KiranaViewModel,
    onOpenScanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val subtotal by viewModel.cartSubtotal.collectAsStateWithLifecycle()
    val gstAmount by viewModel.cartGstAmount.collectAsStateWithLifecycle()
    val grandTotal by viewModel.cartGrandTotal.collectAsStateWithLifecycle()
    val changeDue by viewModel.cartChangeDue.collectAsStateWithLifecycle()

    val customerName by viewModel.customerName.collectAsStateWithLifecycle()
    val customerPhone by viewModel.customerPhone.collectAsStateWithLifecycle()
    val selectedCustomerId by viewModel.selectedCustomerId.collectAsStateWithLifecycle()
    val paymentMode by viewModel.paymentMode.collectAsStateWithLifecycle()
    val paymentReference by viewModel.paymentReference.collectAsStateWithLifecycle()
    val discountAmount by viewModel.discountAmount.collectAsStateWithLifecycle()
    val cashReceived by viewModel.cashReceived.collectAsStateWithLifecycle()

    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val customersWithBalance by viewModel.allCustomersWithBalance.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var showCustomerPicker by remember { mutableStateOf(false) }
    var showQuickItemDialog by remember { mutableStateOf(false) }
    var editingDiscountItemId by remember { mutableStateOf<Long?>(null) }
    var lineDiscountInput by remember { mutableStateOf("") }

    var discountInput by remember { mutableStateOf(if (discountAmount > 0) discountAmount.toString() else "") }
    var cashReceivedInput by remember { mutableStateOf(if (cashReceived > 0) cashReceived.toString() else "") }

    val focusManager = LocalFocusManager.current

    // Keep inputs synced if view model changes externally
    LaunchedEffect(discountAmount) {
        if (discountAmount == 0.0 && discountInput.isNotEmpty() && discountInput != "0") {
            discountInput = ""
        }
    }
    LaunchedEffect(cashReceived) {
        if (cashReceived == 0.0 && cashReceivedInput.isNotEmpty() && cashReceivedInput != "0") {
            cashReceivedInput = ""
        }
    }

    // Matching products for quick suggestions
    val matchingProducts = remember(searchQuery, allProducts) {
        val q = searchQuery.trim()
        if (q.length >= 2) {
            allProducts.filter { it.isActive && (it.name.contains(q, ignoreCase = true) || it.barcode.contains(q, ignoreCase = true) || it.sku.contains(q, ignoreCase = true)) }.take(6)
        } else {
            emptyList()
        }
    }

    // Current Customer Due calculation for Khata
    val selectedCustomerObj = customersWithBalance.find { it.customer.id == selectedCustomerId }
    val currentCustomerDue = selectedCustomerObj?.currentBalance ?: 0.0

    // Validation rules
    val isCartEmpty = cartItems.isEmpty()
    val hasInvalidQuantity = cartItems.any { it.quantity <= 0.0 }
    val hasInvalidPrice = cartItems.any { it.rate < 0.0 }
    val hasInsufficientStock = cartItems.any { item ->
        val prod = allProducts.find { it.id == item.productId }
        prod != null && prod.currentStock < item.quantity
    }
    val isCashInvalid = paymentMode == "CASH" && (cashReceived < grandTotal)
    val isKhataInvalid = paymentMode == "KHATA" && (selectedCustomerId == null || selectedCustomerId!! <= 0L)

    val checkoutBlockReason: String? = when {
        isCartEmpty -> "Cart is empty"
        hasInvalidQuantity -> "Quantity must be > 0"
        hasInvalidPrice -> "Price cannot be negative"
        hasInsufficientStock -> "Insufficient stock for one or more items"
        isCashInvalid -> "Cash received (₹${String.format(Locale.ENGLISH, "%.2f", cashReceived)}) is less than total"
        isKhataInvalid -> "Select a registered customer for Khata sale"
        else -> null
    }
    val canCheckout = checkoutBlockReason == null

    BoxWithConstraints(modifier = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        val isWideScreen = maxWidth >= 720.dp

        if (isWideScreen) {
            // Tablet / Large Screen: Two-column layout
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Left Column: Top Search/Scanner + Cart Items
                Column(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                ) {
                    PosTopBar(
                        searchQuery = searchQuery,
                        onSearchChange = { searchQuery = it },
                        onSearchSubmit = {
                            if (searchQuery.isNotBlank()) {
                                viewModel.handleScannedBarcodeInPos(searchQuery)
                                searchQuery = ""
                                focusManager.clearFocus()
                            }
                        },
                        onOpenScanner = onOpenScanner,
                        onOpenQuickItem = { showQuickItemDialog = true }
                    )

                    if (matchingProducts.isNotEmpty()) {
                        QuickProductSuggestionsRow(
                            products = matchingProducts,
                            onProductSelected = { prod ->
                                viewModel.addToCart(prod, 1.0)
                                searchQuery = ""
                                focusManager.clearFocus()
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    CartListSection(
                        cartItems = cartItems,
                        onUpdateQty = { id, qty -> viewModel.updateCartItemQuantity(id, qty) },
                        onRemoveItem = { id -> viewModel.removeFromCart(id) },
                        onOpenDiscountDialog = { id, currentDisc ->
                            editingDiscountItemId = id
                            lineDiscountInput = if (currentDisc > 0) currentDisc.toString() else ""
                        },
                        onClearCart = { viewModel.clearCart() },
                        modifier = Modifier.weight(1f)
                    )
                }

                // Right Column: Summary, Payment & Checkout
                Card(
                    modifier = Modifier
                        .weight(0.9f)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            PosBottomSummary(
                                subtotal = subtotal,
                                discountAmount = discountAmount,
                                discountInput = discountInput,
                                onDiscountInputChange = { input ->
                                    discountInput = input
                                    val parsed = input.toDoubleOrNull() ?: 0.0
                                    viewModel.discountAmount.value = parsed.coerceAtLeast(0.0)
                                },
                                onApplyPresetDiscount = { preset ->
                                    discountInput = preset.toInt().toString()
                                    viewModel.discountAmount.value = preset
                                },
                                gstAmount = gstAmount,
                                grandTotal = grandTotal
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            PosPaymentSection(
                                paymentMode = paymentMode,
                                onSelectPaymentMode = { viewModel.paymentMode.value = it },
                                cashReceived = cashReceived,
                                cashReceivedInput = cashReceivedInput,
                                onCashInputChange = { input ->
                                    cashReceivedInput = input
                                    val parsed = input.toDoubleOrNull() ?: 0.0
                                    viewModel.cashReceived.value = parsed.coerceAtLeast(0.0)
                                },
                                grandTotal = grandTotal,
                                changeDue = changeDue,
                                onQuickCashExact = {
                                    cashReceivedInput = grandTotal.toInt().toString()
                                    viewModel.cashReceived.value = grandTotal
                                },
                                onQuickCashAmount = { amount ->
                                    cashReceivedInput = amount.toInt().toString()
                                    viewModel.cashReceived.value = amount
                                },
                                selectedCustomerId = selectedCustomerId,
                                customerName = customerName,
                                customerPhone = customerPhone,
                                currentCustomerDue = currentCustomerDue,
                                onOpenCustomerPicker = { showCustomerPicker = true },
                                paymentReference = paymentReference,
                                onPaymentReferenceChange = { viewModel.paymentReference.value = it }
                            )
                        }

                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            if (checkoutBlockReason != null && cartItems.isNotEmpty()) {
                                Text(
                                    text = "⚠ $checkoutBlockReason",
                                    color = Color(0xFFDC2626),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                            }

                            Button(
                                onClick = { viewModel.completeSale() },
                                enabled = canCheckout,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("pos_complete_sale_btn"),
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PointOfSale,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "COMPLETE SALE",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                }
            }
        } else {
            // Handheld / Compact Screen: Stacked layout
            Column(modifier = Modifier.fillMaxSize()) {
                PosTopBar(
                    searchQuery = searchQuery,
                    onSearchChange = { searchQuery = it },
                    onSearchSubmit = {
                        if (searchQuery.isNotBlank()) {
                            viewModel.handleScannedBarcodeInPos(searchQuery)
                            searchQuery = ""
                            focusManager.clearFocus()
                        }
                    },
                    onOpenScanner = onOpenScanner,
                    onOpenQuickItem = { showQuickItemDialog = true }
                )

                if (matchingProducts.isNotEmpty()) {
                    QuickProductSuggestionsRow(
                        products = matchingProducts,
                        onProductSelected = { prod ->
                            viewModel.addToCart(prod, 1.0)
                            searchQuery = ""
                            focusManager.clearFocus()
                        }
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Cart items scrollable list (weights to leave space for pinned bottom panel)
                CartListSection(
                    cartItems = cartItems,
                    onUpdateQty = { id, qty -> viewModel.updateCartItemQuantity(id, qty) },
                    onRemoveItem = { id -> viewModel.removeFromCart(id) },
                    onOpenDiscountDialog = { id, currentDisc ->
                        editingDiscountItemId = id
                        lineDiscountInput = if (currentDisc > 0) currentDisc.toString() else ""
                    },
                    onClearCart = { viewModel.clearCart() },
                    modifier = Modifier.weight(1f)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Bottom Checkout Section
                Card(
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        PosBottomSummary(
                            subtotal = subtotal,
                            discountAmount = discountAmount,
                            discountInput = discountInput,
                            onDiscountInputChange = { input ->
                                discountInput = input
                                val parsed = input.toDoubleOrNull() ?: 0.0
                                viewModel.discountAmount.value = parsed.coerceAtLeast(0.0)
                            },
                            onApplyPresetDiscount = { preset ->
                                discountInput = preset.toInt().toString()
                                viewModel.discountAmount.value = preset
                            },
                            gstAmount = gstAmount,
                            grandTotal = grandTotal
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        PosPaymentSection(
                            paymentMode = paymentMode,
                            onSelectPaymentMode = { viewModel.paymentMode.value = it },
                            cashReceived = cashReceived,
                            cashReceivedInput = cashReceivedInput,
                            onCashInputChange = { input ->
                                cashReceivedInput = input
                                val parsed = input.toDoubleOrNull() ?: 0.0
                                viewModel.cashReceived.value = parsed.coerceAtLeast(0.0)
                            },
                            grandTotal = grandTotal,
                            changeDue = changeDue,
                            onQuickCashExact = {
                                cashReceivedInput = grandTotal.toInt().toString()
                                viewModel.cashReceived.value = grandTotal
                            },
                            onQuickCashAmount = { amount ->
                                cashReceivedInput = amount.toInt().toString()
                                viewModel.cashReceived.value = amount
                            },
                            selectedCustomerId = selectedCustomerId,
                            customerName = customerName,
                            customerPhone = customerPhone,
                            currentCustomerDue = currentCustomerDue,
                            onOpenCustomerPicker = { showCustomerPicker = true },
                            paymentReference = paymentReference,
                            onPaymentReferenceChange = { viewModel.paymentReference.value = it }
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        if (checkoutBlockReason != null && cartItems.isNotEmpty()) {
                            Text(
                                text = "⚠ $checkoutBlockReason",
                                color = Color(0xFFDC2626),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }

                        Button(
                            onClick = { viewModel.completeSale() },
                            enabled = canCheckout,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .testTag("pos_complete_sale_btn"),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PointOfSale,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "COMPLETE SALE",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                    }
                }
            }
        }
    }

    // Customer Selection Dialog for Khata
    if (showCustomerPicker) {
        KhataCustomerPickerDialog(
            customers = customersWithBalance,
            selectedCustomerId = selectedCustomerId,
            onCustomerSelected = { cust ->
                viewModel.selectedCustomerId.value = cust.customer.id
                viewModel.customerName.value = cust.customer.name
                viewModel.customerPhone.value = cust.customer.phone
                showCustomerPicker = false
            },
            onDismiss = { showCustomerPicker = false }
        )
    }

    // Line Discount Dialog
    if (editingDiscountItemId != null) {
        Dialog(onDismissRequest = { editingDiscountItemId = null }) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(0.85f)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Item Discount",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedTextField(
                        value = lineDiscountInput,
                        onValueChange = { lineDiscountInput = it },
                        label = { Text("Discount Amount (₹)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(
                            onClick = { editingDiscountItemId = null },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val disc = lineDiscountInput.toDoubleOrNull() ?: 0.0
                                editingDiscountItemId?.let { id ->
                                    viewModel.updateCartItemDiscount(id, disc)
                                }
                                editingDiscountItemId = null
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Apply")
                        }
                    }
                }
            }
        }
    }

    // Quick Loose / Custom Item Dialog
    if (showQuickItemDialog) {
        var quickName by remember { mutableStateOf("") }
        var quickPriceStr by remember { mutableStateOf("") }
        var quickQtyStr by remember { mutableStateOf("1") }

        Dialog(onDismissRequest = { showQuickItemDialog = false }) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth(0.9f)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Quick Loose / Custom Item", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("Bill an unlisted item directly into the cart", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = quickName,
                        onValueChange = { quickName = it },
                        label = { Text("Item Name (e.g. Loose Veggies)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = quickPriceStr,
                            onValueChange = { quickPriceStr = it },
                            label = { Text("Rate ₹") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(
                            value = quickQtyStr,
                            onValueChange = { quickQtyStr = it },
                            label = { Text("Qty") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = { showQuickItemDialog = false }, shape = RoundedCornerShape(10.dp)) {
                            Text("Cancel")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val price = quickPriceStr.toDoubleOrNull() ?: 0.0
                                val qty = quickQtyStr.toDoubleOrNull() ?: 1.0
                                if (quickName.isNotBlank() && price > 0.0) {
                                    val dummyProduct = ProductItem(
                                        id = (100000L..999999L).random(),
                                        name = quickName.trim(),
                                        barcode = "",
                                        category = "Loose & Custom",
                                        unit = "Item",
                                        costPrice = price * 0.8,
                                        sellingPrice = price,
                                        mrp = price,
                                        currentStock = 999.0
                                    )
                                    viewModel.addToCart(dummyProduct, qty)
                                    showQuickItemDialog = false
                                }
                            },
                            enabled = quickName.isNotBlank() && (quickPriceStr.toDoubleOrNull() ?: 0.0) > 0.0,
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Add to Cart")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PosTopBar(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    onSearchSubmit: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenQuickItem: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Barcode / Search Input Field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Scan barcode or search product...", fontSize = 13.sp) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = onSearchSubmit,
                        modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add to Cart",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchSubmit() }),
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .weight(1f)
                .testTag("pos_barcode_search_field")
        )

        // Camera Scanner Button (Large Touch Target >= 48dp)
        FilledTonalIconButton(
            onClick = onOpenScanner,
            modifier = Modifier
                .size(52.dp)
                .testTag("pos_camera_scanner_btn"),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = "Camera Scanner",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }

        // Quick Loose Item Button
        IconButton(
            onClick = onOpenQuickItem,
            modifier = Modifier
                .size(52.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                .testTag("pos_quick_loose_item_btn")
        ) {
            Icon(
                imageVector = Icons.Default.FlashOn,
                contentDescription = "Custom Item",
                tint = GroceryOrange,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun QuickProductSuggestionsRow(
    products: List<ProductItem>,
    onProductSelected: (ProductItem) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(products, key = { it.id }) { product ->
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
                modifier = Modifier
                    .clickable { onProductSelected(product) }
                    .testTag("suggestion_prod_${product.id}")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = product.name,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "₹${product.sellingPrice}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun CartListSection(
    cartItems: List<CartItem>,
    onUpdateQty: (Long, Double) -> Unit,
    onRemoveItem: (Long) -> Unit,
    onOpenDiscountDialog: (Long, Double) -> Unit,
    onClearCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (cartItems.isEmpty()) {
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
            modifier = modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ShoppingCart,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = MaterialTheme.colorScheme.outline
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Cart is Empty",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Scan barcode or search product to start billing",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(modifier = modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Cart Items (${cartItems.size})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Clear All",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .clickable { onClearCart() }
                        .padding(4.dp)
                        .testTag("clear_cart_btn")
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(cartItems, key = { it.productId }) { item ->
                    CartItemRow(
                        item = item,
                        onUpdateQty = { delta -> onUpdateQty(item.productId, item.quantity + delta) },
                        onRemove = { onRemoveItem(item.productId) },
                        onOpenDiscount = { onOpenDiscountDialog(item.productId, item.lineDiscount) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CartItemRow(
    item: CartItem,
    onUpdateQty: (Double) -> Unit,
    onRemove: () -> Unit,
    onOpenDiscount: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = modifier
            .fillMaxWidth()
            .testTag("cart_item_${item.productId}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: Product Name & Remove Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Large touch target remove button (>= 48dp)
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("cart_remove_${item.productId}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove Item",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Row 2: Rate, Unit, Stepper Quantity Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Rate and Unit
                Column {
                    Text(
                        text = "Rate: ₹${String.format(Locale.ENGLISH, "%.2f", item.rate)}" + if (item.unit.isNotBlank()) " / ${item.unit}" else "",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (item.mrp > item.rate) {
                        Text(
                            text = "MRP: ₹${String.format(Locale.ENGLISH, "%.2f", item.mrp)}",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }

                // Quantity Controls (- / +) with large touch targets (>= 48dp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onUpdateQty(-1.0) },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp))
                            .testTag("cart_qty_minus_${item.productId}")
                    ) {
                        Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrease", modifier = Modifier.size(18.dp))
                    }

                    val qtyDisplay = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format(Locale.ENGLISH, "%.1f", item.quantity)
                    Text(
                        text = "$qtyDisplay ${item.unit}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .padding(horizontal = 10.dp)
                            .testTag("cart_qty_${item.productId}")
                    )

                    IconButton(
                        onClick = { onUpdateQty(1.0) },
                        modifier = Modifier
                            .size(48.dp)
                            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(10.dp))
                            .testTag("cart_qty_plus_${item.productId}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase",
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            Spacer(modifier = Modifier.height(6.dp))

            // Row 3: Discount and Line Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Discount tap target
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = if (item.lineDiscount > 0) Color(0xFFDCFCE7) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .clickable { onOpenDiscount() }
                        .sizeIn(minHeight = 36.dp)
                        .testTag("cart_line_discount_${item.productId}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (item.lineDiscount > 0) "Discount: -₹${String.format(Locale.ENGLISH, "%.2f", item.lineDiscount)}" else "Add Discount (₹)",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (item.lineDiscount > 0) Color(0xFF15803D) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Line Total
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Line Total",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "₹" + String.format(Locale.ENGLISH, "%.2f", item.totalAmount),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("cart_line_total_${item.productId}")
                    )
                }
            }
        }
    }
}

@Composable
private fun PosBottomSummary(
    subtotal: Double,
    discountAmount: Double,
    discountInput: String,
    onDiscountInputChange: (String) -> Unit,
    onApplyPresetDiscount: (Double) -> Unit,
    gstAmount: Double,
    grandTotal: Double,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        // Subtotal
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "Subtotal:", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "₹" + String.format(Locale.ENGLISH, "%.2f", subtotal),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier.testTag("pos_subtotal")
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Bill Discount row with presets and input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Discount (₹):", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.width(6.dp))
                listOf(10.0, 20.0, 50.0).forEach { preset ->
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .clickable { onApplyPresetDiscount(preset) }
                    ) {
                        Text(
                            text = "₹${preset.toInt()}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = discountInput,
                onValueChange = onDiscountInputChange,
                placeholder = { Text("0.0", fontSize = 12.sp) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier
                    .width(90.dp)
                    .height(48.dp)
                    .testTag("pos_discount_input"),
                shape = RoundedCornerShape(8.dp)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // GST Tax
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "GST (Inclusive):", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = "₹" + String.format(Locale.ENGLISH, "%.2f", gstAmount),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("pos_gst")
            )
        }

        Spacer(modifier = Modifier.height(6.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(6.dp))

        // Grand Total
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Grand Total:",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "₹" + String.format(Locale.ENGLISH, "%.2f", grandTotal),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.testTag("pos_grand_total")
            )
        }
    }
}

@Composable
private fun PosPaymentSection(
    paymentMode: String,
    onSelectPaymentMode: (String) -> Unit,
    cashReceived: Double,
    cashReceivedInput: String,
    onCashInputChange: (String) -> Unit,
    grandTotal: Double,
    changeDue: Double,
    onQuickCashExact: () -> Unit,
    onQuickCashAmount: (Double) -> Unit,
    selectedCustomerId: Long?,
    customerName: String,
    customerPhone: String,
    currentCustomerDue: Double,
    onOpenCustomerPicker: () -> Unit,
    paymentReference: String,
    onPaymentReferenceChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Payment Method",
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))

        // 4 Payment Buttons: CASH, UPI, CARD, KHATA (Touch Target >= 48dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            val modes = listOf(
                Pair("CASH", Color(0xFF16A34A)),
                Pair("UPI", UpiPurple),
                Pair("CARD", Color(0xFF0284C7)),
                Pair("KHATA", Color(0xFFDC2626))
            )

            modes.forEach { (mode, color) ->
                val isSelected = paymentMode == mode
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isSelected) color else MaterialTheme.colorScheme.surfaceVariant,
                    border = if (isSelected) BorderStroke(2.dp, color) else null,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .clickable { onSelectPaymentMode(mode) }
                        .testTag("payment_mode_$mode")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = mode,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 12.sp,
                            color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Contextual Section based on Payment Mode
        when (paymentMode) {
            "CASH" -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Cash Received (₹):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = cashReceivedInput,
                            onValueChange = onCashInputChange,
                            placeholder = { Text("0.0", fontSize = 13.sp) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .width(120.dp)
                                .height(50.dp)
                                .testTag("pos_cash_received_input"),
                            shape = RoundedCornerShape(8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Quick Buttons: Exact, ₹100, ₹200, ₹500, ₹1000
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier
                                .weight(1.1f)
                                .height(40.dp)
                                .clickable { onQuickCashExact() }
                                .testTag("cash_quick_exact")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("Exact", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }

                        listOf(100.0, 200.0, 500.0, 1000.0).forEach { tender ->
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clickable { onQuickCashAmount(tender) }
                                    .testTag("cash_quick_${tender.toInt()}")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("₹${tender.toInt()}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Change Due Display
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (cashReceived >= grandTotal) Color(0xFFDCFCE7) else Color(0xFFFEE2E2)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (cashReceived >= grandTotal) "Change Due:" else "Short Cash:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (cashReceived >= grandTotal) Color(0xFF15803D) else Color(0xFFB91C1C)
                            )
                            Text(
                                text = "₹" + String.format(
                                    Locale.ENGLISH,
                                    "%.2f",
                                    if (cashReceived >= grandTotal) changeDue else (grandTotal - cashReceived)
                                ),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (cashReceived >= grandTotal) Color(0xFF15803D) else Color(0xFFB91C1C),
                                modifier = Modifier.testTag("pos_change_due")
                            )
                        }
                    }
                }
            }

            "KHATA" -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Select Customer Button / Card
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (selectedCustomerId != null) Color(0xFFEFF6FF) else Color(0xFFFEF2F2),
                        border = BorderStroke(1.dp, if (selectedCustomerId != null) Color(0xFF93C5FD) else Color(0xFFFCA5A5)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenCustomerPicker() }
                            .testTag("khata_select_customer_btn")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = if (selectedCustomerId != null) Color(0xFF1D4ED8) else Color(0xFFDC2626),
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = if (selectedCustomerId != null) customerName else "Select Customer (Required) *",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (selectedCustomerId != null) Color(0xFF1D4ED8) else Color(0xFFDC2626)
                                    )
                                    if (customerPhone.isNotBlank()) {
                                        Text(
                                            text = "Phone: $customerPhone",
                                            fontSize = 11.sp,
                                            color = Color(0xFF2563EB)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "Choose ▾",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = GroceryOrange
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Current Customer Due Card
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (currentCustomerDue > 0) Color(0xFFFEF2F2) else Color(0xFFF0FDF4)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("khata_current_due")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Current Customer Due:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (currentCustomerDue > 0) Color(0xFFDC2626) else Color(0xFF15803D)
                            )
                            Text(
                                text = if (selectedCustomerId != null) {
                                    "₹" + String.format(Locale.ENGLISH, "%.2f", currentCustomerDue)
                                } else {
                                    "Select Customer"
                                },
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (currentCustomerDue > 0) Color(0xFFDC2626) else Color(0xFF15803D)
                            )
                        }
                    }
                }
            }

            "UPI", "CARD" -> {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = if (paymentMode == "UPI") "UPI Ref / UTR (Optional):" else "Card Slip / Auth Code (Optional):",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = paymentReference,
                        onValueChange = onPaymentReferenceChange,
                        placeholder = {
                            Text(
                                if (paymentMode == "UPI") "e.g. 412356789012 or UPI Ref" else "e.g. AUTH-4891",
                                fontSize = 12.sp
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("pos_payment_reference_input"),
                        shape = RoundedCornerShape(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KhataCustomerPickerDialog(
    customers: List<CustomerWithBalance>,
    selectedCustomerId: Long?,
    onCustomerSelected: (CustomerWithBalance) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }

    val filteredCustomers = remember(searchQuery, customers) {
        val q = searchQuery.trim()
        if (q.isEmpty()) customers
        else customers.filter { it.customer.name.contains(q, ignoreCase = true) || it.customer.phone.contains(q) }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.75f)
                .testTag("khata_customer_picker_dialog")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Khata Customer",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by name or mobile...", fontSize = 12.sp) },
                    leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp)
                )

                Spacer(modifier = Modifier.height(10.dp))

                if (filteredCustomers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No registered customers found",
                            color = MaterialTheme.colorScheme.outline,
                            fontSize = 13.sp
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredCustomers, key = { it.customer.id }) { custWithBal ->
                            val isSelected = custWithBal.customer.id == selectedCustomerId
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = if (isSelected) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCustomerSelected(custWithBal) }
                                    .testTag("customer_item_${custWithBal.customer.id}")
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = custWithBal.customer.name,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 14.sp
                                        )
                                        if (custWithBal.customer.phone.isNotBlank()) {
                                            Text(
                                                text = "Mobile: ${custWithBal.customer.phone}",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Due",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "₹${String.format(Locale.ENGLISH, "%.2f", custWithBal.currentBalance)}",
                                            fontWeight = FontWeight.ExtraBold,
                                            fontSize = 13.sp,
                                            color = if (custWithBal.currentBalance > 0) Color(0xFFDC2626) else Color(0xFF15803D)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
