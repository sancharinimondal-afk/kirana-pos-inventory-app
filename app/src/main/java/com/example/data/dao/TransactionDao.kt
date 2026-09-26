package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SaleTransaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun getAllTransactions(): Flow<List<SaleTransaction>>

    @Query("SELECT * FROM transactions ORDER BY id ASC")
    suspend fun getAllTransactionsDirect(): List<SaleTransaction>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getTransactionById(id: Long): SaleTransaction?

    @Query("SELECT * FROM transactions WHERE invoiceNumber = :invoiceNumber LIMIT 1")
    suspend fun getTransactionByInvoiceNumber(invoiceNumber: String): SaleTransaction?

    @Query("SELECT * FROM transactions WHERE timestamp >= :startOfDay AND isCancelled = 0 ORDER BY timestamp DESC")
    fun getTodayTransactions(startOfDay: Long): Flow<List<SaleTransaction>>

    @Query("SELECT SUM(grandTotal) FROM transactions WHERE timestamp >= :startOfDay AND isCancelled = 0")
    fun getTodaySalesTotal(startOfDay: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM transactions WHERE timestamp >= :startOfDay AND isCancelled = 0")
    fun getTodaySalesCount(startOfDay: Long): Flow<Int>

    @Query("SELECT * FROM transactions WHERE timestamp >= :startTime AND timestamp <= :endTime AND isCancelled = 0 ORDER BY timestamp DESC")
    fun getTransactionsInPeriod(startTime: Long, endTime: Long): Flow<List<SaleTransaction>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :startTime AND timestamp <= :endTime AND isCancelled = 0 ORDER BY timestamp DESC")
    suspend fun getTransactionsInPeriodDirect(startTime: Long, endTime: Long): List<SaleTransaction>

    @Query("SELECT SUM(grandTotal) FROM transactions WHERE timestamp >= :startTime AND timestamp <= :endTime AND isCancelled = 0")
    fun getSalesTotalInPeriod(startTime: Long, endTime: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM transactions WHERE timestamp >= :startTime AND timestamp <= :endTime AND isCancelled = 0")
    fun getSalesCountInPeriod(startTime: Long, endTime: Long): Flow<Int>

    @Query("SELECT SUM(grandTotal) FROM transactions WHERE isCancelled = 0")
    fun getTotalLifetimeSales(): Flow<Double?>

    @Query("SELECT COUNT(*) FROM transactions WHERE isCancelled = 0")
    fun getTotalLifetimeTransactionsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTransactionCountDirect(): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertTransaction(transaction: SaleTransaction): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllTransactions(transactions: List<SaleTransaction>)

    @Query("UPDATE transactions SET isCancelled = 1, cancellationReason = :reason, cancelledAt = :cancelledAt WHERE id = :id")
    suspend fun cancelTransaction(id: Long, reason: String, cancelledAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM transactions WHERE customerId = :customerId ORDER BY timestamp DESC")
    fun getTransactionsByCustomerId(customerId: Long): Flow<List<SaleTransaction>>

    @Query("SELECT * FROM transactions WHERE customerId = :customerId ORDER BY timestamp DESC")
    suspend fun getTransactionsByCustomerIdDirect(customerId: Long): List<SaleTransaction>

    @Query("SELECT invoiceNumber FROM transactions WHERE invoiceNumber LIKE :pattern")
    suspend fun getInvoiceNumbersMatching(pattern: String): List<String>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteTransactionById(id: Long)

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()
}
