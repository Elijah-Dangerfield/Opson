package com.opson.libraries.diary

import com.opson.libraries.nutrition.ActivityFrom
import com.opson.libraries.nutrition.BaselineChange
import com.opson.libraries.nutrition.BaselineInputs
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.BurnMeasure
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalBaselines
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalRecalculated
import com.opson.libraries.nutrition.GoalStatus
import com.opson.libraries.nutrition.GoalTargets
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.WeightGoal
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Goals in settings: the `goals` key round-trips every type, a pre-v2 `weightGoal` migrates
 * without losing anything, `weightGoal` stays in step for older builds, and an older build's edit
 * is folded back in.
 */
class GoalBookTest {

    private val start = LocalDate(2026, 10, 30)

    private val inputs = BaselineInputs(
        restingFrom = RestingFrom.Health,
        restingKcal = 1800,
        activityFrom = ActivityFrom.Health,
        everyday = EverydayActivity.SomeWalking,
        activityKcal = 650,
        digesting = true,
        weightKg = 90.0,
        heightCm = 180.0,
        ageYears = 30,
        sex = BodySex.Male,
        bodyFatPercent = 22.0,
    )

    private val weight = Goal(
        id = Goal.WEIGHT_ID,
        type = GoalType.Weight(GoalDirection.Lose, targetKg = 80.0, kgPerWeek = 0.5, startKg = 90.0),
        startDate = start,
        averaging = GoalAveraging.Weekly,
        toleranceKcal = 150,
        baseline = GoalBaselines.of(inputs),
        declined = inputs.copy(digesting = false),
    )

    private fun roundTrip(settings: Settings): Settings =
        SettingsJson.decodeFromString(Settings.serializer(), SettingsJson.encodeToString(Settings.serializer(), settings))

    @Test
    fun everyType_roundTrips_asOneTopLevelList() {
        val goals = listOf(
            weight,
            Goal("balance", GoalType.EnergyBalance(-400), start, status = GoalStatus.Paused),
            Goal("burn", GoalType.Burn(600, BurnMeasure.Total), start),
            Goal("steps", GoalType.Steps(9000), start),
            Goal("track", GoalType.TrackEveryDay, start),
            Goal("meals", GoalType.Meals(4), start, status = GoalStatus.Done),
        )
        val settings = Settings(goals = goals)
        val json = SettingsJson.encodeToJsonElement(Settings.serializer(), settings).jsonObject
        assertEquals(6, json.getValue("goals").jsonArray.size)
        assertEquals(goals, roundTrip(settings).goals)
    }

    @Test
    fun answeredRecalculations_roundTrip_andAChangeThisBuildDoesntKnowIsLeftOut() {
        val answered = weight.recorded(GoalRecalculated(start, setOf(BaselineChange.Weight, BaselineChange.Activity), 1950, 1880, accepted = true))
        assertEquals(answered.history, GoalBook.goals(roundTrip(GoalBook.withGoals(Settings(), listOf(answered)))).single().history)

        val raw = """{"goals":[{"id":"weight","kind":"weight","startDate":"2026-10-30","direction":"lose","history":[{"date":"2026-11-02","changes":["Weight","Moon"],"newAimKcal":1900,"accepted":false}]}]}"""
        val entry = SettingsJson.decodeFromString(Settings.serializer(), raw).goals!!.single().history.single()
        assertEquals(setOf(BaselineChange.Weight), entry.changes)
        assertNull(entry.oldAimKcal)
    }

    @Test
    fun theHistory_keepsTheLatestAnswers() {
        val full = (1..Goal.MAX_HISTORY + 3).fold(weight) { goal, day ->
            goal.recorded(GoalRecalculated(LocalDate(2026, 11, day), emptySet(), null, 1900 + day, accepted = day % 2 == 0))
        }
        assertEquals(Goal.MAX_HISTORY, full.history.size)
        assertEquals(LocalDate(2026, 11, Goal.MAX_HISTORY + 3), full.history.last().date)
    }

