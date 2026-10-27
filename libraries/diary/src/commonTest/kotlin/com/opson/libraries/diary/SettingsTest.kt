package com.opson.libraries.diary

import com.opson.libraries.nutrition.DayGroup
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.RangeEnd
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.FastingPlan
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RevealOn
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.WaterUnit
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * [Settings] defaults against the prototype's `DEFAULTS`
 * (`docs/design/handoff/phone-logic.js`) and the shared plan's table, and the
 * JSON they persist and sync as, including what an unknown value decodes to.
 *
 * Not covered: when `changedAt` moves, which the settings store owns
 * (`SettingsStoreTest` in `:libraries:diary:impl`).
 */
class SettingsTest {

    @Test
    fun defaultsMatchThePrototype() {
        val s = Settings()

        // mode:'full', overrides:{}, lock:false, changedAt:null
        assertEquals(NumbersSettings(preset = PresetId.Full), s.numbers)
        assertEquals(emptyMap(), s.numbers.overrides)
        assertEquals(false, s.numbers.pauseBeforeMore)
        assertNull(s.numbers.changedAt)
        // exercise:'some', quiet:true, tone:'plain', weekStart:'Mon', askTags:true
        assertEquals(ExerciseAdjustment.Some, s.exercise)
        assertEquals(true, s.quiet)
        assertEquals(Tone.Plain, s.tone)
        assertEquals(DayOfWeek.MONDAY, s.weekStart)
        assertEquals(true, s.askTags)
        // events:{bigday:'full', newfood:'subtle', recap:'subtle', reminders:'subtle'}
        assertEquals(EventLevel.Full, s.bigDayEvent)
        assertEquals(EventLevel.Subtle, s.newFoodEvent)
        assertEquals(EventLevel.Subtle, s.recapEvent)
        assertEquals(EventLevel.Subtle, s.remindersEvent)
        // mealRem:{Breakfast:true, Lunch:true, Snack:false, Dinner:true}
        assertEquals(MealReminders(breakfast = true, lunch = true, snack = false, dinner = true), s.mealReminders)
        // Not in the prototype: the times Today used before they were a setting, and the opt-in.
        assertEquals(MealTimes(LocalTime(8, 0), LocalTime(12, 30), LocalTime(15, 30), LocalTime(19, 0)), s.mealTimes)
        assertEquals(false, s.remindersChosen)
        assertEquals(EventLevel.Off, s.mealReminderLevel)
        // ringMode:'target', trRange:'1W', trCards: all true
        assertEquals(RingMode.Target, s.ringMode)
        assertEquals(TrendsRange.Week, s.trendsRange)
        assertEquals(TrendsCards(intake = true, recap = true, nutrients = true, expended = true, weight = true), s.trendsCards)
        // waterGoal:8, glassMl:250, waterUnit:'ml', waterRem:false
        assertEquals(8, s.waterGlasses)
        assertEquals(250, s.glassMl)
        assertEquals(2_000, s.waterGoalMl)
        assertEquals(WaterUnit.Ml, s.waterUnit)
        assertEquals(false, s.waterReminders)
        // fasting:false, fastToday:true, fastPlan:'14:10'
        assertEquals(false, s.fasting)
        assertEquals(true, s.fastToday)
        assertEquals(FastingPlan.FourteenTen, s.fastPlan)
        // Undefined in DEFAULTS; the prototype's reads treat undefined as these.
        assertEquals(true, s.mealGroups) // S.mealGroups !== false
        assertNull(s.healthToday) // S.healthToday ?? (preset is Full)
        assertEquals(true, s.healthOpen) // S.healthOpen !== false
        assertEquals(
            HealthRows(steps = true, heartRate = true, restingHeartRate = true, hrv = false, sleep = true, vo2Max = false, workouts = true),
            s.healthRows,
        )
        // The shared plan's additions.
        assertEquals(Targets.Default, s.targets)
        assertEquals(MacroMode.Ratios, s.macroMode)
        assertEquals(WeightUnit.Kg, s.weightUnit)
        assertEquals(PaletteChoice.Pine, s.palette)
        assertEquals(ThemeMode.System, s.theme)
        assertEquals(TextSize.Normal, s.textSize)
        assertNull(s.modeTimerEnded)
        assertEquals(OnboardingAnswers(), s.onboarding)
    }

