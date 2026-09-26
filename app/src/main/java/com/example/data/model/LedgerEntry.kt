package com.example.data.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Ledger entry for customer Khata accounts.
 *
 * Clearly defined ledger types:
 * - CREDIT_SALE: Sale on credit / Udhar (+ balance, customer owes shop)
 * - PAYMENT_RECEIVED: Payment deposited by customer / Jama (- balance, shop received money)
 * - PAYMENT: Alias for PAYMENT_RECEIVED for backwards compatibility
 * - DEBIT_ADJUSTMENT: Manual debit adjustment (+ balance, increases debt owed by customer)
 * - CREDIT_ADJUSTMENT: Manual credit adjustment (- balance, discount/waiver)
 * - OPENING_BALANCE: Initial opening debt balance (+ balance, customer owes shop)
 *
 * CONSISTENT BALANCE RULE:
 * Positive balance = customer owes shop
 * Negative balance = shop owes customer
 *
 * Balance = (CREDIT_SALE + DEBIT_ADJUSTMENT + OPENING_BALANCE) - (PAYMENT_RECEIVED + PAYMENT + CREDIT_ADJUSTMENT)
 */
@Entity(
    tableName = "ledger_entries",
    foreignKeys = [
        ForeignKey(
            entity = Customer::class,
            parentColumns = ["id"],
            childColumns = ["customerId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["customerId"]),
        Index(value = ["date"]),
        Index(value = ["type"])
    ]
)
data class LedgerEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val customerId: Long,
    val date: Long = System.currentTimeMillis(),
    val type: String, // CREDIT_SALE, PAYMENT_RECEIVED, PAYMENT, DEBIT_ADJUSTMENT, CREDIT_ADJUSTMENT, OPENING_BALANCE
    val amount: Double,
    val reference: String = "", // e.g. invoiceNumber or receipt ref
    val note: String = ""
) {
    companion object {
        const val TYPE_CREDIT_SALE = "CREDIT_SALE"
        const val TYPE_PAYMENT_RECEIVED = "PAYMENT_RECEIVED"
        const val TYPE_PAYMENT = "PAYMENT"
        const val TYPE_DEBIT_ADJUSTMENT = "DEBIT_ADJUSTMENT"
        const val TYPE_CREDIT_ADJUSTMENT = "CREDIT_ADJUSTMENT"
        const val TYPE_OPENING_BALANCE = "OPENING_BALANCE"

        fun isPositiveEffect(type: String): Boolean {
            return type == TYPE_CREDIT_SALE || type == TYPE_DEBIT_ADJUSTMENT || type == TYPE_OPENING_BALANCE
        }

        fun isNegativeEffect(type: String): Boolean {
            return type == TYPE_PAYMENT_RECEIVED || type == TYPE_PAYMENT || type == TYPE_CREDIT_ADJUSTMENT
        }
    }
}
