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
import com.opson.libraries.ui.time.DateCopy
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone

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

/** Which "Saved." line follows a save: where the weigh-in will show, if anywhere. */
enum class SavedNote { Plain, TrendOnly, OutOfView }

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

/**
 * The Weight page. There is no visibility switch here (design v3): Numbers,
 * Trend only and Hidden live in Modes and numbers, behind the pause.
 */
data class WeightState(
    val loading: Boolean = true,
    /** Show: the smoothed weight, the large number. Soften: the slope word. `null` when hidden or before any weigh-in. */
    val trend: Display? = null,
    /** Today's (or the latest) reading, small beside the trend. Only at Show. */
    val latest: Display? = null,
    val latestWhen: WeighInWhen? = null,
    val range: WeightRange = WeightRange.M1,
    /** `null` while weight is hidden, a delayed weight before its reveal included. */
    val chart: WeightChart? = null,
    val hiddenCard: Boolean = false,
    val input: String = "",
    /** Dots instead of digits unless the weight is shown. */
    val inputMasked: Boolean = true,
    /** "After you save, it only shows up in the trend." at Trend only. */
    val trendOnlyNote: Boolean = false,
    val unit: WeightUnit = WeightUnit.Kg,
    val saved: SavedNote? = null,
)

/**
 * The line, and at Show the weigh-ins behind it with the first and last dates
 * under them and the 1W / 1M / 3M control. Heights are in `0..1`; the trend is
 * empty under two readings.
 */
data class WeightChart(
    val trend: List<Float>,
    val dots: List<Float>?,
    val from: String?,
    val to: String?,
    val ranges: Boolean,
)

/**
 * Builds [WeightState] on [Surface.Weight]. No weight change readout anywhere
 * ("−1.6 kg"), pending a clinician's view, even in Full numbers.
 */
object WeightStateBuilder {

    fun build(inputs: WeightInputs, ui: WeightUi, policy: VisibilityPolicy): WeightState {
        val level = policy.effectiveLevel(Metric.Weight, Surface.Weight)
        val shown = level == Visibility.Show
        val unit = inputs.settings.weightUnit
        val latest = inputs.weighIns.lastOrNull()
        val curve = WeightCurves.curve(inputs.weighIns, inputs.today, ui.range.days, inputs.timeZone)
        val context = FormatContext(weightUnit = unit, trendKgPerWeek = curve.kgPerWeek)

        return WeightState(
            loading = false,
            // Bare: the screen sets the unit small beside the figure, as v3 draws it.
            trend = policy.resolve(Metric.Weight, curve.trendKg, Surface.Weight, context.copy(bare = true)).takeUnless { it is Display.Hidden },
            latest = latest?.let { policy.resolve(Metric.Weight, it.kg, Surface.Weight, FormatContext(weightUnit = unit)) as? Display.Shown },
            latestWhen = if (shown && latest != null) {
                weighInDayText(latest, inputs.today, inputs.timeZone)?.let(WeighInWhen::Day) ?: WeighInWhen.ThisMorning
            } else {
                null
            },
            range = ui.range,
            chart = if (level == Visibility.Hide) {
                null
            } else {
                WeightChart(
                    trend = curve.trend,
                    dots = if (shown) curve.readings else null,
                    from = if (shown) curve.from?.let(DateCopy::monthDay) else null,
                    to = if (shown) curve.to?.let(DateCopy::monthDay) else null,
                    ranges = shown,
                )
            },
            hiddenCard = level == Visibility.Hide,
            input = ui.input,
            inputMasked = !shown,
            trendOnlyNote = level == Visibility.Soften,
            unit = unit,
            saved = when {
                !ui.saved -> null
                shown -> SavedNote.Plain
                level == Visibility.Soften -> SavedNote.TrendOnly
                else -> SavedNote.OutOfView
            },
        )
    }
}
