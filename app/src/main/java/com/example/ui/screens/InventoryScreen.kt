package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.shadow
import java.util.Locale
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.TableView
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.KiranaCategories
import com.example.data.model.ProductItem
import com.example.ui.KiranaViewModel
import com.example.ui.StockFilterOption
import com.example.ui.components.AdjustStockDialog
import com.example.ui.components.ProductCard
import com.example.ui.theme.LowStockAlertColor
import com.example.ui.theme.OutOfStockAlertColor
import com.example.ui.theme.kiranaTextFieldColors

@Composable
fun InventoryScreen(
    viewModel: KiranaViewModel,
    onOpenAddProduct: (ProductItem?) -> Unit,
    onOpenScanner: () -> Unit,
    onNavigateToCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val stockFilter by viewModel.stockFilter.collectAsStateWithLifecycle()

    val filteredProducts by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val lowStockProducts by viewModel.lowStockProducts.collectAsStateWithLifecycle()
    val outOfStockProducts by viewModel.outOfStockProducts.collectAsStateWithLifecycle()
    val inactiveProducts by viewModel.inactiveProducts.collectAsStateWithLifecycle()

    val cartItems by viewModel.cartItems.collectAsStateWithLifecycle()
    val cartGrandTotal by viewModel.cartGrandTotal.collectAsStateWithLifecycle()

    var productToAdjust by remember { mutableStateOf<ProductItem?>(null) }
    var sortOption by remember { mutableStateOf("Name A-Z") }
    val sortOptions = listOf("Name A-Z", "Stock Low-High", "Price Low-High")
    var showLoadDemoConfirmDialog by remember { mutableStateOf(false) }

    // Active products count
    val activeProductsCount = allProducts.count { it.isActive }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .testTag("inventory_screen")
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // 1. TOP BAR: Search Field + Add Product + Export CSV (Single clean row)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text("Search by name, barcode, SKU...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                                    Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                                }
                            }
                            IconButton(
                                onClick = onOpenScanner,
                                modifier = Modifier.testTag("inventory_scan_barcode_btn")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan Barcode",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = kiranaTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("inventory_search_field")
                )

                // Add Product Button
                Button(
                    onClick = { onOpenAddProduct(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("inventory_add_product_btn")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }

                // Export CSV Icon Button
                FilledTonalIconButton(
                    onClick = { viewModel.exportCsv(context) },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .size(46.dp)
                        .testTag("inventory_export_csv_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.TableView,
                        contentDescription = "Export Excel CSV",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 2. UNIFIED FILTER ROW: Stock Status Chips + Category Chips (Single horizontal scroll)
            val standardCats = KiranaCategories.ALL_CATEGORIES
            val existingCats = allProducts.map { it.category.trim() }.filter { it.isNotBlank() }.distinct()
            val allCategoryList = (standardCats.map { it.nameEn } + existingCats).distinct()

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // All Products Chip
                item {
                    FilterChip(
                        selected = stockFilter == StockFilterOption.ALL && (selectedCategory == "All" || selectedCategory == "All Categories"),
                        onClick = {
                            viewModel.stockFilter.value = StockFilterOption.ALL
                            viewModel.selectedCategory.value = "All"
                        },
                        label = { Text("All ($activeProductsCount)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("tab_filter_all")
                    )
                }

                // Low Stock Chip
                item {
                    FilterChip(
                        selected = stockFilter == StockFilterOption.LOW_STOCK,
                        onClick = {
                            viewModel.stockFilter.value = if (stockFilter == StockFilterOption.LOW_STOCK) StockFilterOption.ALL else StockFilterOption.LOW_STOCK
                        },
                        leadingIcon = if (lowStockProducts.isNotEmpty()) {
                            { Icon(Icons.Default.Warning, contentDescription = null, tint = LowStockAlertColor, modifier = Modifier.size(12.dp)) }
                        } else null,
                        label = { Text("Low Stock (${lowStockProducts.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = LowStockAlertColor.copy(alpha = 0.2f),
                            selectedLabelColor = LowStockAlertColor
                        ),
                        modifier = Modifier.testTag("tab_filter_low_stock")
                    )
                }

                // Out of Stock Chip
                item {
                    FilterChip(
                        selected = stockFilter == StockFilterOption.OUT_OF_STOCK,
                        onClick = {
                            viewModel.stockFilter.value = if (stockFilter == StockFilterOption.OUT_OF_STOCK) StockFilterOption.ALL else StockFilterOption.OUT_OF_STOCK
                        },
                        leadingIcon = if (outOfStockProducts.isNotEmpty()) {
                            { Icon(Icons.Default.Warning, contentDescription = null, tint = OutOfStockAlertColor, modifier = Modifier.size(12.dp)) }
                        } else null,
                        label = { Text("Out of Stock (${outOfStockProducts.size})", fontSize = 11.5.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = OutOfStockAlertColor.copy(alpha = 0.2f),
                            selectedLabelColor = OutOfStockAlertColor
                        ),
                        modifier = Modifier.testTag("tab_filter_out_of_stock")
                    )
                }

                // Inactive Chip (if any exist)
                if (inactiveProducts.isNotEmpty()) {
                    item {
                        FilterChip(
                            selected = stockFilter == StockFilterOption.INACTIVE,
                            onClick = {
                                viewModel.stockFilter.value = if (stockFilter == StockFilterOption.INACTIVE) StockFilterOption.ALL else StockFilterOption.INACTIVE
                            },
                            label = { Text("Inactive (${inactiveProducts.size})", fontSize = 11.5.sp) },
                            modifier = Modifier.testTag("tab_filter_inactive")
                        )
                    }
                }

                // Category Chips
                items(allCategoryList) { category ->
                    val isCatSelected = category.equals(selectedCategory, ignoreCase = true)
                    val count = allProducts.count {
                        it.category.equals(category, ignoreCase = true) ||
                            it.category.contains(category, ignoreCase = true) ||
                            category.contains(it.category, ignoreCase = true)
                    }
                    val catInfo = KiranaCategories.getCategoryInfo(category)
                    FilterChip(
                        selected = isCatSelected,
                        onClick = {
                            viewModel.selectedCategory.value = if (isCatSelected) "All" else category
                        },
                        label = {
                            Text(
                                text = "${catInfo.emoji} $category ($count)",
                                fontSize = 11.sp,
                                fontWeight = if (isCatSelected) FontWeight.Bold else FontWeight.Medium
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.secondary,
                            selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier.testTag("cat_chip_${category.lowercase().replace(" ", "_")}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 3. SORT & RESULT COUNT BAR (Clean, non-intrusive)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${filteredProducts.size} ${if (filteredProducts.size == 1) "product" else "products"}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Compact Sort Buttons
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("Sort:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    sortOptions.forEach { opt ->
                        val isSortSelected = sortOption == opt
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (isSortSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            modifier = Modifier
                                .clickable { sortOption = opt }
                                .padding(horizontal = 2.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = opt,
                                fontSize = 10.5.sp,
                                fontWeight = if (isSortSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSortSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Sorted Products List
            val sortedProducts = remember(filteredProducts, sortOption) {
                when (sortOption) {
                    "Stock Low-High" -> filteredProducts.sortedBy { it.currentStock }
                    "Price Low-High" -> filteredProducts.sortedBy { it.sellingPrice }
                    else -> filteredProducts.sortedBy { it.name.lowercase() }
                }
            }

            // Products Grid: Responsive for Small Phones & Large Tablets
            if (sortedProducts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (allProducts.isEmpty()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "Store Inventory is Empty",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Add products to start billing or load demo products for testing.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { onOpenAddProduct(null) },
                                modifier = Modifier.testTag("empty_add_product_btn")
                            ) {
                                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Add Product")
                            }
                            OutlinedButton(
                                onClick = { showLoadDemoConfirmDialog = true },
                                modifier = Modifier.testTag("load_demo_products_btn")
                            ) {
                                Text("LOAD DEMO PRODUCTS", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No products found matching criteria",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Tap '+ Add' to register a new grocery item",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            } else {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val isWideScreen = maxWidth >= 680.dp
                    LazyVerticalGrid(
                        columns = if (isWideScreen) GridCells.Adaptive(minSize = 340.dp) else GridCells.Fixed(1),
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        contentPadding = PaddingValues(top = 4.dp, bottom = if (cartItems.isNotEmpty()) 170.dp else 100.dp)
                    ) {
                        items(sortedProducts, key = { it.id }) { product ->
                            val itemCartQty = cartItems.find { it.productId == product.id }?.quantity ?: 0.0
                            ProductCard(
                                product = product,
                                onEdit = { onOpenAddProduct(it) },
                                onAdjustStock = { productToAdjust = it },
                                onDeactivate = { viewModel.deactivateProduct(it) },
                                onReactivate = { viewModel.reactivateProduct(it) },
                                onAddToCart = { viewModel.addToCart(it) },
                                cartQuantity = itemCartQty,
                                onDelete = { viewModel.deleteProduct(it) }
                            )
                        }
                    }
                }
            }
        }

        // Floating Cart Bar (Shows what's inside cart and total with one-tap navigation)
        if (cartItems.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .shadow(10.dp, RoundedCornerShape(16.dp))
                    .testTag("floating_cart_bar"),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCart() }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BadgedBox(
                            badge = {
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(
                                        text = cartItems.size.toString(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ShoppingCart,
                                contentDescription = "Cart",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "${cartItems.size} ${if (cartItems.size == 1) "item" else "items"} in cart",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "Total: ₹${String.format(Locale.ENGLISH, "%.2f", cartGrandTotal)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Button(
                        onClick = onNavigateToCart,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        modifier = Modifier.testTag("view_cart_btn")
                    ) {
                        Text(
                            text = "VIEW CART →",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // Floating Action Button to Add Product (Accessibility & Convenience)
        FloatingActionButton(
            onClick = { onOpenAddProduct(null) },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = if (cartItems.isNotEmpty()) 86.dp else 24.dp, end = 20.dp)
                .testTag("fab_add_product")
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Add Product")
        }
    }

    // Dedicated Stock Adjustment Dialog (Mandatory per Phase 9)
    if (productToAdjust != null) {
        AdjustStockDialog(
            product = productToAdjust!!,
            onDismiss = { productToAdjust = null },
            onConfirmAdjustment = { type, qty, reason, ref ->
                viewModel.performStockAdjustment(
                    productId = productToAdjust!!.id,
                    adjustmentType = type,
                    quantity = qty,
                    reason = reason,
                    reference = ref
                )
            }
        )
    }

    // Dedicated Confirmation Dialog for LOAD DEMO PRODUCTS (Explicit User Action Required)
    if (showLoadDemoConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showLoadDemoConfirmDialog = false },
            title = {
                Text("LOAD DEMO PRODUCTS", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            },
            text = {
                Text("Load demo products into store inventory? This will add standard grocery items (Atta, Dal, Oil, Salt, etc.) to your real catalog for testing. This action requires your explicit confirmation.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLoadDemoConfirmDialog = false
                        viewModel.loadDemoCatalog()
                    },
                    modifier = Modifier.testTag("confirm_load_demo_btn")
                ) {
                    Text("LOAD DEMO PRODUCTS")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showLoadDemoConfirmDialog = false },
                    modifier = Modifier.testTag("cancel_load_demo_btn")
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}
