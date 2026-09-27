package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.CustomerWithBalance
import com.example.ui.KiranaViewModel
import com.example.ui.StockFilterOption
import com.example.ui.theme.GroceryOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Professional Grocery Store Dashboard (Phase 15).
 *
 * Header:
 * - Shop Name
 * - Date
 *
 * Cards (All 8 mandatory metrics):
 * - Today's Sales
 * - Today's Bills
 * - Today's Gross Profit
 * - Khata Outstanding
 * - Products
 * - Low Stock
 * - Out of Stock
 * - Inventory Value
 *
 * Quick Actions (All 7 mandatory):
 * - New Sale
 * - Purchase
 * - Add Product
 * - Adjust Stock
 * - Customers
 * - Reports
 * - Backup
 */
@Composable
fun DashboardScreen(
    viewModel: KiranaViewModel,
    onNavigateToPos: () -> Unit,
    onNavigateToInventory: (StockFilterOption) -> Unit,
    onOpenAddProduct: () -> Unit,
    onOpenScanner: () -> Unit,
    onOpenPurchase: () -> Unit = {},
    onOpenReports: () -> Unit = {},
    onOpenBackup: () -> Unit = {},
    onOpenStockAlert: () -> Unit = {},
    onOpenCustomers: () -> Unit = {},
    onOpenGitHubReleaseGuide: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val shopSettings by viewModel.shopSettings.collectAsStateWithLifecycle()
    val allCustomersWithBalance by viewModel.allCustomersWithBalance.collectAsStateWithLifecycle()

    // Real-time Database Metrics
    val todaySalesTotal by viewModel.todaySalesTotal.collectAsStateWithLifecycle()
    val todaySalesCount by viewModel.todaySalesCount.collectAsStateWithLifecycle()
    val todayGrossProfit by viewModel.todayGrossProfit.collectAsStateWithLifecycle()
    val totalKhataOutstanding by viewModel.totalKhataOutstanding.collectAsStateWithLifecycle()

    val productCount by viewModel.productCount.collectAsStateWithLifecycle()
    val lowStockCount by viewModel.lowStockCount.collectAsStateWithLifecycle()
    val outOfStockCount by viewModel.outOfStockCount.collectAsStateWithLifecycle()
    val totalCostValue by viewModel.totalCostValue.collectAsStateWithLifecycle()

    val totalLowStockCount = lowStockCount + outOfStockCount

    val currentDateStr = remember {
        SimpleDateFormat("EEEE, dd MMMM yyyy", Locale.ENGLISH).format(Date())
    }

    var showCustomersDialog by remember { mutableStateOf(false) }

    val quickActions = listOf(
        QuickActionItem("New Sale", Icons.Default.PointOfSale, Color(0xFFCCFBF1), Color(0xFF0F766E)) {
            onNavigateToPos()
        },
        QuickActionItem("Purchase", Icons.Default.ShoppingCart, Color(0xFFEFF6FF), Color(0xFF2563EB)) {
            onOpenPurchase()
        },
        QuickActionItem("Add Product", Icons.Default.Add, Color(0xFFF0FDF4), Color(0xFF16A34A)) {
            onOpenAddProduct()
        },
        QuickActionItem("Adjust Stock", Icons.Default.Inventory2, Color(0xFFFEF3C7), Color(0xFFD97706)) {
            onNavigateToInventory(StockFilterOption.ALL)
        },
        QuickActionItem("Customers", Icons.Default.Group, Color(0xFFF5F3FF), Color(0xFF7C3AED)) {
            showCustomersDialog = true
            onOpenCustomers()
        },
        QuickActionItem("Reports", Icons.Default.BarChart, Color(0xFFFDF2F8), Color(0xFFDB2777)) {
            onOpenReports()
        },
        QuickActionItem("Backup", Icons.Default.CloudUpload, Color(0xFFECFEFF), Color(0xFF0891B2)) {
            onOpenBackup()
        }
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .testTag("dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 80.dp)
    ) {
        // ==========================================
        // 1. DASHBOARD HEADER: Shop Name & Date
        // ==========================================
        item {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("welcome_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Storefront,
                                contentDescription = "Shop Storefront",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = shopSettings.shopName.ifBlank { "Shree Ganesh Kirana Store" },
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.testTag("dashboard_shop_name")
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = currentDateStr,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.testTag("dashboard_date")
                        )
                    }

                    // Barcode Scanner Action
                    IconButton(
                        onClick = onOpenScanner,
                        modifier = Modifier
                            .size(42.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("dashboard_scan_barcode_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan Barcode",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        // ==========================================
        // 2. QUICK ACTIONS SECTION (All 7 required)
        // ==========================================
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "QUICK ACTIONS",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
                )

                // Row 1 of Quick Actions (4 items)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickActions.take(4).forEach { action ->
                        QuickActionButton(
                            item = action,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 2 of Quick Actions (3 items)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    quickActions.drop(4).forEach { action ->
                        QuickActionButton(
                            item = action,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Stock Alert Notification Banner
        if (totalLowStockCount > 0) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEE2E2)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onOpenStockAlert() }
                        .testTag("dashboard_stock_alert_card")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFFCA5A5),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Alert",
                                    tint = Color(0xFF991B1B),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Inventory Stock Alert ($totalLowStockCount items)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF991B1B)
                            )
                            Text(
                                text = "$lowStockCount items low in stock • $outOfStockCount out of stock",
                                fontSize = 11.sp,
                                color = Color(0xFF7F1D1D)
                            )
                        }

                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "View Alerts",
                            tint = Color(0xFF991B1B),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // ==========================================
        // 3. DASHBOARD CARDS (All 8 mandatory metrics)
        // ==========================================
        item {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "STORE PERFORMANCE & INVENTORY",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 8.dp)
                )

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Row 1: Today's Sales & Today's Bills
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 1. Today's Sales
                        DashboardMetricCard(
                            title = "Today's Sales",
                            value = "₹ " + String.format(Locale.ENGLISH, "%,.2f", todaySalesTotal ?: 0.0),
                            subtitle = if (todaySalesCount > 0) "$todaySalesCount sales made today" else "No sales recorded today",
                            icon = Icons.Default.MonetizationOn,
                            backgroundColor = GroceryOrange,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToPos() }
                                .testTag("card_todays_sales")
                        )

                        // 2. Today's Bills
                        DashboardMetricCard(
                            title = "Today's Bills",
                            value = todaySalesCount.toString(),
                            subtitle = "Receipts & invoices generated",
                            icon = Icons.AutoMirrored.Filled.ReceiptLong,
                            backgroundColor = Color(0xFF0284C7),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenReports() }
                                .testTag("card_todays_bills")
                        )
                    }

                    // Row 2: Today's Gross Profit & Khata Outstanding
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 3. Today's Gross Profit
                        DashboardMetricCard(
                            title = "Today's Gross Profit",
                            value = "₹ " + String.format(Locale.ENGLISH, "%,.2f", todayGrossProfit),
                            subtitle = "Sales revenue minus COGS",
                            icon = Icons.Default.TrendingUp,
                            backgroundColor = if (todayGrossProfit >= 0) Color(0xFF16A34A) else Color(0xFFDC2626),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpenReports() }
                                .testTag("card_todays_gross_profit")
                        )

                        // 4. Khata Outstanding
                        DashboardMetricCard(
                            title = "Khata Outstanding",
                            value = "₹ " + String.format(Locale.ENGLISH, "%,.2f", totalKhataOutstanding),
                            subtitle = "Total pending customer credit",
                            icon = Icons.Default.AccountBalanceWallet,
                            backgroundColor = if (totalKhataOutstanding > 0) Color(0xFFE11D48) else Color(0xFF475569),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showCustomersDialog = true }
                                .testTag("card_khata_outstanding")
                        )
                    }

                    // Row 3: Products & Inventory Value
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 5. Products
                        DashboardMetricCard(
                            title = "Products",
                            value = productCount.toString(),
                            subtitle = "Active items in store catalog",
                            icon = Icons.Default.Inventory2,
                            backgroundColor = Color(0xFF4F46E5),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToInventory(StockFilterOption.ALL) }
                                .testTag("card_products")
                        )

                        // 8. Inventory Value
                        DashboardMetricCard(
                            title = "Inventory Value",
                            value = "₹ " + String.format(Locale.ENGLISH, "%,.2f", totalCostValue ?: 0.0),
                            subtitle = "Total valuation at cost price",
                            icon = Icons.Default.BarChart,
                            backgroundColor = Color(0xFF0D9488),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToInventory(StockFilterOption.ALL) }
                                .testTag("card_inventory_value")
                        )
                    }

                    // Row 4: Low Stock & Out of Stock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 6. Low Stock
                        DashboardMetricCard(
                            title = "Low Stock",
                            value = lowStockCount.toString(),
                            subtitle = "Items below minimum threshold",
                            icon = Icons.Default.Warning,
                            backgroundColor = if (lowStockCount > 0) Color(0xFFD97706) else Color(0xFF64748B),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToInventory(StockFilterOption.LOW_STOCK) }
                                .testTag("card_low_stock")
                        )

                        // 7. Out of Stock
                        DashboardMetricCard(
                            title = "Out of Stock",
                            value = outOfStockCount.toString(),
                            subtitle = "Items with zero inventory",
                            icon = Icons.Default.ErrorOutline,
                            backgroundColor = if (outOfStockCount > 0) Color(0xFFDC2626) else Color(0xFF64748B),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onNavigateToInventory(StockFilterOption.OUT_OF_STOCK) }
                                .testTag("card_out_of_stock")
                        )
                    }
                }
            }
        }
    }

    // Customers / Khata Overview Dialog
    if (showCustomersDialog) {
        CustomerKhataOverviewDialog(
            customers = allCustomersWithBalance,
            onDismiss = { showCustomersDialog = false },
            onSelectCustomer = { cust ->
                viewModel.selectedCustomerId.value = cust.customer.id
                showCustomersDialog = false
                onNavigateToPos()
            }
        )
    }
}