    @Test
    fun everyFieldSurvivesAJsonRoundTrip() {
        val s = Settings(
            numbers = NumbersSettings(
                preset = PresetId.Later,
                overrides = mapOf(Metric.Water to Visibility.Hide, Metric.Protein to Visibility.Soften),
                pauseBeforeMore = true,
                changedAt = Instant.parse("2026-10-20T08:30:00Z"),
            ),
            modeTimerEnded = ModeTimerEnded(LocalDate(2026, 10, 1), PresetId.Full),
            targets = Targets.Default.raise(),
            macroMode = MacroMode.Grams,
            exercise = ExerciseAdjustment.None,
            ringMode = RingMode.Remaining,
            trendsRange = TrendsRange.ThreeMonths,
            weekStart = DayOfWeek.SUNDAY,
            waterUnit = WaterUnit.FlOz,
            weightUnit = WeightUnit.Lb,
            fastPlan = FastingPlan.SixteenEight,
            palette = PaletteChoice.Espresso,
            onboarding = OnboardingAnswers(why = listOf("curious"), feel = "rather_not", hard = true),
            mealTimes = MealTimes(breakfast = LocalTime(7, 15), dinner = LocalTime(18, 45)),
            remindersChosen = true,
        )

        val decoded = SettingsJson.decodeFromString(Settings.serializer(), SettingsJson.encodeToString(Settings.serializer(), s))

        assertEquals(s, decoded)
    }

    @Test
    fun theWireSpellingIsThePrototypes() {
        val json = SettingsJson.encodeToString(
            Settings.serializer(),
            Settings(
                numbers = NumbersSettings(preset = PresetId.Later, overrides = mapOf(Metric.EnergyOut to Visibility.Soften), revealOn = RevealOn.Week),
                waterUnit = WaterUnit.FlOz,
            ),
        )

        listOf("\"preset\":\"coast\"", "\"preset\":\"later\"", "\"revealOn\":\"week\"", "\"energyOut\":\"soften\"", "\"fastPlan\":\"14:10\"", "\"waterUnit\":\"oz\"", "\"exercise\":\"some\"")
            .forEach { assertTrue(it in json, "missing $it in $json") }
    }

    @Test
    fun missingFieldsTakeTheirDefaults() {
        assertEquals(Settings(), SettingsJson.decodeFromString(Settings.serializer(), "{}"))
    }

    @Test
    fun valuesThisBuildDoesNotKnowFailClosed() {
        val decoded = SettingsJson.decodeFromString(
            Settings.serializer(),
            """{"numbers":{"preset":"hyperfocus","overrides":{"calories":"glimpse","sparkle":"show"}},"tone":"sassy","shiny":true}""",
        )

        assertEquals(PresetId.NoNumbers, decoded.numbers.preset, "an unknown mode shows fewer numbers, never more")
        assertEquals(mapOf(Metric.Calories to Visibility.Hide), decoded.numbers.overrides)
        assertEquals(Tone.Plain, decoded.tone)
    }

    @Test
    fun aTargetWithoutItsStartingPointKeepsTheFloorWhereItIs() {
        val decoded = SettingsJson.decodeFromString(
            Settings.serializer(),
            """{"targets":{"energyKcal":2300,"minKcal":2100,"maxKcal":2500,"proteinG":120,"carbsG":250,"fatG":70,"fiberG":30}}""",
        )

        assertEquals(2300, decoded.targets.floorKcal)
    }

    @Test
    fun theRevealTimeTravelsAsHoursAndMinutes_andDefaultsToNine() {
        val s = Settings(numbers = NumbersSettings(preset = PresetId.Later, revealAt = LocalTime(19, 30)))
        val json = SettingsJson.encodeToString(Settings.serializer(), s)
        assertTrue("\"revealAt\":\"19:30\"" in json, json)
        assertEquals(s, SettingsJson.decodeFromString(Settings.serializer(), json))

        // Settings written before v3 have no reveal time.
        val old = SettingsJson.decodeFromString(Settings.serializer(), """{"numbers":{"preset":"delayed"}}""")
        assertEquals(LocalTime(21, 0), old.numbers.revealAt)
        val broken = SettingsJson.decodeFromString(Settings.serializer(), """{"numbers":{"revealAt":"teatime"}}""")
        assertEquals(LocalTime(21, 0), broken.numbers.revealAt)
    }

