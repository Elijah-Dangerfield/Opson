package com.opson.features.today.impl

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.ui.time.DateCopy
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus

/**
 * What the Explorer layout shows: the foods someone keeps coming back to, and
 * the ones they tried for the first time this month. Names only, never an
 * amount.
 */
data class ExplorerStats(
    val safeFoods: List<String>,
    /** Newest first. */
    val newFoods: List<NewFoodSeen>,
)

data class NewFoodSeen(val name: String, val firstLogged: LocalDate)

object ExplorerStatsBuilder {
    /** How far back a food counts as "had before". A longer window costs a bigger scan for little. */
    const val LOOKBACK_DAYS = 180

    /** Six safe foods, as the prototype shows. */
    const val SAFE_FOOD_COUNT = 6

    /**
     * [frequent] is `DiaryRepository.frequentFoods`, most logged first. [days]
     * covers the last [LOOKBACK_DAYS] up to [today]; a food is new when its first
     * entry in that window is in [today]'s month.
     */
    fun from(frequent: List<LogItem>, days: List<Day>, today: LocalDate): ExplorerStats {
        val monthStart = LocalDate(today.year, today.month, 1)
        val firstSeen = LinkedHashMap<String, LogItem>()
        days.sortedBy { it.date }.forEach { day ->
            day.allItems.sortedBy { it.loggedAt }.forEach { item -> firstSeen.getOrPut(item.foodKey()) { item } }
        }
        val newFoods = firstSeen.values
            .filter { it.day >= monthStart && it.day <= today }
            .sortedByDescending { it.loggedAt }
            .map { NewFoodSeen(name = shortName(it.name), firstLogged = it.day) }
        return ExplorerStats(
            safeFoods = frequent.map { shortName(it.name) }.distinct().take(SAFE_FOOD_COUNT),
            newFoods = newFoods,
        )
    }

    /** "Today", "Yesterday", a weekday within the week ("Sat"), or "Oct 20". */
    fun seenLabel(date: LocalDate, today: LocalDate): String = when {
        date == today -> DateCopy.label(RelativeDay.Today)
        date == today.minus(DatePeriod(days = 1)) -> DateCopy.label(RelativeDay.Yesterday)
        date > today.minus(DatePeriod(days = WEEK_DAYS)) -> DateCopy.shortWeekday(date.dayOfWeek)
        else -> DateCopy.monthDay(date)
    }

    private const val WEEK_DAYS = 7

    /** The same food however it was logged: its catalog or custom id, else its name. */
    private fun LogItem.foodKey(): String = foodId?.let { "f:$it" } ?: customFoodId?.let { "c:$it" } ?: "n:${name.trim().lowercase()}"

    /** "Greek yogurt, plain" reads "Greek yogurt" in a chip. */
    internal fun shortName(name: String): String {
        val cut = name.indexOfFirst { it == ',' || it == '(' }
        return (if (cut > 0) name.substring(0, cut) else name).trim().ifEmpty { name.trim() }
    }
}
