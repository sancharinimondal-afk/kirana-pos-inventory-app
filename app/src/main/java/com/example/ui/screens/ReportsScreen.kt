package com.example.ui.screens

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditCalendar
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billing.ProfitCalculator
import com.example.ui.KiranaViewModel
import com.example.ui.theme.GroceryGreen
import com.example.ui.theme.GroceryOrange
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class TopItemSold(
    val name: String,
    val quantity: Double,
    val unit: String
)

enum class ReportPeriodType(val title: String, val tag: String) {
    TODAY("Today", "reports_tab_today"),
    LAST_7_DAYS("Last 7 Days", "reports_tab_last_7_days"),
    THIS_MONTH("This Month", "reports_tab_this_month"),
    LAST_MONTH("Last Month", "reports_tab_last_month"),
    CUSTOM("Custom Date Range", "reports_tab_custom_date_range")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: KiranaViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // Reactive streams from database
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    val allSaleItems by viewModel.allSaleItems.collectAsStateWithLifecycle()
    val lowStockCount by viewModel.lowStockCount.collectAsStateWithLifecycle()
    val outOfStockCount by viewModel.outOfStockCount.collectAsStateWithLifecycle()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val periods = remember { ReportPeriodType.entries.toTypedArray() }

    // Standard date bounds calculation
    val (startOfToday, endOfToday) = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val s = cal.timeInMillis
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        Pair(s, cal.timeInMillis)
    }

    val (startOfLast7Days, endOfLast7Days) = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        Pair(cal.timeInMillis, endOfToday)
    }

    val (startOfThisMonth, endOfThisMonth) = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        Pair(cal.timeInMillis, endOfToday)
    }

    val (startOfLastMonth, endOfLastMonth) = remember {
        val cal = Calendar.getInstance()
        cal.add(Calendar.MONTH, -1)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val s = cal.timeInMillis
        val maxDay = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        cal.set(Calendar.DAY_OF_MONTH, maxDay)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        Pair(s, cal.timeInMillis)
    }

    // Custom date range state
    var customStartDate by remember { mutableLongStateOf(startOfLast7Days) }
    var customEndDate by remember { mutableLongStateOf(endOfToday) }

    // Active period time bounds
    val (activePeriodStart, activePeriodEnd) = when (periods[selectedTabIndex]) {
        ReportPeriodType.TODAY -> Pair(startOfToday, endOfToday)
        ReportPeriodType.LAST_7_DAYS -> Pair(startOfLast7Days, endOfLast7Days)
        ReportPeriodType.THIS_MONTH -> Pair(startOfThisMonth, endOfThisMonth)
        ReportPeriodType.LAST_MONTH -> Pair(startOfLastMonth, endOfLastMonth)
        ReportPeriodType.CUSTOM -> Pair(customStartDate, customEndDate)
    }

    // Filter transactions: strictly within period bounds and EXCLUDING cancelled transactions
    val periodTransactions = remember(allTransactions, activePeriodStart, activePeriodEnd) {
        allTransactions.filter { tx ->
            tx.timestamp in activePeriodStart..activePeriodEnd && !tx.isCancelled && tx.cancelledAt == null
        }
    }

    // Associated snapshot line items
    val periodSaleItems = remember(allSaleItems, periodTransactions) {
        val txIds = periodTransactions.map { it.id }.toSet()
        allSaleItems.filter { it.transactionId in txIds }
    }

    // Core authoritative calculation using ProfitCalculator (Shared Engine)
    val periodReport = remember(periodTransactions, periodSaleItems) {
        ProfitCalculator.calculatePeriodReport(periodTransactions, periodSaleItems)
    }

    // Top Selling Items in the period
    val topSellingItems = remember(periodTransactions, periodSaleItems) {
        val countMap = mutableMapOf<String, Pair<Double, String>>()
        if (periodSaleItems.isNotEmpty()) {
            for (item in periodSaleItems) {
                val current = countMap[item.productName]
                if (current == null) {
                    countMap[item.productName] = Pair(item.quantity, item.unit)
                } else {
                    countMap[item.productName] = Pair(current.first + item.quantity, current.second)
                }
            }
        } else {
            for (tx in periodTransactions) {
                try {
                    val array = JSONArray(tx.itemsJson)
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val name = obj.optString("name", "Item")
                        val qty = obj.optDouble("quantity", 1.0)
                        val unit = obj.optString("unit", "Pc")
                        val current = countMap[name]
                        if (current == null) {
                            countMap[name] = Pair(qty, unit)
                        } else {
                            countMap[name] = Pair(current.first + qty, current.second)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
        countMap.entries
            .map { TopItemSold(it.key, it.value.first, it.value.second) }
            .sortedByDescending { it.quantity }
            .take(5)
    }

    // CSV Export State
    var exportedFile by remember { mutableStateOf<File?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }

    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Business Reports & Profit",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "${periods[selectedTabIndex].title} • Cancelled bills excluded",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            viewModel.exportSalesCsv(context) { file ->
                                if (file != null) {
                                    exportedFile = file
                                    showExportDialog = true
                                } else {
                                    Toast.makeText(context, "Failed to generate CSV export", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = "Export CSV",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
                .testTag("reports_screen")
        ) {
            // Horizontal Scrollable Tabs: Today, Last 7 Days, This Month, Last Month, Custom Date Range
            ScrollableTabRow(
                selectedTabIndex = selectedTabIndex,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.primary,
                edgePadding = 12.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                        color = MaterialTheme.colorScheme.primary,
                        height = 3.dp
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                periods.forEachIndexed { index, periodType ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { selectedTabIndex = index },
                        text = {
                            Text(
                                text = periodType.title,
                                fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 13.sp,
                                color = if (selectedTabIndex == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        modifier = Modifier.testTag(periodType.tag)
                    )
                }
            }

            // Custom Date Range Banner & Picker controls
            if (periods[selectedTabIndex] == ReportPeriodType.CUSTOM) {
                Card(
                    shape = RoundedCornerShape(0.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DateRange,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "${dateFormatter.format(Date(customStartDate))} — ${dateFormatter.format(Date(customEndDate))}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val cal = Calendar.getInstance().apply { timeInMillis = customStartDate }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val newCal = Calendar.getInstance().apply {
                                                set(y, m, d, 0, 0, 0)
                                                set(Calendar.MILLISECOND, 0)
                                            }
                                            customStartDate = newCal.timeInMillis
                                            if (customStartDate > customEndDate) {
                                                customEndDate = customStartDate + (24 * 3600 * 1000L - 1)
                                            }
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("btn_pick_start_date")
                            ) {
                                Text("From", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = {
                                    val cal = Calendar.getInstance().apply { timeInMillis = customEndDate }
                                    DatePickerDialog(
                                        context,
                                        { _, y, m, d ->
                                            val newCal = Calendar.getInstance().apply {
                                                set(y, m, d, 23, 59, 59)
                                                set(Calendar.MILLISECOND, 999)
                                            }
                                            customEndDate = newCal.timeInMillis
                                            if (customEndDate < customStartDate) {
                                                customStartDate = customEndDate - (24 * 3600 * 1000L - 1)
                                            }
                                        },
                                        cal.get(Calendar.YEAR),
                                        cal.get(Calendar.MONTH),
                                        cal.get(Calendar.DAY_OF_MONTH)
                                    ).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp).testTag("btn_pick_end_date")
                            ) {
                                Text("To", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                contentPadding = PaddingValues(top = 14.dp, bottom = 40.dp)
            ) {
                // PRIMARY PROFIT & FINANCIAL HERO CARD
                item {
                    Card(
                        shape = RoundedCornerShape(18.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        modifier = Modifier.fillMaxWidth().testTag("report_hero_card")
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            // Top Row: Period Title & Margin Tag
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${periods[selectedTabIndex].title.uppercase()} FINANCIAL SUMMARY",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.5.sp
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "₹ ${String.format(Locale.getDefault(), "%,.2f", periodReport.totalSales)}",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 26.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        modifier = Modifier.testTag("report_total_sales")
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (periodReport.overallMarginPercent >= 0) Color(0xFF15803D) else Color(0xFFDC2626),
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.TrendingUp,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${String.format(Locale.ENGLISH, "%.1f", periodReport.overallMarginPercent)}% Margin",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f))
                            Spacer(modifier = Modifier.height(12.dp))

                            // Grid of Key Financials: Cost, Gross Profit, Bills, GST
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Cost of Goods (COGS)", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f), fontSize = 11.sp)
                                    Text(
                                        text = "₹ ${String.format(Locale.getDefault(), "%,.2f", periodReport.totalCost)}",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.testTag("report_total_cost")
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Gross Profit", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f), fontSize = 11.sp)
                                    Text(
                                        text = "₹ ${String.format(Locale.getDefault(), "%,.2f", periodReport.totalGrossProfit)}",
                                        color = if (periodReport.totalGrossProfit >= 0) Color(0xFF16A34A) else Color(0xFFDC2626),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                        modifier = Modifier.testTag("report_gross_profit")
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Completed Bills", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f), fontSize = 11.sp)
                                    Text(
                                        text = "${periodReport.totalBills} bills",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.testTag("report_total_bills")
                                    )
                                }

                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Total GST Collected", color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f), fontSize = 11.sp)
                                    Text(
                                        text = "₹ ${String.format(Locale.getDefault(), "%,.2f", periodReport.totalGst)}",
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        modifier = Modifier.testTag("report_total_gst")
                                    )
                                }
                            }

                            if (periodReport.totalDiscount > 0.0) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Total Discounts Given", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        Text(
                                            text = "₹ ${String.format(Locale.getDefault(), "%,.2f", periodReport.totalDiscount)}",
                                            color = Color(0xFFFBBF24),
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Avg Bill Value", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                        Text(
                                            text = "₹ ${String.format(Locale.getDefault(), "%,.2f", periodReport.averageBillValue)}",
                                            color = Color.White,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 13.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // PAYMENT MODE BREAKDOWN
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Payment Breakdown",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            PaymentModeRow(
                                label = "Cash",
                                amount = periodReport.paymentBreakdown.cashSales,
                                total = periodReport.totalSales,
                                icon = Icons.Default.MonetizationOn,
                                color = GroceryGreen
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            PaymentModeRow(
                                label = "UPI / QR",
                                amount = periodReport.paymentBreakdown.upiSales,
                                total = periodReport.totalSales,
                                icon = Icons.Default.QrCode,
                                color = Color(0xFF2563EB)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            PaymentModeRow(
                                label = "Khata Udhar",
                                amount = periodReport.paymentBreakdown.khataSales,
                                total = periodReport.totalSales,
                                icon = Icons.Default.AccountBalanceWallet,
                                color = GroceryOrange
                            )
                            if (periodReport.paymentBreakdown.cardSales > 0.0) {
                                Spacer(modifier = Modifier.height(8.dp))
                                PaymentModeRow(
                                    label = "Card / Bank",
                                    amount = periodReport.paymentBreakdown.cardSales,
                                    total = periodReport.totalSales,
                                    icon = Icons.Default.ShoppingCart,
                                    color = Color(0xFF8B5CF6)
                                )
                            }
                        }
                    }
                }

                // TOP SELLING ITEMS CARD
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Top Selling Items",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = null,
                                    tint = GroceryOrange,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            if (topSellingItems.isEmpty()) {
                                Text(
                                    text = "No sales recorded in this period.",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            } else {
                                topSellingItems.forEachIndexed { index, item ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 5.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Surface(
                                                shape = CircleShape,
                                                color = GroceryOrange.copy(alpha = 0.12f),
                                                modifier = Modifier.size(22.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = "${index + 1}",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = GroceryOrange
                                                    )
                                                }
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = item.name,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else String.format(Locale.ENGLISH, "%.1f", item.quantity)
                                        Text(
                                            text = "$qtyStr ${item.unit}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    if (index < topSellingItems.size - 1) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                    }
                                }
                            }
                        }
                    }
                }

                // SALES & PROFIT AUDIT TRAIL (BREAKDOWN PER TRANSACTION)
                item {
                    Text(
                        text = "SALES AUDIT TRAIL (${periodReport.salesBreakdown.size} Bills)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        letterSpacing = 0.6.sp,
                        modifier = Modifier.padding(start = 2.dp, top = 4.dp)
                    )
                }

                if (periodReport.salesBreakdown.isEmpty()) {
                    item {
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(modifier = Modifier.padding(20.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "No non-cancelled sales found in this period.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    items(periodReport.salesBreakdown.reversed()) { sale ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                            modifier = Modifier.fillMaxWidth().testTag("sale_breakdown_${sale.invoiceNumber}")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = sale.invoiceNumber,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = when (sale.paymentMode.uppercase().trim()) {
                                            "CASH" -> Color(0xFFDCFCE7)
                                            "UPI" -> Color(0xFFDBEAFE)
                                            "KHATA" -> Color(0xFFFEF3C7)
                                            else -> Color(0xFFF3E8FF)
                                        }
                                    ) {
                                        Text(
                                            text = sale.paymentMode.uppercase(),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (sale.paymentMode.uppercase().trim()) {
                                                "CASH" -> Color(0xFF166534)
                                                "UPI" -> Color(0xFF1E40AF)
                                                "KHATA" -> Color(0xFFB45309)
                                                else -> Color(0xFF6B21A8)
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(sale.timestamp)),
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                Spacer(modifier = Modifier.height(8.dp))
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                Spacer(modifier = Modifier.height(8.dp))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text("Revenue (Net)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = "₹ ${String.format(Locale.getDefault(), "%,.2f", sale.revenue)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }

                                    Column {
                                        Text("Cost (Historical)", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = "₹ ${String.format(Locale.getDefault(), "%,.2f", sale.cost)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Gross Profit", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text(
                                            text = "₹ ${String.format(Locale.getDefault(), "%,.2f", sale.grossProfit)}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (sale.grossProfit >= 0) Color(0xFF16A34A) else Color(0xFFDC2626)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // CSV Export Action
                item {
                    Button(
                        onClick = {
                            viewModel.exportSalesCsv(context) { file ->
                                if (file != null) {
                                    exportedFile = file
                                    showExportDialog = true
                                } else {
                                    Toast.makeText(context, "Failed to generate CSV export", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("export_sales_report_btn")
                    ) {
                        Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Export Detailed Sales CSV",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
        }
    }

    // CSV Export Success Dialog
    if (showExportDialog && exportedFile != null) {
        val file = exportedFile!!
        AlertDialog(
            onDismissRequest = { showExportDialog = false },
            title = {
                Text(
                    text = "Report Exported Successfully",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Your sales and profit audit CSV has been exported:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = file.absolutePath,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        shareReportFile(context, file)
                        showExportDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Share CSV")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}

@Composable
private fun PaymentModeRow(
    label: String,
    amount: Double,
    total: Double,
    icon: ImageVector,
    color: Color
) {
    val percentage = if (total > 0.0) (amount / total * 100.0).toInt() else 0

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                shape = CircleShape,
                color = color.copy(alpha = 0.12f),
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(text = label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = "$percentage% of total", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Text(
            text = "₹ ${String.format(Locale.getDefault(), "%,.2f", amount)}",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun shareReportFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Kirana Sales & Profit Report - ${file.name}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share Sales Report CSV"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share file: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
