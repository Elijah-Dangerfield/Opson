package com.opson.features.today.impl

import com.opson.libraries.diary.FlipFigure
import com.opson.libraries.diary.MainNumberChoice
import com.opson.libraries.diary.Reading
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Visibility

/**
 * Which readings each flippable figure on Today's card may show (owner feedback, 2026-10-29: "a
 * lot of the data we display has a flip side"). A reading is offered only where the levels show
 * it in full on Today, so a flip never uncovers what the mode keeps back: a figure whose other
 * side is hidden has one reading and no ⇄. After "numbers have been hard" a figure never flips
 * into a reading framed as a budget or a gap to close (a deficit the person didn't set, grams
 * "left"), only into the plain record of what was eaten or a share of the goal.
 *
 * [level] is the effective level on Today for the day on show. The builder reads the choice
 * through [com.opson.libraries.diary.Flips.pick], so one the mode stops allowing falls back to the
 * first reading here rather than showing.
 */
object TodayFlips {
    /**
     * The main number on design sync 4's card: the gap to the goal (needs the remainder on show),
     * the deficit or surplus against Burned ([burnedShown]: known, and energy out on show), then
     * eaten. After "numbers have been hard" the balance is offered only where the goal style is a
     * set deficit or surplus ([balanceIsTheGoal]), which the person picked on Targets.
     */
    fun mainNumber(level: (Metric) -> Visibility, burnedShown: Boolean, hard: Boolean, balanceIsTheGoal: Boolean): List<MainNumberChoice> {
        if (level(Metric.Calories) != Visibility.Show) return emptyList()
        return buildList {
            if (level(Metric.Remaining) == Visibility.Show) add(MainNumberChoice.Goal)
            if (burnedShown && level(Metric.EnergyOut) == Visibility.Show && (!hard || balanceIsTheGoal)) add(MainNumberChoice.Balance)
            add(MainNumberChoice.Eaten)
        }
    }

    /**
     * The macro row: grams eaten, grams left, a percent of the goal. Only when some macro on the
     * row is on show with a goal to count against ([anyWithGoal]); a softened macro keeps its word
     * whichever way the row reads.
     */
    fun macros(anyWithGoal: Boolean, hard: Boolean): List<Reading> = when {
        !anyWithGoal -> emptyList()
        hard -> listOf(Reading.Eaten, Reading.Percent)
        else -> listOf(Reading.Eaten, Reading.Left, Reading.Percent)
    }

    /** The older card's Deficit so far and Burned so far. The stat itself needs both energies on show, so both readings do too. */
    fun deficitSoFar(level: (Metric) -> Visibility): List<Reading> =
        if (level(Metric.EnergyOut) == Visibility.Show && level(Metric.Calories) == Visibility.Show) {
            listOf(Reading.Deficit, Reading.Burned)
        } else {
            emptyList()
        }

    /** Glasses drunk and glasses to go: one count either way, so wherever the water stat shows at all. */
    fun water(level: (Metric) -> Visibility): List<Reading> =
        if (level(Metric.Water) != Visibility.Hide) listOf(Reading.Eaten, Reading.Left) else emptyList()
}

/** A figure that flips: what it shows now, and what a tap shows. Absent when there is only one reading. */
data class FlipState(val figure: FlipFigure, val current: Reading, val next: Reading)
