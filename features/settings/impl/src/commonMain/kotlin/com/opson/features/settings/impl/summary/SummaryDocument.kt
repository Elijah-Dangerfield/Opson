package com.opson.features.settings.impl.summary

import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.Total
import com.opson.libraries.diary.Totals
import com.opson.libraries.diary.ui.label
import com.opson.libraries.medication.DoseFormat
import com.opson.libraries.medication.DoseUnit
import com.opson.libraries.medication.MedicationName
import com.opson.libraries.medication.amount
import com.opson.libraries.medication.inSentence
import com.opson.libraries.medication.label
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WaterUnit
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.pdf.PdfStyle
import com.opson.libraries.ui.time.DateCopy
import kotlinx.datetime.LocalDate
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.summary_doc_ate_no_details
import opson.libraries.resources.generated.resources.summary_doc_calories
import opson.libraries.resources.generated.resources.summary_doc_carbs
import opson.libraries.resources.generated.resources.summary_doc_day_note
import opson.libraries.resources.generated.resources.summary_doc_fat
import opson.libraries.resources.generated.resources.summary_doc_feeling
import opson.libraries.resources.generated.resources.summary_doc_fiber
import opson.libraries.resources.generated.resources.summary_doc_hunger
import opson.libraries.resources.generated.resources.summary_doc_included
import opson.libraries.resources.generated.resources.summary_doc_made
import opson.libraries.resources.generated.resources.summary_doc_meal_note
import opson.libraries.resources.generated.resources.summary_doc_meal_time
import opson.libraries.resources.generated.resources.summary_doc_moods
import opson.libraries.resources.generated.resources.summary_doc_no_weight
import opson.libraries.resources.generated.resources.summary_doc_nothing
import opson.libraries.resources.generated.resources.summary_doc_numbers_note
import opson.libraries.resources.generated.resources.summary_doc_pair
import opson.libraries.resources.generated.resources.summary_doc_protein
import opson.libraries.resources.generated.resources.summary_doc_range
import opson.libraries.resources.generated.resources.summary_doc_title
import opson.libraries.resources.generated.resources.summary_doc_untracked
import opson.libraries.resources.generated.resources.summary_doc_water
import opson.libraries.resources.generated.resources.summary_doc_weight
import opson.libraries.resources.generated.resources.summary_include_check_ins
import opson.libraries.resources.generated.resources.summary_include_hunger
import opson.libraries.resources.generated.resources.summary_include_meals
import opson.libraries.resources.generated.resources.summary_include_medication
import opson.libraries.resources.generated.resources.medication_symptom_with_severity
import opson.libraries.resources.generated.resources.summary_doc_medication
import opson.libraries.resources.generated.resources.summary_doc_medication_changed
import opson.libraries.resources.generated.resources.summary_doc_medication_dose
import opson.libraries.resources.generated.resources.summary_doc_medication_none
import opson.libraries.resources.generated.resources.summary_doc_side_effects
import opson.libraries.resources.generated.resources.summary_doc_side_effects_day
import opson.libraries.resources.generated.resources.summary_include_notes
import opson.libraries.resources.generated.resources.summary_include_numbers
import opson.libraries.resources.generated.resources.summary_include_weight
import opson.libraries.resources.generated.resources.today_checkin_energy
import opson.libraries.resources.generated.resources.today_checkin_mood_level
import opson.libraries.resources.generated.resources.today_checkin_hunger
import opson.libraries.resources.generated.resources.today_checkin_sleep
import org.jetbrains.compose.resources.StringResource

/**
 * Copy that is resolved where it is shown: by `stringResource` in the preview, and by
 * `getString` when the PDF is written. The builder stays pure, so previews and the leak test
 * build the same lines the PDF does.
 */
sealed interface SummaryText {
    /** The person's own words (a note, a food's name) or copy already spelled, such as a date. */
    data class Plain(val text: String) : SummaryText

    data class Res(val resource: StringResource, val args: List<SummaryText> = emptyList()) : SummaryText

    data class Joined(val parts: List<SummaryText>, val separator: String = ", ") : SummaryText
}

/**
 * One line of the summary. A numbers line has a part per number, each carrying its [Metric] so
 * the preview tags it the way every other surface tags a number.
 */
data class SummaryLine(
    val parts: List<SummaryLinePart>,
    val style: PdfStyle = PdfStyle.Body,
    val indented: Boolean = false,
)

data class SummaryLinePart(val text: SummaryText, val metric: Metric? = null)

/** How numbers read: the person's units and water goal. */
data class SummaryUnits(val weight: WeightUnit = WeightUnit.Kg, val water: WaterUnit = WaterUnit.Ml, val waterGoalMl: Int)

/**
 * Turns [SummaryContent] into lines, in the order they read top to bottom. [shown] is which of the
 * chosen numbers appear: every one in the PDF, and in the preview only those the mode shows
 * outright, so a number the mode keeps out of sight is never on the phone's screen. [format] is
 * the policy that spells them, which for those numbers always shows them.
 */
object SummaryLines {

    /** The separator between numbers on a numbers line, in the PDF. */
    const val NUMBERS_SEPARATOR = "  ·  "

