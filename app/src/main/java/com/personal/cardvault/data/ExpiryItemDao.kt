package com.personal.cardvault.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.personal.cardvault.expiry.Cycle
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExpiryItemDao {
    @Insert
    suspend fun insert(item: ExpiryItemEntity): Long

    @Query("SELECT * FROM expiry_items")
    fun observeAll(): Flow<List<ExpiryItemEntity>>

    @Query("SELECT * FROM expiry_items WHERE id = :id")
    suspend fun findById(id: Int): ExpiryItemEntity?

    @Query("SELECT * FROM expiry_items")
    suspend fun getAll(): List<ExpiryItemEntity>

    @Insert
    suspend fun insertAll(items: List<ExpiryItemEntity>)

    @Query("DELETE FROM expiry_items")
    suspend fun deleteAll(): Int

    @Query("""
        UPDATE expiry_items
        SET name = :name, category = :category, starts_on = :startsOn,
            valid_through = :validThrough, notes = :notes, cycle = :cycle,
            cycle_anchor_day = :cycleAnchorDay
        WHERE id = :id
        """)
    suspend fun update(
        id: Int,
        name: String,
        category: String,
        startsOn: LocalDate?,
        validThrough: LocalDate,
        notes: String,
        cycle: Cycle,
        cycleAnchorDay: Int?,
    ): Int

    @Query("DELETE FROM expiry_items WHERE id = :id")
    suspend fun deleteById(id: Int): Int

    @Query("UPDATE expiry_items SET is_paused = :isPaused WHERE id = :id")
    suspend fun updatePaused(id: Int, isPaused: Boolean): Int

    @Query("UPDATE expiry_items SET early_notified_for = :validThrough WHERE id = :id")
    suspend fun markEarlyNotified(id: Int, validThrough: LocalDate): Int

    @Query("UPDATE expiry_items SET due_notified_for = :validThrough WHERE id = :id")
    suspend fun markDueNotified(id: Int, validThrough: LocalDate): Int

    @Query(
        """
        UPDATE expiry_items SET valid_through = :newValidThrough
        WHERE id = :id AND valid_through = :expectedValidThrough AND is_paused = 0
        """,
    )
    suspend fun renewIfCurrent(
        id: Int,
        expectedValidThrough: LocalDate,
        newValidThrough: LocalDate,
    ): Int

    @Query(
        """
        UPDATE expiry_items SET is_paused = 1
        WHERE id = :id AND valid_through = :expectedValidThrough AND is_paused = 0
        """,
    )
    suspend fun pauseIfCurrent(id: Int, expectedValidThrough: LocalDate): Int
}
