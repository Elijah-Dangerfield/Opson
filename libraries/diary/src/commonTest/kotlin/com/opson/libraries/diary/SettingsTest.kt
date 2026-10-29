package com.opson.libraries.diary

import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.DayGroup
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.FastingPlan
import com.opson.libraries.nutrition.GroupShape
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RangeEnd
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.RevealOn
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.WaterUnit
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals(ThemeMode.System, s.theme)
        assertEquals(TextSize.Normal, s.textSize)
        assertNull(s.modeTimerEnded)
        assertEquals(OnboardingAnswers(), s.onboarding)
    }

    @Test
    fun everyFieldSurvivesAJsonRoundTrip() {
        val s = Settings(
            numbers = NumbersSettings(
                preset = PresetId.MacrosWithGrams,
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
            theme = ThemeMode.Dark,
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
                numbers = NumbersSettings.later(RevealOn.Week).let { it.copy(overrides = it.overrides + (Metric.EnergyOut to Visibility.Soften)) },
                waterUnit = WaterUnit.FlOz,
            ),
        )

        listOf("\"preset\":\"coast\"", "\"preset\":\"full\"", "\"revealOn\":\"week\"", "\"energyOut\":\"soften\"", "\"fastPlan\":\"14:10\"", "\"waterUnit\":\"oz\"", "\"exercise\":\"some\"")
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
    fun aTargetWithoutItsStartingPoint_startsFromWhereItIs() {
        val decoded = SettingsJson.decodeFromString(
            Settings.serializer(),
            """{"targets":{"energyKcal":2300,"minKcal":2100,"maxKcal":2500,"proteinG":120,"carbsG":250,"fatG":70,"fiberG":30}}""",
        )

        assertEquals(2300, decoded.targets.startingKcal)
    }

    @Test
    fun theRevealTimeTravelsAsHoursAndMinutes_andDefaultsToNine() {
        val s = Settings(numbers = NumbersSettings.later().copy(revealAt = LocalTime(19, 30)))
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
        val shaped = byDay.shapeGroup(1, GroupShape.Surplus(0, 0), burnKcal = 1900)!!.stepSurplus(up = false, group = 1)!!.shapeGroup(0, GroupShape.Exact)!!
        val deficitDay = byDay.shapeGroup(1, GroupShape.Deficit(0, 0), burnKcal = 2900)!!.stepDeficit(up = false, group = 1)!!
        listOf(
            Targets.Default,
            deficitDay,
            Targets.Default.useDeficit(burnKcal = 2420).stepDeficit(up = false)!!,
            byDay,
            shaped,
            Targets.Default.step(RangeEnd.Low, up = true)!!,
            Targets.Default.useExact().stepNumber(up = true)!!,
            Targets.Default.useSurplus(burnKcal = 1780).stepSurplus(up = false)!!,
        ).forEach { targets ->
            val s = Settings(targets = targets)
            assertEquals(s, SettingsJson.decodeFromString(Settings.serializer(), SettingsJson.encodeToString(Settings.serializer(), s)))
        }
    }

    @Test
    fun whereRestingEnergyComesFrom_travelsAsTopLevelKeys_andAnUnknownValueIsSafe() {
        val s = Settings(
            restingSource = RestingSource.Own,
            restingOwnKcal = 1600,
            activityInBurned = false,
            birthYear = 1990,
            heightCm = 170.0,
            bodySex = BodySex.Unsaid,
        )
        val json = SettingsJson.encodeToJsonElement(Settings.serializer(), s).jsonObject
        assertEquals("own", json.getValue("restingSource").jsonPrimitive.content)
        assertEquals("unsaid", json.getValue("bodySex").jsonPrimitive.content)
        assertEquals(s, SettingsJson.decodeFromJsonElement(Settings.serializer(), json))

        val future = SettingsJson.decodeFromString(Settings.serializer(), """{"restingSource":"lab","bodySex":"other"}""")
        assertEquals(RestingSource.Calculate, future.restingSource)
        assertEquals(BodySex.Unsaid, future.bodySex)
        assertEquals(RestingSource.Calculate, Settings().restingSource)
        assertTrue(Settings().activityInBurned)
    }

    @Test
    fun heightUnit_travelsAsATopLevelKey_andUntilPickedFollowsPoundsThenTheRegion() {
        val s = Settings(heightCm = 181.61, heightUnit = HeightUnit.FtIn)
        val json = SettingsJson.encodeToJsonElement(Settings.serializer(), s).jsonObject
        assertEquals("ft_in", json.getValue("heightUnit").jsonPrimitive.content)
        assertEquals(s, SettingsJson.decodeFromJsonElement(Settings.serializer(), json))
        assertNull(SettingsJson.decodeFromString(Settings.serializer(), "{}").heightUnit)

        assertEquals(HeightUnit.Cm, Settings().heightUnitOr(HeightUnit.Cm))
        assertEquals(HeightUnit.FtIn, Settings().heightUnitOr(HeightUnit.FtIn), "the region, before anything is picked")
        assertEquals(HeightUnit.FtIn, Settings(weightUnit = WeightUnit.Lb).heightUnitOr(HeightUnit.Cm), "pounds kept inches")
        assertEquals(HeightUnit.Cm, Settings(weightUnit = WeightUnit.Lb, heightUnit = HeightUnit.Cm).heightUnitOr(HeightUnit.FtIn))
    }

    @Test
    fun everydayActivity_travelsAsATopLevelKey_andAnUnknownOrMissingOneIsMostlySitting() {
        val s = Settings(everydayActivity = EverydayActivity.OnMyFeet)
        val json = SettingsJson.encodeToJsonElement(Settings.serializer(), s).jsonObject
        assertEquals("on_my_feet", json.getValue("everydayActivity").jsonPrimitive.content)
        assertEquals(s, SettingsJson.decodeFromJsonElement(Settings.serializer(), json))
        assertEquals(EverydayActivity.MostlySitting, SettingsJson.decodeFromString(Settings.serializer(), """{"everydayActivity":"athlete"}""").everydayActivity)
        assertEquals(EverydayActivity.MostlySitting, SettingsJson.decodeFromString(Settings.serializer(), "{}").everydayActivity)
    }

    /**
     * A deficit day goes up inside `targetStyle` with its range kept beside it. A build from
     * before deficit days reads an unknown shape as a range, so that day reads as its own range:
     * never nothing, and never lower than the range it had.
     */
    @Test
    fun aDeficitDay_keepsItsRangeOnTheWire_soAnOlderBuildReadsThatRange() {
        val byDay = requireNotNull(Targets.Default.useByDay().addDayGroup()).moveDay(DayOfWeek.TUESDAY, 1).step(RangeEnd.High, up = true, group = 1)!!
        val targets = byDay.shapeGroup(1, GroupShape.Deficit(0, 0), burnKcal = 2600)!!
        val json = SettingsJson.encodeToJsonElement(Settings.serializer(), Settings(targets = targets)).jsonObject
        val group = json.getValue("targetStyle").jsonObject.getValue("groups").jsonArray[1].jsonObject
        assertEquals("deficit", group.getValue("shape").jsonPrimitive.content)
        assertEquals(450, group.getValue("deficitKcal").jsonPrimitive.content.toInt())
        assertEquals(1900 to 2350, group.getValue("minKcal").jsonPrimitive.content.toInt() to group.getValue("maxKcal").jsonPrimitive.content.toInt())

        // What an older build does with a shape it doesn't know.
        val unknownShape = JsonObject(group + ("shape" to JsonPrimitive("from_a_later_build")))
        val style = json.getValue("targetStyle").jsonObject
        val groups = style.getValue("groups").jsonArray.mapIndexed { index, it -> if (index == 1) unknownShape else it }
        val older = JsonObject(json + ("targetStyle" to JsonObject(style + ("groups" to JsonArray(groups)))))
        val read = SettingsJson.decodeFromJsonElement(Settings.serializer(), older).targets
        assertEquals(DayGroup(setOf(DayOfWeek.TUESDAY), 1900, 2350), (read.style as TargetStyle.ByDay).groups[1])
    }

    @Test
    fun aDeficitDayPastWhereItStarted_isHeldThere() {
        val targets = """"energyKcal":2100,"minKcal":1900,"maxKcal":2300,"proteinG":120,"carbsG":250,"fatG":70,"fiberG":30"""
        val style = """{"kind":"by_day","groups":[{"days":["mon"],"minKcal":1900,"maxKcal":2300,"shape":"deficit","deficitKcal":1500,"startingDeficitKcal":200},""" +
            """{"days":["tue"],"minKcal":1900,"maxKcal":2300,"shape":"deficit","deficitKcal":-50}]}"""
        val read = SettingsJson.decodeFromString(Settings.serializer(), """{"targets":{$targets},"targetStyle":$style}""").targets.style
        assertEquals(listOf(GroupShape.Deficit(200, 200), GroupShape.Deficit(0, 0)), (read as TargetStyle.ByDay).groups.map { it.shape })
    }

    @Test
    fun theGoalStyleTravelsAsATopLevelKey_soA01SaveOfTargetsCannotDropIt() {
        val s = Settings(targets = Targets.Default.copy(style = TargetStyle.Surplus(300, 300)))
        val json = SettingsJson.encodeToJsonElement(Settings.serializer(), s).jsonObject

        assertEquals("surplus", json.getValue("targetStyle").jsonObject.getValue("kind").jsonPrimitive.content)
        assertFalse("style" in json.getValue("targets").jsonObject, "nothing inside targets for a 0.1 save to lose")
        // A plain range is written too, so a change back to it reaches the server's merge.
        assertEquals("range", SettingsJson.encodeToJsonElement(Settings.serializer(), Settings()).jsonObject
            .getValue("targetStyle").jsonObject.getValue("kind").jsonPrimitive.content)
    }

    @Test
    fun aStyleSavedInsideTargets_beforeItMovedUp_stillReads_andTheTopLevelOneWins() {
        val targets = """"energyKcal":2100,"minKcal":1900,"maxKcal":2300,"proteinG":120,"carbsG":250,"fatG":70,"fiberG":30"""
        fun decode(json: String) = SettingsJson.decodeFromString(Settings.serializer(), json).targets.style

        assertEquals(TargetStyle.Exact, decode("""{"targets":{$targets,"style":{"kind":"exact"}}}"""))
        assertEquals(
            TargetStyle.Surplus(200, 250),
            decode(
                """{"targets":{$targets,"style":{"kind":"exact"}},"targetStyle":{"kind":"surplus","surplusKcal":200,"startingSurplusKcal":250}}""",
            ),
        )
        assertEquals(TargetStyle.Range, decode("""{"targets":{$targets},"targetStyle":null}"""))
    }

    @Test
    fun targetsFromBeforeGoalStylesAreARange_thatStartedWhereItIs() {
        val decoded = SettingsJson.decodeFromString(
            Settings.serializer(),
            """{"targets":{"energyKcal":2100,"minKcal":1900,"maxKcal":2300,"proteinG":120,"carbsG":250,"fatG":70,"fiberG":30}}""",
        )
        assertEquals(TargetStyle.Range, decoded.targets.style)
        assertEquals(1900, decoded.targets.startingMinKcal)
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
                """{"days":["tue","sat"],"minKcal":500,"maxKcal":700}]}""",
        )
        val groups = (repaired as TargetStyle.ByDay).groups
        assertEquals(DayOfWeek.entries.toSet() - DayOfWeek.SATURDAY, groups[0].days)
        assertEquals(DayGroup(setOf(DayOfWeek.SATURDAY), 1900, 2300), groups[1])

        // A surplus past where it started is held there, the style's and a group's, as a deficit
        // is; an unknown group shape is a range.
        assertEquals(TargetStyle.Surplus(150, 150), decode("""{"kind":"surplus","surplusKcal":5000,"startingSurplusKcal":150}"""))
        assertEquals(TargetStyle.Surplus(0, 0), decode("""{"kind":"surplus","surplusKcal":-300}"""))
        val shapes = decode(
            """{"kind":"by_day","groups":[{"days":["mon"],"minKcal":1900,"maxKcal":2300,"shape":"surplus","surplusKcal":9000,"startingSurplusKcal":100},""" +
                """{"days":["tue"],"minKcal":1900,"maxKcal":2300,"shape":"exact"},""" +
                """{"days":["wed"],"minKcal":1900,"maxKcal":2300,"shape":"binge"}]}""",
        )
        assertEquals(
            listOf(GroupShape.Surplus(100, 100), GroupShape.Exact, GroupShape.Range),
            (shapes as TargetStyle.ByDay).groups.map { it.shape },
        )
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
    fun a01PaletteIsDropped_andTheAppearanceKept() {
        val old = SettingsJson.decodeFromString(Settings.serializer(), """{"palette":"espresso","theme":"dark","tone":"warm"}""")

        assertEquals(Settings(theme = ThemeMode.Dark, tone = Tone.Warm), old)
        assertFalse("palette" in SettingsJson.encodeToJsonElement(Settings.serializer(), old).jsonObject, "the server keeps 0.1's own")
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
