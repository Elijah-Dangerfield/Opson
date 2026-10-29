package com.opson.libraries.widgets

import kotlinx.datetime.LocalDate
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Everything a home screen or Lock Screen widget draws, already decided. The visibility policy ran
 * in [WidgetSnapshotBuilder]; a widget renders these strings, fractions and colours and nothing
 * else. A number the mode hides is absent, never masked, so no platform code can show it.
 *
 * Written as JSON ([WidgetJson]) to where each platform's widget reads it: SharedPreferences on
 * Android, a file in the App Group container on iOS. The Swift extension decodes the same shape
 * (`apps/ios/OpsonWidgets/WidgetSnapshot.swift`); change both together.
 *
 * **Growing it.** [version] changes only for a change an older reader can't survive (a renamed or
 * retyped field). Anything new is added with a default here and as an optional on the Swift side,
 * and [version] stays: the extension that ships with an update reads the JSON the previous app
 * wrote until the new app runs, and an older reader skips keys it doesn't know.
 * `apps/ios/TestFixtures/widgets/snapshot.json` is this shape as the app writes it, checked on
 * both sides (`WidgetSnapshotFixtureTest`, `scripts/check_widget_snapshot.sh`).
 *
 * @property days today, then tomorrow as an empty day, so the widget turns over at midnight while
 * the app is not running. [dayFor] picks one.
 * @property logLabel what a screen reader says for the + button.
 * @property logLink the URL the + opens ([WidgetLinks.LOG]).
 * @property todayLink the URL the rest of a widget opens ([WidgetLinks.TODAY]).
 * @property waterLink the URL the water widget's add opens ([WidgetLinks.WATER_ADD]).
 * @property fasting the fasting timer, only while it is on and fasting is offered in the mode.
 * @property labels the widgets' own words: titles, and what a widget says when the mode keeps its
 * numbers in the app.
 */
