package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persists the invoice sequence counter safely in Room per year or prefix.
 * For example: prefix = "2026", lastSequenceNumber = 42 -> Next invoice: INV-2026-000043
 */
@Entity(tableName = "invoice_sequence")
data class InvoiceSequence(
    @PrimaryKey
    val prefix: String, // e.g. "2026"
    val lastSequenceNumber: Long = 0L
)
