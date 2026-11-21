package com.personal.cardvault.data

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.personal.cardvault.expiry.Cycle
import com.personal.cardvault.expiry.Validity
import java.time.LocalDate

@Entity(tableName = "expiry_items")
data class ExpiryItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val category: String,
    @Embedded val validity: Validity,
    @ColumnInfo(name = "notes") val notes: String,
    @ColumnInfo(name = "is_paused") val isPaused: Boolean,
    val cycle: Cycle,
    @ColumnInfo(name = "cycle_anchor_day") val cycleAnchorDay: Int?,
    @ColumnInfo(name = "early_notified_for") val earlyNotifiedFor: LocalDate?,
    @ColumnInfo(name = "due_notified_for") val dueNotifiedFor: LocalDate?,
)
