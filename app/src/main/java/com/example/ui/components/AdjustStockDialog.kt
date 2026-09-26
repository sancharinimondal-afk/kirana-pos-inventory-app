package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.ProductItem
import com.example.ui.theme.GroceryGreen
import com.example.ui.theme.GroceryOrange
import com.example.ui.theme.kiranaTextFieldColors
import java.util.Locale

enum class StockAdjustmentType(val label: String) {
    ADD("Add"),
    REMOVE("Remove"),
    SET_EXACT("Set Exact Quantity")
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AdjustStockDialog(
    product: ProductItem,
    onDismiss: () -> Unit,
    onConfirmAdjustment: (type: StockAdjustmentType, quantity: Double, reason: String, reference: String) -> Unit
) {
    var adjustmentType by remember { mutableStateOf(StockAdjustmentType.ADD) }
    var quantityStr by remember { mutableStateOf("") }
    var reasonStr by remember { mutableStateOf("Physical Count Audit") }
    var referenceStr by remember { mutableStateOf("ADJ-" + System.currentTimeMillis().toString().takeLast(6)) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showConfirmationDialog by remember { mutableStateOf(false) }

    val currentStock = product.currentStock
    val parsedQuantity = quantityStr.toDoubleOrNull() ?: 0.0

    val calculatedNewStock = when (adjustmentType) {
        StockAdjustmentType.ADD -> currentStock + parsedQuantity
        StockAdjustmentType.REMOVE -> currentStock - parsedQuantity
        StockAdjustmentType.SET_EXACT -> if (quantityStr.isBlank()) currentStock else parsedQuantity
    }

    val isNegativeStock = calculatedNewStock < 0.0

    val formatStock: (Double) -> String = { stockVal ->
        if (stockVal % 1.0 == 0.0) stockVal.toInt().toString() else String.format(Locale.ENGLISH, "%.2f", stockVal)
    }

    val reasonPresets = listOf(
        "Physical Count Audit",
        "Damaged Items",
        "Expired Stock",
        "Supplier Return",
        "Customer Return",
        "Stock Correction"
    )

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 560.dp)
                .padding(vertical = 16.dp)
                .testTag("adjust_stock_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tune,
                                    contentDescription = "Adjust Stock",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Adjust Stock",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = product.name,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("adjust_stock_close_btn")
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Current Stock Card & Live Comparison: Old Stock → New Stock
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adjust_stock_preview_card")
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Current Stock",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${formatStock(currentStock)} ${product.unit}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.testTag("adjust_stock_current_value")
                                )
                            }

                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Transition",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )

                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "New Stock",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isNegativeStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${formatStock(calculatedNewStock)} ${product.unit}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = if (isNegativeStock) MaterialTheme.colorScheme.error else GroceryGreen,
                                    modifier = Modifier.testTag("adjust_stock_new_value")
                                )
                            }
                        }

                        if (quantityStr.isNotBlank() && parsedQuantity > 0.0) {
                            Spacer(modifier = Modifier.height(8.dp))
                            val deltaText = when (adjustmentType) {
                                StockAdjustmentType.ADD -> "+${formatStock(parsedQuantity)} ${product.unit}"
                                StockAdjustmentType.REMOVE -> "-${formatStock(parsedQuantity)} ${product.unit}"
                                StockAdjustmentType.SET_EXACT -> {
                                    val diff = calculatedNewStock - currentStock
                                    if (diff >= 0) "+${formatStock(diff)} ${product.unit}" else "${formatStock(diff)} ${product.unit}"
                                }
                            }
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = if (isNegativeStock) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
                            ) {
                                Text(
                                    text = "Adjustment: $deltaText",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isNegativeStock) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                )
                            }
                        }

                        if (isNegativeStock) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Warning",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Warning: Resulting stock will become negative.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Adjustment Type Selector
                Text(
                    text = "Adjustment Type",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StockAdjustmentType.values().forEach { type ->
                        val isSelected = adjustmentType == type
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    adjustmentType = type
                                    errorMessage = null
                                }
                                .testTag("adjust_type_${type.name.lowercase()}")
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = when (type) {
                                            StockAdjustmentType.ADD -> Icons.Default.Add
                                            StockAdjustmentType.REMOVE -> Icons.Default.Remove
                                            StockAdjustmentType.SET_EXACT -> Icons.Default.Tune
                                        },
                                        contentDescription = null,
                                        tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = type.label,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Quantity Input
                val quantityLabel = when (adjustmentType) {
                    StockAdjustmentType.ADD -> "Quantity to Add (${product.unit}) *"
                    StockAdjustmentType.REMOVE -> "Quantity to Remove (${product.unit}) *"
                    StockAdjustmentType.SET_EXACT -> "Exact New Stock (${product.unit}) *"
                }

                OutlinedTextField(
                    value = quantityStr,
                    onValueChange = {
                        quantityStr = it
                        errorMessage = null
                    },
                    label = { Text(quantityLabel) },
                    placeholder = { Text("e.g. 5") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = kiranaTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adjust_stock_quantity_input")
                )

                // Quick Increment/Decrement Chips
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    listOf(1, 5, 10, 25, 50).forEach { qty ->
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.clickable {
                                val cur = quantityStr.toDoubleOrNull() ?: 0.0
                                quantityStr = (cur + qty).toInt().toString()
                                errorMessage = null
                            }
                        ) {
                            Text(
                                text = "+$qty",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Reason Field
                OutlinedTextField(
                    value = reasonStr,
                    onValueChange = {
                        reasonStr = it
                        errorMessage = null
                    },
                    label = { Text("Reason for Adjustment *") },
                    placeholder = { Text("e.g. Physical inventory count") },
                    singleLine = true,
                    colors = kiranaTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adjust_stock_reason_input")
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Reason Presets Chips
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    reasonPresets.forEach { preset ->
                        val isSelected = reasonStr == preset
                        FilterChip(
                            selected = isSelected,
                            onClick = { reasonStr = preset },
                            label = { Text(preset, fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Reference / Notes Field
                OutlinedTextField(
                    value = referenceStr,
                    onValueChange = { referenceStr = it },
                    label = { Text("Reference / Document #") },
                    placeholder = { Text("e.g. AUDIT-2026-09 or PO-1234") },
                    singleLine = true,
                    colors = kiranaTextFieldColors(),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("adjust_stock_reference_input")
                )

                // Error message
                if (errorMessage != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage!!,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(12.dp))

                // Actions: Cancel & Review / Confirm
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancel")
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = {
                            val qty = quantityStr.toDoubleOrNull()
                            if (qty == null || (adjustmentType != StockAdjustmentType.SET_EXACT && qty <= 0.0) || (adjustmentType == StockAdjustmentType.SET_EXACT && qty < 0.0)) {
                                errorMessage = "Please enter a valid positive quantity"
                                return@Button
                            }
                            if (reasonStr.isBlank()) {
                                errorMessage = "Please provide a reason for stock adjustment"
                                return@Button
                            }
                            errorMessage = null
                            showConfirmationDialog = true
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("adjust_stock_submit_btn")
                    ) {
                        Text("Apply Adjustment")
                    }
                }
            }
        }
    }

    // Explicit Confirmation Dialog (Mandatory per requirement)
    if (showConfirmationDialog) {
        val qty = quantityStr.toDoubleOrNull() ?: 0.0
        AlertDialog(
            onDismissRequest = { showConfirmationDialog = false },
            title = {
                Text(
                    text = "Confirm Stock Adjustment",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Column {
                    Text("Please confirm the following audited stock adjustment:")
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text("Product: ${product.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Type: ${adjustmentType.label}", fontSize = 12.sp)
                            Text("Old Stock: ${formatStock(currentStock)} ${product.unit}", fontSize = 12.sp)
                            Text("New Stock: ${formatStock(calculatedNewStock)} ${product.unit}", fontWeight = FontWeight.Bold, color = GroceryGreen, fontSize = 13.sp)
                            Text("Reason: ${reasonStr.trim()}", fontSize = 12.sp)
                            if (referenceStr.isNotBlank()) {
                                Text("Reference: ${referenceStr.trim()}", fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "This action will record an audited stock movement in the inventory ledger.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmationDialog = false
                        onConfirmAdjustment(
                            adjustmentType,
                            qty,
                            reasonStr.trim(),
                            referenceStr.trim()
                        )
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.testTag("confirm_stock_adjustment_btn")
                ) {
                    Text("Confirm & Apply")
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmationDialog = false },
                    modifier = Modifier.testTag("cancel_stock_adjustment_btn")
                ) {
                    Text("Back")
                }
            }
        )
    }
}
