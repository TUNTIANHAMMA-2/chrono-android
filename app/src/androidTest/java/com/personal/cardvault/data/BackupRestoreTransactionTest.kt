package com.personal.cardvault.data

import android.database.sqlite.SQLiteException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.personal.cardvault.backup.BackupCard
import com.personal.cardvault.backup.BackupExpiryItem
import com.personal.cardvault.backup.toCardPayload
import com.personal.cardvault.backup.toExpiryItemEntity
import com.personal.cardvault.expiry.Cycle
import com.personal.cardvault.expiry.Validity
import com.personal.cardvault.vault.CardPayload
import com.personal.cardvault.vault.encryptCardPayload
import com.personal.cardvault.vault.generateDek
import java.time.LocalDate
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real Room/SQLite coverage for dev-plan.md 9.4 step 7, M31 and M33.
 * This tests the database boundary, not SAF, BiometricPrompt, Keystore or notification delivery.
 * Build on the server; run on Android only at a user-initiated checkpoint (sections 12 and D-1).
 */
@RunWith(AndroidJUnit4::class)
class BackupRestoreTransactionTest {
    private lateinit var database: AppDatabase

    private val restoredItem = BackupExpiryItem(
        name = "恢复事项", category = "订阅", startsOn = null, validThrough = "2026-02-28",
        notes = "保留锚点", isPaused = true, cycle = "MONTHLY", cycleAnchorDay = 31,
    ).toExpiryItemEntity()
    private val restoredMeta = VaultMetaEntity(
        wrappedDek = ByteArray(48) { (it + 40).toByte() },
        wrapIv = ByteArray(12) { (it + 60).toByte() },
    )

