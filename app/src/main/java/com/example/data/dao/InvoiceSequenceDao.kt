package com.example.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.model.InvoiceSequence

@Dao
interface InvoiceSequenceDao {
    @Query("SELECT lastSequenceNumber FROM invoice_sequence WHERE prefix = :prefix LIMIT 1")
    suspend fun getLastSequence(prefix: String): Long?

    @Query("SELECT * FROM invoice_sequence ORDER BY prefix ASC")
    suspend fun getAllSequencesDirect(): List<InvoiceSequence>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSequence(sequence: InvoiceSequence)

    @Query("DELETE FROM invoice_sequence")
    suspend fun deleteAllSequences()
}
