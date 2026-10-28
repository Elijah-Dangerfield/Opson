package com.opson.libraries.widgets

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.ringValue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

/**
 * What the snapshot is built from: today's day and the person's settings, and today's Burned
 * ([burnedKcal], with `release.goalsAndEnergy`), which a set deficit or surplus aims at.
 */
data class WidgetInputs(
    val today: Day,
    val settings: Settings,
    val burnedKcal: Int? = null,
)

/**
 * The widget's state as a pure function of today and the policy (design v3 `widgets.png`), so the
 * app, the Widgets page, previews and the leak test all run the same mapping.
 *
 * The card follows the calories level on [Surface.Widget], as the handoff's `wg`: shown is the
 * numbers card (amount, bar, range box); anything else is the four meal circles. A softened level
 * gets the circles too, because a soft word on a home screen says nothing a tick doesn't, and a
 * delayed one never opens on a widget. Nothing here asks which mode is on.
 */
object WidgetSnapshotBuilder {
    /** The snapshot's shape. The Swift decoder checks it; bump both together. */
    const val VERSION = 1

    /** The bar runs to 20% past the top of the range, so the range box sits inside it, as Today's. */
    private const val BAR_HEADROOM = 1.2

    fun build(inputs: WidgetInputs, policy: VisibilityPolicy, copy: WidgetCopy): WidgetSnapshot {
        val settings = inputs.settings
        val tomorrow = Day.empty(inputs.today.date.plus(DatePeriod(days = 1)))
        return WidgetSnapshot(
            version = VERSION,
            days = listOf(inputs.today, tomorrow).map { day(it, settings, policy, copy, inputs.burnedKcal) },
            logLabel = copy.log,
            logLink = WidgetLinks.LOG,
            colors = WidgetPalette.colors,
        )
    }

    private fun day(day: Day, settings: Settings, policy: VisibilityPolicy, copy: WidgetCopy, burnedKcal: Int?): WidgetDay {
        // An untracked day has nothing to read it by, as on Today.
        val energy = if (day.untracked) null else energy(day, settings, policy, burnedKcal)
        val meals = if (energy == null && !day.untracked && day.mealGroupsOn(settings)) meals(day, copy) else emptyList()
        return WidgetDay(
            day = day.date.toString(),
            title = copy.today,
            energy = energy,
            meals = meals,
            note = when {
                energy != null || meals.isNotEmpty() -> null
                day.untracked -> copy.noteUntracked
                else -> copy.noteLog
            },
        )
    }

    /** Only when calories are shown: amount and unit as Today's ring, the bar as Today's hero. */
    private fun energy(day: Day, settings: Settings, policy: VisibilityPolicy, burnedKcal: Int?): WidgetEnergy? {
        val eaten = day.totals().kcal.known
        val context = FormatContext(day = day.date)
        val ring = policy.ringValue(Metric.Calories, eaten, Surface.Widget, context) ?: return null
        val label = policy.resolve(Metric.Calories, eaten, Surface.Widget, context) as? Display.Shown ?: return null
        // The day's own goal, so a "different by day" range draws as it does on Targets, and a set
        // deficit or surplus aims at today's Burned as Today's does.
        val range = settings.targets.rangeFor(day.date, settings.numbers.preset, burnedKcal)
        val scaleMax = range.maxKcal * BAR_HEADROOM
        val bar = policy.resolve(Metric.Calories, eaten, Surface.Widget, context.copy(of = scaleMax, bare = true)) as? Display.Shown
        return WidgetEnergy(
            amount = ring.value,
            unit = ring.unit,
            label = label.text,
            fraction = (bar?.fraction ?: 0f).coerceIn(0f, 1f),
            rangeStart = fractionOf(range.minKcal, scaleMax),
            rangeEnd = fractionOf(range.maxKcal, scaleMax),
        )
    }

    private fun fractionOf(kcal: Int, scaleMax: Double): Float =
        if (scaleMax <= 0.0) 0f else (kcal / scaleMax).toFloat().coerceIn(0f, 1f)

    private fun meals(day: Day, copy: WidgetCopy): List<WidgetMeal> = MealSlot.meals.map { slot ->
        val done = day.isMealDone(slot)
        val meal = copy.meal(slot)
        WidgetMeal(slot = slot.wire, label = meal.label, done = done, description = if (done) meal.done else meal.notYet)
    }
}
