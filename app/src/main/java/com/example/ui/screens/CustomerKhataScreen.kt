package com.example.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.billing.BillingCalculator
import com.example.data.model.Customer
import com.example.data.model.CustomerWithBalance
import com.example.data.model.LedgerEntry
import com.example.ui.KiranaViewModel
import com.example.ui.theme.GroceryNavy
import com.example.ui.theme.GroceryOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Data model for a single row in the running ledger.
 * Holds the ledger entry along with the calculated running balance after this transaction.
 */
data class RunningLedgerRow(
    val entry: LedgerEntry,
    val runningBalance: Double,
    val isPositiveEffect: Boolean, // true = increases customer debt (+), false = decreases customer debt (-)
    val effectAmount: Double
)

@Composable
fun CustomerKhataScreen(
    viewModel: KiranaViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allCustomersWithBalance by viewModel.allCustomersWithBalance.collectAsStateWithLifecycle()
    val totalKhataOutstanding by viewModel.totalKhataOutstanding.collectAsStateWithLifecycle()
    val shopSettings by viewModel.shopSettings.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var showAddCustomerDialog by remember { mutableStateOf(false) }

    // Dialog state for payment / credit / adjustment
    var showPaymentDialogForCustomer by remember { mutableStateOf<Customer?>(null) }
    var showCreditDialogForCustomer by remember { mutableStateOf<Customer?>(null) }
    var showAdjustmentDialogForCustomer by remember { mutableStateOf<Customer?>(null) }

    val filteredCustomers = remember(allCustomersWithBalance, searchQuery) {
        if (searchQuery.isBlank()) allCustomersWithBalance
        else allCustomersWithBalance.filter {
            it.customer.name.contains(searchQuery, ignoreCase = true) ||
                    it.customer.phone.contains(searchQuery, ignoreCase = true)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("customer_khata_screen")
    ) {
        if (selectedCustomer != null) {
            // Customer Ledger Details View
            val customer = selectedCustomer!!
            val customerEntries by viewModel.getCustomerEntries(customer.id)
                .collectAsStateWithLifecycle(initialValue = emptyList())
            val customerBalance by viewModel.getCustomerBalance(customer.id)
                .collectAsStateWithLifecycle(initialValue = 0.0)

            CustomerLedgerView(
                customer = customer,
                currentBalance = customerBalance,
                entries = customerEntries,
                shopName = shopSettings.shopName,
                onBack = { selectedCustomer = null },
                onAcceptPayment = { showPaymentDialogForCustomer = customer },
                onAddCredit = { showCreditDialogForCustomer = customer },
                onAddAdjustment = { showAdjustmentDialogForCustomer = customer },
                onShareWhatsApp = {
                    shareLedgerOnWhatsApp(context, shopSettings.shopName, customer, customerBalance, customerEntries)
                }
            )
        } else {
            // Customer List Screen
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(modifier = Modifier.height(10.dp))

                // Outstanding Balance Overview Banner
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Total Khata / Udhar Outstanding",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "₹ ${String.format(Locale.getDefault(), "%,.2f", totalKhataOutstanding)}",
                                fontSize = 22.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (totalKhataOutstanding > 0) Color(0xFFDC2626) else Color(0xFF16A34A)
                            )
                            Text(
                                text = "${allCustomersWithBalance.size} active Khata customers",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFFF5F3FF),
                            modifier = Modifier.size(46.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Group,
                                    contentDescription = null,
                                    tint = Color(0xFF7C3AED),
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Search Bar
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search by customer name or phone...", fontSize = 13.sp) },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = com.example.ui.theme.kiranaTextFieldColors(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("search_customer_field")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Customer List (Showing LIVE current balance)
                if (filteredCustomers.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isBlank()) "No customers registered yet" else "No matching customer found",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (searchQuery.isBlank()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Tap the + button below to add your first customer",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .testTag("customer_list"),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 80.dp)
                    ) {
                        items(filteredCustomers, key = { it.customer.id }) { item ->
                            CustomerListItemCard(
                                customerWithBalance = item,
                                onClick = { selectedCustomer = item.customer },
                                onPayment = { showPaymentDialogForCustomer = item.customer },
                                onCredit = { showCreditDialogForCustomer = item.customer }
                            )
                        }
                    }
                }
            }

            // FAB to Add Customer
            FloatingActionButton(
                onClick = { showAddCustomerDialog = true },
                containerColor = GroceryOrange,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .testTag("add_customer_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Customer")
            }
        }

        // Dialogs
        if (showAddCustomerDialog) {
            AddCustomerDialog(
                onDismiss = { showAddCustomerDialog = false },
                onSave = { name, phone, address, openingBalance ->
                    viewModel.addCustomer(
                        Customer(
                            id = 0,
                            name = name,
                            phone = phone,
                            address = address,
                            openingBalance = openingBalance
                        )
                    )
                    showAddCustomerDialog = false
                }
            )
        }

        showPaymentDialogForCustomer?.let { customer ->
            RecordPaymentDialog(
                customer = customer,
                onDismiss = { showPaymentDialogForCustomer = null },
                onRecord = { amount, ref, note ->
                    viewModel.addKhataPayment(customer.id, amount, ref, note)
                    showPaymentDialogForCustomer = null
                }
            )
        }

        showCreditDialogForCustomer?.let { customer ->
            RecordCreditDialog(
                customer = customer,
                onDismiss = { showCreditDialogForCustomer = null },
                onRecord = { amount, ref, note ->
                    viewModel.addKhataCredit(customer.id, amount, ref, note)
                    showCreditDialogForCustomer = null
                }
            )
        }

        showAdjustmentDialogForCustomer?.let { customer ->
            RecordAdjustmentDialog(
                customer = customer,
                onDismiss = { showAdjustmentDialogForCustomer = null },
                onRecord = { amount, isDebit, ref, note ->
                    viewModel.addKhataAdjustment(customer.id, amount, isDebit, ref, note)
                    showAdjustmentDialogForCustomer = null
                }
            )
        }
    }
}