    @Test
    fun everyGoalStyleSurvivesAJsonRoundTrip() {
        val byDay = requireNotNull(Targets.Default.useByDay().addDayGroup())
            .moveDay(DayOfWeek.MONDAY, 1)
            .step(RangeEnd.High, up = true, group = 1)!!
        listOf(
            Targets.Default,
            Targets.Default.useDeficit(burnKcal = 2420).stepDeficit(up = false)!!,
            byDay,
            Targets.Default.step(RangeEnd.Low, up = true)!!,
        ).forEach { targets ->
            val s = Settings(targets = targets)
            assertEquals(s, SettingsJson.decodeFromString(Settings.serializer(), SettingsJson.encodeToString(Settings.serializer(), s)))
        }
    }

    @Test
    fun targetsFromBeforeGoalStylesAreARange_withTheLowEndPinned() {
        val decoded = SettingsJson.decodeFromString(
            Settings.serializer(),
            """{"targets":{"energyKcal":2100,"minKcal":1900,"maxKcal":2300,"proteinG":120,"carbsG":250,"fatG":70,"fiberG":30}}""",
        )
        assertEquals(TargetStyle.Range, decoded.targets.style)
        assertEquals(1900, decoded.targets.lowFloorKcal)
    }

    @Test
    fun aGoalStyleThatBreaksTheRulesIsRepairedOnTheWayIn() {
        val targets = """"energyKcal":2100,"minKcal":1900,"maxKcal":2300,"proteinG":120,"carbsG":250,"fatG":70,"fiberG":30"""
        fun decode(style: String) = SettingsJson.decodeFromString(
            Settings.serializer(),
            """{"targets":{$targets,"style":$style}}""",
        ).targets.style

        assertEquals(TargetStyle.Range, decode("""{"kind":"crash_diet"}"""))
        // A deficit past where it started is held there.
        assertEquals(TargetStyle.Deficit(150, 150), decode("""{"kind":"deficit","deficitKcal":900,"startingDeficitKcal":150}"""))
        // A day in two groups stays in the first; days in none join the first; a range under the floor takes the base.
        val repaired = decode(
            """{"kind":"by_day","groups":[{"days":["mon","tue"],"minKcal":1900,"maxKcal":2300},""" +
                """{"days":["tue","sat"],"minKcal":1200,"maxKcal":1400}]}""",
        )
        val groups = (repaired as TargetStyle.ByDay).groups
        assertEquals(DayOfWeek.entries.toSet() - DayOfWeek.SATURDAY, groups[0].days)
        assertEquals(DayGroup(setOf(DayOfWeek.SATURDAY), 1900, 2300), groups[1])
    }

    @Test
    fun mealTimesTravelAsHoursAndMinutes_andAnUnreadableOneFallsBackOnItsOwn() {
        val s = Settings(mealTimes = MealTimes(lunch = LocalTime(13, 5)))
        val json = SettingsJson.encodeToString(Settings.serializer(), s)
        assertTrue("\"lunch\":\"13:05\"" in json, json)

        val broken = SettingsJson.decodeFromString(
            Settings.serializer(),
            """{"mealTimes":{"breakfast":"brunch","dinner":"18:30"}}""",
        )
        assertEquals(MealTimes(dinner = LocalTime(18, 30)), broken.mealTimes)
    }

    @Test
    fun settingsFrom01NeverTurnRemindersOn() {
        // What 0.1 synced for everyone: the prototype defaults, with nobody having chosen them.
        val old = SettingsJson.decodeFromString(
            Settings.serializer(),
            """{"remindersEvent":"subtle","mealReminders":{"breakfast":true,"lunch":true,"snack":false,"dinner":true}}""",
        )
        assertEquals(false, old.remindersChosen)
        assertEquals(EventLevel.Off, old.mealReminderLevel)
        assertEquals(EventLevel.Full, old.copy(remindersChosen = true, remindersEvent = EventLevel.Full).mealReminderLevel)
    }

    @Test
    fun mealHelpersLeaveUncategorizedAlone() {
        val reminders = MealReminders()
        assertEquals(reminders, reminders.with(MealSlot.Uncategorized, on = true))
        assertEquals(false, reminders.isOn(MealSlot.Uncategorized))
        assertEquals(true, reminders.with(MealSlot.Snack, on = true).isOn(MealSlot.Snack))
        assertEquals(3, reminders.countOn)
        assertEquals(MealTimes(), MealTimes().with(MealSlot.Uncategorized, LocalTime(1, 0)))
        assertEquals(LocalTime(19, 0), MealTimes().at(MealSlot.Dinner))
    }
}
