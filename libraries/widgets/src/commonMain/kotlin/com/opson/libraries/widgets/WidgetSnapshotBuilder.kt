package com.opson.libraries.widgets

import com.opson.libraries.core.fillTemplate
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.totals
import com.opson.libraries.nutrition.DayGoal
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WaterProgress
import com.opson.libraries.nutrition.ringValue
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus

/**
 * What the snapshot is built from: today's day and the person's settings, today's Burned
 * ([burnedKcal], with `release.goalsAndEnergy`), which a set deficit or surplus aims at, and the
 * fasting timer's state ([fasting]), only while the timer is on and fasting is offered.
 */
data class WidgetInputs(
    val today: Day,
    val settings: Settings,
    val burnedKcal: Int? = null,
    val fasting: FastingInputs? = null,
)

/**
 * The widgets' state as a pure function of today and the policy (design v3 `widgets.png`), so the
 * app, the Widgets page, previews and the leak test all run the same mapping.
 *
 * The Today widget follows the calories level on [Surface.Widget], as the handoff's `wg`: shown is
 * the numbers card (amount, bar, range box); anything else is the four meal circles. A softened
 * level gets the circles too, because a soft word on a home screen says nothing a tick doesn't,
 * and a delayed one never opens on a widget. The other widgets follow the same rule for their own
 * numbers: a figure is in the snapshot only when its level on [Surface.Widget] is Show, and a
 * widget without its figure draws a neutral state (meals done, or a line saying the mode keeps it
 * in the app). Water is the exception, as on Today: softened water is the glasses with no amount.
 * Nothing here asks which mode is on.
 */
object WidgetSnapshotBuilder {
    /**
     * The snapshot's breaking version. The Swift decoder checks it. New fields don't bump it (see
     * [WidgetSnapshot]); a rename or a retype does, on both sides together.
     */
    const val VERSION = 1

    /** The bar runs to 20% past the top of the range, so the range box sits inside it, as Today's. */
    private const val BAR_HEADROOM = 1.2

    private val MACROS = listOf(Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber)

    fun build(inputs: WidgetInputs, policy: VisibilityPolicy, copy: WidgetCopy): WidgetSnapshot {
        val settings = inputs.settings
        val tomorrow = Day.empty(inputs.today.date.plus(DatePeriod(days = 1)))
        return WidgetSnapshot(
            version = VERSION,
            days = listOf(inputs.today, tomorrow).map { day(it, settings, policy, copy, inputs.burnedKcal) },
            logLabel = copy.log,
            logLink = WidgetLinks.LOG,
            colors = WidgetPalette.colors,
            todayLink = WidgetLinks.TODAY,
            waterLink = WidgetLinks.WATER_ADD,
            fasting = inputs.fasting?.let { WidgetFasting(plan = it.plan.key, phases = FastingPhases.build(it, copy.fasting)) },
            labels = copy.labels,
        )
    }

