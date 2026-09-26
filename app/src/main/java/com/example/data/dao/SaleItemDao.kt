package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.SaleItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSaleItem(item: SaleItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllSaleItems(items: List<SaleItemEntity>)

    @Query("SELECT * FROM sale_items ORDER BY id ASC")
    suspend fun getAllSaleItemsDirect(): List<SaleItemEntity>

    @Query("SELECT * FROM sale_items ORDER BY id ASC")
    fun getAllSaleItems(): Flow<List<SaleItemEntity>>

    @Query("SELECT * FROM sale_items WHERE transactionId = :txId ORDER BY id ASC")
    suspend fun getSaleItemsByTransactionId(txId: Long): List<SaleItemEntity>

    @Query("SELECT * FROM sale_items WHERE transactionId = :txId ORDER BY id ASC")
    fun observeSaleItemsByTransactionId(txId: Long): Flow<List<SaleItemEntity>>

    @Query("SELECT si.* FROM sale_items si INNER JOIN transactions t ON si.transactionId = t.id WHERE t.timestamp >= :startTime AND t.timestamp <= :endTime AND t.isCancelled = 0 ORDER BY si.id ASC")
    suspend fun getSaleItemsInPeriodDirect(startTime: Long, endTime: Long): List<SaleItemEntity>

    @Query("SELECT si.* FROM sale_items si INNER JOIN transactions t ON si.transactionId = t.id WHERE t.timestamp >= :startTime AND t.timestamp <= :endTime AND t.isCancelled = 0 ORDER BY si.id ASC")
    fun observeSaleItemsInPeriod(startTime: Long, endTime: Long): Flow<List<SaleItemEntity>>

    @Query("SELECT COUNT(*) FROM sale_items WHERE productId = :productId")
    suspend fun getSaleCountForProduct(productId: Long): Int

    @Query("SELECT * FROM sale_items WHERE productId = :productId ORDER BY id DESC")
    suspend fun getSaleItemsForProductDirect(productId: Long): List<SaleItemEntity>

    @Query("DELETE FROM sale_items WHERE transactionId = :txId")
    suspend fun deleteSaleItemsByTransactionId(txId: Long)

    @Query("DELETE FROM sale_items")
    suspend fun deleteAllSaleItems()
}