    fun build(
        content: SummaryContent,
        shown: Set<Metric>,
        format: VisibilityPolicy,
        units: SummaryUnits,
        madeOn: LocalDate,
    ): List<SummaryLine> = buildList {
        val range = content.range
        add(line(SummaryText.Res(Res.string.summary_doc_title), PdfStyle.Title))
        add(
            line(
                SummaryText.Res(
                    Res.string.summary_doc_range,
                    listOf(SummaryText.Plain(DateCopy.range(range.start, range.end)), SummaryText.Plain(range.end.year.toString())),
                ),
                PdfStyle.Subtitle,
            ),
        )
        add(line(SummaryText.Res(Res.string.summary_doc_made, listOf(SummaryText.Plain(DateCopy.monthDay(madeOn) + ", " + madeOn.year))), PdfStyle.Small))
        add(line(SummaryText.Res(Res.string.summary_doc_included, listOf(SummaryText.Joined(included(content.choices)))), PdfStyle.Small))
        if (content.choices.numbers && shown.isNotEmpty()) add(line(SummaryText.Res(Res.string.summary_doc_numbers_note), PdfStyle.Small))

        content.days.forEach { day ->
            add(line(SummaryText.Plain(DateCopy.longWeekdayMonthDay(day.date)), PdfStyle.Heading))
            when {
                day.untracked -> add(line(SummaryText.Res(Res.string.summary_doc_untracked), PdfStyle.Small))
                day.isEmpty -> add(line(SummaryText.Res(Res.string.summary_doc_nothing), PdfStyle.Small))
            }
            day.meals.forEach { meal ->
                val slot = SummaryText.Res(meal.slot.label)
                add(
                    line(
                        when {
                            meal.time != null -> SummaryText.Res(
                                Res.string.summary_doc_meal_time,
                                listOf(slot, SummaryText.Plain(DateCopy.time(meal.time.hour, meal.time.minute))),
                            )
                            meal.ateNoDetails && meal.foods.isEmpty() -> SummaryText.Res(Res.string.summary_doc_ate_no_details, listOf(slot))
                            else -> slot
                        },
                        PdfStyle.Label,
                    ),
                )
                if (meal.foods.isNotEmpty()) add(line(SummaryText.Plain(meal.foods.joinToString("; ")), indented = true))
                meal.hunger?.let { add(line(SummaryText.Res(Res.string.summary_doc_hunger, listOf(SummaryText.Res(it.label))), indented = true)) }
                meal.note?.let { add(line(SummaryText.Res(Res.string.summary_doc_meal_note, listOf(SummaryText.Plain(it))), indented = true)) }
            }
            day.note?.let { add(line(SummaryText.Res(Res.string.summary_doc_day_note, listOf(SummaryText.Plain(it))))) }
            day.feeling?.let { add(line(SummaryText.Res(Res.string.summary_doc_feeling, listOf(SummaryText.Res(it.label))))) }
            day.checkIn?.let { add(line(checkIn(it))) }
            numbers(day.totals, day.waterMl, shown, format, units, day.date)?.let(::add)
        }

        content.weighIns?.takeIf { Metric.Weight in shown }?.let { weighIns ->
            add(line(SummaryText.Res(Res.string.summary_doc_weight), PdfStyle.Heading))
            if (weighIns.isEmpty()) add(line(SummaryText.Res(Res.string.summary_doc_no_weight), PdfStyle.Small))
            weighIns.forEach { weighIn ->
                val text = (format.resolve(Metric.Weight, weighIn.kg, Surface.Settings, FormatContext(weightUnit = units.weight, dayClosed = true)) as? Display.Shown)?.text
                    ?: return@forEach
                add(
                    SummaryLine(
                        listOf(SummaryLinePart(SummaryText.Plain("${DateCopy.weekdayMonthDay(weighIn.date)}: $text"), Metric.Weight)),
                    ),
                )
            }
        }

        content.medication?.let { addAll(medication(it)) }
    }

    /**
     * "Medication", its name, a line per dose ("Thu, Oct 23: 1 mg, upper left abdomen"), each change
     * of dose, then the side effect days with how bad. No dose is a food number, so no mode hides them.
     */
    private fun medication(medication: SummaryMedication): List<SummaryLine> = buildList {
        add(line(SummaryText.Res(Res.string.summary_doc_medication), PdfStyle.Heading))
        add(line(medication.name.summaryText(), PdfStyle.Label))
        if (medication.doses.isEmpty()) add(line(SummaryText.Res(Res.string.summary_doc_medication_none), PdfStyle.Small))
        medication.doses.forEach { dose ->
            val amount = amount(dose.dose, dose.unit)
            val what = dose.site?.let { SummaryText.Joined(listOf(amount, SummaryText.Res(it.inSentence))) } ?: amount
            add(
                line(
                    SummaryText.Res(
                        Res.string.summary_doc_medication_dose,
                        listOf(SummaryText.Plain("${DateCopy.weekdayMonthDay(dose.date)}, ${DateCopy.time(dose.time.hour, dose.time.minute)}"), what),
                    ),
                    indented = true,
                ),
            )
        }
        medication.changes.forEach { change ->
            add(
                line(
                    SummaryText.Res(
                        Res.string.summary_doc_medication_changed,
                        listOf(amount(change.dose, change.unit), SummaryText.Plain(DateCopy.monthDay(change.day))),
                    ),
                ),
            )
        }
        if (medication.sideEffects.isNotEmpty()) {
            add(line(SummaryText.Res(Res.string.summary_doc_side_effects), PdfStyle.Label))
            medication.sideEffects.forEach { day ->
                val felt = day.symptoms.entries.sortedBy { it.key.ordinal }.map { (symptom, severity) ->
                    SummaryText.Res(Res.string.medication_symptom_with_severity, listOf(SummaryText.Res(symptom.label), SummaryText.Res(severity.label)))
                }
                add(
                    line(
                        SummaryText.Res(Res.string.summary_doc_side_effects_day, listOf(SummaryText.Plain(DateCopy.weekdayMonthDay(day.day)), SummaryText.Joined(felt))),
                        indented = true,
                    ),
                )
            }
        }
    }

