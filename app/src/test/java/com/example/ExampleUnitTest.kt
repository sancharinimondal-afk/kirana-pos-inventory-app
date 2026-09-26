package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.model.ProductItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleUnitTest {
    @Test
    fun testPartialIndexBehavior() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .addCallback(object : androidx.room.RoomDatabase.Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP INDEX IF EXISTS `index_products_barcode`")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`) WHERE `barcode` != ''")
                }
                override fun onOpen(db: SupportSQLiteDatabase) {
                    db.execSQL("DROP INDEX IF EXISTS `index_products_barcode`")
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_products_barcode` ON `products` (`barcode`) WHERE `barcode` != ''")
                }
            })
            .allowMainThreadQueries()
            .build()
        try {
            val id1 = db.productDao().insertProduct(
                ProductItem(name = "P1", barcode = "", category = "General", costPrice = 10.0, sellingPrice = 15.0, mrp = 15.0, currentStock = 5.0)
            )
            val id2 = db.productDao().insertProduct(
                ProductItem(name = "P2", barcode = "", category = "General", costPrice = 10.0, sellingPrice = 15.0, mrp = 15.0, currentStock = 5.0)
            )
            println("Inserted P1=$id1, P2=$id2")
            val all = db.productDao().getAllProducts().first()
            println("Total products in db: ${all.size}")
            assertEquals(2, all.size)
            assertEquals("", all[0].barcode)
            assertEquals("", all[1].barcode)
        } catch (e: Exception) {
            println("Caught exception: ${e::class.java.name}: ${e.message}")
            throw e
        } finally {
            db.close()
        }
    }
}


