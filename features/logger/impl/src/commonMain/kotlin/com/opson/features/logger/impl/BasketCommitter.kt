package com.opson.features.logger.impl

import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.LoggedToMeal
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.RecentLogEvents
import com.opson.libraries.foods.FoodCatalog
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock

/**
 * What a commit wrote. How it felt no longer follows an add (design sync 2, 3.10): it opens only
 * from a meal card on Today.
 */
data class Committed(val slot: MealSlot, val itemIds: List<String>)

/**
 * The one place a basket becomes diary items, shared by the logger sheet and
 * the amount sheet's "Add to {meal}". One `addItems` write, then the time of any item given one
 * on the amount sheet; the picks' hunger
 * and fullness merged into the meal's feeling; catalog foods cached for the
 * next offline search; one telemetry event per source; the basket cleared; and
 * [RecentLogEvents] told, so Today can scroll, outline and offer Undo.
 */
@Inject
class BasketCommitter(
    private val diary: DiaryRepository,
    private val foodCatalog: FoodCatalog,
    private val basketStore: BasketStore,
    private val recentLogEvents: RecentLogEvents,
    private val telemetry: LoggerTelemetry,
    private val clock: Clock,
) {
    /** Null when the basket is empty. */
    suspend fun commit(day: LocalDate, slot: MealSlot): Committed? {
        val basket = basketStore.basket.value.ifEmpty { return null }
        val written = diary.addItems(day, slot, basket.map { it.draft }, LogMethod.Search)
        // A time picked on the amount sheet: the item is written now, then given that time.
        val items = written.zip(basket) { item, picked ->
            val at = picked.loggedAt ?: return@zip item
            item.copy(loggedAt = at).also { diary.updateItem(it) }
        }

        val hunger = basket.firstNotNullOfOrNull { it.hunger }
        val fullness = basket.firstNotNullOfOrNull { it.fullness }
        if (hunger != null || fullness != null) {
            val existing = diary.observeDay(day).first().feelings[slot] ?: MealFeeling()
            diary.setFeeling(day, slot, existing.copy(hunger = hunger ?: existing.hunger, fullness = fullness ?: existing.fullness))
        }
        // Foods picked from the diary were cached when they were first logged; only fresh ones need it.
        basket.mapNotNull { it.food }.forEach { foodCatalog.markUsed(it) }

        basket.groupingBy { it.source }.eachCount().forEach { (source, count) -> telemetry.methodPicked(LogVia.of(source), count) }
        basketStore.clear()
        return announce(day, slot, items)
    }

    /** Tells Today about items written some other way (yesterday's copy, a quick number). */
    fun announce(day: LocalDate, slot: MealSlot, items: List<LogItem>): Committed {
        val ids = items.map { it.id }
        val written = items.firstOrNull()?.slot ?: slot
        if (ids.isNotEmpty()) recentLogEvents.logged(LoggedToMeal(day, written, ids, clock.now()))
        return Committed(written, ids)
    }
}