private data class QuickActionItem(
    val title: String,
    val icon: ImageVector,
    val backgroundColor: Color,
    val iconColor: Color,
    val onClick: () -> Unit
)

@Composable
private fun QuickActionButton(
    item: QuickActionItem,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
            .height(84.dp)
            .clickable(onClick = item.onClick)
            .testTag("quick_action_${item.title.lowercase().replace(" ", "_")}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                shape = CircleShape,
                color = item.backgroundColor,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = item.icon,
                        contentDescription = item.title,
                        tint = item.iconColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = item.title,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DashboardMetricCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.height(112.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    shape = CircleShape,
                    color = backgroundColor.copy(alpha = 0.15f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = backgroundColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Column {
                Text(
                    text = value,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CustomerKhataOverviewDialog(
    customers: List<CustomerWithBalance>,
    onDismiss: () -> Unit,
    onSelectCustomer: (CustomerWithBalance) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    val filtered = remember(searchQuery, customers) {
        if (searchQuery.isBlank()) customers else {
            customers.filter {
                it.customer.name.contains(searchQuery, ignoreCase = true) ||
                        it.customer.phone.contains(searchQuery)
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Group, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Customers & Khata Ledger", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search customer name / mobile") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                if (filtered.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No customers found", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filtered, key = { it.customer.id }) { custWithBal ->
                            val balance = custWithBal.currentBalance
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectCustomer(custWithBal) }
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(custWithBal.customer.name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                                        Text(custWithBal.customer.phone.ifBlank { "No phone" }, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "₹ " + String.format(Locale.ENGLISH, "%.2f", balance),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 13.sp,
                                            color = if (balance > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                                        )
                                        Text(
                                            text = if (balance > 0) "Credit Due" else "Clear",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = if (balance > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Close")
            }
        }
    )
}