    @Test
    fun noGoalsKey_isNull_andAnEmptyListIsWritten() {
        assertNull(SettingsJson.decodeFromString(Settings.serializer(), "{}").goals)
        val json = SettingsJson.encodeToJsonElement(Settings.serializer(), Settings(goals = emptyList())).jsonObject
        assertEquals(JsonArray(emptyList()), json["goals"], "clearing every goal reaches the server")
    }

    @Test
    fun aKindThisBuildDoesntKnow_isKeptAsItCame() {
        val raw = """{"goals":[{"id":"sleep","kind":"sleep","startDate":"2026-10-30","hours":8},{"id":"t","kind":"track_every_day","startDate":"2026-10-30"}]}"""
        val settings = SettingsJson.decodeFromString(Settings.serializer(), raw)
        assertEquals(2, settings.goals!!.size)
        assertFalse(settings.goals!!.first().drivesTarget)
        val written = SettingsJson.encodeToJsonElement(Settings.serializer(), settings).jsonObject
        assertEquals("8", written.getValue("goals").jsonArray.first().jsonObject.getValue("hours").jsonPrimitive.content)
    }

    @Test
    fun aGoalThisBuildCantReadInFull_isKeptAsItCame_andWrittenBackUntouched() {
        val goals = listOf(
            """{"id":"weight","kind":"weight","startDate":"2026-10-30","direction":"sideways","kgPerWeek":0.5}""",
            """{"kind":"steps","startDate":"2026-10-30","count":5}""",
            """{"id":"b","kind":"energy_balance","startDate":"2026-10-30","kcalPerDay":"lots"}""",
            """{"id":"m","kind":"burn","startDate":"2026-10-30","kcal":400,"measure":"heart_points"}""",
            """{"id":"e","kind":"energy_balance","startDate":"2026-10-30","kcalPerDay":-300,"averaging":"monthly"}""",
            """{"id":"s","kind":"steps","startDate":"2026-10-30","count":9000,"status":"archived"}""",
        )
        val settings = SettingsJson.decodeFromString(Settings.serializer(), """{"weightGoal":{"direction":"lose"},"goals":[${goals.joinToString(",")}]}""")
        val read = settings.goals!!
        assertEquals(goals.size, read.size, "nothing dropped")
        assertTrue(read.all { it.type is GoalType.Unknown && !it.drivesTarget }, "nothing guessed at or acted on")
        assertNull(GoalBook.driving(settings))
        assertEquals(GoalDirection.Lose, GoalBook.withGoals(settings, GoalBook.goals(settings)).weightGoal.direction, "older builds' weight goal left as it was")

        // Saved again with a goal of this build's own beside them: every one goes back as it came.
        val saved = GoalBook.withGoal(settings, Goal("t", GoalType.TrackEveryDay, start))
        val written = SettingsJson.encodeToJsonElement(Settings.serializer(), saved).jsonObject.getValue("goals").jsonArray
        assertEquals(goals.map { SettingsJson.parseToJsonElement(it) }, written.take(goals.size))
        assertEquals(goals.size + 1, written.size)
    }

    @Test
    fun aPreV2WeightGoal_migratesLosslessly_andWritesTheSameTarget() {
        val old = WeightGoal(GoalDirection.Lose, targetKg = 80.0, kgPerWeek = 0.5, startKg = 90.0, startDate = start, targetsKcal = 1950)
        val targets = Targets.Default.withBand(1950, 1750, 2150)
        val settings = Settings(weightGoal = old, targets = targets, bodySex = BodySex.Male)
        val goal = GoalBook.goals(settings).single()
        assertEquals(GoalType.Weight(GoalDirection.Lose, 80.0, 0.5, 90.0), goal.type)
        assertEquals(start, goal.startDate)
        assertTrue(goal.drivesTarget)
        assertEquals(1950, goal.targetsKcal)
        assertEquals(2500, goal.baseline!!.burnKcal, "the aim plus the 550 it took off")
        assertNull(goal.baseline!!.inputs)
        assertEquals(1950, GoalTargets.of(goal)!!.aimKcal)
        // Saving it changes nothing an older build reads.
        assertEquals(old, GoalBook.withGoals(settings, listOf(goal)).weightGoal)
    }

