package com.example.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.model.ProductItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllProducts(): Flow<List<ProductItem>>

    @Query("SELECT * FROM products ORDER BY id ASC")
    suspend fun getAllProductsDirect(): List<ProductItem>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductById(id: Long): ProductItem?

    @Query("SELECT * FROM products WHERE barcode = :barcode AND barcode != '' LIMIT 1")
    suspend fun getProductByBarcode(barcode: String): ProductItem?

    @Query("SELECT * FROM products WHERE barcode = :barcode AND barcode != '' LIMIT 1")
    fun observeProductByBarcode(barcode: String): Flow<ProductItem?>

    @Query("SELECT * FROM products WHERE currentStock > 0 AND currentStock <= minStockAlert ORDER BY currentStock ASC")
    fun getLowStockProducts(): Flow<List<ProductItem>>

    @Query("SELECT * FROM products WHERE currentStock <= 0 ORDER BY name ASC")
    fun getOutOfStockProducts(): Flow<List<ProductItem>>

    @Query("SELECT COUNT(*) FROM products")
    fun getProductCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products")
    suspend fun getProductCountDirect(): Int

    @Query("SELECT COUNT(*) FROM products WHERE currentStock > 0 AND currentStock <= minStockAlert")
    fun getLowStockCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM products WHERE currentStock <= 0")
    fun getOutOfStockCount(): Flow<Int>

    @Query("SELECT SUM(currentStock * costPrice) FROM products")
    fun getTotalInventoryCostValue(): Flow<Double?>

    @Query("SELECT SUM(currentStock * mrp) FROM products")
    fun getTotalInventoryMrpValue(): Flow<Double?>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertProduct(product: ProductItem): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAllProducts(products: List<ProductItem>)

    @Update
    suspend fun updateProduct(product: ProductItem)

    @Query("UPDATE products SET currentStock = :newStock, lastUpdated = :timestamp WHERE id = :productId")
    suspend fun updateStock(productId: Long, newStock: Double, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE products SET currentStock = currentStock - :qtySold, lastUpdated = :timestamp WHERE id = :productId")
    suspend fun deductStock(productId: Long, qtySold: Double, timestamp: Long = System.currentTimeMillis())

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY name ASC")
    fun getActiveProducts(): Flow<List<ProductItem>>

    @Query("SELECT * FROM products WHERE isActive = 1 ORDER BY id ASC")
    suspend fun getActiveProductsDirect(): List<ProductItem>

    @Query("UPDATE products SET isActive = 0, lastUpdated = :timestamp WHERE id = :id")
    suspend fun deactivateProduct(id: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE products SET isActive = 1, lastUpdated = :timestamp WHERE id = :id")
    suspend fun reactivateProduct(id: Long, timestamp: Long = System.currentTimeMillis())

    @Delete
    suspend fun deleteProduct(product: ProductItem)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: Long)

    @Query("DELETE FROM products")
    suspend fun deleteAllProducts()
}
