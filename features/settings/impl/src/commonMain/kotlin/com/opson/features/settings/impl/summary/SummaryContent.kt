package com.opson.features.settings.impl.summary

import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Totals
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.hidesAnyOf
import com.opson.libraries.medication.DoseMarks
import com.opson.libraries.medication.DoseUnit
import com.opson.libraries.medication.InjectionSite
import com.opson.libraries.medication.MedicationName
import com.opson.libraries.medication.MedicationRecord
import com.opson.libraries.medication.SymptomDay
import com.opson.libraries.medication.displayName
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.Visibility
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime

/**
 * What the person picked to go in. Each part is theirs to switch; [weight] and [numbers] start
 * on only when their mode shows those numbers outright ([defaultsFor]).
 */
data class SummaryChoices(
    val mealsAndTimes: Boolean = true,
    val notes: Boolean = true,
    val hunger: Boolean = true,
    val checkIns: Boolean = true,
    val weight: Boolean = false,
    val numbers: Boolean = false,
    /** Doses, changes of dose and side effect days (`release.medication`). On when tracking is. */
    val medication: Boolean = false,
) {
    /** The numbers these choices put in the PDF. */
    val metrics: Set<Metric>
        get() = buildSet {
            if (numbers) addAll(NUMBER_METRICS)
            if (weight) add(Metric.Weight)
        }

    val isEmpty: Boolean get() = !mealsAndTimes && !notes && !hunger && !checkIns && !weight && !numbers && !medication

    fun with(part: SummaryPart, on: Boolean): SummaryChoices = when (part) {
        SummaryPart.MealsAndTimes -> copy(mealsAndTimes = on)
        SummaryPart.Notes -> copy(notes = on)
        SummaryPart.Hunger -> copy(hunger = on)
        SummaryPart.CheckIns -> copy(checkIns = on)
        SummaryPart.Weight -> copy(weight = on)
        SummaryPart.Numbers -> copy(numbers = on)
        SummaryPart.Medication -> copy(medication = on)
    }

    fun has(part: SummaryPart): Boolean = when (part) {
        SummaryPart.MealsAndTimes -> mealsAndTimes
        SummaryPart.Notes -> notes
        SummaryPart.Hunger -> hunger
        SummaryPart.CheckIns -> checkIns
        SummaryPart.Weight -> weight
        SummaryPart.Numbers -> numbers
        SummaryPart.Medication -> medication
    }

    companion object {
        /** Calories, the macros and water: the "Calories, macros and water" switch. */
        val NUMBER_METRICS: List<Metric> = listOf(Metric.Calories, Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber, Metric.Water)

        fun defaultsFor(numbers: NumbersSettings): SummaryChoices = SummaryChoices(
            weight = numbers.level(Metric.Weight) == Visibility.Show,
            numbers = !numbers.hidesAnyOf(NUMBER_METRICS),
        )
    }
}

/** One switch under What goes in, in the order the screen lists them. */
enum class SummaryPart(val metrics: List<Metric>) {
    MealsAndTimes(emptyList()),
    Notes(emptyList()),
    Hunger(emptyList()),
    CheckIns(emptyList()),
    Weight(listOf(Metric.Weight)),
    Numbers(SummaryChoices.NUMBER_METRICS),

    /** Only listed while there is a medication to tell of. A dose is not a food number: no mode hides it. */
    Medication(emptyList()),
}

/** Which days, inclusive. */
data class SummaryRange(val start: LocalDate, val end: LocalDate) {
    init {
        require(start <= end) { "A range runs forwards: $start to $end" }
    }

    companion object {
        /** The longest range one summary covers, so the PDF stays a size someone will read. */
        const val MAX_DAYS = 90

        /** The [days] days ending [today]. */
        fun last(days: Int, today: LocalDate): SummaryRange = SummaryRange(today.minus(DatePeriod(days = days - 1)), today)
    }
}

/** One day as the summary tells it. Only what was picked is filled in. */
data class SummaryDay(
    val date: LocalDate,
    val untracked: Boolean,
    val meals: List<SummaryMeal>,
    val note: String?,
    val feeling: DayFeeling?,
    val checkIn: CheckIn?,
    /** The day's totals and water, when numbers are in. */
    val totals: Totals?,
    val waterMl: Int?,
) {
    val isEmpty: Boolean
        get() = meals.isEmpty() && note == null && feeling == null && checkIn == null && totals == null && waterMl == null
}

/**
 * A meal with something in it. [time] is the first food's, on the phone's clock; [foods] are
 * names with their amounts ("Porridge, 1 bowl"), only with meals and times in.
 */
