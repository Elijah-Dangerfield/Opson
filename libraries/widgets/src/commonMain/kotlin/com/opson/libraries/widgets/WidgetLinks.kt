package com.opson.libraries.widgets

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime

/** The URL a widget's + opens. No slot in it: [NextMeal] picks one when it's tapped. */
object WidgetLinks {
    const val LOG = "opson://widget/log"

    /** Whether [url] is the widget's log link, whatever query or trailing slash it picked up. */
    fun isLog(url: String): Boolean = url.substringBefore('#').substringBefore('?').trimEnd('/') == LOG
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

/** A widget asked for the Logger on [day], in [slot] (null: the Logger's default). */
data class WidgetLogOpen(val slot: MealSlot?, val day: LocalDate)

/** Taps on a widget, which the app routes. */
interface WidgetOpens {
    /** Logger opens asked for by a widget's +, for App.kt to route. */
    val requests: Flow<WidgetLogOpen>

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
