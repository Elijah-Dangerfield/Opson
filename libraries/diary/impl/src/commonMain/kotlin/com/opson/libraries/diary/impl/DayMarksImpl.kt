package com.opson.libraries.diary.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.diary.DayMark
import com.opson.libraries.diary.DayMarks
import com.opson.libraries.diary.db.DayRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * [DayMarks] from two cheap reads: the dates with an entry the person logged themselves (one
 * `DISTINCT` column; a repeat's foods are not showing up) and the day records, which hold the
 * wrap-up, the untracked mark, "ate, no details", water and the check-in. No food row is read, so
 * a long history costs a few thousand short rows.
 */
@ContributesBinding(AppScope::class)
@Inject
class DayMarksImpl(private val tables: DiaryTables) : DayMarks {

    override fun observe(): Flow<List<DayMark>> = combine(
        tables.entries.observeDaysLoggedByHand(),
        tables.days.observeRange(EARLIEST, LATEST),
    ) { withEntries, records -> marks(withEntries, records) }.distinctUntilChanged()

    internal companion object {
        // Day keys are ISO dates, so these bound every date as plain strings compare.
        const val EARLIEST = "0000-01-01"
        const val LATEST = "9999-12-31"

        /** Water counts as logging: a glass is something the person came back to log. */
        fun marks(withEntries: List<String>, records: List<DayRecordEntity>): List<DayMark> {
            val logged = withEntries.toSet()
            val byDay = records.associateBy { it.day }
            return (logged + byDay.keys).mapNotNull { key ->
                val date = Catching { LocalDate.parse(key) }.getOrNull() ?: return@mapNotNull null
                val record = byDay[key]
                DayMark(
                    date = date,
                    logged = key in logged || record?.ateNoDetailsSlots()?.isNotEmpty() == true || (record?.waterMl ?: 0) > 0,
                    closed = record?.closedAtEpochMs != null,
                    untracked = record?.untracked == true,
                    checkedIn = record?.checkIn.toCheckIn()?.isEmpty == false,
                )
            }.filter { it.logged || it.closed || it.untracked || it.checkedIn }.sortedBy { it.date }
        }
    }
}
