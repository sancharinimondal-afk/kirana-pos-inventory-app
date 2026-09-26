package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.PurchaseEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchase(purchase: PurchaseEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllPurchases(purchases: List<PurchaseEntity>)

    @Query("SELECT * FROM purchases ORDER BY timestamp DESC")
    fun getAllPurchases(): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases ORDER BY id ASC")
    suspend fun getAllPurchasesDirect(): List<PurchaseEntity>

    @Query("SELECT * FROM purchases WHERE id = :id LIMIT 1")
    suspend fun getPurchaseById(id: Long): PurchaseEntity?

    @Query("SELECT * FROM purchases WHERE purchaseNumber = :purchaseNumber LIMIT 1")
    suspend fun getPurchaseByNumber(purchaseNumber: String): PurchaseEntity?

    @Query("SELECT * FROM purchases WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    fun getPurchasesInPeriod(startTime: Long, endTime: Long): Flow<List<PurchaseEntity>>

    @Query("SELECT * FROM purchases WHERE timestamp >= :startTime AND timestamp <= :endTime ORDER BY timestamp DESC")
    suspend fun getPurchasesInPeriodDirect(startTime: Long, endTime: Long): List<PurchaseEntity>

    @Query("SELECT SUM(grandTotal) FROM purchases WHERE timestamp >= :startTime AND timestamp <= :endTime")
    fun getPurchasesTotalInPeriod(startTime: Long, endTime: Long): Flow<Double?>

    @Query("SELECT COUNT(*) FROM purchases WHERE timestamp >= :startTime AND timestamp <= :endTime")
    fun getPurchasesCountInPeriod(startTime: Long, endTime: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM purchases")
    fun getPurchaseCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM purchases")
    suspend fun getPurchaseCountDirect(): Int

    @Query("SELECT SUM(grandTotal) FROM purchases")
    fun getTotalPurchasesAmount(): Flow<Double?>

    @Query("SELECT SUM(grandTotal) FROM purchases")
    suspend fun getTotalPurchasesAmountDirect(): Double?

    @Query("DELETE FROM purchases WHERE id = :id")
    suspend fun deletePurchaseById(id: Long)

    @Query("DELETE FROM purchases")
    suspend fun deleteAllPurchases()
}
