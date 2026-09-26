package com.example

import android.content.Context
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.AppDatabase
import com.example.data.KiranaRepository
import com.example.data.MIGRATION_1_2
import com.example.data.MIGRATION_2_3
import com.example.data.MIGRATION_3_4
import com.example.data.MIGRATION_4_5
import com.example.data.MIGRATION_5_6
import com.example.data.MIGRATION_6_7
import com.example.data.MIGRATION_7_8
import com.example.data.MIGRATION_8_9
import com.example.data.model.ShopSettings
import com.example.ui.KiranaViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ShopSettingsPersistenceTest {

    private lateinit var context: Context
    private val dbName = "test_shop_settings_lifecycle.db"
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        context.deleteDatabase(dbName)
    }

    @Test
    fun testShopSettingsFullLifecyclePersistence() = runTest(testDispatcher) {
        // --- STEP 1: INITIAL LAUNCH (Room -> Repository -> ViewModel -> UI) ---
        var db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()
        var repo = KiranaRepository(db)
        var viewModel = KiranaViewModel(repo, ioDispatcher = testDispatcher)

        advanceUntilIdle()

        // Verify default startup settings loaded from Room / Repository
        val initialSettings = viewModel.shopSettings.value
        assertNotNull(initialSettings)
        assertEquals("Shree Ganesh Kirana & General Store", initialSettings.shopName)

        // --- STEP 2: CHANGE EVERY SETTING & SAVE ---
        val customizedSettings = ShopSettings(
            id = 1,
            shopName = "Mondal Brothers Super Kirana",
            tagline = "Best Prices Daily Since 1995",
            ownerName = "Subhash Chandra Mondal",
            phone = "+91 91234 56789",
            address = "Holding 42, College Road, Near Station",
            city = "Kolkata - 700001, WB",
            gstin = "19AABCM1234N1Z2",
            upiId = "mondalbros@icici",
            printerPaperWidth = "80mm",
            receiptFooterNote = "ধন্যবাদ! আবার আসুন! স্বাগতম",
            termsNote = "Exchange within 7 days with valid tax invoice.",
            lowStockThresholdDefault = 12.5
        )

        // UI -> ViewModel -> Repository -> Room
        val saveJob = viewModel.updateShopSettings(customizedSettings)
        saveJob.join()
        advanceUntilIdle()

        // Verify updated in currently running ViewModel state
        val updatedRunningSettings = viewModel.shopSettings.value
        assertEquals("Mondal Brothers Super Kirana", updatedRunningSettings.shopName)
        assertEquals("Best Prices Daily Since 1995", updatedRunningSettings.tagline)
        assertEquals("Subhash Chandra Mondal", updatedRunningSettings.ownerName)
        assertEquals("+91 91234 56789", updatedRunningSettings.phone)
        assertEquals("Holding 42, College Road, Near Station", updatedRunningSettings.address)
        assertEquals("Kolkata - 700001, WB", updatedRunningSettings.city)
        assertEquals("19AABCM1234N1Z2", updatedRunningSettings.gstin)
        assertEquals("mondalbros@icici", updatedRunningSettings.upiId)
        assertEquals("80mm", updatedRunningSettings.printerPaperWidth)
        assertEquals("ধন্যবাদ! আবার আসুন! স্বাগতম", updatedRunningSettings.receiptFooterNote)
        assertEquals("Exchange within 7 days with valid tax invoice.", updatedRunningSettings.termsNote)
        assertEquals(12.5, updatedRunningSettings.lowStockThresholdDefault, 0.001)

        // --- STEP 3: CLOSE APPLICATION (Clear ViewModel, simulate process death, close DB connection) ---
        viewModel.viewModelScope.cancel()
        db.close()

        // --- STEP 4 & 5: REOPEN APPLICATION & VERIFY EVERY SETTING REMAINS ---
        // Simulating clean cold restart: new DB instance, new Repository, new ViewModel
        var reopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()
        var reopenedRepo = KiranaRepository(reopenedDb)
        var reopenedViewModel = KiranaViewModel(reopenedRepo, ioDispatcher = testDispatcher)

        advanceUntilIdle()

        // First, check direct Room persistence
        val directDbSettings = reopenedDb.shopSettingsDao().getSettingsSync()
        assertNotNull("Direct DB settings must not be null", directDbSettings)
        assertEquals("Mondal Brothers Super Kirana", directDbSettings?.shopName)
        assertEquals("Best Prices Daily Since 1995", directDbSettings?.tagline)
        assertEquals("Subhash Chandra Mondal", directDbSettings?.ownerName)
        assertEquals("+91 91234 56789", directDbSettings?.phone)
        assertEquals("Holding 42, College Road, Near Station", directDbSettings?.address)
        assertEquals("Kolkata - 700001, WB", directDbSettings?.city)
        assertEquals("19AABCM1234N1Z2", directDbSettings?.gstin)
        assertEquals("mondalbros@icici", directDbSettings?.upiId)
        assertEquals("80mm", directDbSettings?.printerPaperWidth)
        assertEquals("ধন্যবাদ! আবার আসুন! স্বাগতম", directDbSettings?.receiptFooterNote)
        assertEquals("Exchange within 7 days with valid tax invoice.", directDbSettings?.termsNote)
        assertEquals(12.5, directDbSettings!!.lowStockThresholdDefault, 0.001)

        // Verify via Repository Flow (Room -> Repository -> ViewModel)
        val persistedSettings = reopenedViewModel.shopSettings.first { it.shopName == "Mondal Brothers Super Kirana" }
        assertEquals("Mondal Brothers Super Kirana", persistedSettings.shopName)
        assertEquals("Best Prices Daily Since 1995", persistedSettings.tagline)
        assertEquals("Subhash Chandra Mondal", persistedSettings.ownerName)
        assertEquals("+91 91234 56789", persistedSettings.phone)
        assertEquals("Holding 42, College Road, Near Station", persistedSettings.address)
        assertEquals("Kolkata - 700001, WB", persistedSettings.city)
        assertEquals("19AABCM1234N1Z2", persistedSettings.gstin)
        assertEquals("mondalbros@icici", persistedSettings.upiId)
        assertEquals("80mm", persistedSettings.printerPaperWidth)
        assertEquals("ধন্যবাদ! আবার আসুন! স্বাগতম", persistedSettings.receiptFooterNote)
        assertEquals("Exchange within 7 days with valid tax invoice.", persistedSettings.termsNote)
        assertEquals(12.5, persistedSettings.lowStockThresholdDefault, 0.001)

        // --- STEP 6 & 7: SIMULATE FORCE-STOP AND REOPEN AGAIN, VERIFY AGAIN ---
        reopenedViewModel.viewModelScope.cancel()
        reopenedDb.close()

        val secondReopenedDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9)
            .allowMainThreadQueries()
            .build()
        val secondRepo = KiranaRepository(secondReopenedDb)
        val secondViewModel = KiranaViewModel(secondRepo, ioDispatcher = testDispatcher)

        advanceUntilIdle()

        val secondDbSettings = secondReopenedDb.shopSettingsDao().getSettingsSync()
        assertNotNull("Second direct DB settings must not be null", secondDbSettings)
        assertEquals("Mondal Brothers Super Kirana", secondDbSettings?.shopName)

        val verifiedSettings = secondViewModel.shopSettings.first { it.shopName == "Mondal Brothers Super Kirana" }
        assertEquals("Mondal Brothers Super Kirana", verifiedSettings.shopName)
        assertEquals("Best Prices Daily Since 1995", verifiedSettings.tagline)
        assertEquals("Subhash Chandra Mondal", verifiedSettings.ownerName)
        assertEquals("+91 91234 56789", verifiedSettings.phone)
        assertEquals("Holding 42, College Road, Near Station", verifiedSettings.address)
        assertEquals("Kolkata - 700001, WB", verifiedSettings.city)
        assertEquals("19AABCM1234N1Z2", verifiedSettings.gstin)
        assertEquals("mondalbros@icici", verifiedSettings.upiId)
        assertEquals("80mm", verifiedSettings.printerPaperWidth)
        assertEquals("ধন্যবাদ! আবার আসুন! স্বাগতম", verifiedSettings.receiptFooterNote)
        assertEquals("Exchange within 7 days with valid tax invoice.", verifiedSettings.termsNote)
        assertEquals(12.5, verifiedSettings.lowStockThresholdDefault, 0.001)

        secondViewModel.viewModelScope.cancel()
        secondReopenedDb.close()
    }
}
