package com.opson.features.settings.impl.summary

import com.opson.features.settings.impl.SettingsFixtures
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.CheckInMood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayFeeling
import com.opson.libraries.diary.DayHunger
import com.opson.libraries.diary.Energy
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Sleep
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.toInstant

/**
 * A week of someone's log for the provider summary's previews and leak surfaces: three days with
 * meals, hunger, notes and a check-in, a day left out, and two weigh-ins. Every state goes
 * through [ProviderSummaryStateBuilder], so the leak test checks the real mapping.
 */
object ProviderSummaryFixtures {

    val today: LocalDate = LocalDate(2026, 10, 25)
    private val zone = TimeZone.UTC

    private fun item(day: LocalDate, slot: MealSlot, at: LocalTime, name: String, amount: String, kcal: Double, protein: Double, carbs: Double, fat: Double, fiber: Double) =
        LogItem(
            id = "$day-$slot-$name",
            day = day,
            slot = slot,
            loggedAt = LocalDateTime(day, at).toInstant(zone),
            name = name,
            precision = Precision.Estimate,
            method = LogMethod.Search,
            amountText = amount,
            kcal = kcal,
            protein = protein,
            carbs = carbs,
            fat = fat,
            fiber = fiber,
        )

    private fun day(date: LocalDate, vararg items: LogItem) = MealSlot.entries.associateWith { slot -> items.filter { it.slot == slot } }

    val days: List<Day> = run {
        val sat = today.minus(DatePeriod(days = 1))
        val fri = today.minus(DatePeriod(days = 2))
        val week = (6 downTo 0).map { today.minus(DatePeriod(days = it)) }
        week.map { date ->
            when (date) {
                today -> Day(
                    date = date,
                    itemsBySlot = day(
                        date,
                        item(date, MealSlot.Breakfast, LocalTime(8, 5), "Porridge", "1 bowl", 320.0, 11.0, 54.0, 6.5, 8.0),
                        item(date, MealSlot.Breakfast, LocalTime(8, 6), "Coffee with milk", "1 mug", 45.0, 2.0, 4.0, 2.0, 0.0),
                        item(date, MealSlot.Lunch, LocalTime(12, 40), "Tomato soup", "1 bowl", 210.0, 5.0, 28.0, 8.0, 4.0),
                    ),
                    waterMl = 1500,
                    feelings = mapOf(
                        MealSlot.Breakfast to MealFeeling(hunger = Hunger.Hungry, note = "Before the train"),
                        MealSlot.Lunch to MealFeeling(hunger = Hunger.Neutral),
                    ),
                    note = "A steadier day than yesterday",
                    feeling = DayFeeling.Steady,
                    checkIn = CheckIn(hunger = DayHunger.Steady, energy = Energy.Good, sleep = Sleep.Ok, moods = setOf(CheckInMood.Calm)),
                )
                sat -> Day(
                    date = date,
                    itemsBySlot = day(date, item(date, MealSlot.Dinner, LocalTime(19, 15), "Pasta with pesto", "1 plate", 640.0, 18.0, 82.0, 24.0, 6.0)),
                    ateNoDetails = setOf(MealSlot.Lunch),
                    feelings = mapOf(MealSlot.Dinner to MealFeeling(hunger = Hunger.Starving, note = "Skipped the afternoon snack")),
                    checkIn = CheckIn(hunger = DayHunger.Often, sleep = Sleep.Poor),
                )
                fri -> Day(date = date, untracked = true)
                else -> Day.empty(date)
            }
        }
    }

    val weighIns: List<WeighIn> = listOf(
        WeighIn("w1", LocalDateTime(today.minus(DatePeriod(days = 4)), LocalTime(7, 0)).toInstant(zone), 61.8),
        WeighIn("w2", LocalDateTime(today, LocalTime(7, 10)).toInstant(zone), 62.1),
    )

    fun policy(preset: PresetId): VisibilityPolicy =
        VisibilityPolicy(NumbersSettings(preset = preset), SettingsFixtures.clock, zone)

    /** Every part switched on, in [policy]'s mode, a week back from [today]. */
    fun input(policy: VisibilityPolicy, everything: Boolean = true): SummaryInput {
        val settings = SettingsFixtures.settings(numbers = policy.settings)
        return SummaryInput(
            today = today,
            zone = zone,
            settings = settings,
            range = SummaryRangeChoice.Week,
            dates = SummaryRange.last(SummaryRangeChoice.Week.days ?: 0, today),
            choices = if (everything) SummaryChoices(weight = true, numbers = true) else SummaryChoices.defaultsFor(settings.numbers),
            days = days,
            weighIns = weighIns,
            warned = everything,
        )
    }

    fun state(policy: VisibilityPolicy = policy(PresetId.Full)): ProviderSummaryState =
        ProviderSummaryStateBuilder.build(input(policy), policy)

    /** No numbers, with weight and numbers switched in after the question. */
    fun hiddenState(policy: VisibilityPolicy = policy(PresetId.NoNumbers)): ProviderSummaryState = state(policy)

    /** The mode's own starting choices, and the question open over them. */
    fun warningState(policy: VisibilityPolicy = policy(PresetId.NoNumbers)): ProviderSummaryState =
        ProviderSummaryStateBuilder.build(input(policy, everything = false).copy(confirming = SummaryPart.Numbers), policy)

    /** Pick dates, the range running Oct 19 to today. */
    fun customState(policy: VisibilityPolicy = policy(PresetId.Full)): ProviderSummaryState = ProviderSummaryStateBuilder.build(
        input(policy).copy(range = SummaryRangeChoice.Custom, dates = SummaryRange(LocalDate(2026, 10, 19), today)),
        policy,
    )
}