    @Test
    fun aPreV2GoalMovedByHand_migratesAsOverridden_andJustTrackAsNoGoal() {
        val old = WeightGoal(GoalDirection.Gain, targetKg = 70.0, kgPerWeek = 0.25, startKg = 65.0, startDate = start, targetsKcal = 2600)
        val moved = Settings(weightGoal = old, targets = Targets.Default)
        val goal = GoalBook.goals(moved).single()
        assertFalse(goal.setsTarget)
        assertFalse(goal.drivesTarget)
        assertEquals(emptyList(), GoalBook.goals(Settings(weightGoal = WeightGoal(GoalDirection.JustTrack))))
        assertEquals(emptyList(), GoalBook.goals(Settings()))
    }

    @Test
    fun weightGoal_followsTheGoals_forOlderBuilds() {
        val (stamped, targets) = GoalTargets.apply(weight, Targets.Default, scaleMacros = false)!!
        val saved = GoalBook.withGoals(Settings(targets = targets), listOf(stamped))
        assertEquals(GoalDirection.Lose, saved.weightGoal.direction)
        assertEquals(stamped.targetsKcal, saved.weightGoal.targetsKcal)
        assertTrue(saved.weightGoal.drives(saved.targets))
        // Overridden: an older build sees a goal that no longer sets the target.
        val overridden = GoalBook.withGoals(saved, listOf(stamped.copy(setsTarget = false)))
        assertNull(overridden.weightGoal.targetsKcal)
        // Removed: answered, as just tracking.
        assertEquals(GoalDirection.JustTrack, GoalBook.without(saved, Goal.WEIGHT_ID).weightGoal.direction)
        // Reading back gives the goals as written.
        assertEquals(listOf(stamped), GoalBook.goals(saved))
    }

    @Test
    fun anOlderBuildsEdit_isFoldedBackIn_keepingWhatItDidntKnow() {
        val (stamped, targets) = GoalTargets.apply(weight, Targets.Default, scaleMacros = false)!!
        val saved = GoalBook.withGoals(Settings(targets = targets), listOf(stamped, Goal("track", GoalType.TrackEveryDay, start)))
        // An older build set a new pace through its own goal flow, with a target of its own.
        val edited = saved.copy(
            weightGoal = saved.weightGoal.copy(kgPerWeek = 0.25, targetsKcal = 2300),
            targets = saved.targets.withBand(2300, 2100, 2500),
        )
        val goals = GoalBook.goals(edited)
        val rebuilt = goals.first()
        assertEquals(0.25, (rebuilt.type as GoalType.Weight).kgPerWeek)
        assertEquals(2300, GoalTargets.of(rebuilt)!!.aimKcal, "the older build's target stands")
        assertTrue(rebuilt.drivesTarget)
        assertEquals(GoalAveraging.Weekly, rebuilt.averaging)
        assertEquals(150, rebuilt.toleranceKcal)
        assertEquals("track", goals[1].id)
    }

    @Test
    fun anOlderBuildsHandEdit_toTargets_readsAsOverridden() {
        val (stamped, targets) = GoalTargets.apply(weight, Targets.Default, scaleMacros = false)!!
        val saved = GoalBook.withGoals(Settings(targets = targets), listOf(stamped))
        val raised = saved.copy(targets = saved.targets.raise())
        assertFalse(GoalBook.goals(raised).single().drivesTarget)
        assertNull(GoalBook.driving(raised))
    }

