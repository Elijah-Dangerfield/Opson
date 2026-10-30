package com.opson.libraries.goals.ui

import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightProjection
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.ui.time.DateCopy

/**
 * A projection laid out for drawing, with no scale or unit left in it: each series a height in
 * `0..1` of the chart (0 at the bottom), one per week, evenly spaced across the width. [low],
 * [middle] and [high] are eating at the band's low end, the target and its high end. [goal] is
 * the goal weight's height, when there is one; [reachedAt] how far across (0 to 1) the middle
 * series gets there.
 */
data class ProjectionShape(
    val low: List<Float>,
    val middle: List<Float>,
    val high: List<Float>,
    val goal: Float? = null,
    val reachedAt: Float? = null,
) {
    /** The series' first height: where every line starts. */
    val start: Float get() = middle.firstOrNull() ?: 0.5f
}

/**
 * When the projection gets there, in dates already put into words: [middle] eating the target,
 * [best] and [worst] at the two ends of the band. Each is null when that series doesn't get there
 * within the projection.
 */
data class ProjectionForecast(val middle: String?, val best: String?, val worst: String?)

/**
 * A projection ready for [ProjectionChart]: its [shape], the words around it, and a [forecast]
 * for a goal weight. [startWeight] is where it starts ("88 kg"), [endWeight] the goal weight or,
 * without one, where the middle line ends; [startDate] and [endDate] its span. Every weight went
 * through the policy: a chart is only built where weight is on show.
 */
data class ProjectionChartUi(
    val shape: ProjectionShape,
    val startWeight: String,
    val endWeight: String,
    val startDate: String,
    val endDate: String,
    /** True when [endWeight] is the goal weight, false when it is just where the line ends. */
    val toGoal: Boolean,
    val forecast: ProjectionForecast? = null,
)

/** Pure: a [WeightProjection] into a [ProjectionChartUi], so the math is tested without drawing. */
object ProjectionCharts {

    /** Room above and below the lines, as a share of the span, so nothing touches the edge. */
    private const val PADDING = 0.12

    /** Never a span narrower than this, so a small change doesn't fill the chart top to bottom. */
    private const val MIN_SPAN_KG = 2.0

    /**
     * [projection] laid out, with [goalKg] as its own line when there is one, and the band's best
     * and worst read for [direction]. Null where weight isn't on show in [policy] (a chart of
     * weight is weight, even without its numbers), or with fewer than two points.
     */
    fun ui(
        projection: WeightProjection,
        goalKg: Double?,
        direction: GoalDirection?,
        unit: WeightUnit,
        policy: VisibilityPolicy,
    ): ProjectionChartUi? {
        if (policy.effectiveLevel(Metric.Weight, Surface.Settings) != Visibility.Show) return null
        val shape = shape(projection, goalKg) ?: return null
        val points = projection.points
        fun weight(kg: Double): String? =
            (policy.resolve(Metric.Weight, kg, Surface.Settings, FormatContext(weightUnit = unit)) as? Display.Shown)?.text
        val start = weight(points.first().targetKg) ?: return null
        val end = weight(goalKg ?: points.last().targetKg) ?: return null
        val forecast = goalKg?.let {
            val gaining = direction == GoalDirection.Gain
            ProjectionForecast(
                middle = projection.targetReached?.let(DateCopy::monthDayYear),
                best = (if (gaining) projection.highKcalReached else projection.lowKcalReached)?.let(DateCopy::monthDay),
                worst = (if (gaining) projection.lowKcalReached else projection.highKcalReached)?.let(DateCopy::monthDay),
            )
        }
        return ProjectionChartUi(
            shape = shape,
            startWeight = start,
            endWeight = end,
            startDate = DateCopy.monthDay(points.first().date),
            endDate = DateCopy.monthDay(points.last().date),
            toGoal = goalKg != null,
            forecast = forecast,
        )
    }

    /**
     * The heights: every series and the goal on one scale, padded, at least [MIN_SPAN_KG] tall.
     * Null with fewer than two points, where there is no line to draw.
     */
    fun shape(projection: WeightProjection, goalKg: Double?): ProjectionShape? {
        val points = projection.points
        if (points.size < 2) return null
        val all = points.flatMap { listOf(it.lowKcalKg, it.targetKg, it.highKcalKg) } + listOfNotNull(goalKg)
        var bottom = all.min()
        var top = all.max()
        if (top - bottom < MIN_SPAN_KG) {
            val middle = (top + bottom) / 2
            bottom = middle - MIN_SPAN_KG / 2
            top = middle + MIN_SPAN_KG / 2
        }
        val pad = (top - bottom) * PADDING
        bottom -= pad
        top += pad
        fun height(kg: Double): Float = ((kg - bottom) / (top - bottom)).toFloat().coerceIn(0f, 1f)
        val reached = projection.targetReached?.let { date ->
            val first = points.first().date.toEpochDays()
            val span = (points.last().date.toEpochDays() - first).toFloat()
            if (span <= 0f) null else ((date.toEpochDays() - first) / span).coerceIn(0f, 1f)
        }
        return ProjectionShape(
            low = points.map { height(it.lowKcalKg) },
            middle = points.map { height(it.targetKg) },
            high = points.map { height(it.highKcalKg) },
            goal = goalKg?.let(::height),
            reachedAt = reached,
        )
    }
}
