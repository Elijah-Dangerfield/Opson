package com.opson.libraries.widgets

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Everything a home screen widget draws, already decided. The visibility policy ran in
 * [WidgetSnapshotBuilder]; a widget renders these strings, fractions and colours and nothing else.
 * A number the mode hides is absent, never masked, so no platform code can show it.
 *
 * Written as JSON ([WidgetJson]) to where each platform's widget reads it: SharedPreferences on
 * Android, the App Group's UserDefaults on iOS. The Swift extension decodes the same shape
 * (`apps/ios/OpsonWidgets/WidgetSnapshot.swift`, on `hold/0.2-widgets-native` until the widget
 * ships); change both together and bump [version].
 *
 * @property days today, then tomorrow as an empty day, so the widget turns over at midnight while
 * the app is not running. [dayFor] picks one.
 * @property logLabel what a screen reader says for the + button.
 * @property logLink the URL the + opens ([WidgetLinks.LOG]).
 */
@Serializable
data class WidgetSnapshot(
    val version: Int,
    val days: List<WidgetDay>,
    val logLabel: String,
    val logLink: String,
    val colors: WidgetColors,
) {
    /**
     * The entry for [date]: its own when there is one, otherwise the last (an empty day, right for
     * any day after it while the app has not run) or, for a clock set back, the first.
     */
    fun dayFor(date: LocalDate): WidgetDay? {
        val iso = date.toString()
        return days.firstOrNull { it.day == iso }
            ?: days.lastOrNull()?.takeIf { iso > it.day }
            ?: days.firstOrNull()
    }
}

/**
 * One day on the widget. At most one of [energy], [meals] and [note] is drawn, in that order.
 *
 * @property day the ISO date this entry is for.
 * @property energy only when calories are shown in this mode.
 * @property meals the four meals, only when [energy] is null and meal groups are on.
 * @property note a line for when there is neither: meal groups off, or an untracked day.
 */
@Serializable
data class WidgetDay(
    val day: String,
    val title: String,
    val energy: WidgetEnergy? = null,
    val meals: List<WidgetMeal> = emptyList(),
    val note: String? = null,
)

/**
 * Calories eaten, as the numbers card draws it: [amount] large over [unit], then a bar.
 *
 * @property label the whole amount for a screen reader, "1,245 kcal".
 * @property fraction how far the bar fills, 0 to 1. The bar runs to 20% past the top of the range.
 * @property rangeStart where the range box starts on the bar, 0 to 1.
 * @property rangeEnd where it ends.
 */
@Serializable
data class WidgetEnergy(
    val amount: String,
    val unit: String,
    val label: String,
    val fraction: Float,
    val rangeStart: Float,
    val rangeEnd: Float,
)

/**
 * One meal circle: a tick when [done], a dashed ring when not.
 *
 * @property slot the meal's wire name, which picks its colours in [WidgetSwatch.meals].
 * @property description what a screen reader says, "Breakfast, done".
 */
@Serializable
data class WidgetMeal(
    val slot: String,
    val label: String,
    val done: Boolean,
    val description: String,
)

/** The app's colours in both appearances. The widget follows the system's. */
@Serializable
data class WidgetColors(
    val light: WidgetSwatch,
    val dark: WidgetSwatch,
)

/**
 * The theme tokens a widget uses, as `#RRGGBB`. From the app's own theme, so the widget's colours
 * live in one place.
 *
 * @property bar the eaten bar's fill. v3 draws it in ink.
 * @property meals by meal wire name.
 */
@Serializable
data class WidgetSwatch(
    val background: String,
    val ink: String,
    val muted: String,
    val track: String,
    val bar: String,
    val accent: String,
    val onAccent: String,
    val meals: Map<String, WidgetTone>,
)

/** A meal's tick or ring ([content]) and the circle behind a tick ([container]). */
@Serializable
data class WidgetTone(
    val content: String,
    val container: String,
)

/** The snapshot's JSON, for the platform stores. */
object WidgetJson {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(snapshot: WidgetSnapshot): String = json.encodeToString(WidgetSnapshot.serializer(), snapshot)

    /** Null for nothing stored, or for JSON from a version this build can't read. */
    fun decode(text: String?): WidgetSnapshot? {
        if (text.isNullOrBlank()) return null
        return try {
            json.decodeFromString(WidgetSnapshot.serializer(), text).takeIf { it.version == WidgetSnapshotBuilder.VERSION }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
