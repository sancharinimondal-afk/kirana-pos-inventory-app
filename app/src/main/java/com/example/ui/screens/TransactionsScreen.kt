package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.SaleTransaction
import com.example.ui.KiranaViewModel
import com.example.ui.theme.UpiPurple
import com.example.ui.theme.kiranaTextFieldColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TransactionsScreen(
    viewModel: KiranaViewModel,
    modifier: Modifier = Modifier
) {
    val allTransactions by viewModel.allTransactions.collectAsStateWithLifecycle()
    var searchQuery by remember { mutableStateOf("") }

    val filtered = remember(allTransactions, searchQuery) {
        if (searchQuery.isBlank()) {
            allTransactions
        } else {
            allTransactions.filter { t ->
                t.invoiceNumber.contains(searchQuery, ignoreCase = true) ||
                        t.customerName.contains(searchQuery, ignoreCase = true) ||
                        t.customerPhone.contains(searchQuery, ignoreCase = true) ||
                        t.paymentMode.contains(searchQuery, ignoreCase = true)
            }
        }
    }

    val totalRevenue = allTransactions.sumOf { it.grandTotal }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .testTag("transactions_screen")
    ) {
        Spacer(modifier = Modifier.height(6.dp))

        // Total Summary Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Total Recorded Sales",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "₹" + String.format(Locale.ENGLISH, "%.2f", totalRevenue),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = "${allTransactions.size} Bills",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by Invoice #, Mobile, Customer...", fontSize = 13.sp) },
            leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            colors = kiranaTextFieldColors(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tx_search_field")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // List of transactions
        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = "No bills",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (searchQuery.isBlank()) "No sales transactions recorded yet." else "No bills match '$searchQuery'",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { it.id }) { tx ->
                    TransactionItemCard(
                        transaction = tx,
                        onClick = { viewModel.openPastTransactionReceipt(tx) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransactionItemCard(
    transaction: SaleTransaction,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH)
    val dateStr = dateFormat.format(Date(transaction.timestamp))

    val paymentModeColor = when (transaction.paymentMode) {
        "UPI" -> UpiPurple
        "KHATA" -> Color(0xFFE65100)
        else -> Color(0xFF2E7D32)
    }

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("tx_item_${transaction.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = transaction.invoiceNumber,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = paymentModeColor.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = transaction.paymentMode,
                            color = paymentModeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = dateStr,
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (transaction.customerName.isNotBlank() || transaction.customerPhone.isNotBlank()) {
                    val cust = listOfNotNull(
                        transaction.customerName.takeIf { it.isNotBlank() },
                        transaction.customerPhone.takeIf { it.isNotBlank() }
                    ).joinToString(" • ")
                    Text(
                        text = "Cust: $cust",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "₹" + String.format(Locale.ENGLISH, "%.2f", transaction.grandTotal),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Print Bill",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Receipt",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
