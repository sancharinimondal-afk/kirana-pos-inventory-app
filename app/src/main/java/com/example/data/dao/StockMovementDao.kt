package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.StockMovementEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface StockMovementDao {
    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertMovement(movement: StockMovementEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllMovements(movements: List<StockMovementEntity>)

    @Query("SELECT * FROM stock_movements ORDER BY timestamp DESC, id DESC")
    fun getAllMovements(): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements ORDER BY id ASC")
    suspend fun getAllMovementsDirect(): List<StockMovementEntity>

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY timestamp DESC, id DESC")
    fun getMovementsForProduct(productId: Long): Flow<List<StockMovementEntity>>

    @Query("SELECT * FROM stock_movements WHERE productId = :productId ORDER BY timestamp ASC, id ASC")
    suspend fun getMovementsForProductDirect(productId: Long): List<StockMovementEntity>

    @Query("SELECT COUNT(*) FROM stock_movements WHERE productId = :productId")
    suspend fun getMovementCountForProduct(productId: Long): Int

    @Query("DELETE FROM stock_movements")
    suspend fun deleteAllMovements()
}
