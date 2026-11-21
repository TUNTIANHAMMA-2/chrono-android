package com.personal.cardvault.expiry

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.personal.cardvault.data.ExpiryItemEntity
import com.personal.cardvault.data.expiryDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class ExpiryEditState(
    val id: Int? = null,
    val name: String = "",
    val category: String = "",
    val startsOn: String = "",
    val validThrough: String = "",
    val cycle: Cycle = Cycle.NONE,
    val notes: String = "",
)

/** State and persistence wiring for creating and modifying expiry items. */
class ExpiryEditViewModel(application: Application) : AndroidViewModel(application) {
    private val database = expiryDatabase(application)

    private val mutableState = MutableStateFlow(ExpiryEditState())
    val state = mutableState.asStateFlow()

    fun beginCreate() {
        val today = LocalDate.now()
        mutableState.value = ExpiryEditState(
            startsOn = today.toString(),
            validThrough = today.plusDays(1).toString(),
        )
    }

    fun load(id: Int, onLoaded: (Boolean) -> Unit = {}) {
        viewModelScope.launch(Dispatchers.IO) {
            val item = database.expiryItemDao().findById(id)
            mutableState.value = item?.let {
                ExpiryEditState(
                    id = it.id,
                    name = it.name,
                    category = it.category,
                    startsOn = it.validity.startsOn?.toString().orEmpty(),
                    validThrough = it.validity.validThrough.toString(),
                    cycle = it.cycle,
                    notes = it.notes,
                )
            } ?: ExpiryEditState()
            withContext(Dispatchers.Main) { onLoaded(item != null) }
        }
    }

    fun updateName(value: String) = updateState { copy(name = value) }
    fun updateCategory(value: String) = updateState { copy(category = value) }
    fun updateStartsOn(value: String) = updateState { copy(startsOn = value) }
    fun updateValidThrough(value: String) = updateState { copy(validThrough = value) }
    fun updateCycle(value: String) = updateState { copy(cycle = Cycle.valueOf(value)) }
    fun updateNotes(value: String) = updateState { copy(notes = value) }

    fun save(onSaved: () -> Unit) {
        val editor = state.value
        val trimmedName = editor.name.trim()
        if (trimmedName.isEmpty()) return
        val itemStartsOn = editor.startsOn.trim().ifBlank { null }?.let {
            runCatching { LocalDate.parse(it) }.getOrNull()
        }
        val itemValidThrough = runCatching { LocalDate.parse(editor.validThrough.trim()) }.getOrNull()
            ?: return

        viewModelScope.launch(Dispatchers.IO) {
            if (editor.id == null) {
                val insertedId = database.expiryItemDao().insert(
                    ExpiryItemEntity(
                        name = trimmedName,
                        category = editor.category.trim(),
                        validity = Validity(itemStartsOn, itemValidThrough),
                        notes = editor.notes.trim(),
                        isPaused = false,
                        cycle = editor.cycle,
                        cycleAnchorDay = cycleAnchorDayForSave(null, null, null, itemValidThrough, editor.cycle),
                        earlyNotifiedFor = null,
                        dueNotifiedFor = null,
                    ),
                ).toInt()
                rerunExpiryReminderForItem(getApplication(), database, insertedId)
            } else {
                val previous = database.expiryItemDao().findById(editor.id) ?: return@launch
                database.expiryItemDao().update(
                    id = editor.id,
                    name = trimmedName,
                    category = editor.category.trim(),
                    startsOn = itemStartsOn,
                    validThrough = itemValidThrough,
                    notes = editor.notes.trim(),
                    cycle = editor.cycle,
                    cycleAnchorDay = cycleAnchorDayForSave(
                        previous.validity.validThrough,
                        previous.cycle,
                        previous.cycleAnchorDay,
                        itemValidThrough,
                        editor.cycle,
                    ),
                )
                rerunExpiryReminderForItem(getApplication(), database, editor.id)
            }
            withContext(Dispatchers.Main) { onSaved() }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        val id = state.value.id ?: return
        viewModelScope.launch(Dispatchers.IO) {
            database.expiryItemDao().deleteById(id)
            rerunExpiryReminderForItem(getApplication(), database, id)
            withContext(Dispatchers.Main) { onDeleted() }
        }
    }

    private fun updateState(transform: ExpiryEditState.() -> ExpiryEditState) {
        mutableState.update(transform)
    }
}
