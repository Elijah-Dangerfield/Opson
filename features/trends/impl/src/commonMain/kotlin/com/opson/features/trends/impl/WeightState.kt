package com.opson.features.trends.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_weight_caption_dots
import opson.libraries.resources.generated.resources.trends_weight_caption_trend
import org.jetbrains.compose.resources.StringResource

/** The weight view picker. Each one is a weight override: Hide, Soften, Show. */
enum class WeightView(val level: Visibility) {
    Hidden(Visibility.Hide),
    TrendOnly(Visibility.Soften),
    Numbers(Visibility.Show),
}

enum class WeightRange(val days: Int) {
    W1(7),
    M1(28),
    M3(90),
}

/** When the shown weight was measured. */
sealed interface WeighInWhen {
    data object ThisMorning : WeighInWhen

    /** `Yesterday`, `Thu, Oct 22`. */
    data class Day(val text: String) : WeighInWhen
}

/** Which "Saved." line follows a save: the plain one only when the weight is on screen. */
enum class SavedNote { Plain, OutOfView }

data class WeightInputs(
    val today: LocalDate,
    val settings: Settings,
    /** Oldest first. */
    val weighIns: List<WeighIn>,
    val timeZone: TimeZone,
)

/** The page's own choices, which no repository holds. */
data class WeightUi(
    val range: WeightRange = WeightRange.M1,
    val input: String = "",
    val saved: Boolean = false,
)

data class WeightState(
    val loading: Boolean = true,
    /** Delay shows as Trend only, as the prototype. */
    val selected: WeightView = WeightView.Hidden,
    /** Only at Show. */
    val now: Display? = null,
    val nowWhen: WeighInWhen? = null,
    /** The 7-day average, only at Show. */
    val average: Display? = null,
    val range: WeightRange = WeightRange.M1,
    /** `null` while weight is hidden, a delayed weight before its reveal included. */
    val chart: WeightChart? = null,
    val hiddenCard: Boolean = false,
    val input: String = "",
    /** Dots instead of digits unless the weight is shown. */
    val inputMasked: Boolean = true,
    val unit: WeightUnit = WeightUnit.Kg,
    val saved: SavedNote? = null,
)

data class WeightChart(
    /** The smoothed line, heights in `0..1`; empty under two readings. */
    val trend: List<Float>,
    /** Every weigh-in, only at Show. */
    val dots: List<Float>?,
    val caption: StringResource,
)

/**
 * Builds [WeightState] on [Surface.Weight]. No weight change readout anywhere
 * ("−1.6 lbs"), per the competitive review: a clinician should look at that
 * before it ships, even in Full numbers.
 */
object WeightStateBuilder {

    private const val AVERAGE_DAYS = 7

    fun build(inputs: WeightInputs, ui: WeightUi, policy: VisibilityPolicy): WeightState {
        val level = policy.effectiveLevel(Metric.Weight, Surface.Weight)
        val shown = level == Visibility.Show
        val unit = inputs.settings.weightUnit
        val context = FormatContext(weightUnit = unit)
        val latest = inputs.weighIns.lastOrNull()
        val weekFrom = inputs.today.minus(AVERAGE_DAYS - 1, DateTimeUnit.DAY)
        val lastWeek = inputs.weighIns.filter { it.measuredAt.toLocalDateTime(inputs.timeZone).date >= weekFrom }

        return WeightState(
            loading = false,
            selected = when (policy.level(Metric.Weight)) {
                Visibility.Show -> WeightView.Numbers
                Visibility.Soften, Visibility.Delay -> WeightView.TrendOnly
                Visibility.Hide -> WeightView.Hidden
            },
            now = latest?.let { policy.resolve(Metric.Weight, it.kg, Surface.Weight, context) as? Display.Shown },
            nowWhen = if (shown && latest != null) {
                weighInDayText(latest, inputs.today, inputs.timeZone)?.let(WeighInWhen::Day) ?: WeighInWhen.ThisMorning
            } else {
                null
            },
            average = policy.resolve(Metric.Weight, lastWeek.map { it.kg }.averageOrNull(), Surface.Weight, context) as? Display.Shown,
            range = ui.range,
            chart = if (level == Visibility.Hide) {
                null
            } else {
                val curve = WeightCurves.curve(inputs.weighIns, inputs.today, ui.range.days, inputs.timeZone)
                WeightChart(
                    trend = curve.trend,
                    dots = if (shown) curve.readings else null,
                    caption = if (shown) Res.string.trends_weight_caption_dots else Res.string.trends_weight_caption_trend,
                )
            },
            hiddenCard = level == Visibility.Hide,
            input = ui.input,
            inputMasked = !shown,
            unit = unit,
            saved = when {
                !ui.saved -> null
                shown -> SavedNote.Plain
                else -> SavedNote.OutOfView
            },
        )
    }
}
