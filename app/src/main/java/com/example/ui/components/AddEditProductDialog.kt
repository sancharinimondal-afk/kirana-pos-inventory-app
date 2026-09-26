package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.example.data.model.KiranaCategories
import com.example.data.model.ProductItem
import com.example.ui.theme.GroceryNavy
import com.example.ui.theme.GroceryOrange
import com.example.ui.theme.kiranaTextFieldColors
import java.util.Locale
import kotlin.random.Random

@Composable
fun AddEditProductDialog(
    initialProduct: ProductItem? = null,
    existingProducts: List<ProductItem> = emptyList(),
    onDismiss: () -> Unit,
    onSave: (ProductItem) -> Unit,
    onOpenScanner: () -> Unit,
    scannedBarcode: String? = null
) {
    val isEditMode = initialProduct != null

    // 1. Product Name *
    var name by remember { mutableStateOf(initialProduct?.name ?: "") }

    // 2. Barcode/SKU
    var barcode by remember { mutableStateOf(initialProduct?.barcode ?: (scannedBarcode ?: "")) }

    // 3. Category *
    var category by remember { mutableStateOf(initialProduct?.category ?: "Atta & Flour") }
    var isCustomCategory by remember { mutableStateOf(false) }
    var customCategoryText by remember { mutableStateOf("") }

    // 4. Unit *
    var unit by remember { mutableStateOf(initialProduct?.unit ?: "Piece") }
    var isCustomUnit by remember { mutableStateOf(false) }
    var customUnitText by remember { mutableStateOf("") }

    // 5. Cost Price *
    var costPriceStr by remember {
        mutableStateOf(if (isEditMode) String.format(Locale.ENGLISH, "%.2f", initialProduct!!.costPrice) else "")
    }

    // 6. Selling Price *
    var sellingPriceStr by remember {
        mutableStateOf(if (isEditMode) String.format(Locale.ENGLISH, "%.2f", initialProduct!!.sellingPrice) else "")
    }

    // 7. MRP *
    var mrpStr by remember {
        mutableStateOf(if (isEditMode) String.format(Locale.ENGLISH, "%.2f", initialProduct!!.mrp) else "")
    }

    // 8. Opening Stock
    var openingStockStr by remember {
        mutableStateOf(
            if (isEditMode) {
                if (initialProduct!!.currentStock % 1.0 == 0.0) initialProduct.currentStock.toInt().toString()
                else String.format(Locale.ENGLISH, "%.2f", initialProduct.currentStock)
            } else "0"
        )
    }

    // 9. Low Stock Alert
    var lowStockAlertStr by remember {
        mutableStateOf(
            if (isEditMode) {
                if (initialProduct!!.minStockAlert % 1.0 == 0.0) initialProduct.minStockAlert.toInt().toString()
                else String.format(Locale.ENGLISH, "%.2f", initialProduct.minStockAlert)
            } else "5"
        )
    }

    // 10. GST Rate
    var gstRateStr by remember {
        mutableStateOf(
            if (isEditMode) {
                if (initialProduct!!.gstRate % 1.0 == 0.0) initialProduct.gstRate.toInt().toString()
                else String.format(Locale.ENGLISH, "%.1f", initialProduct.gstRate)
            } else "0"
        )
    }

    // 11. Rack Location
    var rackLocation by remember { mutableStateOf(initialProduct?.rackLocation ?: "") }

    // Validation State
    var validationAttempted by remember { mutableStateOf(false) }
    var formErrorMessage by remember { mutableStateOf<String?>(null) }

    // Update barcode if external scanner returned a code
    if (!scannedBarcode.isNullOrBlank() && barcode.isBlank()) {
        barcode = scannedBarcode
    }

    val standardUnits = listOf(
        "Piece", "kg", "g", "Litre", "ml", "Packet", "Box", "Dozen", "Bundle", "Bag", "Carton", "Sachet", "Tin"
    )

    val rackSuggestions = listOf("Rack A", "Rack B", "Rack C", "Counter", "Deep Fridge", "Godown")

    val gstRatePresets = listOf(0.0, 5.0, 12.0, 18.0, 28.0)

    val parsedCost = costPriceStr.trim().toDoubleOrNull()
    val parsedSelling = sellingPriceStr.trim().toDoubleOrNull()
    val parsedMrp = mrpStr.trim().toDoubleOrNull()

    fun applyMarkup(pct: Double) {
        val baseCost = parsedCost ?: 0.0
        if (baseCost > 0.0) {
            val newSell = baseCost * (1.0 + pct / 100.0)
            sellingPriceStr = String.format(Locale.ENGLISH, "%.2f", newSell)
            if (parsedMrp == null || parsedMrp < newSell) {
                mrpStr = String.format(Locale.ENGLISH, "%.2f", newSell * 1.05)
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.94f)
                .padding(vertical = 8.dp)
                .testTag("add_edit_product_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // ==========================================
                // 1. PINNED HEADER
                // ==========================================
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isEditMode) Icons.Default.Inventory2 else Icons.Default.Storefront,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = if (isEditMode) "Edit Product" else "Add New Product",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isEditMode) "Update product information and pricing" else "Register a new grocery inventory item",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.testTag("dialog_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // ==========================================
                // 2. SCROLLABLE FORM BODY
                // ==========================================
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    // FIELD 1: Product Name *
                    Column {
                        OutlinedTextField(
                            value = name,
                            onValueChange = {
                                name = it
                                if (validationAttempted && it.isNotBlank()) formErrorMessage = null
                            },
                            label = { Text("Product Name *") },
                            placeholder = { Text("e.g. Aashirvaad Shudh Chakki Atta 5kg") },
                            colors = kiranaTextFieldColors(),
                            isError = validationAttempted && name.trim().isBlank(),
                            supportingText = {
                                if (validationAttempted && name.trim().isBlank()) {
                                    Text("Product Name is required", color = MaterialTheme.colorScheme.error)
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_name_field"),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp)
                        )
                    }

                    // FIELD 2: Barcode/SKU
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = barcode,
                                onValueChange = {
                                    barcode = it
                                    if (validationAttempted) formErrorMessage = null
                                },
                                label = { Text("Barcode/SKU") },
                                placeholder = { Text("Scan or enter barcode / SKU") },
                                colors = kiranaTextFieldColors(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("product_barcode_field"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            IconButton(
                                onClick = onOpenScanner,
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                                    .testTag("dialog_scan_barcode_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan Barcode",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.width(4.dp))
                            IconButton(
                                onClick = {
                                    val randomCode = "890" + (100000000L..999999999L).random(Random(System.currentTimeMillis()))
                                    barcode = randomCode
                                    if (validationAttempted) formErrorMessage = null
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                    .testTag("dialog_generate_barcode_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoFixHigh,
                                    contentDescription = "Generate Barcode/SKU",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // FIELD 3: Category *
                    Column {
                        Text(
                            text = "Category *",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val allCats = KiranaCategories.ALL_CATEGORIES
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            allCats.chunked(2).forEach { rowPair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    rowPair.forEach { catInfo ->
                                        val isSelected = !isCustomCategory && (category == catInfo.id || category == catInfo.nameEn)
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                            border = BorderStroke(
                                                width = if (isSelected) 1.5.dp else 1.dp,
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .clickable {
                                                    category = catInfo.nameEn
                                                    isCustomCategory = false
                                                    if (validationAttempted) formErrorMessage = null
                                                }
                                                .testTag("cat_option_${catInfo.nameEn.lowercase().replace(" ", "_").replace("&", "and")}")
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(text = catInfo.emoji, fontSize = 16.sp)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = catInfo.nameEn,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.weight(1f)
                                                )
                                                if (isSelected) {
                                                    Icon(
                                                        imageVector = Icons.Default.Check,
                                                        contentDescription = "Selected",
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (rowPair.size == 1) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        FilterChip(
                            selected = isCustomCategory,
                            onClick = { isCustomCategory = !isCustomCategory },
                            label = { Text("+ Custom Category", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                            shape = RoundedCornerShape(8.dp),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.testTag("custom_category_chip")
                        )

                        AnimatedVisibility(visible = isCustomCategory) {
                            OutlinedTextField(
                                value = customCategoryText,
                                onValueChange = {
                                    customCategoryText = it
                                    category = it
                                    if (validationAttempted && it.isNotBlank()) formErrorMessage = null
                                },
                                label = { Text("Custom Category Name *") },
                                placeholder = { Text("e.g. Frozen Foods, Stationery") },
                                colors = kiranaTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("custom_category_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    // FIELD 4: Unit *
                    Column {
                        Text(
                            text = "Unit *",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(standardUnits) { u ->
                                val isSelected = !isCustomUnit && unit.equals(u, ignoreCase = true)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        unit = u
                                        isCustomUnit = false
                                        if (validationAttempted) formErrorMessage = null
                                    },
                                    label = { Text(u, fontSize = 11.sp) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.testTag("unit_chip_${u.lowercase()}")
                                )
                            }
                            item {
                                FilterChip(
                                    selected = isCustomUnit,
                                    onClick = { isCustomUnit = true },
                                    label = { Text("+ Custom Unit", fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier.testTag("custom_unit_chip")
                                )
                            }
                        }

                        AnimatedVisibility(visible = isCustomUnit) {
                            OutlinedTextField(
                                value = customUnitText,
                                onValueChange = {
                                    customUnitText = it
                                    unit = it
                                    if (validationAttempted && it.isNotBlank()) formErrorMessage = null
                                },
                                label = { Text("Custom Unit * (e.g. Sachet, Tin, Pouch)") },
                                colors = kiranaTextFieldColors(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .testTag("custom_unit_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }
                    }

                    // FIELDS 5, 6, 7: PRICING CARD (Cost Price *, Selling Price *, MRP *)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Pricing & Margins",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // FIELD 5: Cost Price *
                                val costErr = validationAttempted && (parsedCost == null || parsedCost < 0.0)
                                OutlinedTextField(
                                    value = costPriceStr,
                                    onValueChange = {
                                        costPriceStr = it
                                        if (validationAttempted) formErrorMessage = null
                                    },
                                    label = { Text("Cost Price *") },
                                    placeholder = { Text("0.00") },
                                    colors = kiranaTextFieldColors(),
                                    isError = costErr,
                                    supportingText = {
                                        if (costErr) Text("Must be ≥ 0", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("product_cost_field"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )

                                // FIELD 6: Selling Price *
                                val sellErr = validationAttempted && (parsedSelling == null || parsedSelling <= 0.0)
                                OutlinedTextField(
                                    value = sellingPriceStr,
                                    onValueChange = {
                                        sellingPriceStr = it
                                        if (validationAttempted) formErrorMessage = null
                                    },
                                    label = { Text("Selling Price *") },
                                    placeholder = { Text("0.00") },
                                    colors = kiranaTextFieldColors(),
                                    isError = sellErr,
                                    supportingText = {
                                        if (sellErr) Text("Must be > 0", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("product_selling_field"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )

                                // FIELD 7: MRP *
                                val mrpErr = validationAttempted && (parsedMrp == null || parsedMrp <= 0.0 || (parsedSelling != null && parsedSelling > parsedMrp))
                                OutlinedTextField(
                                    value = mrpStr,
                                    onValueChange = {
                                        mrpStr = it
                                        if (validationAttempted) formErrorMessage = null
                                    },
                                    label = { Text("MRP *") },
                                    placeholder = { Text("0.00") },
                                    colors = kiranaTextFieldColors(),
                                    isError = mrpErr,
                                    supportingText = {
                                        if (mrpErr) {
                                            if (parsedMrp == null || parsedMrp <= 0.0) Text("Must be > 0", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                                            else Text("Must be ≥ Sell", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                                        }
                                    },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("product_mrp_field"),
                                    singleLine = true,
                                    shape = RoundedCornerShape(10.dp)
                                )
                            }

                            // Quick Markup on Cost Buttons
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Markup on Cost:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(modifier = Modifier.width(6.dp))
                                listOf(10.0, 15.0, 20.0, 25.0, 30.0).forEach { pct ->
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                        modifier = Modifier
                                            .padding(horizontal = 2.dp)
                                            .clickable { applyMarkup(pct) }
                                    ) {
                                        Text(
                                            text = "+${pct.toInt()}%",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            // MRP Violation Warning (Selling Price > MRP)
                            if (parsedSelling != null && parsedMrp != null && parsedSelling > parsedMrp) {
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFFFEF3C7),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "⚠ Selling price (₹$parsedSelling) exceeds MRP (₹$parsedMrp). Selling price must be ≤ MRP.",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF92400E)
                                        )
                                    }
                                }
                            }

                            // Margin info
                            if (parsedCost != null && parsedSelling != null && parsedSelling > parsedCost && parsedCost > 0.0) {
                                val margin = ((parsedSelling - parsedCost) / parsedCost) * 100
                                val profit = parsedSelling - parsedCost
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "⚡ Margin: ${String.format(Locale.ENGLISH, "%.1f", margin)}% (+₹${String.format(Locale.ENGLISH, "%.2f", profit)}/unit)",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    if (parsedMrp != null && parsedMrp > parsedSelling) {
                                        Text(
                                            text = "Discount: ₹${String.format(Locale.ENGLISH, "%.2f", parsedMrp - parsedSelling)} OFF MRP",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF2E7D32)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // FIELD 8: Opening Stock & FIELD 9: Low Stock Alert
                    if (isEditMode) {
                        // Product stock must NOT be silently modified from product-information editing.
                        // Use dedicated stock adjustment.
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_stock_locked_card")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val stockDisplay = if (initialProduct!!.currentStock % 1.0 == 0.0) {
                                        initialProduct.currentStock.toInt().toString()
                                    } else {
                                        String.format(Locale.ENGLISH, "%.2f", initialProduct.currentStock)
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Lock,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Current Stock: $stockDisplay ${initialProduct.unit}",
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer
                                    ) {
                                        Text(
                                            text = "Stock Locked",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Product stock cannot be modified from product editing. Use dedicated stock adjustment in inventory list.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        // Low Stock Alert for Edit Mode
                        val parsedAlert = lowStockAlertStr.trim().toDoubleOrNull()
                        val alertErr = validationAttempted && (parsedAlert == null || parsedAlert < 0.0)
                        OutlinedTextField(
                            value = lowStockAlertStr,
                            onValueChange = {
                                lowStockAlertStr = it
                                if (validationAttempted) formErrorMessage = null
                            },
                            label = { Text("Low Stock Alert (${unit.trim()})") },
                            placeholder = { Text("5") },
                            colors = kiranaTextFieldColors(),
                            isError = alertErr,
                            supportingText = {
                                if (alertErr) Text("Must be a valid decimal number ≥ 0", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_min_alert_field"),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    } else {
                        // Adding brand new product: Opening Stock + Low Stock Alert
                        val parsedStock = openingStockStr.trim().toDoubleOrNull()
                        val stockErr = validationAttempted && (parsedStock == null || parsedStock < 0.0)
                        val parsedAlert = lowStockAlertStr.trim().toDoubleOrNull()
                        val alertErr = validationAttempted && (parsedAlert == null || parsedAlert < 0.0)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = openingStockStr,
                                onValueChange = {
                                    openingStockStr = it
                                    if (validationAttempted) formErrorMessage = null
                                },
                                label = { Text("Opening Stock") },
                                placeholder = { Text("0") },
                                colors = kiranaTextFieldColors(),
                                isError = stockErr,
                                supportingText = {
                                    if (stockErr) Text("Must be ≥ 0", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("product_stock_field"),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )

                            OutlinedTextField(
                                value = lowStockAlertStr,
                                onValueChange = {
                                    lowStockAlertStr = it
                                    if (validationAttempted) formErrorMessage = null
                                },
                                label = { Text("Low Stock Alert") },
                                placeholder = { Text("5") },
                                colors = kiranaTextFieldColors(),
                                isError = alertErr,
                                supportingText = {
                                    if (alertErr) Text("Must be ≥ 0", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("product_min_alert_field"),
                                singleLine = true,
                                shape = RoundedCornerShape(10.dp)
                            )
                        }

                        // Quick Opening Stock stepper
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Quick Add Stock:", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(modifier = Modifier.width(6.dp))
                            listOf(5, 10, 25, 50, 100).forEach { qty ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .clickable {
                                            val cur = openingStockStr.trim().toDoubleOrNull() ?: 0.0
                                            val newVal = cur + qty
                                            openingStockStr = if (newVal % 1.0 == 0.0) newVal.toInt().toString() else String.format(Locale.ENGLISH, "%.2f", newVal)
                                            if (validationAttempted) formErrorMessage = null
                                        }
                                ) {
                                    Text(
                                        text = "+$qty",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 3.dp)
                                    )
                                }
                            }
                        }
                    }

                    // FIELD 10: GST Rate
                    Column {
                        Text(
                            text = "GST Rate (%)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val currentGstDouble = gstRateStr.trim().toDoubleOrNull() ?: 0.0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            gstRatePresets.forEach { rate ->
                                val isSelected = (currentGstDouble == rate)
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        gstRateStr = if (rate % 1.0 == 0.0) rate.toInt().toString() else rate.toString()
                                        if (validationAttempted) formErrorMessage = null
                                    },
                                    label = { Text("${rate.toInt()}%", fontSize = 11.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal) },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("gst_chip_${rate.toInt()}")
                                )
                            }
                        }

                        val parsedGst = gstRateStr.trim().toDoubleOrNull()
                        val gstErr = validationAttempted && (parsedGst == null || parsedGst < 0.0)
                        OutlinedTextField(
                            value = gstRateStr,
                            onValueChange = {
                                gstRateStr = it
                                if (validationAttempted) formErrorMessage = null
                            },
                            label = { Text("Custom GST Rate (%)") },
                            placeholder = { Text("0.0") },
                            colors = kiranaTextFieldColors(),
                            isError = gstErr,
                            supportingText = {
                                if (gstErr) Text("Must be a valid number ≥ 0", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                                .testTag("product_gst_field"),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )
                    }

                    // FIELD 11: Rack Location
                    Column {
                        Text(
                            text = "Rack Location",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        OutlinedTextField(
                            value = rackLocation,
                            onValueChange = { rackLocation = it },
                            label = { Text("Rack / Shelf / Storage Location") },
                            placeholder = { Text("e.g. Rack A-1, Shelf 3, Deep Fridge") },
                            colors = kiranaTextFieldColors(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_rack_field"),
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp)
                        )

                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(rackSuggestions) { r ->
                                val isSelected = rackLocation.contains(r, ignoreCase = true)
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                    modifier = Modifier.clickable { rackLocation = r }
                                ) {
                                    Text(
                                        text = r,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Validation Error Banner
                    if (formErrorMessage != null) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("product_form_error_banner")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "⚠ $formErrorMessage",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // ==========================================
                // 3. PINNED BOTTOM ACTION BAR
                // ==========================================
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("dialog_cancel_button")
                    ) {
                        Text("Cancel", color = MaterialTheme.colorScheme.onSurface)
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Button(
                        onClick = {
                            validationAttempted = true

                            // 1. Name required
                            val cleanName = name.trim()
                            if (cleanName.isBlank()) {
                                formErrorMessage = "Product Name is required"
                                return@Button
                            }

                            // 2. Barcode/SKU duplicate check
                            val cleanBarcode = barcode.trim()
                            if (cleanBarcode.isNotBlank()) {
                                val duplicate = existingProducts.firstOrNull { existing ->
                                    existing.id != (initialProduct?.id ?: 0L) &&
                                        (existing.barcode.equals(cleanBarcode, ignoreCase = true) ||
                                         existing.sku.equals(cleanBarcode, ignoreCase = true))
                                }
                                if (duplicate != null) {
                                    formErrorMessage = "Barcode/SKU '$cleanBarcode' is already in use by '${duplicate.name}'. Duplicate barcode/SKU is not allowed."
                                    return@Button
                                }
                            }

                            // 3. Category required
                            val cleanCategory = category.trim()
                            if (cleanCategory.isBlank()) {
                                formErrorMessage = "Category is required"
                                return@Button
                            }

                            // 4. Unit required
                            val cleanUnit = unit.trim()
                            if (cleanUnit.isBlank()) {
                                formErrorMessage = "Unit is required"
                                return@Button
                            }

                            // 5. Cost Price: Cost >= 0
                            val pCost = costPriceStr.trim().toDoubleOrNull()
                            if (costPriceStr.trim().isBlank() || pCost == null) {
                                formErrorMessage = "Cost Price must be a valid decimal number"
                                return@Button
                            }
                            if (pCost < 0.0) {
                                formErrorMessage = "Cost Price cannot be negative (must be ≥ 0)"
                                return@Button
                            }

                            // 6. Selling price: Selling price > 0
                            val pSelling = sellingPriceStr.trim().toDoubleOrNull()
                            if (sellingPriceStr.trim().isBlank() || pSelling == null) {
                                formErrorMessage = "Selling Price must be a valid decimal number"
                                return@Button
                            }
                            if (pSelling <= 0.0) {
                                formErrorMessage = "Selling Price must be greater than 0"
                                return@Button
                            }

                            // 7. MRP: MRP > 0 and Selling price <= MRP
                            val pMrp = mrpStr.trim().toDoubleOrNull()
                            if (mrpStr.trim().isBlank() || pMrp == null) {
                                formErrorMessage = "MRP must be a valid decimal number"
                                return@Button
                            }
                            if (pMrp <= 0.0) {
                                formErrorMessage = "MRP must be greater than 0"
                                return@Button
                            }
                            if (pSelling > pMrp) {
                                formErrorMessage = "Selling Price (₹$pSelling) cannot exceed MRP (₹$pMrp)"
                                return@Button
                            }

                            // 8. Opening Stock: Stock >= 0 (Only applied when creating new product)
                            val finalStock = if (isEditMode) {
                                initialProduct!!.currentStock
                            } else {
                                val pStock = if (openingStockStr.trim().isBlank()) 0.0 else openingStockStr.trim().toDoubleOrNull()
                                if (pStock == null || pStock < 0.0) {
                                    formErrorMessage = "Opening Stock must be a valid decimal quantity ≥ 0"
                                    return@Button
                                }
                                pStock
                            }

                            // 9. Alert: Alert >= 0
                            val pAlert = if (lowStockAlertStr.trim().isBlank()) 5.0 else lowStockAlertStr.trim().toDoubleOrNull()
                            if (pAlert == null || pAlert < 0.0) {
                                formErrorMessage = "Low Stock Alert must be a valid decimal quantity ≥ 0"
                                return@Button
                            }

                            // 10. GST: GST >= 0
                            val pGst = if (gstRateStr.trim().isBlank()) 0.0 else gstRateStr.trim().toDoubleOrNull()
                            if (pGst == null || pGst < 0.0) {
                                formErrorMessage = "GST Rate must be a valid decimal number ≥ 0"
                                return@Button
                            }

                            formErrorMessage = null

                            // Build final ProductItem
                            val finalBarcode = if (cleanBarcode.isBlank()) {
                                if (isEditMode && initialProduct!!.barcode.isNotBlank()) {
                                    initialProduct.barcode
                                } else {
                                    "SKU-" + System.currentTimeMillis().toString().takeLast(6) + "-" + (100..999).random()
                                }
                            } else {
                                cleanBarcode
                            }

                            val product = (initialProduct ?: ProductItem(
                                barcode = finalBarcode,
                                name = cleanName,
                                category = cleanCategory,
                                unit = cleanUnit,
                                costPrice = pCost,
                                sellingPrice = pSelling,
                                mrp = pMrp,
                                currentStock = finalStock,
                                minStockAlert = pAlert,
                                gstRate = pGst,
                                rackLocation = rackLocation.trim()
                            )).copy(
                                barcode = finalBarcode,
                                name = cleanName,
                                bengaliName = "",
                                category = cleanCategory,
                                unit = cleanUnit,
                                costPrice = pCost,
                                sellingPrice = pSelling,
                                mrp = pMrp,
                                currentStock = finalStock,
                                minStockAlert = pAlert,
                                gstRate = pGst,
                                rackLocation = rackLocation.trim(),
                                lastUpdated = System.currentTimeMillis()
                            )

                            onSave(product)
                            onDismiss()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_product_button")
                    ) {
                        Icon(imageVector = Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isEditMode) "Save Changes" else "Save Product")
                    }
                }
            }
        }
    }
}
