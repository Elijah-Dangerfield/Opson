package com.opson.features.trends.impl

import com.opson.libraries.diary.WeighIn
import com.opson.libraries.nutrition.WeightTrend
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/** Heights in `0..1` for `TrendLine`: the smoothed [trend] and the readings behind it. */
data class WeightCurve(val trend: List<Float>, val readings: List<Float>)

/**
 * Turns weigh-ins into chart heights. The heights carry no scale and the chart
 * has no axis, so at "Trend only" the line says which way things are going and
 * nothing about how much anyone weighs.
 */
object WeightCurves {

    /** The prototype's padding above and below the visible readings. */
    private const val PAD_KG = 0.3

    /**
     * The last [days] days (today included) of [weighIns], which are oldest
     * first. The EMA runs over every weigh-in given, so the line at the left
     * edge already carries the days before it, then is cut to the window. Each
     * weigh-in is a point, several in a day included. Empty when nothing falls
     * in the window; [WeightCurve.trend] is empty under two readings, since one
     * reading is not a trend.
     */
    fun curve(weighIns: List<WeighIn>, today: LocalDate, days: Int, timeZone: TimeZone): WeightCurve {
        if (weighIns.isEmpty()) return WeightCurve(emptyList(), emptyList())
        val ema = WeightTrend.ema(weighIns.map { it.kg })
        val from = today.minus(days - 1, DateTimeUnit.DAY)
        val first = weighIns.indexOfFirst { it.measuredAt.toLocalDateTime(timeZone).date >= from }
        if (first < 0) return WeightCurve(emptyList(), emptyList())
        val readings = weighIns.subList(first, weighIns.size).map { it.kg }
        val trend = ema.subList(first, ema.size)
        val low = readings.min() - PAD_KG
        val high = readings.max() + PAD_KG
        fun height(kg: Double): Float = ((kg - low) / (high - low)).toFloat().coerceIn(0f, 1f)
        return WeightCurve(
            trend = if (readings.size < 2) emptyList() else trend.map(::height),
            readings = readings.map(::height),
        )
    }
}
