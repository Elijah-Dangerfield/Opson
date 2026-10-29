package com.opson.libraries.widgets

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/**
 * The URLs a widget opens. Every one starts `opson://widget/`, so the app hands it to [WidgetOpens]
 * before the nav graph, and only a widget sends them.
 */
object WidgetLinks {
    /** The +. No slot in it: [NextMeal] picks one when it's tapped. */
    const val LOG = "opson://widget/log"

    /** The rest of a widget: Today, on today. */
    const val TODAY = "opson://widget/today"

    /** The water widget's add: a glass on today, then Today. */
    const val WATER_ADD = "opson://widget/water/add"

    private const val SLOT = "slot"

    /** The log link for one meal, from a row on the meals widget; [LOG] itself for null. */
    fun log(slot: MealSlot?): String = if (slot == null) LOG else "$LOG?$SLOT=${slot.wire}"

    /** Whether [url] is the widget's log link, whatever query or trailing slash it picked up. */
    fun isLog(url: String): Boolean = path(url) == LOG

    /** What [url] asks for, or null when it is not a widget's link. */
    fun parse(url: String): WidgetLink? = when (path(url)) {
        LOG -> WidgetLink.Log(query(url)[SLOT]?.let(MealSlot::fromWire))
        TODAY -> WidgetLink.Today
        WATER_ADD -> WidgetLink.AddWater
        else -> null
    }

    private fun path(url: String): String = url.substringBefore('#').substringBefore('?').trimEnd('/')

    private fun query(url: String): Map<String, String> =
        url.substringBefore('#').substringAfter('?', missingDelimiterValue = "")
            .split('&')
            .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()
}

/** A widget link, parsed. */
sealed interface WidgetLink {
    /** The Logger, on [slot] when a meals widget row asked for one. */
    data class Log(val slot: MealSlot?) : WidgetLink

    data object Today : WidgetLink

    data object AddWater : WidgetLink
}

/**
 * The meal a widget's + logs to, decided at the tap: the first meal not done yet, starting from
 * the one the time of day suggests. Null leaves it to the Logger's own default (the time of day,
 * or Uncategorized with meal groups off), which is also the answer once the rest of the day's meals
 * are done.
 *
 * At the tap rather than in the snapshot, because a slot written at 9 am is wrong at 7 pm.
 */
object NextMeal {
    fun of(day: Day, settings: Settings, now: LocalTime): MealSlot? {
        if (!day.mealGroupsOn(settings)) return null
        val from = MealSlot.meals.indexOf(MealSlot.defaultFor(now)).coerceAtLeast(0)
        return MealSlot.meals.drop(from).firstOrNull { !day.isMealDone(it) }
    }
}

/** Where a widget tap takes the app, for App.kt to route. */
sealed interface WidgetOpen

/** A widget asked for the Logger on [day], in [slot] (null: the Logger's default). */
data class WidgetLogOpen(val slot: MealSlot?, val day: LocalDate) : WidgetOpen

/** A widget asked for Today, already on today. */
data object WidgetTodayOpen : WidgetOpen

/** Taps on a widget, which the app routes. */
interface WidgetOpens {
    /** Where a widget's tap asked to go, for App.kt to route. */
    val requests: Flow<WidgetOpen>

    /**
     * Hands a URL the app was opened with to the widgets. True when it was a widget's, which is
     * then handled and should go no further; false for anything else.
     */
    fun opened(url: String): Boolean
}

/** The current snapshot, rebuilt whenever today, the settings, the mode or the date move. */
interface WidgetSnapshotSource {
    fun observe(): Flow<WidgetSnapshot>
}

/**
 * Where a platform's widgets read the snapshot, and the nudge to redraw them. Android keeps it in
 * SharedPreferences and updates the Glance widget; iOS writes the App Group and reloads WidgetKit.
 */
interface WidgetPublisher {
    suspend fun publish(snapshot: WidgetSnapshot)

    /** Takes the snapshot away, so a widget falls back to "Open Opson". */
    suspend fun clear()
}