    @Before
    fun createDatabase() = runBlocking<Unit> {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, AppDatabase::class.java,
        ).build()
        for (id in 1..2) {
            database.expiryItemDao().insert(ExpiryItemEntity(
                id = id, name = "旧事项$id", category = "旧类别",
                validity = Validity(LocalDate.of(2026, 10, 18), LocalDate.of(2026, 11, 18)),
                notes = "旧备注", isPaused = false, cycle = Cycle.YEARLY, cycleAnchorDay = 18,
                earlyNotifiedFor = LocalDate.of(2026, 11, 18),
                dueNotifiedFor = LocalDate.of(2026, 11, 18),
            ))
            database.paymentCardDao().insert(PaymentCardEntity(
                id = id, iv = ByteArray(12) { id.toByte() }, ciphertext = ByteArray(32) { id.toByte() },
            ))
        }
        database.vaultMetaDao().upsert(VaultMetaEntity(
            wrappedDek = ByteArray(48) { 3 }, wrapIv = ByteArray(12) { 4 },
        ))
    }

    @After
    fun closeDatabase() {
        if (::database.isInitialized) database.close()
    }

    @Test
    fun restoreReplacesAllThreeTablesAndStoresPreparedCardCiphertext() = runBlocking<Unit> {
        val dek = generateDek()
        try {
            val card = BackupCard("测试卡", "", "00001234", "01", "2030", "007", "虚构数据")
            val encrypted = encryptCardPayload(dek, card.toCardPayload())
            val preparedCard = PaymentCardEntity(iv = encrypted.iv, ciphertext = encrypted.ciphertext)

            database.replaceWithBackup(listOf(restoredItem), listOf(preparedCard), restoredMeta)

            val storedItem = database.expiryItemDao().getAll().single()
            assertTrue(storedItem.id > 0)
            assertEquals(restoredItem.copy(id = storedItem.id), storedItem)
            assertNull(storedItem.earlyNotifiedFor)
            assertNull(storedItem.dueNotifiedFor)
            val storedCard = database.paymentCardDao().getAll().single()
            assertTrue(storedCard.id > 0)
            assertArrayEquals(encrypted.iv, storedCard.iv)
            assertArrayEquals(encrypted.ciphertext, storedCard.ciphertext)
            // Independent JCA decrypt verifies that the database retained the prepared bytes.
            val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(dek, "AES"), GCMParameterSpec(128, storedCard.iv))
            }
            val payload = Json.decodeFromString(
                CardPayload.serializer(),
                cipher.doFinal(storedCard.ciphertext).decodeToString(),
            )
            assertEquals(card.toCardPayload(), payload)
            val storedMeta = database.vaultMetaDao().get()!!
            assertArrayEquals(restoredMeta.wrappedDek, storedMeta.wrappedDek)
            assertArrayEquals(restoredMeta.wrapIv, storedMeta.wrapIv)
            assertEquals(1, rows("SELECT id FROM vault_meta").size)
        } finally {
            dek.fill(0)
        }
    }

    @Test
    fun itemsOnlyRestoreRemovesOldCardsAndLeavesVaultUncreated() = runBlocking<Unit> {
        database.replaceWithBackup(listOf(restoredItem), emptyList(), null)

        val storedItem = database.expiryItemDao().getAll().single()
        assertEquals(restoredItem.copy(id = storedItem.id), storedItem)
        assertTrue(database.paymentCardDao().getAll().isEmpty())
        assertNull(database.vaultMetaDao().get())
        assertTrue(rows("SELECT id FROM vault_meta").isEmpty())
    }

    @Test
    fun emptyBackupClearsAllThreeTables() = runBlocking<Unit> {
        database.replaceWithBackup(emptyList(), emptyList(), null)

        assertTrue(database.expiryItemDao().getAll().isEmpty())
        assertTrue(database.paymentCardDao().getAll().isEmpty())
        assertTrue(rows("SELECT id FROM vault_meta").isEmpty())
    }

    @Test
    fun itemInsertFailureRollsBackAllDeletes() = runBlocking<Unit> {
        assertInsertFailureRollsBack("expiry_items")
    }

    @Test
    fun cardInsertFailureRollsBackDeletesAndNewItems() = runBlocking<Unit> {
        assertInsertFailureRollsBack("payment_cards")
    }

    @Test
    fun metaInsertFailureRollsBackDeletesNewItemsAndNewCards() = runBlocking<Unit> {
        assertInsertFailureRollsBack("vault_meta")
    }

    private suspend fun assertInsertFailureRollsBack(table: String) {
        val before = snapshot()
        // Simulate a write failure without adding constraints on otherwise permitted backup data.
        // RAISE(ABORT) aborts only the statement; the outer Room transaction must undo earlier writes.
        database.openHelper.writableDatabase.execSQL("""
            CREATE TRIGGER reject_restore_insert BEFORE INSERT ON $table
            BEGIN SELECT RAISE(ABORT, 'injected_restore_failure'); END
        """.trimIndent())
        try {
            try {
                database.replaceWithBackup(
                    listOf(restoredItem),
                    listOf(PaymentCardEntity(iv = ByteArray(12) { 8 }, ciphertext = ByteArray(32) { 9 })),
                    restoredMeta,
                )
                fail("Expected the injected insert failure")
            } catch (exception: SQLiteException) {
                assertTrue(exception.message.orEmpty().contains("injected_restore_failure"))
            }
            assertEquals("Every old row and byte must survive a failed restore", before, snapshot())
        } finally {
            database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_restore_insert")
        }
    }

    private fun snapshot(): List<List<List<String?>>> = listOf(
        rows("SELECT * FROM expiry_items ORDER BY id"),
        rows("SELECT id, hex(iv), hex(ciphertext) FROM payment_cards ORDER BY id"),
        rows("SELECT id, hex(wrappedDek), hex(wrapIv) FROM vault_meta ORDER BY id"),
    )

    private fun rows(sql: String): List<List<String?>> =
        database.openHelper.readableDatabase.query(sql).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add((0 until cursor.columnCount).map { if (cursor.isNull(it)) null else cursor.getString(it) })
                }
            }
        }
}
