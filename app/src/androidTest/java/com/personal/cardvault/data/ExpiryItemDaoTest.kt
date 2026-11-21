package com.personal.cardvault.data

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.cardvault.expiry.Cycle
import com.personal.cardvault.expiry.Validity
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** U17: real Room/SQLite updates, with expectations from dev-plan.md sections 5.1 and 7.4. */
@RunWith(AndroidJUnit4::class)
class ExpiryItemDaoTest {
    private lateinit var database: AppDatabase
    private lateinit var dao: ExpiryItemDao
    private val expected = LocalDate.of(2026, 11, 18)
    private val renewed = LocalDate.of(2026, 12, 18)

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java,
        ).build()
        dao = database.expiryItemDao()
    }

    @After
    fun closeDatabase() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun u17_matchingRenewalChangesOnlyTargetExpiryAndReturnsOne() = runBlocking {
        val id = insertItem()
        val otherId = insertItem()
        val before = row(id)
        val otherBefore = row(otherId)

        assertEquals(1, dao.renewIfCurrent(id, expected, renewed))

        assertEquals(before + ("valid_through" to "2026-12-18"), row(id))
        assertEquals(otherBefore, row(otherId))
    }

    @Test
    fun u17_matchingPauseChangesOnlyTargetPauseFlagAndReturnsOne() = runBlocking {
        val id = insertItem()
        val otherId = insertItem()
        val before = row(id)
        val otherBefore = row(otherId)

        assertEquals(1, dao.pauseIfCurrent(id, expected))

        assertEquals(before + ("is_paused" to "1"), row(id))
        assertEquals(otherBefore, row(otherId))
    }

    @Test
    fun u17_mismatchedExpiryMakesBothUpdatesReturnZeroWithoutChangingData() = runBlocking {
        val id = insertItem()
        val before = row(id)
        val staleExpiry = LocalDate.of(2026, 10, 18)

        assertEquals(0, dao.renewIfCurrent(id, staleExpiry, renewed))
        assertEquals(before, row(id))
        assertEquals(0, dao.pauseIfCurrent(id, staleExpiry))
        assertEquals(before, row(id))
    }

    @Test
    fun u17_pausedItemMakesBothUpdatesReturnZeroWithoutChangingData() = runBlocking {
        val id = insertItem(paused = true)
        val before = row(id)

        assertEquals(0, dao.renewIfCurrent(id, expected, renewed))
        assertEquals(before, row(id))
        assertEquals(0, dao.pauseIfCurrent(id, expected))
        assertEquals(before, row(id))
    }

    @Test
    fun u17_repeatedRenewalReturnsZeroAndKeepsFirstRenewal() = runBlocking {
        val id = insertItem()
        assertEquals(1, dao.renewIfCurrent(id, expected, renewed))
        val afterFirst = row(id)

        assertEquals(0, dao.renewIfCurrent(id, expected, renewed))
        assertEquals(afterFirst, row(id))
    }

    @Test
    fun u17_repeatedPauseReturnsZeroAndKeepsPausedData() = runBlocking {
        val id = insertItem()
        assertEquals(1, dao.pauseIfCurrent(id, expected))
        val afterFirst = row(id)

        assertEquals(0, dao.pauseIfCurrent(id, expected))
        assertEquals(afterFirst, row(id))
    }

    @Test
    fun u17_pauseFromOldNotificationAfterRenewalReturnsZero() = runBlocking {
        val id = insertItem()
        assertEquals(1, dao.renewIfCurrent(id, expected, renewed))
        val afterRenewal = row(id)

        assertEquals(0, dao.pauseIfCurrent(id, expected))
        assertEquals(afterRenewal, row(id))
    }

    @Test
    fun u17_renewalAfterPauseReturnsZero() = runBlocking {
        val id = insertItem()
        assertEquals(1, dao.pauseIfCurrent(id, expected))
        val afterPause = row(id)

        assertEquals(0, dao.renewIfCurrent(id, expected, renewed))
        assertEquals(afterPause, row(id))
    }

    @Test
    fun u17_deletedItemMakesBothUpdatesReturnZeroAndKeepsOtherItems() = runBlocking {
        val id = insertItem()
        val otherId = insertItem()
        val otherBefore = row(otherId)
        database.openHelper.writableDatabase.execSQL(
            "DELETE FROM expiry_items WHERE id = ?", arrayOf<Any>(id),
        )

        assertEquals(0, dao.renewIfCurrent(id, expected, renewed))
        assertEquals(0, dao.pauseIfCurrent(id, expected))
        database.openHelper.readableDatabase.query(
            "SELECT COUNT(*) FROM expiry_items WHERE id = ?", arrayOf<Any>(id),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        assertEquals(otherBefore, row(otherId))
    }

    private suspend fun insertItem(paused: Boolean = false): Int = dao.insert(
        ExpiryItemEntity(
            name = "测试订阅",
            category = "订阅",
            validity = Validity(LocalDate.of(2026, 10, 18), expected),
            notes = "保留备注",
            isPaused = paused,
            cycle = Cycle.MONTHLY,
            cycleAnchorDay = 18,
            earlyNotifiedFor = LocalDate.of(2026, 10, 18),
            dueNotifiedFor = LocalDate.of(2026, 10, 18),
        ),
    ).toInt()

    // Read real stored values without requiring a product read API in this task.
    private fun row(id: Int): Map<String, String?> =
        database.openHelper.readableDatabase.query(
            "SELECT * FROM expiry_items WHERE id = ?", arrayOf<Any>(id),
        ).use { cursor ->
            assertTrue("Expected inserted item $id", cursor.moveToFirst())
            cursor.columnNames.associateWith { column ->
                val index = cursor.getColumnIndexOrThrow(column)
                if (cursor.isNull(index)) null else cursor.getString(index)
            }
        }
}