    private fun amount(dose: Double, unit: DoseUnit): SummaryText = SummaryText.Res(unit.amount, listOf(SummaryText.Plain(DoseFormat.number(dose))))

    private fun MedicationName.summaryText(): SummaryText = when (this) {
        is MedicationName.Listed -> SummaryText.Res(label)
        is MedicationName.Own -> SummaryText.Plain(text)
    }

    private fun included(choices: SummaryChoices): List<SummaryText> = SummaryPart.entries.filter(choices::has).map {
        SummaryText.Res(
            when (it) {
                SummaryPart.MealsAndTimes -> Res.string.summary_include_meals
                SummaryPart.Notes -> Res.string.summary_include_notes
                SummaryPart.Hunger -> Res.string.summary_include_hunger
                SummaryPart.CheckIns -> Res.string.summary_include_check_ins
                SummaryPart.Weight -> Res.string.summary_include_weight
                SummaryPart.Numbers -> Res.string.summary_include_numbers
                SummaryPart.Medication -> Res.string.summary_include_medication
            },
        )
    }

    private fun checkIn(checkIn: CheckIn): SummaryText = SummaryText.Joined(
        listOfNotNull(
            checkIn.hunger?.let { pair(Res.string.today_checkin_hunger, it.label) },
            checkIn.energy?.let { pair(Res.string.today_checkin_energy, it.label) },
            checkIn.mood?.let { pair(Res.string.today_checkin_mood_level, it.label) },
            checkIn.sleep?.let { pair(Res.string.today_checkin_sleep, it.label) },
            checkIn.moods.takeIf { it.isNotEmpty() }?.let { moods ->
                SummaryText.Res(Res.string.summary_doc_moods, listOf(SummaryText.Joined(moods.sortedBy { it.ordinal }.map { SummaryText.Res(it.label) })))
            },
        ),
        separator = ". ",
    )

    private fun pair(label: StringResource, value: StringResource) =
        SummaryText.Res(Res.string.summary_doc_pair, listOf(SummaryText.Res(label), SummaryText.Res(value)))

    /** The day's numbers that are [shown], each through the policy; null when none of them has a value. */
    private fun numbers(
        totals: Totals?,
        waterMl: Int?,
        shown: Set<Metric>,
        format: VisibilityPolicy,
        units: SummaryUnits,
        day: LocalDate,
    ): SummaryLine? {
        val context = FormatContext(goal = GoalStyle.None, weightUnit = units.weight, unit = units.water, day = day, dayClosed = true)
        fun part(metric: Metric, value: Double?, label: StringResource, of: Double? = null): SummaryLinePart? {
            if (metric !in shown || value == null) return null
            val display = format.resolve(metric, value, Surface.Settings, context.copy(of = of)) as? Display.Shown ?: return null
            return SummaryLinePart(SummaryText.Res(label, listOf(SummaryText.Plain(display.text))), metric)
        }
        val parts = listOfNotNull(
            totals?.let { part(Metric.Calories, it.kcal.shownValue, Res.string.summary_doc_calories) },
            totals?.let { part(Metric.Protein, it.protein.shownValue, Res.string.summary_doc_protein) },
            totals?.let { part(Metric.Carbs, it.carbs.shownValue, Res.string.summary_doc_carbs) },
            totals?.let { part(Metric.Fat, it.fat.shownValue, Res.string.summary_doc_fat) },
            totals?.let { part(Metric.Fiber, it.fiber.shownValue, Res.string.summary_doc_fiber) },
            part(Metric.Water, waterMl?.toDouble(), Res.string.summary_doc_water, of = units.waterGoalMl.toDouble()),
        )
        return parts.takeIf { it.isNotEmpty() }?.let { SummaryLine(it, PdfStyle.Small) }
    }

    /** A total with no food that had the number says nothing, never a 0. */
    private val Total.shownValue: Double? get() = value?.takeIf { knownCount > 0 }

    private fun line(text: SummaryText, style: PdfStyle = PdfStyle.Body, indented: Boolean = false) =
        SummaryLine(listOf(SummaryLinePart(text)), style, indented)
}
