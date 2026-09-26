package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.Customer
import com.example.data.model.CustomerWithBalance
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {
    @Query("SELECT * FROM customers ORDER BY name ASC")
    fun getAllCustomers(): Flow<List<Customer>>

    @Query("""
        SELECT c.*, 
        COALESCE((
            SELECT SUM(CASE 
                WHEN type IN ('CREDIT_SALE', 'DEBIT_ADJUSTMENT', 'OPENING_BALANCE') THEN amount 
                WHEN type IN ('PAYMENT', 'PAYMENT_RECEIVED', 'CREDIT_ADJUSTMENT') THEN -amount 
                ELSE 0.0 END) 
            FROM ledger_entries 
            WHERE customerId = c.id
        ), 0.0) AS currentBalance
        FROM customers c
        ORDER BY c.name ASC
    """)
    fun getAllCustomersWithBalance(): Flow<List<CustomerWithBalance>>

    @Query("""
        SELECT c.*, 
        COALESCE((
            SELECT SUM(CASE 
                WHEN type IN ('CREDIT_SALE', 'DEBIT_ADJUSTMENT', 'OPENING_BALANCE') THEN amount 
                WHEN type IN ('PAYMENT', 'PAYMENT_RECEIVED', 'CREDIT_ADJUSTMENT') THEN -amount 
                ELSE 0.0 END) 
            FROM ledger_entries 
            WHERE customerId = c.id
        ), 0.0) AS currentBalance
        FROM customers c
        ORDER BY c.name ASC
    """)
    suspend fun getAllCustomersWithBalanceDirect(): List<CustomerWithBalance>

    @Query("""
        SELECT c.*, 
        COALESCE((
            SELECT SUM(CASE 
                WHEN type IN ('CREDIT_SALE', 'DEBIT_ADJUSTMENT', 'OPENING_BALANCE') THEN amount 
                WHEN type IN ('PAYMENT', 'PAYMENT_RECEIVED', 'CREDIT_ADJUSTMENT') THEN -amount 
                ELSE 0.0 END) 
            FROM ledger_entries 
            WHERE customerId = c.id
        ), 0.0) AS currentBalance
        FROM customers c
        WHERE c.id = :customerId
        LIMIT 1
    """)
    fun getCustomerWithBalance(customerId: Long): Flow<CustomerWithBalance?>

    @Query("""
        SELECT c.*, 
        COALESCE((
            SELECT SUM(CASE 
                WHEN type IN ('CREDIT_SALE', 'DEBIT_ADJUSTMENT', 'OPENING_BALANCE') THEN amount 
                WHEN type IN ('PAYMENT', 'PAYMENT_RECEIVED', 'CREDIT_ADJUSTMENT') THEN -amount 
                ELSE 0.0 END) 
            FROM ledger_entries 
            WHERE customerId = c.id
        ), 0.0) AS currentBalance
        FROM customers c
        WHERE c.id = :customerId
        LIMIT 1
    """)
    suspend fun getCustomerWithBalanceDirect(customerId: Long): CustomerWithBalance?

    @Query("SELECT * FROM customers ORDER BY id ASC")
    suspend fun getAllCustomersDirect(): List<Customer>

    @Query("SELECT * FROM customers WHERE id = :id LIMIT 1")
    suspend fun getCustomerById(id: Long): Customer?

    @Query("SELECT * FROM customers WHERE phone = :phone LIMIT 1")
    suspend fun getCustomerByPhone(phone: String): Customer?

    @Query("SELECT * FROM customers WHERE name = :name LIMIT 1")
    suspend fun getCustomerByName(name: String): Customer?

    @Query("SELECT * FROM customers WHERE phone = :phone OR name = :name LIMIT 1")
    suspend fun findCustomer(name: String, phone: String): Customer?

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun getCustomerCountDirect(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllCustomers(customers: List<Customer>)

    @Update
    suspend fun updateCustomer(customer: Customer)

    @Query("DELETE FROM customers WHERE id = :id")
    suspend fun deleteCustomerById(id: Long)

    @Query("DELETE FROM customers")
    suspend fun deleteAllCustomers()
}
