package com.example.data.model

import androidx.room.Embedded

/**
 * Data class representing a Customer with their LIVE calculated current Khata balance.
 *
 * Balance Formula:
 * Opening Balance + Credit Sales + Debit Adjustments - Payments - Credit Adjustments = Current Balance
 *
 * Positive balance = customer owes shop.
 * Negative balance = shop owes customer.
 */
data class CustomerWithBalance(
    @Embedded
    val customer: Customer,
    val currentBalance: Double
)
