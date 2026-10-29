package com.opson.features.trends.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.medication.DoseMarks
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.ui.components.ChartReadout
import com.opson.libraries.ui.time.DateCopy
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.daysUntil

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
    /** `release.progressPhotos`, read when the page opens. */
    val photosReleased: Boolean = false,
    /** The day of the newest progress photo, `null` before the first. */
    val photosLastTaken: LocalDate? = null,
    /** Dose ticks (`release.medication`, while tracking): the start and each change of dose. */
    val doseMarks: List<DoseMarks.Mark> = emptyList(),
)

/** The Progress photos row under the log card. */
data class PhotosRow(val lastTaken: LocalDate?)

/** The page's own choices, which no repository holds. */
data class WeightUi(
    val range: WeightRange = WeightRange.M1,
    val input: String = "",
    val saved: Boolean = false,
    /** The last save didn't reach the database. */
    val failed: Boolean = false,
    /** The last save was refused: zero, or more than anyone weighs. */
    val notAWeight: Boolean = false,
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
    /** `null` while weight is hidden, a delayed weight before its reveal included, and before any weigh-in. */
    val chart: WeightChart? = null,
    /** No weigh-in yet, and weight not hidden: the empty card stands where the chart would. */
    val empty: Boolean = false,
    val hiddenCard: Boolean = false,
    val input: String = "",
    /** Dots instead of digits unless the weight is shown. */
    val inputMasked: Boolean = true,
    /** "After you save, it only shows up in the trend." at Trend only. */
    val trendOnlyNote: Boolean = false,
    val unit: WeightUnit = WeightUnit.Kg,
    val saved: SavedNote? = null,
    /** The weigh-in wasn't saved: it stays in the field, with a line saying so. */
    val saveFailed: Boolean = false,
    /**
     * What was typed is not a weight (zero, or past the 700 kg ceiling), so Save kept nothing. Said
     * out loud because a masked field doesn't show the typo.
     */
    val notAWeight: Boolean = false,
    /** `null` while the release flag is off or Modes has photos at Hide. */
    val photos: PhotosRow? = null,
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
    /** Why the line is missing in this range, when it is; `null` when there is a line. */
    val gap: ChartGap? = null,
    /** One per weigh-in: its day, and at Show the trend there and the reading. */
    val readouts: List<ChartReadout?> = emptyList(),
    /** Dose ticks under the line, placed on its x axis. Only where there is a line. */
    val doseTicks: List<DoseTick> = emptyList(),
)

/**
 * A tick under the weight line: [x] is `0..1` along the line's points, which are evenly spaced
 * one per weigh-in, so a tick between two weigh-ins sits between them in proportion to the days.
 */
data class DoseTick(val x: Float, val mark: DoseMarks.Mark)

/** Places dose marks on the weight line's x axis ([WeightCurve.points] order). */
object DoseTicks {
    fun place(marks: List<DoseMarks.Mark>, points: List<WeightPoint>): List<DoseTick> {
        if (points.size < 2) return emptyList()
        val first = points.first().day
        val last = points.last().day
        return marks.filter { it.day >= first && it.day <= last }.map { DoseTick(position(it.day, points), it) }
    }

    private fun position(day: LocalDate, points: List<WeightPoint>): Float {
        val span = (points.size - 1).toFloat()
        val onOrAfter = points.indexOfFirst { it.day >= day }
        val before = points.indexOfLast { it.day < day }
        if (before < 0 || points[onOrAfter].day == day) return onOrAfter / span
        val from = points[before].day
        val to = points[onOrAfter].day
        val share = from.daysUntil(day).toFloat() / from.daysUntil(to)
        return (before + share * (onOrAfter - before)) / span
    }
}

/** Why a range has no line: nothing in it, or a single weigh-in, which is not a trend yet. */
enum class ChartGap { NoneInRange, OneReading }

/**
 * Builds [WeightState] on [Surface.Weight]. No weight change readout anywhere
 * ("−1.6 kg"), pending a clinician's view, even in All numbers.
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
            chart = if (level == Visibility.Hide || inputs.weighIns.isEmpty()) {
                null
            } else {
                WeightChart(
                    trend = curve.trend,
                    dots = if (shown) curve.readings else null,
                    from = if (shown) curve.from?.let(DateCopy::monthDay) else null,
                    to = if (shown) curve.to?.let(DateCopy::monthDay) else null,
                    ranges = shown,
                    gap = when {
                        curve.readings.isEmpty() -> ChartGap.NoneInRange
                        curve.trend.isEmpty() -> ChartGap.OneReading
                        else -> null
                    },
                    readouts = TrendsReadouts.weight(curve, inputs.today, unit, policy, weighIns = shown),
                    doseTicks = DoseTicks.place(inputs.doseMarks, curve.points),
                )
            },
            empty = level != Visibility.Hide && inputs.weighIns.isEmpty(),
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
            saveFailed = ui.failed,
            notAWeight = ui.notAWeight,
            // Modes' Progress photos row, Show or Hide; Hide removes this row (design sync 2, 8.x).
            photos = if (inputs.photosReleased && policy.settings.showsProgressPhotos) PhotosRow(inputs.photosLastTaken) else null,
        )
    }
}
