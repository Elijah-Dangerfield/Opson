package com.opson.features.logger.impl

import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.RecentLogEvents
import com.opson.libraries.foods.FoodCatalog
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

/**
 * What a log wrote. [event] is what [RecentLogEvents] was told, so the screen that logged can
 * offer its own Undo for exactly these items; null when nothing was written.
 */
data class Committed(val slot: MealSlot, val itemIds: List<String>, val event: LoggedToMeal? = null)

/**
 * The one place picks become diary items: search's +, the sheet's Recent, Describe, a saved
 * meal, the photo estimate and the food detail all log through it. One `addItems` write, then
 * the time of any item the food detail gave one; catalog foods cached for the next offline
 * search; one telemetry event per source; and [RecentLogEvents] told, so Today can scroll,
 * outline and offer Undo.
 */
@Inject
class FoodLogger(
    private val diary: DiaryRepository,
    private val foodCatalog: FoodCatalog,
    private val recentLogEvents: RecentLogEvents,
    private val telemetry: LoggerTelemetry,
    private val clock: Clock,
) {
    /** Null when [picks] is empty. */
    suspend fun log(day: LocalDate, slot: MealSlot, picks: List<LogPick>): Committed? {
        if (picks.isEmpty()) return null
        val written = diary.addItems(day, slot, picks.map { it.draft }, LogMethod.Search)
        // A time picked on the food detail: the item is written now, then given that time.
        val items = written.zip(picks) { item, pick ->
            val at = pick.loggedAt ?: return@zip item
            item.copy(loggedAt = at).also { diary.updateItem(it) }
        }

        // Foods picked from the diary were cached when they were first logged; only fresh ones need it.
        picks.mapNotNull { it.food }.forEach { foodCatalog.markUsed(it) }

        picks.groupingBy { it.source }.eachCount().forEach { (source, count) -> telemetry.methodPicked(LogVia.of(source), count) }
        return announce(day, slot, items)
    }

    /** Tells Today about items written some other way (yesterday's copy, a quick number). */
    fun announce(day: LocalDate, slot: MealSlot, items: List<LogItem>): Committed {
        val ids = items.map { it.id }
        val written = items.firstOrNull()?.slot ?: slot
        val event = if (ids.isEmpty()) null else LoggedToMeal(day, written, ids, clock.now())
        event?.let(recentLogEvents::logged)
        return Committed(written, ids, event)
    }
}