    @Test
    fun whilePregnant_aCalorieGoalStopsSettingTheTarget() {
        val (stamped, targets) = GoalTargets.apply(weight, Targets.Default, scaleMacros = false)!!
        val saved = GoalBook.withGoals(Settings(targets = targets), listOf(stamped))
        val pregnant = saved.copy(bodyStage = com.opson.libraries.nutrition.BodyStage.Pregnant)
        assertNull(GoalBook.driving(pregnant))
        assertFalse(GoalBook.goals(pregnant).single().setsTarget)
        assertTrue(GoalBook.driving(saved) != null, "and sets it again once that's over")
    }

    @Test
    fun onlyOneGoalSetsTheTarget() {
        val balance = Goal("balance", GoalType.EnergyBalance(-300), start)
        val both = GoalBook.withGoal(GoalBook.withGoals(Settings(), listOf(weight)), balance)
        val goals = GoalBook.goals(both)
        assertEquals("balance", goals.first().id)
        assertEquals(GoalStatus.Paused, goals.first { it.id == Goal.WEIGHT_ID }.status)
        assertEquals("balance", GoalBook.driving(both)?.id)
    }

    @Test
    fun applyingTheTarget_writesTargets_andStamps() {
        val settings = GoalBook.applyTarget(Settings(macroMode = MacroMode.Grams), weight)
        val goal = GoalBook.driving(settings)!!
        assertEquals(settings.targets.energyKcal, goal.targetsKcal)
        assertEquals(GoalTargets.of(weight)!!.aimKcal, settings.targets.energyKcal)
        assertEquals(Targets.Default.proteinG, settings.targets.proteinG, "grams stay put in gram mode")
        // No baseline yet: nothing to write.
        val waiting = weight.copy(baseline = null)
        assertEquals(Settings(), GoalBook.applyTarget(Settings(), waiting))
    }

    @Test
    fun aBirthdayUnderEighteen_pausesWeightAndDeficitGoals_andTheTargetGoesBackToATypicalDay() {
        val today = LocalDate(2026, 10, 30)
        val steps = Goal("steps", GoalType.Steps(8000), start)
        val set = GoalBook.withGoal(GoalBook.applyTarget(Settings(macroMode = MacroMode.Grams), weight), steps)
        val adult = set.withBirthDate(LocalDate(1990, 1, 1))
        assertTrue(GoalBook.pauseWeightPlans(adult, today) === adult, "an adult keeps every goal")

        val teen = set.withBirthDate(LocalDate(2010, 1, 1))
        val paused = GoalBook.pauseWeightPlans(teen, today)
        val goals = GoalBook.goals(paused)
        assertEquals(GoalStatus.Paused, goals.first { it.id == Goal.WEIGHT_ID }.status)
        assertEquals(GoalStatus.Active, goals.first { it.id == "steps" }.status, "a steps goal isn't about weight")
        assertNull(GoalBook.driving(paused))
        val typicalDay = GoalTargets.aimKcal(GoalType.Weight(GoalDirection.Maintain), weight.baseline!!)
        assertEquals(typicalDay, paused.targets.energyKcal, "a deficit no longer sets what they eat")
        assertTrue(set.targets.energyKcal!! < typicalDay!!)
        assertTrue(GoalBook.pauseWeightPlans(paused, today) === paused, "nothing left to pause")
    }

    @Test
    fun aLegacyGoalWithNoStartDate_countsEveryDay() {
        val old = WeightGoal(GoalDirection.Maintain, targetKg = 70.0, startKg = 70.0)
        assertEquals(LocalDate.fromEpochDays(0), GoalBook.goals(Settings(weightGoal = old)).single().startDate)
        assertTrue(GoalBook.goals(Settings(weightGoal = old)).single().drivesTarget, "never stamped: it sets the target once it can")
        // JsonObject import kept honest: the weight goal itself still round-trips as one object.
        assertTrue(SettingsJson.encodeToJsonElement(Settings.serializer(), Settings(weightGoal = old)).jsonObject["weightGoal"] is JsonObject)
    }
}
