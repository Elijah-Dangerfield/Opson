package com.opson.features.trends.impl

import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.JournalFacts
import com.opson.libraries.diary.MealJournal
import com.opson.libraries.diary.MealPatterns
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Mood
import com.opson.libraries.diary.TrendsRange
import com.opson.libraries.diary.ui.label
import com.opson.libraries.ui.components.ChartReading
import com.opson.libraries.ui.components.ChartReadout
import com.opson.libraries.ui.components.IntakeBar
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

/**
 * The Hunger card, read from the meal notes over the page's span. Hunger has no amount, so every
 * mode gets it, Blind included; and it is information, not a score, so nothing on it is coloured
 * as better or worse.
 */
sealed interface HungerTrend {
    /** No hunger level on any meal in the span yet: one plain sentence instead of an empty chart. */
    data object Empty : HungerTrend

    data class Chart(
        /** How many meals the card reads. */
        val answers: Int,
        /**
         * A bar per day on Week, per week on the longer ranges: the average hunger, a fifth of the
         * height for stuffed up to full height for starving, so an answered day never reads as a
         * gap. `null` where nothing was noted.
         */
        val bars: List<IntakeBar>,
        val perWeek: Boolean,
        /** The four meals in order, for those with any hunger. */
        val meals: List<MealHunger>,
        /** Each bar inspected, `null` where nothing was noted: its day or week, and the nearest answer to its average. */
        val readouts: List<ChartReadout?> = emptyList(),
    ) : HungerTrend
}

/**
 * One meal's row on the stuffed-to-starving track, both ends `0..1`: [average] is the dot, [spread]
 * the lowest to the highest answer behind it, and [usual] the answer given most.
 */
data class MealHunger(val slot: MealSlot, val average: Float, val spread: ClosedFloatingPointRange<Float>, val usual: Hunger)

/** A value and how many meals had it. */
data class Tally<T>(val value: T, val meals: Int)

/**
 * The Notes card: how many meals carry a note, and what older "How it felt" rows said, each part
 * only when someone answered it. Never the note text: a note is the person's own words, and a
 * number typed into one would turn into an amount on a chart.
 */
data class JournalCard(
    val noted: Int,
    val meals: Int,
    /** In the scale's order, only the ones picked. */
    val fullness: List<Tally<Fullness>>,
    /** Most often first. */
    val moods: List<Tally<Mood>>,
    /** "All notes", when a Notes list is there to open ([com.opson.features.trends.NotesListLink]). */
    val linksToNotes: Boolean,
)

/** Hunger and Notes for [TrendsStateBuilder]. Pure, over [MealJournal]'s facts. */
internal object TrendsJournal {

    /** The steps the bars stack, one more than the levels so stuffed still stands. */
    private val BAR_STEPS = MealPatterns.HUNGER_STEPS + 1

    fun facts(inputs: TrendsInputs, span: TrendsSpan): JournalFacts =
        MealJournal.of(inputs.days, span.start, minOf(span.end, inputs.today))

    fun hunger(inputs: TrendsInputs, span: TrendsSpan, facts: JournalFacts): HungerTrend {
        if (facts.hungerMeals == 0) return HungerTrend.Empty
        val points: List<Pair<IntakeBar, ChartReadout?>> = when (span.range) {
            TrendsRange.Week -> span.days.map { date ->
                val levels = facts.hungerByDay[date]
                IntakeBar(TrendsCalendar.weekdayLabel(date), barHeight(levels), isCurrent = date == inputs.today) to
                    readout(TrendsReadouts.day(date, inputs.today), levels)
            }
            TrendsRange.FourWeeks, TrendsRange.ThreeMonths -> {
                val starts = TrendsCalendar.weekStarts(inputs.today, inputs.settings.weekStart, TrendsCalendar.barCount(span.range))
                starts.mapIndexed { index, start ->
                    val dates = (0 until TrendsCalendar.DAYS_IN_WEEK).map { start.plus(it, DateTimeUnit.DAY) }.filter { it <= inputs.today }
                    val levels = dates.flatMap { facts.hungerByDay[it].orEmpty() }
                    IntakeBar(TrendsStateBuilder.weekBarLabel(starts, index, span.range), barHeight(levels), isCurrent = index == starts.lastIndex) to
                        readout(TrendsReadouts.week(start, dates.last()), levels)
                }
            }
        }
        return HungerTrend.Chart(
            answers = facts.hungerMeals,
            bars = points.map { it.first },
            readouts = points.map { it.second },
            perWeek = span.range != TrendsRange.Week,
            meals = MealSlot.meals.mapNotNull { slot ->
                facts.hungerBySlot[slot]?.let {
                    MealHunger(slot, position(it.average), position(it.lowest.toDouble())..position(it.highest.toDouble()), it.usual)
                }
            },
        )
    }

    /** `null` with nothing written in the span: the Hunger card's empty sentence says enough. */
    fun journal(facts: JournalFacts, linksToNotes: Boolean): JournalCard? {
        if (facts.isEmpty) return null
        return JournalCard(
            noted = facts.notedMeals,
            meals = facts.meals,
            fullness = Fullness.entries.mapNotNull { value -> facts.fullness[value]?.let { Tally(value, it) } },
            moods = facts.moods.entries
                .sortedWith(compareByDescending<Map.Entry<Mood, Int>> { it.value }.thenBy { it.key.ordinal })
                .map { Tally(it.key, it.value) },
            linksToNotes = linksToNotes,
        )
    }

    /** A bar's point: nothing where nothing was noted, the same rule as [barHeight]'s gap. */
    private fun readout(title: String, levels: List<Int>?): ChartReadout? =
        levels?.takeIf { it.isNotEmpty() }?.let { ChartReadout(title, listOf(ChartReading(word = TrendsReadouts.hungerWord(it).label))) }

    private fun barHeight(levels: List<Int>?): Float? =
        levels?.takeIf { it.isNotEmpty() }?.let { ((it.average() + 1) / BAR_STEPS).toFloat() }

    private fun position(level: Double): Float = (level / MealPatterns.HUNGER_STEPS).toFloat()
}
