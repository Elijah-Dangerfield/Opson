package com.opson.features.trends.impl

import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.MealPatterns
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.nutrition.ui.chartReading
import com.opson.libraries.ui.components.ChartReadout
import com.opson.libraries.ui.time.DateCopy
import com.opson.libraries.ui.time.RelativeDay
import kotlinx.datetime.LocalDate
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_chart_trend
import opson.libraries.resources.generated.resources.trends_chart_weigh_in
import kotlin.math.roundToInt

/**
 * What the Trends charts say about one inspected point, for the builders. Pure. Every amount goes
 * through the policy here and is kept only when shown ([chartReading]), so a label can carry a
 * date in every mode and a number only where the card itself would.
 */
internal object TrendsReadouts {

    /** `Today`, `Yesterday`, `Thu, Oct 22`. */
    fun day(date: LocalDate, today: LocalDate): String = DateCopy.label(RelativeDay.of(date, today))

    /** `Oct 19 to 25`, or `Oct 26` for a week that began today. */
    fun week(start: LocalDate, end: LocalDate): String = if (start == end) DateCopy.monthDay(start) else DateCopy.range(start, end)

    /**
     * One per weigh-in in [curve]: its day, the trend there when the curve has a line, and with
     * [weighIns] the reading itself. Below Show both amounts are dropped and the day is all it says.
     */
    fun weight(curve: WeightCurve, today: LocalDate, unit: WeightUnit, policy: VisibilityPolicy, weighIns: Boolean): List<ChartReadout?> {
        val context = FormatContext(weightUnit = unit)
        val line = curve.trend.isNotEmpty()
        return curve.points.map { point ->
            ChartReadout(
                title = day(point.day, today),
                readings = listOfNotNull(
                    policy.resolve(Metric.Weight, point.trendKg, Surface.Weight, context)
                        .chartReading(Metric.Weight, Res.string.trends_chart_trend)
                        .takeIf { line },
                    policy.resolve(Metric.Weight, point.kg, Surface.Weight, context)
                        .chartReading(Metric.Weight, Res.string.trends_chart_weigh_in)
                        .takeIf { weighIns },
                ),
            )
        }
    }

    /** An average of hunger levels as the nearest answer, stuffed `0` to starving [MealPatterns.HUNGER_STEPS]. */
    fun hungerWord(levels: List<Int>): Hunger = hungerWord(levels.average())

    fun hungerWord(level: Double): Hunger =
        Hunger.entries[MealPatterns.HUNGER_STEPS - level.roundToInt().coerceIn(0, MealPatterns.HUNGER_STEPS)]
}
