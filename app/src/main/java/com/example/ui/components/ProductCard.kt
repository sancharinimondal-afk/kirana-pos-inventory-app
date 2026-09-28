package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.ProductItem
import com.example.ui.theme.GroceryGreen
import com.example.ui.theme.GroceryOrange
import com.example.ui.theme.InStockColor
import com.example.ui.theme.InStockContainer
import com.example.ui.theme.LowStockAlertColor
import com.example.ui.theme.LowStockAlertContainer
import com.example.ui.theme.OutOfStockAlertColor
import com.example.ui.theme.OutOfStockAlertContainer
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProductCard(
    product: ProductItem,
    onEdit: (ProductItem) -> Unit,
    onAdjustStock: (ProductItem) -> Unit,
    onDeactivate: (ProductItem) -> Unit,
    onReactivate: ((ProductItem) -> Unit)? = null,
    onAddToCart: ((ProductItem) -> Unit)? = null,
    cartQuantity: Double = 0.0,
    onDelete: ((ProductItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showBarcodePreview by remember { mutableStateOf(false) }
    var showDeactivateConfirm by remember { mutableStateOf(false) }

    val (stockColor, stockBg, stockStatusLabel) = when {
        !product.isActive -> Triple(Color(0xFF64748B), Color(0xFFF1F5F9), "Inactive")
        product.currentStock <= 0.0 -> Triple(OutOfStockAlertColor, OutOfStockAlertContainer, "Out of Stock")
        product.currentStock <= product.minStockAlert -> Triple(LowStockAlertColor, LowStockAlertContainer, "Low Stock")
        else -> Triple(InStockColor, InStockContainer, "In Stock")
    }

    val stockDisplay = if (product.currentStock % 1.0 == 0.0) {
        product.currentStock.toInt().toString()
    } else {
        String.format(Locale.ENGLISH, "%.2f", product.currentStock)
    }

    val costDisplay = String.format(Locale.ENGLISH, "%.2f", product.costPrice)
    val sellingDisplay = String.format(Locale.ENGLISH, "%.2f", product.sellingPrice)
    val mrpDisplay = String.format(Locale.ENGLISH, "%.2f", product.mrp)
    val rackDisplay = if (product.rackLocation.isNotBlank()) product.rackLocation else "Unassigned"

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (product.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = if (product.isActive) 1.5.dp else 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header Row: Thumbnail + Product Name & Barcode/SKU + Stock Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                // Product Thumbnail
                ProductThumbnail(
                    name = product.name,
                    category = product.category,
                    size = 52.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                // Name, Barcode/SKU, and Category
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    // Barcode / SKU
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = "Barcode/SKU",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = product.sku,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Category & Rack Tags
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val catInfo = com.example.data.model.KiranaCategories.getCategoryInfo(product.category)
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = catInfo.tagBgColor
                        ) {
                            Text(
                                text = "${catInfo.emoji} ${catInfo.nameEn}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = catInfo.tagColor,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // Rack Location Tag
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridOn,
                                    contentDescription = "Rack",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    text = "Rack: $rackDisplay",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Low-stock status badge
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = stockBg
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        if (product.isLowStock || product.isOutOfStock) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alert",
                                tint = stockColor,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                        }
                        Text(
                            text = stockStatusLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = stockColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Pricing & Current Stock Grid
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Cost Price
                    Column {
                        Text(
                            text = "Cost",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹$costDisplay",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Selling Price
                    Column {
                        Text(
                            text = "Selling Price",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹$sellingDisplay",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // MRP
                    Column {
                        Text(
                            text = "MRP",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "₹$mrpDisplay",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            textDecoration = if (product.mrp > product.sellingPrice) TextDecoration.LineThrough else TextDecoration.None,
                            color = if (product.mrp > product.sellingPrice) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Current Stock & Unit
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Current Stock",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        val unitTrimmed = product.unit.trim()
                        val stockText = if (unitTrimmed.firstOrNull()?.isDigit() == true) {
                            "$stockDisplay pkts ($unitTrimmed)"
                        } else {
                            "$stockDisplay $unitTrimmed"
                        }
                        Text(
                            text = stockText,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = stockColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(8.dp))

            // Actions Row: Primary actions on the left, Cart & Overflow Menu on the right
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Actions: Adjust Stock & Edit
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalButton(
                        onClick = { onAdjustStock(product) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("adjust_stock_${product.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Adjust Stock",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Adjust Stock", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    OutlinedButton(
                        onClick = { onEdit(product) },
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("edit_product_${product.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "Edit",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                // Right Actions: Add to Cart & More Options (⋮)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (onAddToCart != null && product.isActive) {
                        if (cartQuantity > 0.0) {
                            BadgedBox(
                                badge = {
                                    Badge(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ) {
                                        val displayQty = if (cartQuantity % 1.0 == 0.0) cartQuantity.toInt().toString() else String.format(Locale.ENGLISH, "%.1f", cartQuantity)
                                        Text(text = displayQty, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            ) {
                                FilledIconButton(
                                    onClick = { onAddToCart(product) },
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                                    modifier = Modifier
                                        .size(36.dp)
                                        .testTag("add_to_cart_${product.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddShoppingCart,
                                        contentDescription = "In Cart",
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        } else {
                            FilledTonalIconButton(
                                onClick = { onAddToCart(product) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("add_to_cart_${product.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AddShoppingCart,
                                    contentDescription = "Add to Cart",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Overflow Menu (⋮) for Barcode, Deactivate, and Delete
                    Box {
                        var showMenu by remember { mutableStateOf(false) }

                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("product_menu_${product.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View / Print Barcode") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.QrCode,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    showBarcodePreview = true
                                }
                            )

                            if (product.isActive) {
                                DropdownMenuItem(
                                    text = { Text("Deactivate Product", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.VisibilityOff,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        showDeactivateConfirm = true
                                    }
                                )
                            } else if (onReactivate != null) {
                                DropdownMenuItem(
                                    text = { Text("Reactivate Product", color = GroceryGreen) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = null,
                                            tint = GroceryGreen,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onReactivate(product)
                                    }
                                )
                            }

                            if (onDelete != null) {
                                DropdownMenuItem(
                                    text = { Text("Delete Product", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        onDelete(product)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Barcode Preview Dialog
    if (showBarcodePreview) {
        AlertDialog(
            onDismissRequest = { showBarcodePreview = false },
            title = {
                Text(
                    text = "Barcode Label Preview",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = product.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Rate: ₹ $sellingDisplay | MRP: ₹ $mrpDisplay",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 1.dp,
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.height(44.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val barcodeStr = if (product.barcode.isNotBlank()) product.barcode else "890123456789"
                                barcodeStr.forEachIndexed { i, char ->
                                    val barWidth = if (char.code % 3 == 0) 3.dp else if (char.code % 2 == 0) 2.dp else 1.dp
                                    Box(
                                        modifier = Modifier
                                            .width(barWidth)
                                            .height(if (i % 5 == 0) 44.dp else 38.dp)
                                            .background(Color.Black)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = if (product.barcode.isNotBlank()) product.barcode else product.sku,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                letterSpacing = 2.sp,
                                color = Color.Black
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { showBarcodePreview = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    Text("Close")
                }
            }
        )
    }

    // Deactivate Confirmation Dialog
    if (showDeactivateConfirm) {
        AlertDialog(
            onDismissRequest = { showDeactivateConfirm = false },
            title = {
                Text(
                    text = "Deactivate Product?",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to deactivate '${product.name}'? Deactivating hides it from sales and catalog while preserving all past transactions, purchases, and audited stock records."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeactivateConfirm = false
                        onDeactivate(product)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("confirm_deactivate_btn_${product.id}")
                ) {
                    Text("Deactivate")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDeactivateConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
