package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddShoppingCart
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.KiranaCategories
import com.example.data.model.ProductItem
import com.example.ui.theme.GroceryGreen
import java.util.Locale

private data class ProductStockStyle(
    val color: Color,
    val bg: Color,
    val icon: androidx.compose.ui.graphics.vector.ImageVector?,
    val label: String
)

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
    onIncrementCart: ((ProductItem) -> Unit)? = null,
    onDecrementCart: ((ProductItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showBarcodePreview by remember { mutableStateOf(false) }
    var showDeactivateConfirm by remember { mutableStateOf(false) }
    val isDark = isSystemInDarkTheme()

    val stockStyle = when {
        !product.isActive -> ProductStockStyle(Color(0xFF64748B), Color(0xFF64748B).copy(alpha = 0.16f), null, "Inactive")
        product.currentStock <= 0.0 -> ProductStockStyle(Color(0xFFEF4444), Color(0xFFEF4444).copy(alpha = 0.16f), Icons.Default.Warning, "Out of Stock")
        product.currentStock <= product.minStockAlert -> ProductStockStyle(Color(0xFFF59E0B), Color(0xFFF59E0B).copy(alpha = 0.16f), Icons.Default.Warning, "Low Stock")
        else -> ProductStockStyle(Color(0xFF10B981), Color(0xFF10B981).copy(alpha = 0.16f), Icons.Default.CheckCircle, "In Stock")
    }
    val stockColor = stockStyle.color
    val stockBg = stockStyle.bg
    val stockIcon = stockStyle.icon
    val stockStatusLabel = stockStyle.label

    val catInfo = KiranaCategories.getCategoryInfo(product.category)

    val stockDisplay = if (product.currentStock % 1.0 == 0.0) {
        product.currentStock.toInt().toString()
    } else {
        String.format(Locale.ENGLISH, "%.2f", product.currentStock)
    }

    val costDisplay = String.format(Locale.ENGLISH, "%.2f", product.costPrice)
    val sellingDisplay = String.format(Locale.ENGLISH, "%.2f", product.sellingPrice)
    val mrpDisplay = String.format(Locale.ENGLISH, "%.2f", product.mrp)
    val discountAmt = (product.mrp - product.sellingPrice).coerceAtLeast(0.0)
    val discountPct = if (product.mrp > 0) ((discountAmt / product.mrp) * 100).toInt() else 0
    val profitAmt = (product.sellingPrice - product.costPrice).coerceAtLeast(0.0)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("product_card_${product.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (product.isActive) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = if (product.isActive) catInfo.adaptiveBorder(isDark) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (product.isActive) 2.dp else 0.dp)
    ) {
        Column {
            // 1. Vibrant Category Indicator Ribbon (Top accent bar)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.5.dp)
                    .background(if (product.isActive) catInfo.tagColor else Color(0xFF64748B))
            )

            Column(modifier = Modifier.padding(14.dp)) {
                // 2. Header Row: Title & Stock Status Capsule
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Text(
                            text = product.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (product.bengaliName.isNotBlank()) {
                            Text(
                                text = product.bengaliName,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Stock status pill
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = stockBg,
                        border = BorderStroke(1.dp, stockColor.copy(alpha = 0.35f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.5.dp)
                        ) {
                            if (stockIcon != null) {
                                Icon(
                                    imageVector = stockIcon,
                                    contentDescription = null,
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

                Spacer(modifier = Modifier.height(8.dp))

                // 3. Metadata Row: Category Tag + Rack Tag + Barcode Tag (Neatly aligned)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Category Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = catInfo.adaptiveBg(isDark),
                        border = BorderStroke(1.dp, catInfo.adaptiveBorder(isDark))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Category,
                                contentDescription = null,
                                tint = catInfo.adaptiveText(isDark),
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = catInfo.nameEn,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = catInfo.adaptiveText(isDark)
                            )
                        }
                    }

                    // Rack Location Chip
                    if (product.rackLocation.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.5.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.GridOn,
                                    contentDescription = "Rack",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(11.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Rack: ${product.rackLocation}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Barcode / SKU Chip
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.5.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Barcode/SKU",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(11.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (product.barcode.isNotBlank()) product.barcode else product.sku,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 4. Structured 2-Column Info Grid: [Pricing & Margin Pod] & [Inventory Health Pod]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Left Tile: Pricing & Savings
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Text(
                                text = "SELLING PRICE",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.6.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "₹$sellingDisplay",
                                fontSize = 17.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDark) Color(0xFF34D399) else Color(0xFF059669)
                            )
                            if (product.mrp > product.sellingPrice) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "₹$mrpDisplay",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        textDecoration = TextDecoration.LineThrough,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                    Surface(
                                        shape = RoundedCornerShape(4.dp),
                                        color = Color(0xFF10B981).copy(alpha = 0.16f),
                                        border = BorderStroke(0.5.dp, Color(0xFF10B981).copy(alpha = 0.35f))
                                    ) {
                                        Text(
                                            text = if (discountPct > 0) "$discountPct% OFF" else "Save ₹${discountAmt.toInt()}",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFF10B981),
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Cost: ₹$costDisplay${if (profitAmt > 0) " • Profit ₹${profitAmt.toInt()}" else ""}",
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Right Tile: Current Stock & Visual Health
                    Surface(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.45f),
                        border = BorderStroke(1.dp, stockColor.copy(alpha = 0.25f))
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "CURRENT STOCK",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(stockColor, CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            val unitTrimmed = product.unit.trim()
                            val stockText = if (unitTrimmed.firstOrNull()?.isDigit() == true) {
                                "$stockDisplay pkts ($unitTrimmed)"
                            } else {
                                "$stockDisplay $unitTrimmed"
                            }
                            Text(
                                text = stockText,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = stockColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            val maxRef = (product.minStockAlert.coerceAtLeast(1.0) * 2.0)
                            val ratio = (product.currentStock / maxRef).toFloat().coerceIn(0f, 1f)
                            LinearProgressIndicator(
                                progress = { if (product.currentStock <= 0.0) 0f else ratio },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = stockColor,
                                trackColor = stockColor.copy(alpha = 0.15f)
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "Min alert: ${product.minStockAlert.toInt()} ${product.unit}",
                                fontSize = 9.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
                Spacer(modifier = Modifier.height(8.dp))

                // 5. Harmonized Action Toolbar
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
                                .height(38.dp)
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

                        FilledTonalButton(
                            onClick = { onEdit(product) },
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("edit_product_${product.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = "Edit",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Edit", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    // Right Actions: Cart Stepper / Add to Cart & Overflow Menu
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onAddToCart != null && product.isActive) {
                            if (cartQuantity > 0.0) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .height(38.dp)
                                        .testTag("add_to_cart_${product.id}")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 4.dp)
                                    ) {
                                        IconButton(
                                            onClick = { onDecrementCart?.invoke(product) ?: onAddToCart(product) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Remove,
                                                contentDescription = "Decrease",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                        val displayQty = if (cartQuantity % 1.0 == 0.0) cartQuantity.toInt().toString() else String.format(Locale.ENGLISH, "%.1f", cartQuantity)
                                        Text(
                                            text = "$displayQty in cart",
                                            fontSize = 11.5.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        IconButton(
                                            onClick = { onIncrementCart?.invoke(product) ?: onAddToCart(product) },
                                            modifier = Modifier.size(30.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Add,
                                                contentDescription = "Increase",
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { onAddToCart(product) },
                                    shape = RoundedCornerShape(10.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .height(38.dp)
                                        .testTag("add_to_cart_${product.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.AddShoppingCart,
                                        contentDescription = "Add to Cart",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cart +", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }

                        // Overflow Menu Button
                        Box {
                            var showMenu by remember { mutableStateOf(false) }

                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(38.dp)
                            ) {
                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .testTag("product_menu_${product.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More options",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
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
