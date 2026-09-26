package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.ShopSettings
import kotlinx.coroutines.flow.Flow

@Dao
interface ShopSettingsDao {
    @Query("SELECT * FROM shop_settings WHERE id = 1 LIMIT 1")
    fun getSettings(): Flow<ShopSettings?>

    @Query("SELECT * FROM shop_settings WHERE id = 1 LIMIT 1")
    suspend fun getSettingsSync(): ShopSettings?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(settings: ShopSettings)
}
