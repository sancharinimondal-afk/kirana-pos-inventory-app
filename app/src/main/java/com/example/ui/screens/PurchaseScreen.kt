package com.example.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Percent
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateListOf
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
import com.example.billing.BillingCalculator
import com.example.billing.PurchaseCalculator
import com.example.data.PurchaseItemInput
import com.example.data.model.ProductItem
import com.example.ui.KiranaViewModel
import com.example.ui.theme.GroceryOrange
import com.example.ui.theme.kiranaTextFieldColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PurchaseItemEntry(
    val productName: String,
    val quantity: Double,
    val rate: Double,
    val gstRate: Double = 0.0,
    val lineDiscount: Double = 0.0,
    val sellingPrice: Double? = null,
    val mrp: Double? = null,
    val productId: Long? = null,
    val barcode: String = "",
    val unit: String = "Piece",
    val category: String = "General"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseScreen(
    viewModel: KiranaViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()

    var supplierName by remember { mutableStateOf("") }
    var supplierPhone by remember { mutableStateOf("") }
    var invoiceNumber by remember { mutableStateOf("") }
    val currentDate = remember { SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()).format(Date()) }
    var purchaseDate by remember { mutableStateOf(currentDate) }
    var paymentMode by remember { mutableStateOf("Cash") }
    var overallDiscountInput by remember { mutableStateOf("") }

    // Configurable markup percentage for new products
    var markupPercentage by remember { mutableDoubleStateOf(PurchaseCalculator.defaultMarkupPercentage) }

    val suppliers = listOf("Direct Supplier", "City FMCG Wholesale", "Kolkata Grain Mart", "State Oil Traders", "Metro Distributors")
    var supplierMenuExpanded by remember { mutableStateOf(false) }

    val paymentModes = listOf("Cash", "Credit", "Bank Transfer", "UPI")
    var paymentMenuExpanded by remember { mutableStateOf(false) }

    // Line items list
    val purchaseItems = remember { mutableStateListOf<PurchaseItemEntry>() }

    // Centralized GST-Exclusive Calculation
    val overallDiscount = overallDiscountInput.toDoubleOrNull() ?: 0.0
    val calculationResult by remember(purchaseItems.size, purchaseItems.toList(), overallDiscount) {
        derivedStateOf {
            if (purchaseItems.isNotEmpty()) {
                val inputs = purchaseItems.map {
                    PurchaseCalculator.ItemInput(
                        quantity = it.quantity,
                        purchaseRate = it.rate,
                        gstRate = it.gstRate,
                        lineDiscount = it.lineDiscount
                    )
                }
                PurchaseCalculator.calculatePurchase(inputs, overallDiscount = overallDiscount)
            } else {
                null
            }
        }
    }

    // Dialog state for adding purchase item
    var showAddItemDialog by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Purchase Module",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 19.sp
                        )
                        Text(
                            text = "GST-Exclusive Inward Stocking",
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 11.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = GroceryOrange
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .testTag("purchase_screen"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Model Explanation Card
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Purchase Calculation Model: GST-Exclusive",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                            Text(
                                text = "Taxable = (Qty × Rate) - Discount  |  GST = Taxable × GST%  |  Grand Total = Taxable + GST",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }

            // Supplier & Date Card
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Supplier Name *", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(4.dp))
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = supplierName,
                                onValueChange = { supplierName = it },
                                placeholder = { Text("e.g. Shyam Wholesalers") },
                                colors = kiranaTextFieldColors(),
                                trailingIcon = {
                                    IconButton(onClick = { supplierMenuExpanded = true }) {
                                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("purchase_supplier_input")
                            )
                            DropdownMenu(
                                expanded = supplierMenuExpanded,
                                onDismissRequest = { supplierMenuExpanded = false }
                            ) {
                                suppliers.forEach { sup ->
                                    DropdownMenuItem(
                                        text = { Text(sup) },
                                        onClick = {
                                            supplierName = sup
                                            supplierMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            // Invoice Number
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Supplier Bill / Invoice #", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = invoiceNumber,
                                    onValueChange = { invoiceNumber = it },
                                    placeholder = { Text("INV-00123") },
                                    colors = kiranaTextFieldColors(),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("purchase_invoice_input")
                                )
                            }

                            // Supplier Phone
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Supplier Mobile", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = supplierPhone,
                                    onValueChange = { supplierPhone = it },
                                    placeholder = { Text("10-digit phone") },
                                    colors = kiranaTextFieldColors(),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth().testTag("purchase_phone_input")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            // Date
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Purchase Date", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                OutlinedTextField(
                                    value = purchaseDate,
                                    onValueChange = { purchaseDate = it },
                                    colors = kiranaTextFieldColors(),
                                    trailingIcon = { Icon(Icons.Default.CalendarToday, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Payment Mode
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Payment Mode", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.height(4.dp))
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    OutlinedTextField(
                                        value = paymentMode,
                                        onValueChange = {},
                                        readOnly = true,
                                        colors = kiranaTextFieldColors(),
                                        trailingIcon = {
                                            IconButton(onClick = { paymentMenuExpanded = true }) {
                                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null)
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    DropdownMenu(
                                        expanded = paymentMenuExpanded,
                                        onDismissRequest = { paymentMenuExpanded = false }
                                    ) {
                                        paymentModes.forEach { mode ->
                                            DropdownMenuItem(
                                                text = { Text(mode) },
                                                onClick = {
                                                    paymentMode = mode
                                                    paymentMenuExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Configurable Markup Bar
            item {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("New Product Markup Setting", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                            Text("${markupPercentage.toInt()}% over cost", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(
                            "Applied when user doesn't enter custom selling price (no arbitrary 1.15 multiplier).",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(10.0, 15.0, 20.0, 25.0, 30.0).forEach { pct ->
                                val isSelected = markupPercentage == pct
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { markupPercentage = pct },
                                    label = { Text("${pct.toInt()}%") },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }
                }
            }

            // Purchase Items Section Header
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Purchase Items (${purchaseItems.size})",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Enter purchase cost rate (GST-exclusive)",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { showAddItemDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("purchase_add_item_btn")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add Item", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            // Items List
            if (purchaseItems.isEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No purchase items added yet.\nClick '+ Add Item' above to record inward stock.",
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(purchaseItems.size) { index ->
                    val item = purchaseItems[index]
                    val itemCalc = calculationResult?.items?.getOrNull(index)

                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = item.productName,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${item.quantity} ${item.unit} @ ₹${item.rate}/unit (excl GST)" +
                                                if (item.lineDiscount > 0) " | Disc: ₹${item.lineDiscount}" else "",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = { purchaseItems.removeAt(index) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Remove Item",
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.surfaceVariant
                                    ) {
                                        Text(
                                            text = "GST ${item.gstRate.toInt()}%: ₹${itemCalc?.gstAmount ?: 0.0}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                    if (item.sellingPrice != null && item.sellingPrice > 0) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.primaryContainer
                                        ) {
                                            Text(
                                                text = "Sell: ₹${item.sellingPrice}",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "₹${itemCalc?.lineTotal ?: 0.0}",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 15.sp,
                                    color = GroceryOrange
                                )
                            }
                        }
                    }
                }
            }

            // Summary Card
            if (purchaseItems.isNotEmpty()) {
                item {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Purchase Bill Summary (GST-Exclusive)",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Overall Discount Field
                            OutlinedTextField(
                                value = overallDiscountInput,
                                onValueChange = { overallDiscountInput = it },
                                label = { Text("Overall Bill Discount (₹)") },
                                placeholder = { Text("0.0") },
                                colors = com.example.ui.theme.kiranaTextFieldColors(),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth().testTag("purchase_overall_discount_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                            Spacer(modifier = Modifier.height(8.dp))

                            SummaryRow("Gross Subtotal", "₹${calculationResult?.grossSubtotal ?: 0.0}")
                            if ((calculationResult?.totalDiscount ?: 0.0) > 0) {
                                SummaryRow("Total Discount", "-₹${calculationResult?.totalDiscount ?: 0.0}", color = Color(0xFF16A34A))
                            }
                            SummaryRow("Taxable Amount", "₹${calculationResult?.totalTaxableAmount ?: 0.0}")
                            SummaryRow("Total GST", "+₹${calculationResult?.totalGst ?: 0.0}", color = Color(0xFF2563EB))

                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f), modifier = Modifier.padding(vertical = 8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Grand Total", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(
                                    "₹${calculationResult?.grandTotal ?: 0.0}",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.testTag("purchase_grand_total_text")
                                )
                            }
                        }
                    }
                }
            }

            // Save Purchase Button
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (supplierName.isBlank()) {
                            Toast.makeText(context, "Please enter supplier name", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        if (purchaseItems.isEmpty()) {
                            Toast.makeText(context, "Please add at least one purchase item", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        val inputs = purchaseItems.map { item ->
                            PurchaseItemInput(
                                productId = item.productId ?: 0L,
                                productName = item.productName,
                                barcode = item.barcode,
                                unit = item.unit,
                                quantity = item.quantity,
                                purchaseRate = item.rate,
                                gstRate = item.gstRate,
                                lineDiscount = item.lineDiscount,
                                updateProductCostPrice = true,
                                sellingPrice = item.sellingPrice,
                                mrp = item.mrp,
                                category = item.category
                            )
                        }
                        viewModel.completePurchase(
                            items = inputs,
                            supplierName = supplierName.trim(),
                            supplierPhone = supplierPhone.trim(),
                            paymentMode = paymentMode,
                            discount = overallDiscount,
                            note = if (invoiceNumber.isNotBlank()) "Invoice: ${invoiceNumber.trim()}" else "",
                            configurableMarkupPercentage = markupPercentage,
                            onComplete = { success, error ->
                                if (success) {
                                    Toast.makeText(
                                        context,
                                        "Purchase of ₹${calculationResult?.grandTotal ?: 0.0} saved! Stock increased.",
                                        Toast.LENGTH_LONG
                                    ).show()
                                    onNavigateBack()
                                } else {
                                    Toast.makeText(context, error ?: "Failed to save purchase", Toast.LENGTH_LONG).show()
                                }
                            }
                        )
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = GroceryOrange),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .testTag("save_purchase_btn")
                ) {
                    Text(
                        text = "Complete Purchase & Inward Stock",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }
        }
    }

    // Add Purchase Item Dialog
    if (showAddItemDialog) {
        AddPurchaseItemDialog(
            allProducts = allProducts,
            defaultMarkup = markupPercentage,
            onDismiss = { showAddItemDialog = false },
            onItemAdded = { newItem ->
                purchaseItems.add(newItem)
                showAddItemDialog = false
            }
        )
    }
}

@Composable
private fun SummaryRow(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = color)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddPurchaseItemDialog(
    allProducts: List<ProductItem>,
    defaultMarkup: Double,
    onDismiss: () -> Unit,
    onItemAdded: (PurchaseItemEntry) -> Unit
) {
    val context = LocalContext.current
    var selectedProductFromCatalog by remember { mutableStateOf<ProductItem?>(null) }

    var itemName by remember { mutableStateOf("") }
    var itemBarcode by remember { mutableStateOf("") }
    var itemUnit by remember { mutableStateOf("Piece") }
    var itemCategory by remember { mutableStateOf("General") }
    var itemQty by remember { mutableStateOf("10") }
    var itemRate by remember { mutableStateOf("100") }
    var itemGstRate by remember { mutableDoubleStateOf(0.0) }
    var itemDiscount by remember { mutableStateOf("0") }

    // User selling price and MRP inputs (replaces arbitrary cost × 1.15)
    var userSellingPriceInput by remember { mutableStateOf("") }
    var userMrpInput by remember { mutableStateOf("") }

    val gstOptions = listOf(0.0, 5.0, 12.0, 18.0, 28.0)
    val unitOptions = listOf("Piece", "kg", "g", "L", "ml", "Packet", "Box")

    val parsedQty = itemQty.toDoubleOrNull() ?: 0.0
    val parsedRate = itemRate.toDoubleOrNull() ?: 0.0
    val parsedDiscount = itemDiscount.toDoubleOrNull() ?: 0.0

    // Live preview of prices
    val autoSellingPrice = PurchaseCalculator.resolveSellingPrice(parsedRate, null, defaultMarkup)
    val autoMrp = PurchaseCalculator.resolveMrp(autoSellingPrice, null)

    val previewTaxable = ((parsedQty * parsedRate) - parsedDiscount).coerceAtLeast(0.0)
    val previewGst = if (itemGstRate > 0) BillingCalculator.roundMoney(previewTaxable * itemGstRate / 100.0) else 0.0
    val previewTotal = BillingCalculator.roundMoney(previewTaxable + previewGst)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Purchase Item (Inward)", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Existing catalog quick selection
                if (allProducts.isNotEmpty()) {
                    item {
                        Text("Select from Existing Inventory (or type new)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = GroceryOrange)
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(allProducts.take(10)) { prod ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (selectedProductFromCatalog?.id == prod.id) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable {
                                        selectedProductFromCatalog = prod
                                        itemName = prod.name
                                        itemBarcode = prod.barcode
                                        itemUnit = prod.unit
                                        itemCategory = prod.category
                                        itemRate = if (prod.costPrice > 0) prod.costPrice.toString() else prod.sellingPrice.toString()
                                        itemGstRate = prod.gstRate
                                        userSellingPriceInput = prod.sellingPrice.toString()
                                        userMrpInput = prod.mrp.toString()
                                    }
                                ) {
                                    Text(
                                        text = prod.name,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (selectedProductFromCatalog?.id == prod.id) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // Item Name
                item {
                    OutlinedTextField(
                        value = itemName,
                        onValueChange = { itemName = it },
                        label = { Text("Product Name *") },
                        placeholder = { Text("e.g. Fortune Sunflower Oil 1L") },
                        modifier = Modifier.fillMaxWidth().testTag("dialog_product_name_input"),
                        singleLine = true
                    )
                }

                // Barcode & Unit
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = itemBarcode,
                            onValueChange = { itemBarcode = it },
                            label = { Text("Barcode (Optional)") },
                            modifier = Modifier.weight(1.2f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = itemUnit,
                            onValueChange = { itemUnit = it },
                            label = { Text("Unit") },
                            modifier = Modifier.weight(0.8f),
                            singleLine = true
                        )
                    }
                }

                // Qty & Rate (Cost Rate excl GST)
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = itemQty,
                            onValueChange = { itemQty = it },
                            label = { Text("Quantity *") },
                            modifier = Modifier.weight(1f).testTag("dialog_product_qty_input"),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = itemRate,
                            onValueChange = { itemRate = it },
                            label = { Text("Cost Rate (₹) *") },
                            placeholder = { Text("Excl. GST") },
                            modifier = Modifier.weight(1f).testTag("dialog_product_rate_input"),
                            singleLine = true
                        )
                    }
                }

                // GST Rate & Line Discount
                item {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // GST Rate selector chips
                        Column(modifier = Modifier.weight(1.2f)) {
                            Text("GST Rate %", fontSize = 11.sp, color = Color.Gray)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                items(gstOptions) { rate ->
                                    val isSelected = itemGstRate == rate
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { itemGstRate = rate },
                                        label = { Text("${rate.toInt()}%", fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = GroceryOrange,
                                            selectedLabelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        // Line Discount
                        OutlinedTextField(
                            value = itemDiscount,
                            onValueChange = { itemDiscount = it },
                            label = { Text("Discount (₹)") },
                            modifier = Modifier.weight(0.8f),
                            singleLine = true
                        )
                    }
                }

                // Price Configuration Section (Selling Price / MRP)
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                "Selling Price & MRP Configuration",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                "Enter explicit retail price, or leave blank to apply configurable markup (${defaultMarkup.toInt()}%).",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = userSellingPriceInput,
                                    onValueChange = { userSellingPriceInput = it },
                                    label = { Text("Selling Price (₹)") },
                                    placeholder = { Text("Auto: ₹$autoSellingPrice") },
                                    modifier = Modifier.weight(1f).testTag("dialog_selling_price_input"),
                                    singleLine = true
                                )
                                OutlinedTextField(
                                    value = userMrpInput,
                                    onValueChange = { userMrpInput = it },
                                    label = { Text("MRP (₹)") },
                                    placeholder = { Text("Auto: ₹$autoMrp") },
                                    modifier = Modifier.weight(1f).testTag("dialog_mrp_input"),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }

                // Calculation Preview Box
                item {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFEFF6FF),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Taxable: ₹$previewTaxable  |  GST: ₹$previewGst",
                                fontSize = 11.sp,
                                color = Color(0xFF1E3A8A)
                            )
                            Text(
                                "Total: ₹$previewTotal",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = GroceryOrange
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val qty = itemQty.toDoubleOrNull() ?: 0.0
                    val rate = itemRate.toDoubleOrNull() ?: -1.0
                    val discount = itemDiscount.toDoubleOrNull() ?: 0.0
                    val userSelling = userSellingPriceInput.toDoubleOrNull()
                    val userMrp = userMrpInput.toDoubleOrNull()

                    if (itemName.isBlank()) {
                        Toast.makeText(context, "Please enter product name", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (qty <= 0.0) {
                        Toast.makeText(context, "Quantity must be greater than 0", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (rate < 0.0) {
                        Toast.makeText(context, "Cost rate cannot be negative", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (discount < 0.0) {
                        Toast.makeText(context, "Discount cannot be negative", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    if (userSelling != null && userMrp != null && userSelling > userMrp) {
                        Toast.makeText(context, "Selling price cannot exceed MRP", Toast.LENGTH_SHORT).show()
                        return@Button
                    }

                    onItemAdded(
                        PurchaseItemEntry(
                            productName = itemName.trim(),
                            quantity = qty,
                            rate = rate,
                            gstRate = itemGstRate,
                            lineDiscount = discount,
                            sellingPrice = userSelling,
                            mrp = userMrp,
                            productId = selectedProductFromCatalog?.id,
                            barcode = itemBarcode.trim(),
                            unit = itemUnit.ifBlank { "Piece" },
                            category = itemCategory.ifBlank { "General" }
                        )
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = GroceryOrange),
                modifier = Modifier.testTag("dialog_confirm_add_item_btn")
            ) {
                Text("Add Item")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