data class SummaryMeal(
    val slot: MealSlot,
    val time: LocalTime?,
    val foods: List<String>,
    val ateNoDetails: Boolean,
    val hunger: Hunger?,
    val note: String?,
)

data class SummaryWeighIn(val date: LocalDate, val kg: Double)

data class SummaryContent(
    val range: SummaryRange,
    val choices: SummaryChoices,
    val days: List<SummaryDay>,
    /** Null when weight is not in. */
    val weighIns: List<SummaryWeighIn>?,
    /** Null when medication is not in, or there is none. */
    val medication: SummaryMedication? = null,
)

/**
 * The medication section: what it is, the doses in the range, the changes of dose in it (read
 * from every dose, so a step up on the range's first day counts), and the side effect days.
 */
data class SummaryMedication(
    val name: MedicationName,
    val doses: List<SummaryDose>,
    val changes: List<DoseMarks.Mark.Changed>,
    val sideEffects: List<SymptomDay>,
)

data class SummaryDose(val date: LocalDate, val time: LocalTime, val dose: Double, val unit: DoseUnit, val site: InjectionSite?)

/**
 * Picks what goes in from the log: pure, so what a summary holds for each choice is tested
 * without a screen. Every day in the range is listed, so a day with nothing logged says so
 * rather than silently missing.
 */
object SummaryContentBuilder {

    fun build(
        range: SummaryRange,
        choices: SummaryChoices,
        days: List<Day>,
        weighIns: List<WeighIn>,
        zone: TimeZone,
        medication: MedicationRecord? = null,
    ): SummaryContent =
        SummaryContent(
            range = range,
            choices = choices,
            days = days.filter { it.date >= range.start && it.date <= range.end }.sortedBy { it.date }.map { day(it, choices, zone) },
            weighIns = if (choices.weight) {
                weighIns.map { SummaryWeighIn(it.measuredAt.toLocalDateTime(zone).date, it.kg) }
                    .filter { it.date >= range.start && it.date <= range.end }
            } else {
                null
            },
            medication = medication?.takeIf { choices.medication }?.let { medication(it, range, zone) },
        )

    private fun medication(record: MedicationRecord, range: SummaryRange, zone: TimeZone): SummaryMedication {
        fun inRange(day: LocalDate) = day >= range.start && day <= range.end
        return SummaryMedication(
            name = record.medication.displayName(),
            doses = record.doses.filter { inRange(it.day) }.map { dose ->
                val local = dose.takenAt.toLocalDateTime(zone)
                SummaryDose(dose.day, LocalTime(local.hour, local.minute), dose.dose, dose.unit, dose.site)
            },
            changes = DoseMarks.of(record.medication, record.doses).filterIsInstance<DoseMarks.Mark.Changed>().filter { inRange(it.day) },
            sideEffects = record.sideEffects.filter { inRange(it.day) && it.symptoms.isNotEmpty() },
        )
    }

    private fun day(day: Day, choices: SummaryChoices, zone: TimeZone): SummaryDay = SummaryDay(
        date = day.date,
        untracked = day.untracked,
        meals = MealSlot.entries.mapNotNull { meal(day, it, choices, zone) },
        note = day.note?.takeIf { choices.notes && it.isNotBlank() },
        feeling = day.feeling?.takeIf { choices.checkIns },
        checkIn = day.checkIn?.takeIf { choices.checkIns && !it.isEmpty },
        totals = day.totals().takeIf { choices.numbers && day.allItems.isNotEmpty() },
        waterMl = day.waterMl.takeIf { choices.numbers && it > 0 },
    )

    private fun meal(day: Day, slot: MealSlot, choices: SummaryChoices, zone: TimeZone): SummaryMeal? {
        val items = day.items(slot)
        val feeling = day.feelings[slot]
        val meal = SummaryMeal(
            slot = slot,
            time = items.minOfOrNull { it.loggedAt }?.toLocalDateTime(zone)?.time?.takeIf { choices.mealsAndTimes },
            foods = if (choices.mealsAndTimes) items.map { item -> listOf(item.name, item.amountLabel).filter(String::isNotBlank).joinToString(", ") } else emptyList(),
            ateNoDetails = choices.mealsAndTimes && slot in day.ateNoDetails,
            hunger = feeling?.hunger?.takeIf { choices.hunger },
            note = feeling?.note?.takeIf { choices.notes && it.isNotBlank() },
        )
        return meal.takeIf { it.foods.isNotEmpty() || it.ateNoDetails || it.hunger != null || it.note != null }
    }
}
