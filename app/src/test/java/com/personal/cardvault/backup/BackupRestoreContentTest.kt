package com.personal.cardvault.backup

import com.personal.cardvault.data.ExpiryItemEntity
import com.personal.cardvault.expiry.Cycle
import com.personal.cardvault.expiry.Validity
import com.personal.cardvault.vault.CardPayload
import java.time.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Expectations from dev-plan.md 5.1–5.3, 6.3, 9.2, 9.4 step 7 and U25. */
class BackupRestoreContentTest {
    @Test
    fun literalBackupRestoresAllItemFieldsAndClearsNotificationMarkers() {
        // Independent JSON fixture: expected fields are specified by the document, not an export.
        val content = decode("""
            {
              "exportedAt":"2026-11-18T09:00:00Z",
              "expiryItems":[
                {"name":"月付订阅","category":"订阅","startsOn":"2026-01-01",
                 "validThrough":"2026-02-28","notes":"月底锚点\n保留备注",
                 "isPaused":true,"cycle":"MONTHLY","cycleAnchorDay":31},
                {"name":"年付订阅","category":"","startsOn":null,
                 "validThrough":"2029-02-28","notes":"","isPaused":false,
                 "cycle":"YEARLY","cycleAnchorDay":29},
                {"name":"食品","category":"食品","startsOn":"2026-12-01",
                 "validThrough":"2026-11-18","notes":"日期可自由编辑","isPaused":false,
                 "cycle":"NONE","cycleAnchorDay":null}
              ],
              "cards":[]
            }
        """.trimIndent())

        val restored = content.expiryItems.map { it.toExpiryItemEntity() }

        assertEquals(listOf(
            expectedItem("月付订阅", "订阅", "2026-01-01", "2026-02-28",
                "月底锚点\n保留备注", true, Cycle.MONTHLY, 31),
            expectedItem("年付订阅", "", null, "2029-02-28", "", false, Cycle.YEARLY, 29),
            expectedItem("食品", "食品", "2026-12-01", "2026-11-18",
                "日期可自由编辑", false, Cycle.NONE, null),
        ), restored)
        restored.forEach {
            assertNull(it.earlyNotifiedFor)
            assertNull(it.dueNotifiedFor)
        }
    }

    @Test
    fun exportJsonContainsOnlyDocumentedBackupAndItemFields() {
        val original = expectedItem("订阅", "", null, "2026-11-18", "", false, Cycle.NONE, null)
            .copy(id = 42, earlyNotifiedFor = LocalDate.of(2026, 11, 15),
                dueNotifiedFor = LocalDate.of(2026, 11, 18))
        val backup = BackupContent("2026-11-18T09:00:00Z",
            listOf(original.toBackupExpiryItem()), emptyList())

        val json = Json.parseToJsonElement(Json.encodeToString(BackupContent.serializer(), backup)).jsonObject
        val itemJson = Json.parseToJsonElement(
            Json.encodeToString(BackupExpiryItem.serializer(), backup.expiryItems.single()),
        ).jsonObject

        assertEquals(setOf("exportedAt", "expiryItems", "cards"), json.keys)
        assertEquals(setOf("name", "category", "startsOn", "validThrough", "notes", "isPaused",
            "cycle", "cycleAnchorDay"), itemJson.keys)
    }

    @Test
    fun exportedItemsRoundTripWithoutOldIdsOrNotificationMarkers() {
        for (cycle in Cycle.entries) {
            val original = expectedItem("测试事项", "", "2026-11-01", "2026-11-18",
                "引号\"反斜杠\\\n中文", true, cycle, if (cycle == Cycle.NONE) null else 18)
                .copy(id = 73, earlyNotifiedFor = LocalDate.of(2026, 11, 15),
                    dueNotifiedFor = LocalDate.of(2026, 11, 18))
            val content = BackupContent("2026-11-18T09:00:00Z",
                listOf(original.toBackupExpiryItem()), emptyList())

            val restored = decode(Json.encodeToString(BackupContent.serializer(), content))
                .expiryItems.single().toExpiryItemEntity()

            assertEquals(original.copy(id = 0, earlyNotifiedFor = null, dueNotifiedFor = null), restored)
        }
    }

    @Test
    fun periodicItemWithNoAnchorKeepsNullAnchorWhenRestored() {
        val content = decode("""
            {"exportedAt":"2026-11-18T09:00:00Z","expiryItems":[
              {"name":"订阅","category":"","startsOn":null,"validThrough":"2026-11-30",
               "notes":"","isPaused":false,"cycle":"MONTHLY","cycleAnchorDay":null}
            ],"cards":[]}
        """.trimIndent())

        assertEquals(expectedItem("订阅", "", null, "2026-11-30", "", false, Cycle.MONTHLY, null),
            content.expiryItems.single().toExpiryItemEntity())
    }

    @Test
    fun literalCardBackupPreservesSevenFieldsLeadingZerosAndEmptyStrings() {
        val content = decode("""
            {"exportedAt":"2026-11-18T09:00:00Z","expiryItems":[],"cards":[
              {"name":"测试卡","issuer":"虚构发卡行","number":"00001234","expMonth":"01",
               "expYear":"2030","cvv":"007","notes":"引号\"和换行\n🔑"},
              {"name":"","issuer":"","number":"","expMonth":"","expYear":"","cvv":"","notes":""}
            ]}
        """.trimIndent())

        assertEquals(listOf(
            CardPayload("测试卡", "虚构发卡行", "00001234", "01", "2030", "007", "引号\"和换行\n🔑"),
            CardPayload("", "", "", "", "", "", ""),
        ), content.cards.map { it.toCardPayload() })
        assertEquals(content.cards, content.cards.map { it.toCardPayload().toBackupCard() })
    }

    @Test
    fun itemsOnlyAndEmptyBackupsRemainValidWithoutVaultData() {
        val empty = decode("""{"exportedAt":"2026-11-18T09:00:00Z","expiryItems":[],"cards":[]}""")
        assertEquals(BackupContent("2026-11-18T09:00:00Z", emptyList(), emptyList()), empty)

        val item = expectedItem("仅恢复事项", "", null, "2026-11-18", "", false, Cycle.NONE, null)
        val itemsOnly = BackupContent("2026-11-18T09:00:00Z", listOf(item.toBackupExpiryItem()), emptyList())
        val restored = decode(Json.encodeToString(BackupContent.serializer(), itemsOnly))
        assertEquals(emptyList<BackupCard>(), restored.cards)
        assertEquals(listOf(item), restored.expiryItems.map { it.toExpiryItemEntity() })
    }

    private fun decode(json: String): BackupContent = Json.decodeFromString(BackupContent.serializer(), json)

    private fun expectedItem(
        name: String,
        category: String,
        startsOn: String?,
        validThrough: String,
        notes: String,
        isPaused: Boolean,
        cycle: Cycle,
        anchor: Int?,
    ) = ExpiryItemEntity(
        name = name,
        category = category,
        validity = Validity(startsOn?.let(LocalDate::parse), LocalDate.parse(validThrough)),
        notes = notes,
        isPaused = isPaused,
        cycle = cycle,
        cycleAnchorDay = anchor,
        earlyNotifiedFor = null,
        dueNotifiedFor = null,
    )
}
