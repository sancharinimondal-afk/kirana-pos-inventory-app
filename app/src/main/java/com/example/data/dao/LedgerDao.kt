package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.LedgerEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface LedgerDao {
    @Query("SELECT * FROM ledger_entries WHERE customerId = :customerId ORDER BY date DESC, id DESC")
    fun getEntriesByCustomerId(customerId: Long): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries WHERE customerId = :customerId ORDER BY date ASC, id ASC")
    suspend fun getEntriesByCustomerIdDirect(customerId: Long): List<LedgerEntry>

    @Query("SELECT * FROM ledger_entries ORDER BY id ASC")
    suspend fun getAllLedgerEntriesDirect(): List<LedgerEntry>

    @Query("SELECT * FROM ledger_entries ORDER BY date DESC")
    fun getAllLedgerEntries(): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries WHERE date >= :startTime AND date <= :endTime ORDER BY date DESC")
    fun getLedgerEntriesInPeriod(startTime: Long, endTime: Long): Flow<List<LedgerEntry>>

    @Query("SELECT * FROM ledger_entries WHERE date >= :startTime AND date <= :endTime ORDER BY date DESC")
    suspend fun getLedgerEntriesInPeriodDirect(startTime: Long, endTime: Long): List<LedgerEntry>

    @Query("SELECT COALESCE(SUM(CASE WHEN type IN ('CREDIT_SALE', 'DEBIT_ADJUSTMENT', 'OPENING_BALANCE') THEN amount WHEN type IN ('PAYMENT', 'PAYMENT_RECEIVED', 'CREDIT_ADJUSTMENT') THEN -amount ELSE 0.0 END), 0.0) FROM ledger_entries WHERE customerId = :customerId")
    fun getCustomerBalance(customerId: Long): Flow<Double>

    @Query("SELECT COALESCE(SUM(CASE WHEN type IN ('CREDIT_SALE', 'DEBIT_ADJUSTMENT', 'OPENING_BALANCE') THEN amount WHEN type IN ('PAYMENT', 'PAYMENT_RECEIVED', 'CREDIT_ADJUSTMENT') THEN -amount ELSE 0.0 END), 0.0) FROM ledger_entries WHERE customerId = :customerId")
    suspend fun getCustomerBalanceDirect(customerId: Long): Double

    @Query("SELECT COALESCE(SUM(CASE WHEN type IN ('CREDIT_SALE', 'DEBIT_ADJUSTMENT', 'OPENING_BALANCE') THEN amount WHEN type IN ('PAYMENT', 'PAYMENT_RECEIVED', 'CREDIT_ADJUSTMENT') THEN -amount ELSE 0.0 END), 0.0) FROM ledger_entries")
    fun getTotalKhataOutstanding(): Flow<Double>

    @Query("SELECT COALESCE(SUM(CASE WHEN type IN ('CREDIT_SALE', 'DEBIT_ADJUSTMENT', 'OPENING_BALANCE') THEN amount WHEN type IN ('PAYMENT', 'PAYMENT_RECEIVED', 'CREDIT_ADJUSTMENT') THEN -amount ELSE 0.0 END), 0.0) FROM ledger_entries")
    suspend fun getTotalKhataOutstandingDirect(): Double

    @Query("SELECT * FROM ledger_entries WHERE customerId = :customerId AND reference = :reference AND type = 'CREDIT_SALE' LIMIT 1")
    suspend fun findCreditSaleEntry(customerId: Long, reference: String): LedgerEntry?

    @Query("SELECT * FROM ledger_entries WHERE customerId = :customerId AND reference = :reference ORDER BY id DESC LIMIT 1")
    suspend fun findEntryByReference(customerId: Long, reference: String): LedgerEntry?

    @Query("SELECT * FROM ledger_entries WHERE customerId = :customerId AND type = 'OPENING_BALANCE' LIMIT 1")
    suspend fun getOpeningBalanceEntry(customerId: Long): LedgerEntry?

    @Query("SELECT COUNT(*) FROM ledger_entries WHERE customerId = :customerId AND type = 'OPENING_BALANCE'")
    suspend fun countOpeningBalanceEntries(customerId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLedgerEntry(entry: LedgerEntry): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllLedgerEntries(entries: List<LedgerEntry>)

    @Query("DELETE FROM ledger_entries WHERE id = :id")
    suspend fun deleteLedgerEntryById(id: Long)

    @Query("DELETE FROM ledger_entries WHERE customerId = :customerId")
    suspend fun deleteEntriesByCustomerId(customerId: Long)

    @Query("DELETE FROM ledger_entries")
    suspend fun deleteAllLedgerEntries()
}