@Serializable
data class WidgetSnapshot(
    val version: Int,
    val days: List<WidgetDay>,
    val logLabel: String,
    val logLink: String,
    val colors: WidgetColors,
    val todayLink: String = WidgetLinks.TODAY,
    val waterLink: String = WidgetLinks.WATER_ADD,
    val fasting: WidgetFasting? = null,
    val labels: WidgetLabels = WidgetLabels(),
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
 * One day on the widget. The Today widget draws at most one of [energy], [meals] and [note], in
 * that order.
 *
 * @property day the ISO date this entry is for.
 * @property energy only when calories are shown in this mode.
 * @property meals the four meals, only when [energy] is null and meal groups are on.
 * @property note a line for when there is neither: meal groups off, or an untracked day.
 *
 * The rest is for the other widgets, each present only as far as the mode shows it:
 * @property left what is left to the range, only when the mode shows that number in full.
 * @property progress how many meals are done, the neutral figure every mode has. Null with meal
 * groups off or on an untracked day.
 * @property macros the macros the mode shows in full, in the app's order.
 * @property water null when the mode hides water.
 * @property mealRows what is logged, meal by meal, for the meals widget. Empty on an untracked day.
 */
@Serializable
data class WidgetDay(
    val day: String,
    val title: String,
    val energy: WidgetEnergy? = null,
    val meals: List<WidgetMeal> = emptyList(),
    val note: String? = null,
    val left: WidgetFigure? = null,
    val progress: WidgetProgress? = null,
    val macros: List<WidgetMacro> = emptyList(),
    val water: WidgetWater? = null,
    val mealRows: List<WidgetMealRow> = emptyList(),
)

/**
 * Calories eaten, as the numbers card draws it: [amount] large over [unit], then a bar.
 *
 * @property label the whole amount for a screen reader, "1,245 kcal".
 * @property fraction how far the bar fills, 0 to 1. The bar runs to 20% past the top of the range.
 * @property rangeStart where the range box starts on the bar, 0 to 1.
 * @property rangeEnd where it ends. Equal to [rangeStart] when the goal is one number, drawn as a mark.
 * @property ring how far a ring fills, 0 to 1: eaten over the top of the range, so a full ring is
 * the top of the range and goes no further.
 * @property ringRangeStart where the range starts on that ring, 0 to 1.
 * @property line [amount] and [unit] on one line, "1,245 kcal", in the order the language puts them.
 */
@Serializable
data class WidgetEnergy(
    val amount: String,
    val unit: String,
    val label: String,
    val fraction: Float,
    val rangeStart: Float,
    val rangeEnd: Float,
    val ring: Float = 0f,
    val ringRangeStart: Float = 0f,
    val line: String? = null,
)

/**
 * A figure for a ring or a line: [amount] large over [unit], and [label] whole, for a screen
 * reader and the inline Lock Screen widget. "540" over "to range", or "On" over "target". [line]
 * is [amount] and [unit] on one line, "540 to range".
 */
@Serializable
data class WidgetFigure(
    val amount: String,
    val unit: String,
    val label: String,
    val line: String? = null,
)

/** Meals done today, "2 of 4 meals". A count, not a nutrition number, so every mode has it. */
@Serializable
data class WidgetProgress(
    val done: Int,
    val total: Int,
    val label: String,
)

/**
 * One macro the mode shows in full.
 *
 * @property key `protein`, `carbs`, `fat` or `fiber`, which picks its colour in [WidgetSwatch.nutrients].
 * @property amount "82", over [unit] "g".
 * @property text against its target, "82 / 120 g".
 * @property fraction of the target, 0 to 1.
 * @property line [amount] and [unit] on one line, "82 g".
 * @property description what a screen reader says, "Protein, 82 / 120 g".
 */
@Serializable
data class WidgetMacro(
    val key: String,
    val label: String,
    val amount: String,
    val unit: String,
    val text: String,
    val fraction: Float,
    val line: String? = null,
    val description: String? = null,
)

/**
 * Today's water as the mode shows it.
 *
 * @property text "1,250 of 2,000 ml" when water is shown in full; null when it is softened, which
 * is the glasses alone, as on Today's card.
 * @property filled glasses drunk, capped at [total], as Today's segments.
 * @property label what a screen reader says: [text], or the glasses as words when softened.
 * @property glass the glass the add button adds, "250 ml". A setting, not an amount drunk.
 * @property addLabel what a screen reader says for the add button, "Add a glass, 250 ml".
 */
@Serializable
data class WidgetWater(
    val text: String?,
    val filled: Int,
    val total: Int,
    val label: String,
    val glass: String,
    val addLabel: String? = null,
)

/**
 * One meal on the meals widget.
 *
 * @property foods the names, "Oat porridge with milk, banana", or [WidgetLabels.nothingYet].
 * @property calories the meal's energy, only when the mode shows calories.
 * @property link opens the Logger on this meal.
 */
@Serializable
data class WidgetMealRow(
    val slot: String,
    val label: String,
    val done: Boolean,
    val foods: String,
    val calories: String? = null,
    val link: String,
)

/**
 * The fasting timer as phases the widget steps through on its own, so it stays right with the app
 * closed: each starts at [WidgetFastingPhase.fromMs], and the widget shows the last one that has
 * started. [FastingPhases] works them out.
 */
@Serializable
data class WidgetFasting(
    val plan: String,
    val phases: List<WidgetFastingPhase>,
)

/**
 * @property kind `fasting` (a timer from [sinceMs], ending at [untilMs]), `window` (the eating
 * window, open until [untilMs]) or `done` (the fast is over; the next starts at [untilMs]).
 * @property title "Fasting", "Eating window" or "Fast done".
 * @property caption the words before the time the widget prints: "Ends at", "Until", "Next fast at".
 * Kept for an extension from before [captionBefore] and [captionAfter], which print it, a space and
 * the time.
 * @property captionBefore the line's words before the time, "Ends at ", and [captionAfter] those
 * after it, so a language can put the time anywhere in the line.
 * Times are epoch milliseconds, printed in the device's own clock style.
 */
@Serializable
data class WidgetFastingPhase(
    val fromMs: Long,
    val kind: String,
    val title: String,
    val caption: String = "",
    val untilMs: Long,
    val sinceMs: Long? = null,
    val captionBefore: String? = null,
    val captionAfter: String? = null,
) {
    companion object {
        const val FASTING = "fasting"
        const val WINDOW = "window"
        const val DONE = "done"
    }
}

/**
 * The widgets' titles, and the neutral lines for a widget whose numbers the mode keeps in the app.
 * The defaults are the English copy, for previews; the app writes `strings_widgets.xml`'s.
 */
@Serializable
data class WidgetLabels(
    val calories: String = "Calories",
    val macros: String = "Macros",
    val water: String = "Water",
    val meals: String = "Meals",
    val fasting: String = "Fasting",
    val addGlass: String = "Add a glass",
    val macrosHidden: String = "Your mode keeps macros in the app.",
    val waterHidden: String = "Your mode keeps water in the app.",
    val fastingOff: String = "The fasting timer is off.",
    val nothingYet: String = "Nothing yet",
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
 * @property nutrients `protein`, `carbs`, `fat`, `fiber` and `water`, for the macros and water widgets.
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
    val nutrients: Map<String, WidgetTone> = emptyMap(),
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