@Composable
private fun CustomerListItemCard(
    customerWithBalance: CustomerWithBalance,
    onClick: () -> Unit,
    onPayment: () -> Unit,
    onCredit: () -> Unit
) {
    val customer = customerWithBalance.customer
    val balance = customerWithBalance.currentBalance

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("customer_card_${customer.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = Color(0xFFFFE8DC),
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = customer.name.take(1).uppercase(),
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        color = GroceryOrange
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (customer.phone.isNotBlank()) {
                    Text(
                        text = customer.phone,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // LIVE Current Balance Display (NOT opening balance!)
            Column(horizontalAlignment = Alignment.End) {
                val statusLabel = when {
                    balance > 0.0 -> "Due (Owes Shop)"
                    balance < 0.0 -> "Advance (Shop Owes)"
                    else -> "Settled"
                }
                val statusColor = when {
                    balance > 0.0 -> Color(0xFFDC2626)
                    balance < 0.0 -> Color(0xFF2563EB)
                    else -> Color(0xFF16A34A)
                }

                Text(
                    text = statusLabel,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "₹ ${String.format(Locale.getDefault(), "%.2f", Math.abs(balance))}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = statusColor
                )
            }
        }
    }
}

/**
 * Customer Ledger Detail Screen.
 * Must show:
 * - Current balance
 * - Opening balance
 * - Credit sales
 * - Payments
 * - Adjustments
 * - Running ledger
 */
@Composable
private fun CustomerLedgerView(
    customer: Customer,
    currentBalance: Double,
    entries: List<LedgerEntry>,
    shopName: String,
    onBack: () -> Unit,
    onAcceptPayment: () -> Unit,
    onAddCredit: () -> Unit,
    onAddAdjustment: () -> Unit,
    onShareWhatsApp: () -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    // 1. Calculate Financial Breakdown Metrics
    val openingBalance = remember(entries, customer) {
        val openingEntries = entries.filter { it.type == LedgerEntry.TYPE_OPENING_BALANCE }
        if (openingEntries.isNotEmpty()) {
            openingEntries.sumOf { it.amount }
        } else {
            customer.openingBalance
        }
    }

    val totalCreditSales = remember(entries) {
        entries.filter { it.type == LedgerEntry.TYPE_CREDIT_SALE }.sumOf { it.amount }
    }

    val totalPayments = remember(entries) {
        entries.filter {
            it.type == LedgerEntry.TYPE_PAYMENT_RECEIVED || it.type == LedgerEntry.TYPE_PAYMENT
        }.sumOf { it.amount }
    }

    val totalDebitAdjustments = remember(entries) {
        entries.filter { it.type == LedgerEntry.TYPE_DEBIT_ADJUSTMENT }.sumOf { it.amount }
    }

    val totalCreditAdjustments = remember(entries) {
        entries.filter { it.type == LedgerEntry.TYPE_CREDIT_ADJUSTMENT }.sumOf { it.amount }
    }

    val netAdjustments = remember(totalDebitAdjustments, totalCreditAdjustments) {
        totalDebitAdjustments - totalCreditAdjustments
    }

    // 2. Compute Running Ledger (chronological running balance on every transaction)
    val runningLedgerRows = remember(entries) {
        var bal = 0.0
        val sortedAsc = entries.sortedWith(compareBy({ it.date }, { it.id }))
        val computed = sortedAsc.map { entry ->
            val positive = LedgerEntry.isPositiveEffect(entry.type)
            if (positive) {
                bal += entry.amount
            } else {
                bal -= entry.amount
            }
            RunningLedgerRow(
                entry = entry,
                runningBalance = BillingCalculator.roundMoney(bal),
                isPositiveEffect = positive,
                effectAmount = entry.amount
            )
        }
        computed.reversed() // Display newest transaction at top
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = GroceryNavy)
            }
            Spacer(modifier = Modifier.width(4.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = GroceryNavy
                )
                if (customer.phone.isNotBlank()) {
                    Text(
                        text = customer.phone,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // WhatsApp Share
            IconButton(
                onClick = onShareWhatsApp,
                modifier = Modifier
                    .size(40.dp)
                    .background(Color(0xFFDCFCE7), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share on WhatsApp",
                    tint = Color(0xFF16A34A),
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Hero Card: Current Balance
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val balanceStatus = when {
                    currentBalance > 0.0 -> "Customer Owes Shop"
                    currentBalance < 0.0 -> "Shop Owes Customer (Advance)"
                    else -> "Account Settled"
                }
                val balanceColor = when {
                    currentBalance > 0.0 -> Color(0xFFDC2626)
                    currentBalance < 0.0 -> Color(0xFF2563EB)
                    else -> Color(0xFF16A34A)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Current Balance ($balanceStatus)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "₹ ${String.format(Locale.getDefault(), "%,.2f", Math.abs(currentBalance))}",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = balanceColor
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Breakdown: Opening Balance, Credit Sales, Payments, Adjustments
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricBox(
                        title = "Opening Bal",
                        amount = openingBalance,
                        color = Color(0xFF6B7280),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "Credit Sales",
                        amount = totalCreditSales,
                        color = Color(0xFFDC2626),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "Payments",
                        amount = totalPayments,
                        color = Color(0xFF16A34A),
                        modifier = Modifier.weight(1f)
                    )
                    MetricBox(
                        title = "Adjustments",
                        amount = netAdjustments,
                        color = if (netAdjustments >= 0) Color(0xFFD97706) else Color(0xFF7C3AED),
                        subtitle = "+${String.format(Locale.getDefault(), "%.0f", totalDebitAdjustments)} | -${String.format(Locale.getDefault(), "%.0f", totalCreditAdjustments)}",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onAcceptPayment,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Payment", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Button(
                        onClick = onAddCredit,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Credit", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    OutlinedButton(
                        onClick = onAddAdjustment,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adjust", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Running Ledger Section Title
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RUNNING LEDGER (${runningLedgerRows.size})",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                letterSpacing = 0.8.sp
            )
            Text(
                text = "Live balance audit trail",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Running Ledger Entries List
        if (runningLedgerRows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No ledger entries recorded yet",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(runningLedgerRows, key = { it.entry.id }) { rowItem ->
                    RunningLedgerEntryCard(
                        row = rowItem,
                        dateFormat = dateFormat
                    )
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    title: String,
    amount: Double,
    color: Color,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "₹${String.format(Locale.getDefault(), "%.0f", Math.abs(amount))}",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = color,
                maxLines = 1
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    fontSize = 8.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Card representing an individual transaction in the Running Ledger.
 * Displays Date, Type, Reference/Note, Transaction Effect (+/- amount), and RUNNING BALANCE!
 */
@Composable
private fun RunningLedgerEntryCard(
    row: RunningLedgerRow,
    dateFormat: SimpleDateFormat
) {
    val entry = row.entry
    val isPositive = row.isPositiveEffect
    val runningBal = row.runningBalance

    val (badgeText, badgeBg, badgeTextColor) = when (entry.type) {
        LedgerEntry.TYPE_OPENING_BALANCE -> Triple("Opening Balance", Color(0xFFEFF6FF), Color(0xFF1D4ED8))
        LedgerEntry.TYPE_CREDIT_SALE -> Triple("Credit Sale", Color(0xFFFEF2F2), Color(0xFFDC2626))
        LedgerEntry.TYPE_PAYMENT_RECEIVED, LedgerEntry.TYPE_PAYMENT -> Triple("Payment Received", Color(0xFFF0FDF4), Color(0xFF16A34A))
        LedgerEntry.TYPE_DEBIT_ADJUSTMENT -> Triple("Debit Adjustment (+)", Color(0xFFFFFBEB), Color(0xFFD97706))
        LedgerEntry.TYPE_CREDIT_ADJUSTMENT -> {
            if (entry.reference.startsWith("CANCEL", ignoreCase = true)) {
                Triple("Sale Reversal (-)", Color(0xFFFAF5FF), Color(0xFF7C3AED))
            } else {
                Triple("Credit Adjustment (-)", Color(0xFFFAF5FF), Color(0xFF7C3AED))
            }
        }
        else -> Triple(entry.type, Color(0xFFF3F4F6), Color(0xFF374151))
    }

    Card(
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: Type Badge + Transaction Amount
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = badgeTextColor,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                // Amount (+/-)
                val sign = if (isPositive) "+" else "-"
                val amountColor = if (isPositive) Color(0xFFDC2626) else Color(0xFF16A34A)
                Text(
                    text = "$sign ₹ ${String.format(Locale.getDefault(), "%.2f", entry.amount)}",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = amountColor
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Reference and Note
            if (entry.reference.isNotBlank() || entry.note.isNotBlank()) {
                val desc = listOfNotNull(
                    entry.reference.takeIf { it.isNotBlank() }?.let { "Ref: $it" },
                    entry.note.takeIf { it.isNotBlank() }
                ).joinToString(" • ")

                Text(
                    text = desc,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Bottom Row: Date & RUNNING BALANCE
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dateFormat.format(Date(entry.date)),
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Running Balance badge
                val balColor = when {
                    runningBal > 0.0 -> Color(0xFFDC2626)
                    runningBal < 0.0 -> Color(0xFF2563EB)
                    else -> Color(0xFF16A34A)
                }
                val balSuffix = when {
                    runningBal > 0.0 -> "Dr (Due)"
                    runningBal < 0.0 -> "Cr (Adv)"
                    else -> "Settled"
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "Balance: ₹ ${String.format(Locale.getDefault(), "%.2f", Math.abs(runningBal))} $balSuffix",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = balColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCustomerDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, phone: String, address: String, openingBalance: Double) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var openingBalanceStr by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Customer / Khata", fontWeight = FontWeight.Bold, color = GroceryNavy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; error = null },
                    label = { Text("Customer Name *") },
                    colors = com.example.ui.theme.kiranaTextFieldColors(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = phone,
                    onValueChange = { phone = it },
                    label = { Text("Mobile Phone Number") },
                    colors = com.example.ui.theme.kiranaTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Address / Landmark") },
                    colors = com.example.ui.theme.kiranaTextFieldColors(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = openingBalanceStr,
                    onValueChange = { openingBalanceStr = it },
                    label = { Text("Opening Due Balance (₹)") },
                    colors = com.example.ui.theme.kiranaTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isBlank()) {
                        error = "Customer name is required"
                        return@Button
                    }
                    val opening = openingBalanceStr.toDoubleOrNull() ?: 0.0
                    onSave(name.trim(), phone.trim(), address.trim(), opening)
                },
                colors = ButtonDefaults.buttonColors(containerColor = GroceryOrange)
            ) {
                Text("Save Customer")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RecordPaymentDialog(
    customer: Customer,
    onDismiss: () -> Unit,
    onRecord: (amount: Double, ref: String, note: String) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var ref by remember { mutableStateOf("Cash") }
    var note by remember { mutableStateOf("Payment received") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accept Payment from ${customer.name}", fontWeight = FontWeight.Bold, color = GroceryNavy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it; error = null },
                    label = { Text("Payment Amount (₹) *") },
                    colors = com.example.ui.theme.kiranaTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = ref,
                    onValueChange = { ref = it },
                    label = { Text("Payment Mode / Reference (e.g. Cash, UPI)") },
                    colors = com.example.ui.theme.kiranaTextFieldColors(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Remark") },
                    colors = com.example.ui.theme.kiranaTextFieldColors(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        error = "Please enter a valid payment amount > 0"
                        return@Button
                    }
                    onRecord(amount, ref.trim(), note.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
            ) {
                Text("Record Payment")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RecordCreditDialog(
    customer: Customer,
    onDismiss: () -> Unit,
    onRecord: (amount: Double, ref: String, note: String) -> Unit
) {
    var amountStr by remember { mutableStateOf("") }
    var ref by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("Credit sale / Udhar") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Give Credit / Udhar to ${customer.name}", fontWeight = FontWeight.Bold, color = GroceryNavy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it; error = null },
                    label = { Text("Credit Amount (₹) *") },
                    colors = com.example.ui.theme.kiranaTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = ref,
                    onValueChange = { ref = it },
                    label = { Text("Invoice / Slip Reference") },
                    colors = com.example.ui.theme.kiranaTextFieldColors(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note") },
                    colors = com.example.ui.theme.kiranaTextFieldColors(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        error = "Please enter a valid credit amount > 0"
                        return@Button
                    }
                    onRecord(amount, ref.trim(), note.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
            ) {
                Text("Add Credit")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun RecordAdjustmentDialog(
    customer: Customer,
    onDismiss: () -> Unit,
    onRecord: (amount: Double, isDebit: Boolean, ref: String, note: String) -> Unit
) {
    var isDebit by remember { mutableStateOf(false) } // false = Credit adjustment (-due / discount), true = Debit adjustment (+due)
    var amountStr by remember { mutableStateOf("") }
    var ref by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Account Adjustment for ${customer.name}", fontWeight = FontWeight.Bold, color = GroceryNavy) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }

                Text("Adjustment Type:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = !isDebit,
                        onClick = { isDebit = false }
                    )
                    Text("Credit Adjustment (- Due / Discount)", fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = isDebit,
                        onClick = { isDebit = true }
                    )
                    Text("Debit Adjustment (+ Due / Extra)", fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = amountStr,
                    onValueChange = { amountStr = it; error = null },
                    label = { Text("Adjustment Amount (₹) *") },
                    colors = com.example.ui.theme.kiranaTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = ref,
                    onValueChange = { ref = it },
                    label = { Text("Reference / Reason Code") },
                    colors = com.example.ui.theme.kiranaTextFieldColors(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note / Remark") },
                    colors = com.example.ui.theme.kiranaTextFieldColors(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountStr.toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        error = "Please enter a valid amount > 0"
                        return@Button
                    }
                    onRecord(amount, isDebit, ref.trim(), note.trim())
                },
                colors = ButtonDefaults.buttonColors(containerColor = if (isDebit) Color(0xFFD97706) else Color(0xFF7C3AED))
            ) {
                Text("Apply Adjustment")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun shareLedgerOnWhatsApp(
    context: Context,
    shopName: String,
    customer: Customer,
    balance: Double,
    entries: List<LedgerEntry>
) {
    val dateStr = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date())
    val recentEntriesSummary = entries.take(5).joinToString("\n") { entry ->
        val sign = if (LedgerEntry.isPositiveEffect(entry.type)) "+" else "-"
        "• ${SimpleDateFormat("dd/MM", Locale.getDefault()).format(Date(entry.date))}: $sign₹${String.format(Locale.getDefault(), "%.2f", entry.amount)} (${entry.reference.ifBlank { entry.type }})"
    }

    val statusText = when {
        balance > 0.0 -> "*Current Outstanding Due: ₹${String.format(Locale.getDefault(), "%.2f", balance)}*"
        balance < 0.0 -> "*Advance Balance with Store: ₹${String.format(Locale.getDefault(), "%.2f", Math.abs(balance))}*"
        else -> "*Account Balance: Fully Settled (₹0.00)*"
    }

    val message = """
        🧾 *Khata Account Statement*
        🏪 Store: *$shopName*
        👤 Customer: *${customer.name}*
        📅 Date: $dateStr
        
        💰 $statusText
        
        *Recent Transactions:*
        $recentEntriesSummary
        
        Please check and clear any pending balance at your convenience.
        Thank you for shopping with us! 🙏
    """.trimIndent()

    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, message)
        if (customer.phone.isNotBlank()) {
            val phoneClean = customer.phone.replace("+", "").replace(" ", "").replace("-", "")
            putExtra("jid", "$phoneClean@s.whatsapp.net")
        }
    }

    try {
        context.startActivity(Intent.createChooser(intent, "Share Khata Statement"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open sharing app: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