    private fun day(day: Day, settings: Settings, policy: VisibilityPolicy, copy: WidgetCopy, burnedKcal: Int?): WidgetDay {
        val context = FormatContext(day = day.date)
        val goal = settings.targets.rangeFor(day.date, settings.numbers.preset, burnedKcal)
        // An untracked day has nothing to read it by, as on Today.
        val energy = if (day.untracked) null else energy(day, goal, policy, context, copy)
        val groupsOn = day.mealGroupsOn(settings)
        val meals = if (energy == null && !day.untracked && groupsOn) meals(day, copy) else emptyList()
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
            left = energy?.let { left(day, goal, policy, context, copy) },
            progress = if (day.untracked || !groupsOn) null else progress(day, copy),
            macros = if (day.untracked) emptyList() else macros(day, settings, policy, context, copy),
            water = if (day.untracked) null else water(day, settings, policy, context, copy),
            mealRows = if (day.untracked) emptyList() else mealRows(day, groupsOn, policy, context, copy),
        )
    }

    /** Only when calories are shown: amount and unit as Today's ring, the bar as Today's hero. */
    private fun energy(day: Day, goal: DayGoal, policy: VisibilityPolicy, context: FormatContext, copy: WidgetCopy): WidgetEnergy? {
        val eaten = day.totals().kcal.known
        val ring = policy.ringValue(Metric.Calories, eaten, Surface.Widget, context) ?: return null
        val label = policy.resolve(Metric.Calories, eaten, Surface.Widget, context) as? Display.Shown ?: return null
        // The day's own goal, so a "different by day" range draws as it does on Targets, and a set
        // deficit or surplus aims at today's Burned as Today's does.
        val scaleMax = goal.maxKcal * BAR_HEADROOM
        val bar = policy.resolve(Metric.Calories, eaten, Surface.Widget, context.copy(of = scaleMax, bare = true)) as? Display.Shown
        return WidgetEnergy(
            amount = ring.value,
            unit = ring.unit,
            label = label.text,
            fraction = (bar?.fraction ?: 0f).coerceIn(0f, 1f),
            rangeStart = fractionOf(goal.minKcal.toDouble(), scaleMax),
            rangeEnd = fractionOf(goal.maxKcal.toDouble(), scaleMax),
            ring = fractionOf(eaten, goal.maxKcal.toDouble()),
            ringRangeStart = fractionOf(goal.minKcal.toDouble(), goal.maxKcal.toDouble()),
            line = copy.amountUnit.fillTemplate(ring.value, ring.unit),
        )
    }

    /**
     * What is left to the range, as Today's "To range" stat words it, and only where the mode shows
     * that number in full. Below the range it is the kcal still to the bottom of it; inside or past
     * it, "On target" or "Past range", never a 0 or a negative.
     */
    private fun left(day: Day, goal: DayGoal, policy: VisibilityPolicy, context: FormatContext, copy: WidgetCopy): WidgetFigure? {
        if (policy.effectiveLevel(Metric.Remaining, Surface.Widget, context) != Visibility.Show) return null
        val eaten = day.totals().kcal.known
        val gap = goal.minKcal - eaten
        val bounds = context.copy(eaten = eaten, min = goal.minKcal.toDouble(), max = goal.maxKcal.toDouble())
        val ring = policy.ringValue(Metric.Remaining, gap, Surface.Widget, bounds) ?: return null
        val label = policy.resolve(Metric.Remaining, gap, Surface.Widget, bounds) as? Display.Shown ?: return null
        val below = gap >= 1.0
        val single = goal.minKcal == goal.maxKcal
        val unit = if (below) (if (single) copy.toGoal else copy.toRange) else ring.unit
        return WidgetFigure(
            amount = ring.value,
            unit = unit,
            label = label.text,
            line = copy.amountUnit.fillTemplate(ring.value, unit),
        )
    }

    private fun progress(day: Day, copy: WidgetCopy): WidgetProgress {
        val total = MealSlot.meals.size
        return WidgetProgress(done = day.doneMealCount, total = total, label = copy.mealsDone(day.doneMealCount, total))
    }

    /** Each macro shown in full, against its target as the Logger's strip writes it: "82 / 120 g". */
    private fun macros(day: Day, settings: Settings, policy: VisibilityPolicy, context: FormatContext, copy: WidgetCopy): List<WidgetMacro> {
        val totals = day.totals()
        val targets = settings.targets
        return MACROS.mapNotNull { metric ->
            val (grams, target) = when (metric) {
                Metric.Protein -> totals.protein.known to targets.proteinG
                Metric.Carbs -> totals.carbs.known to targets.carbsG
                Metric.Fat -> totals.fat.known to targets.fatG
                else -> totals.fiber.known to targets.fiberG
            }
            val ring = policy.ringValue(metric, grams, Surface.Widget, context) ?: return@mapNotNull null
            val shown = policy.resolve(metric, grams, Surface.Widget, context.copy(of = target.toDouble(), goal = GoalStyle.Slash)) as? Display.Shown
                ?: return@mapNotNull null
            val label = copy.macroLabels[metric.key] ?: metric.key
            WidgetMacro(
                key = metric.key,
                label = label,
                amount = ring.value,
                unit = ring.unit,
                text = shown.text,
                fraction = (shown.fraction ?: 0f).coerceIn(0f, 1f),
                line = copy.amountUnit.fillTemplate(ring.value, ring.unit),
                description = copy.macroDescription.fillTemplate(label, shown.text),
            )
        }
    }

    /** Water as Today's card has it: the amount when shown, the glasses alone when softened, nothing when hidden. */
    private fun water(day: Day, settings: Settings, policy: VisibilityPolicy, context: FormatContext, copy: WidgetCopy): WidgetWater? {
        val display = policy.resolve(
            Metric.Water,
            day.waterMl.toDouble(),
            Surface.Widget,
            context.copy(of = settings.waterGoalMl.toDouble(), unit = settings.waterUnit),
        )
        if (display is Display.Hidden) return null
        val glasses = WaterProgress.glasses(day.waterMl.toDouble(), settings.glassMl, settings.waterGlasses)
        val text = (display as? Display.Shown)?.text
        val glass = WaterProgress.glassSize(settings.glassMl, settings.waterUnit)
        return WidgetWater(
            text = text,
            filled = glasses.filled,
            total = glasses.total,
            label = text ?: copy.waterGlasses(glasses.filled, glasses.total),
            glass = glass,
            addLabel = copy.addGlassDescription.fillTemplate(copy.labels.addGlass, glass),
        )
    }

    /**
     * The meals widget's rows: the four meals, then anything logged without one; with meal groups
     * off, one row for all of it. A meal's energy only when the mode shows calories.
     */
    private fun mealRows(day: Day, groupsOn: Boolean, policy: VisibilityPolicy, context: FormatContext, copy: WidgetCopy): List<WidgetMealRow> {
        if (!groupsOn) {
            return listOf(mealRow(MealSlot.Uncategorized, copy.allFood, day.allItems, day.allItems.isNotEmpty(), policy, context, copy))
        }
        val loose = day.items(MealSlot.Uncategorized).takeIf { it.isNotEmpty() }?.let { items ->
            mealRow(MealSlot.Uncategorized, copy.allFood, items, done = true, policy, context, copy)
        }
        return MealSlot.meals.map { slot ->
            mealRow(slot, copy.meal(slot).label, day.items(slot), day.isMealDone(slot), policy, context, copy)
        } + listOfNotNull(loose)
    }

    @Suppress("LongParameterList")
    private fun mealRow(
        slot: MealSlot,
        label: String,
        items: List<LogItem>,
        done: Boolean,
        policy: VisibilityPolicy,
        context: FormatContext,
        copy: WidgetCopy,
    ): WidgetMealRow = WidgetMealRow(
        slot = slot.wire,
        label = label,
        done = done,
        foods = if (items.isEmpty()) (if (done) "" else copy.labels.nothingYet) else foods(items),
        calories = if (items.isEmpty()) null else (policy.resolve(Metric.Calories, items.totals().kcal.value, Surface.Widget, context) as? Display.Shown)?.text,
        link = WidgetLinks.log(slot.takeUnless { it == MealSlot.Uncategorized }),
    )

    /** "Oat porridge with milk, banana, coffee with milk": names in a sentence, as Today's planned row. */
    private fun foods(items: List<LogItem>): String = items.mapIndexed { index, item ->
        val name = item.name.trim()
        if (index == 0 || name.length < 2 || name[1].isUpperCase()) name else name.replaceFirstChar { it.lowercaseChar() }
    }.joinToString(", ")

    private fun fractionOf(value: Double, of: Double): Float =
        if (of <= 0.0 || !value.isFinite()) 0f else (value / of).toFloat().coerceIn(0f, 1f)

    private fun meals(day: Day, copy: WidgetCopy): List<WidgetMeal> = MealSlot.meals.map { slot ->
        val done = day.isMealDone(slot)
        val meal = copy.meal(slot)
        WidgetMeal(slot = slot.wire, label = meal.label, done = done, description = if (done) meal.done else meal.notYet)
    }
}
