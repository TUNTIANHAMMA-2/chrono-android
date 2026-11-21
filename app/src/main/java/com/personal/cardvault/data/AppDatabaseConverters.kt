package com.personal.cardvault.data

import androidx.room.TypeConverter
import com.personal.cardvault.expiry.Cycle
import java.time.LocalDate

class AppDatabaseConverters {
    @TypeConverter
    fun localDateToText(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun textToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun cycleToText(value: Cycle?): String? = value?.name

    @TypeConverter
    fun textToCycle(value: String?): Cycle? = value?.let(Cycle::valueOf)
}
