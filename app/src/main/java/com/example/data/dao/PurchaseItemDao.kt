package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.PurchaseItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PurchaseItemDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertPurchaseItem(item: PurchaseItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllPurchaseItems(items: List<PurchaseItemEntity>)

    @Query("SELECT * FROM purchase_items ORDER BY id ASC")
    suspend fun getAllPurchaseItemsDirect(): List<PurchaseItemEntity>

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId ORDER BY id ASC")
    suspend fun getPurchaseItemsByPurchaseId(purchaseId: Long): List<PurchaseItemEntity>

    @Query("SELECT * FROM purchase_items WHERE purchaseId = :purchaseId ORDER BY id ASC")
    fun observePurchaseItemsByPurchaseId(purchaseId: Long): Flow<List<PurchaseItemEntity>>

    @Query("SELECT pi.* FROM purchase_items pi INNER JOIN purchases p ON pi.purchaseId = p.id WHERE p.timestamp >= :startTime AND p.timestamp <= :endTime ORDER BY pi.id ASC")
    suspend fun getPurchaseItemsInPeriodDirect(startTime: Long, endTime: Long): List<PurchaseItemEntity>

    @Query("SELECT pi.* FROM purchase_items pi INNER JOIN purchases p ON pi.purchaseId = p.id WHERE p.timestamp >= :startTime AND p.timestamp <= :endTime ORDER BY pi.id ASC")
    fun observePurchaseItemsInPeriod(startTime: Long, endTime: Long): Flow<List<PurchaseItemEntity>>

    @Query("SELECT COUNT(*) FROM purchase_items WHERE productId = :productId")
    suspend fun getPurchaseCountForProduct(productId: Long): Int

    @Query("SELECT * FROM purchase_items WHERE productId = :productId ORDER BY id DESC")
    suspend fun getPurchaseItemsForProductDirect(productId: Long): List<PurchaseItemEntity>

    @Query("DELETE FROM purchase_items WHERE purchaseId = :purchaseId")
    suspend fun deletePurchaseItemsByPurchaseId(purchaseId: Long)

    @Query("DELETE FROM purchase_items")
    suspend fun deleteAllPurchaseItems()
}
