package com.opson.libraries.nutrition

import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.time.Clock

/**
 * The one place that decides whether a nutrition number is shown, softened,
 * delayed or hidden, and the only place that turns one into text. A port of
 * `vis`, `revealed`, `nv`, `format`, `soften` and `score` in
 * `docs/design/handoff/phone-logic.js`.
 */
class VisibilityPolicy(
    val settings: NumbersSettings,
    private val clock: Clock,
    private val timeZone: TimeZone,
    internal val formatter: NutritionFormatter = EnglishNutritionFormatter,
) {
    val preset: ModePreset get() = Presets[settings.preset]

    /** The configured level, `Delay` included. For the modes matrix and the friction score. */
    fun level(metric: Metric): Visibility = settings.level(metric)

    /** The level after delayed reveal is applied: never [Visibility.Delay]. */
    fun effectiveLevel(metric: Metric, surface: Surface, context: FormatContext = FormatContext()): Visibility {
        val level = level(metric)
        if (level != Visibility.Delay) return level
        return if (isRevealed(surface, context)) Visibility.Show else Visibility.Hide
    }

    /**
     * Whether delayed numbers are open on [surface] for the day in [context].
     * Coast opens on the week view only, and there once a week: with a
     * [FormatContext.day] on [Surface.Week], that day is the week's last, and
     * the week opens at the user's reveal time on it ([isWeekOpen]). Everything
     * else delayed also opens at day close, once the day is closed or past, and
     * from the user's reveal time ([NumbersSettings.revealAt], 9:00 pm unless
     * they picked another) on the day.
     */
    fun isRevealed(surface: Surface, context: FormatContext = FormatContext()): Boolean {
        if (revealsWeekly) return surface == Surface.Week && (context.day?.let(::isWeekOpen) ?: true)
        if (surface == Surface.Week || surface == Surface.DayClose || context.dayClosed) return true
        val now = clock.now().toLocalDateTime(timeZone)
        val day = context.day ?: now.date
        return day < now.date || (day == now.date && now.time >= revealTime)
    }

    /** Delayed numbers open once a week, on the week view (Coast), rather than at day close. */
    val revealsWeekly: Boolean get() = preset.revealOn == RevealOn.Week

    /** When the weekly check for the week ending on [weekEnd] opens: the reveal time on its last day. */
    fun weekOpensAt(weekEnd: LocalDate): LocalDateTime = LocalDateTime(weekEnd, revealTime)

    /** Whether the week ending on [weekEnd] has reached its weekly check. Past weeks always have. */
    fun isWeekOpen(weekEnd: LocalDate): Boolean = clock.now().toLocalDateTime(timeZone) >= weekOpensAt(weekEnd)

    fun resolve(metric: Metric, value: Double?, surface: Surface, context: FormatContext = FormatContext()): Display {
        val level = effectiveLevel(metric, surface, context)
        if (level == Visibility.Hide || value == null || !value.isFinite()) return Display.Hidden
        return if (level == Visibility.Soften) {
            soften(metric, value, context)
        } else {
            Display.Shown(format(metric, value, context), fraction(metric, value, context))
        }
    }

    fun score(): Int = settings.score()

    fun isRaise(next: NumbersSettings): Boolean = settings.isRaise(next)

    private fun soften(metric: Metric, value: Double, context: FormatContext): Display {
        if (metric == Metric.Water) return Display.Soft(text = null, fraction = fraction(metric, value, context))
        val word = softWord(metric, value, context) ?: return Display.Hidden
        return Display.Soft(formatter.soft(word), fraction(metric, value, context))
    }

    private fun softWord(metric: Metric, value: Double, context: FormatContext): SoftWord? {
        if (metric == Metric.Weight) return context.trendKgPerWeek?.let(::slopeWord) ?: SoftWord.TrendOnly
        val of = context.of
        // The prototype would fall through to the macro words here. "Plenty
        // today" for energy expended reads as praise for exercise, and there is
        // no honest word for a softened remainder, so these two hide instead.
        if (metric == Metric.Remaining || metric == Metric.EnergyOut) return null
        if (of == null || of == 0.0) return null
        val p = value / of
        return when {
            metric == Metric.Calories -> when {
                p < 0.35 -> SoftWord.EarlyInTheDay
                p < 0.7 -> SoftWord.PartwayThrough
                p < 0.92 -> SoftWord.MostOfTheWay
                p <= 1.1 -> SoftWord.InYourRange
                else -> SoftWord.ABiggerDay
            }
            metric == Metric.Micros -> when {
                p < 0.5 -> SoftWord.Some
                p < 0.9 -> SoftWord.Most
                else -> SoftWord.Plenty
            }
            else -> when {
                p < 0.4 -> SoftWord.Building
                p < 0.85 -> SoftWord.GettingThere
                else -> SoftWord.PlentyToday
            }
        }
    }

    /**
     * The word for which way the smoothed weight line is going. Only three words, none of them
     * fast or praise, and no amount.
     *
     * TODO(clinician): review the words and the threshold before 1.0.
     */
    private fun slopeWord(kgPerWeek: Double): SoftWord = when {
        !kgPerWeek.isFinite() || abs(kgPerWeek) < STEADY_KG_PER_WEEK -> SoftWord.Steady
        kgPerWeek > 0 -> SoftWord.RisingSlowly
        else -> SoftWord.FallingSlowly
    }

    /** When anything set to Later opens on its day, and when Coast's week opens on its last day: the user's setting. */
    private val revealTime: LocalTime get() = settings.revealAt

    private fun format(metric: Metric, value: Double, context: FormatContext): String = when (metric) {
        Metric.Water -> {
            val of = waterGoal(context)
            when (context.unit) {
                WaterUnit.Ml -> formatter.water(value, of, WaterUnit.Ml)
                WaterUnit.FlOz -> formatter.water(Units.mlToFlOz(value), Units.mlToFlOz(of), WaterUnit.FlOz)
            }
        }
        Metric.Weight -> when (context.weightUnit) {
            WeightUnit.Kg -> formatter.weight(value, WeightUnit.Kg)
            WeightUnit.Lb -> formatter.weight(Units.kgToLb(value), WeightUnit.Lb)
        }
        Metric.Micros -> formatter.percent(value)
        Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber -> grams(value, context)
        Metric.Remaining -> remaining(value, context)
        Metric.Calories, Metric.EnergyOut -> energy(value, context)
    }

    private fun grams(value: Double, context: FormatContext): String {
        val of = context.of?.takeIf { it != 0.0 }
        return when {
            of == null || context.goal == GoalStyle.None -> formatter.grams(value, of = null)
            context.goal == GoalStyle.Slash -> formatter.gramsSlash(value, of)
            else -> formatter.grams(value, of)
        }
    }

    private fun energy(value: Double, context: FormatContext): String {
        val of = context.of?.takeIf { it != 0.0 }
        return when {
            of == null -> formatter.energy(value, context.bare)
            context.goal == GoalStyle.Of -> formatter.energyOf(value, of, context.bare)
            context.goal == GoalStyle.Slash -> formatter.energySlash(value, of, context.bare)
            // Default and None: energy never spells its goal unless asked to.
            else -> formatter.energy(value, context.bare)
        }
    }

    private fun remaining(value: Double, context: FormatContext): String {
        val eaten = context.eaten
        val min = context.min
        val max = context.max
        return when {
            eaten != null && min != null && max != null && eaten >= min && eaten <= max -> formatter.onTarget()
            eaten != null && max != null && eaten > max -> formatter.aboveRange()
            // Never "0 left" and never a negative "left", even when the caller
            // passed no range to compare against.
            value.roundToLong() <= 0 -> formatter.onTarget()
            else -> formatter.left(value)
        }
    }

    private fun fraction(metric: Metric, value: Double, context: FormatContext): Float? {
        val of = when (metric) {
            Metric.Water -> waterGoal(context)
            Metric.Calories, Metric.Micros -> context.of
            else -> if (metric.isMacro) context.of else null
        }
        if (of == null || of <= 0.0) return null
        return (value / of).toFloat()
    }

    private fun waterGoal(context: FormatContext): Double =
        context.of?.takeIf { it != 0.0 } ?: WaterDefaults.GOAL_ML.toDouble()

    companion object {
        /** The default reveal time. The live one is [NumbersSettings.revealAt]. */
        val DAY_CLOSE_REVEAL: LocalTime = NumbersSettings.DEFAULT_REVEAL_AT

        /** Under this much a week either way, the line reads as steady. TODO(clinician). */
        private const val STEADY_KG_PER_WEEK = 0.2
    }
}
