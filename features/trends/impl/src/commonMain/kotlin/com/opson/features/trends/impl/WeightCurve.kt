package com.opson.features.trends.impl

import com.opson.libraries.diary.WeighIn
import com.opson.libraries.nutrition.WeightTrend
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Duration.Companion.days

/**
 * Heights in `0..1` for `TrendLine`: the smoothed [trend] and the [readings]
 * behind it. [trendKg] and [kgPerWeek] are raw, for the policy to resolve
 * (the trend as the large number at Show, the slope as a word at Trend only);
 * nothing here is formatted. Both are `null` with no reading in the window, and
 * the slope also under two readings.
 *
 * [points] are the same weigh-ins raw, one per reading in order, for a chart
 * point's label to resolve.
 */
data class WeightCurve(
    val trend: List<Float>,
    val readings: List<Float>,
    val trendKg: Double? = null,
    val kgPerWeek: Double? = null,
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val points: List<WeightPoint> = emptyList(),
)

/** One weigh-in in the window: its day, what it read, and the smoothed trend there. */
data class WeightPoint(val day: LocalDate, val kg: Double, val trendKg: Double)

/**
 * Turns weigh-ins into chart heights. The heights carry no scale and the chart
 * has no axis, so at "Trend only" the line says which way things are going and
 * nothing about how much anyone weighs.
 */
object WeightCurves {

    /** The prototype's padding above and below the visible readings. */
    private const val PAD_KG = 0.3
    private const val DAYS_PER_WEEK = 7.0

    /**
     * The last [days] days (ending on [today], included) of [weighIns], which
     * are oldest first. The EMA runs over every weigh-in given, so the line at
     * the left edge already carries the days before it, then is cut to the
     * window. Each weigh-in is a point, several in a day included. Empty when
     * nothing falls in the window; [WeightCurve.trend] is empty under two
     * readings, since one reading is not a trend.
     */
    fun curve(weighIns: List<WeighIn>, today: LocalDate, days: Int, timeZone: TimeZone): WeightCurve {
        if (weighIns.isEmpty()) return WeightCurve(emptyList(), emptyList())
        val ema = WeightTrend.ema(weighIns.map { it.kg })
        val from = today.minus(days - 1, DateTimeUnit.DAY)
        val inWindow = weighIns.withIndex().filter { (_, it) ->
            val date = it.measuredAt.toLocalDateTime(timeZone).date
            date >= from && date <= today
        }
        if (inWindow.isEmpty()) return WeightCurve(emptyList(), emptyList())
        val first = inWindow.first().index
        val last = inWindow.last().index
        val readings = inWindow.map { it.value.kg }
        val trend = ema.subList(first, last + 1)
        val low = readings.min() - PAD_KG
        val high = readings.max() + PAD_KG
        fun height(kg: Double): Float = ((kg - low) / (high - low)).toFloat().coerceIn(0f, 1f)
        val span = weighIns[last].measuredAt - weighIns[first].measuredAt
        return WeightCurve(
            trend = if (readings.size < 2) emptyList() else trend.map(::height),
            readings = readings.map(::height),
            trendKg = trend.last(),
            kgPerWeek = if (readings.size < 2 || span <= 0.days) null else (trend.last() - trend.first()) / (span / 1.days) * DAYS_PER_WEEK,
            from = from,
            to = today,
            points = inWindow.map { (index, weighIn) ->
                WeightPoint(weighIn.measuredAt.toLocalDateTime(timeZone).date, weighIn.kg, ema[index])
            },
        )
    }
}
