package com.personal.cardvault.expiry

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.cardvault.data.ExpiryItemEntity
import com.personal.cardvault.data.expiryDatabase
import com.personal.cardvault.ui.components.ExpiryBadgeType
import com.personal.cardvault.ui.screens.expiry.ExpiryItemUiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class ExpiryHomeState(
    val expiredItems: List<ExpiryItemUiModel> = emptyList(),
    val dueSoonItems: List<ExpiryItemUiModel> = emptyList(),
    val activeItems: List<ExpiryItemUiModel> = emptyList(),
    val pausedItems: List<ExpiryItemUiModel> = emptyList(),
)

/** Maps persisted expiry items into the four home-page sections from section 6.2. */
class ExpiryHomeViewModel(application: Application) : AndroidViewModel(application) {
    private val database = expiryDatabase(application)
    private val todayFlow = MutableStateFlow(LocalDate.now())

    val state: StateFlow<ExpiryHomeState> = combine(
        database.expiryItemDao().observeAll(),
        todayFlow,
    ) { items, today ->
        partitionExpiryItems(items, today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpiryHomeState())

    /** 应用回到前台或跨午夜时刷新当天日期，确保倒计时与分区始终准确。 */
    fun refreshToday() {
        val now = LocalDate.now()
        if (todayFlow.value != now) {
            todayFlow.value = now
        }
    }

    fun renew(id: Int, onRenewed: ((itemName: String, newDate: LocalDate) -> Unit)? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = database.expiryItemDao().findById(id) ?: return@launch
            val newValidThrough = renewedValidThrough(
                validThrough = item.validity.validThrough,
                cycle = item.cycle,
                cycleAnchorDay = item.cycleAnchorDay,
                today = LocalDate.now(),
            ) ?: return@launch
            database.expiryItemDao().update(
                id = item.id,
                name = item.name,
                category = item.category,
                startsOn = item.validity.startsOn,
                validThrough = newValidThrough,
                notes = item.notes,
                cycle = item.cycle,
                cycleAnchorDay = item.cycleAnchorDay,
            )
            rerunExpiryReminderForItem(getApplication(), database, id)
            if (onRenewed != null) {
                withContext(Dispatchers.Main) { onRenewed(item.name, newValidThrough) }
            }
        }
    }

    fun setPaused(id: Int, isPaused: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            database.expiryItemDao().updatePaused(id, isPaused)
            rerunExpiryReminderForItem(getApplication(), database, id)
        }
    }
}

fun partitionExpiryItems(items: List<ExpiryItemEntity>, today: LocalDate): ExpiryHomeState {
    val expired = mutableListOf<ExpiryItemUiModel>()
    val dueSoon = mutableListOf<ExpiryItemUiModel>()
    val active = mutableListOf<ExpiryItemUiModel>()
    val paused = mutableListOf<ExpiryItemUiModel>()

    items.forEach { item ->
        val remaining = remainingDays(item.validity, today).toInt()
        val uiItem = item.toUiModel(remaining, today)
        when {
            item.isPaused -> paused += uiItem.copy(badgeType = ExpiryBadgeType.PAUSED)
            remaining < 0 -> expired += uiItem.copy(badgeType = ExpiryBadgeType.EXPIRED)
            remaining <= DUE_SOON_DAYS -> dueSoon += uiItem.copy(
                badgeType = if (remaining == 0) ExpiryBadgeType.DUE_TODAY else ExpiryBadgeType.DUE_SOON,
            )
            else -> active += uiItem
        }
    }

    return ExpiryHomeState(
        expiredItems = expired.sortedBy { it.validThrough },
        dueSoonItems = dueSoon.sortedBy { it.validThrough },
        activeItems = active.sortedBy { it.validThrough },
        pausedItems = paused.sortedBy { it.validThrough },
    )
}

private fun ExpiryItemEntity.toUiModel(remaining: Int, today: LocalDate): ExpiryItemUiModel =
    ExpiryItemUiModel(
        id = id,
        name = name,
        category = category,
        startsOn = validity.startsOn?.toString(),
        validThrough = validity.validThrough.toString(),
        remainingDays = remaining,
        badgeType = when (validityStatus(validity, today)) {
            ValidityStatus.NOT_STARTED -> ExpiryBadgeType.NOT_STARTED
            else -> ExpiryBadgeType.ACTIVE
        },
        isPaused = isPaused,
        cycleText = when (cycle) {
            Cycle.NONE -> ""
            Cycle.MONTHLY -> "按月"
            Cycle.YEARLY -> "按年"
        },
    )

const val DUE_SOON_DAYS = 7
