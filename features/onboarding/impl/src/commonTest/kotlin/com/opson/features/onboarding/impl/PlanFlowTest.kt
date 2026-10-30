package com.opson.features.onboarding.impl

import com.opson.libraries.core.fixed
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.baselineEnvironment
import com.opson.libraries.diary.baselineInputs
import com.opson.libraries.nutrition.GoalBaselines
import com.opson.libraries.nutrition.HealthReadings
import com.opson.libraries.nutrition.PaceTier
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.SexChoice
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Units
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightGoal
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.ui.components.text.DateParts
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The goal flow's pure parts: which steps show, the pace stepper, what is saved, what each mode
 * is shown, and the answers an existing account starts from.
 */
class PlanFlowTest {

    private val today = DEFAULT_TODAY
    private val full = context(PresetId.Full)
    private val lose = OnboardingFixtures.LoseTen

    @Test
    fun theWeightGoal_isOnlyOfferedWhereWeightShows_notAfterNumbersHaveBeenHard_andNotWhilePregnant() {
        assertTrue(PlanFlow.showsGoal(lose, full))
        assertTrue(PlanFlow.showsGoal(lose, context(PresetId.MacrosWithGrams)))
        assertFalse(PlanFlow.showsGoal(lose, context(PresetId.MacrosOnly)), "weight is a trend there")
        assertFalse(PlanFlow.showsGoal(lose, context(PresetId.NoNumbers)))
        assertFalse(PlanFlow.showsGoal(lose, full.copy(hard = true)))
        val pregnant = lose.copy(body = lose.body.copy(sex = SexChoice.Pregnant))
        assertFalse(PlanFlow.showsGoal(pregnant, full))
        assertEquals(WeightGoal.None, PlanFlow.weightGoal(pregnant, full))
    }

    @Test
    fun thePace_stepsWithinWhatIsOffered() {
        val slower = PlanFlow.stepRate(lose, full, faster = false)
        assertEquals(Units.lbToKg(0.25), PlanFlow.rate(slower, full)!!, 1e-9)
        assertEquals(slower, PlanFlow.stepRate(slower, full, faster = false), "nothing slower than a quarter pound")

        var fastest = lose
        repeat(20) { fastest = PlanFlow.stepRate(fastest, full, faster = true) }
        assertTrue(PlanViews.rate(fastest, full).atFastest)
        assertFalse(PlanViews.rate(fastest, full).canFaster)
        // A typical day of about 2,450 leaves about 1,250 over the 1,200 line: 2.5 lb a week.
        assertEquals("2.5 lb", PlanViews.rate(fastest, full).rate)
    }

    @Test
    fun saving_writesTheProfile_theGoal_andTheTargetsItMeans() {
        val saved = PlanFlow.save(Settings(), lose, full)
        assertEquals(BodySex.Male, saved.bodySex)
        assertEquals(BodyStage.None, saved.bodyStage)
        assertEquals(LocalDate(1990, 7, 2), saved.knownBirthDate)
        assertEquals(181.61, saved.heightCm!!, 0.01)
        assertEquals(2200, saved.targets.energyKcal)
        assertTrue(saved.weightGoal.drives(saved.targets))
        assertEquals(WeightUnit.Lb, saved.weightUnit)
        assertTrue(saved.weightUnitChosen, "the weight field's switch is a choice, so the region no longer decides")
        assertEquals(HeightUnit.FtIn, saved.heightUnit)
    }

    @Test
    fun withNoWeightField_theUnitIsLeftToTheRegion() {
        val saved = PlanFlow.save(Settings(), lose, full.copy(hard = true))
        assertFalse(saved.weightUnitChosen)
        assertEquals(WeightUnit.Kg, saved.weightUnit, "the stored default, which the region still overrides when read")
    }

    @Test
    fun theOverview_showsTheWorking_inTheEnergyWords() {
        val view = PlanViews.overview(lose, full, Settings())
        assertEquals("1,780 kcal", (view.resting as Display.Shown).text)
        assertEquals("670 kcal", (view.everyday as Display.Shown).text)
        assertEquals("2,450 kcal", (view.burned as Display.Shown).text)
        val macros = PlanViews.overview(lose, context(PresetId.MacrosWithGrams), Settings())
        assertEquals(Display.Hidden, macros.burned)
    }

    @Test
    fun aBlankDetail_leavesWhatIsStored() {
        val before = Settings(birthYear = 1980, heightCm = 170.0, bodySex = BodySex.Female)
        val saved = PlanFlow.save(before, PlanAnswers(), full)
        assertEquals(1980, saved.birthYear)
        assertEquals(170.0, saved.heightCm)
        assertEquals(BodySex.Female, saved.bodySex)
        assertEquals(before.targets, saved.targets)
    }

    @Test
    fun justTracking_startsANewAccountFromItsBurn_butNeverMovesAnExistingAccountsTargets() {
        val track = lose.copy(goal = GoalAnswer(GoalDirection.JustTrack))
        val fresh = PlanFlow.save(Settings(), track, full)
        assertEquals(2450, fresh.targets.energyKcal, "a usual day of 2,450, above the starting range")
        assertFalse(fresh.weightGoal.drives(fresh.targets), "no weight goal sets it")
        assertEquals(GoalDirection.JustTrack, fresh.weightGoal.direction)

        val mine = Targets.Default.raise().raise()
        val existing = PlanFlow.save(Settings(targets = mine), track, full.copy(fresh = false))
        assertEquals(mine, existing.targets)
    }

    @Test
    fun aLowBurn_neverStartsANewAccountUnderTheAppsStartingRange() {
        val small = PlanAnswers(OnboardingFixtures.Body, GoalAnswer(GoalDirection.JustTrack))
        assertEquals(Targets.Default.energyKcal, PlanFlow.save(Settings(), small, full).targets.energyKcal)
    }

    @Test
    fun theOverview_followsTheMode() {
        val shown = PlanViews.overview(lose, full, Settings())
        assertIs<Display.Shown>(shown.energy)
        assertTrue(shown.compared)
        assertEquals("March 14, 2027", shown.forecast)

        val macros = PlanViews.overview(lose, context(PresetId.MacrosWithGrams), Settings())
        assertEquals(Display.Hidden, macros.energy, "no calorie amount in Macros with grams")
        assertFalse(macros.compared)
        assertIs<Display.Shown>(macros.goalWeight, "weight is on show there")

        val noBurnShown = context(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide))
        val withoutBurn = PlanViews.overview(lose, noBurnShown, Settings())
        assertIs<Display.Shown>(withoutBurn.energy)
        assertFalse(withoutBurn.compared, "the target against what a day uses would give energy out away")
        assertEquals(Display.Hidden, PlanViews.rate(lose, noBurnShown).dailyKcal)

        assertEquals(OverviewKind.Gentle, PlanViews.overview(lose, full.copy(hard = true), Settings()).kind)
    }

    @Test
    fun missingDetails_meanNoTarget_andTheOverviewSaysWhereToAddThem() {
        val noBirthday = lose.copy(body = lose.body.copy(birthDate = null))
        val view = PlanViews.overview(noBirthday, full, Settings())
        assertTrue(view.needsDetails)
        assertEquals(Display.Hidden, view.energy)
        assertEquals(Targets.Default, PlanFlow.save(Settings(), noBirthday, full).targets)
    }

    @Test
    fun aTypedBirthday_hasToMakeSomeoneThirteenToOneHundredAndTwenty() {
        val young = PlanFlow.reduce(PlanAnswers(), PlanAction.BirthdayTyped(DateParts.of(LocalDate(2020, 1, 1))), full)
        assertNull(young.body.birthDate)
        assertEquals("2020", young.body.birthday.year, "what is typed stays in the boxes")
        val fine = PlanFlow.reduce(PlanAnswers(), PlanAction.BirthdayTyped(DateParts.of(LocalDate(2013, 10, 25))), full)
        assertEquals(LocalDate(2013, 10, 25), fine.body.birthDate, "thirteen today")
        val tomorrow = PlanFlow.reduce(PlanAnswers(), PlanAction.BirthdayTyped(DateParts.of(LocalDate(2013, 10, 26))), full)
        assertNull(tomorrow.body.birthDate, "thirteen tomorrow")
        val half = PlanFlow.reduce(fine, PlanAction.BirthdayTyped(DateParts(day = "25", month = "10", year = "201")), full)
        assertNull(half.body.birthDate, "a year not yet whole is no birthday")
    }

    @Test
    fun switchingUnits_movesTheWeightAndTheTarget_andSwitchingBackGivesThemBack() {
        val pounds = PlanFlow.reduce(lose, PlanAction.WeightUnitPicked(WeightUnit.Kg), full)
        assertEquals("81.6", pounds.body.weight.text)
        assertEquals("77.1", pounds.goal.target.text)
        val back = PlanFlow.reduce(pounds, PlanAction.WeightUnitPicked(WeightUnit.Lb), full)
        assertEquals("180", back.body.weight.text)
        assertEquals("170", back.goal.target.text)
        assertEquals(Units.lbToKg(180.0), pounds.body.weightKgOrNull()!!, 1e-9, "what was typed is what counts")
    }

    @Test
    fun anExistingAccount_startsFromWhatItHas() {
        val settings = Settings(
            bodySex = BodySex.Female,
            bodyStage = BodyStage.Breastfeeding,
            heightCm = 165.0,
            weightUnit = WeightUnit.Kg,
            weightGoal = WeightGoal(GoalDirection.Gain, targetKg = 60.0, kgPerWeek = 0.2),
        ).withBirthDate(LocalDate(1995, 2, 3))
        val answers = PlanFlow.answersFrom(settings, latestKg = 57.0, regionHeight = HeightUnit.FtIn)
        assertEquals(SexChoice.Breastfeeding, answers.body.sex)
        assertEquals(LocalDate(1995, 2, 3), answers.body.birthDate)
        assertEquals(165.0, answers.body.heightCmOrNull()!!, 0.2, "shown in feet and inches, to the half inch")
        assertEquals("57", answers.body.weight.text)
        assertEquals(GoalDirection.Gain, answers.goal.direction)
        assertEquals("60", answers.goal.target.text)
        assertEquals(0.2, answers.goal.kgPerWeek)

        val tracking = PlanFlow.answersFrom(Settings(weightGoal = WeightGoal(GoalDirection.JustTrack)), null, HeightUnit.Cm)
        assertEquals(GoalDirection.None, tracking.goal.direction, "asked again, from nothing picked")
    }

    @Test
    fun aBigBody_canPlanPastOnePercent_withNotesAndNoBlock() {
        val kg = lose.withWeightUnit(WeightUnit.Kg)
        val big = kg.copy(
            body = kg.body.copy(weight = WeightInput("150", WeightUnit.Kg)),
            goal = kg.goal.copy(target = WeightInput("120", WeightUnit.Kg), kgPerWeek = null),
        )
        val rates = PlanFlow.rates(big, full)
        assertTrue(rates.last() >= 1.5 - 1e-9, "1% of 150 kg is on offer, and more: ${rates.last()}")
        var fastest = big
        repeat(40) { fastest = PlanFlow.stepRate(fastest, full, faster = true) }
        val view = PlanViews.rate(fastest, full)
        assertTrue(view.lowAim, "under the 1,500 line for a man, so the low-goal note shows")
        assertTrue(view.limitKnown)
        assertEquals(PaceTier.Steady, PlanViews.rate(big.copy(goal = big.goal.copy(kgPerWeek = 1.5)), full).pace?.tier)
    }

    @Test
    fun theTargetSaved_isTheOneTheGoalKeeps_soNothingIsOfferedRightAfter() {
        val readings = HealthReadings(restingKcal = 1900, activityKcal = listOf(600, 650, 700, 640))
        val current = Settings(restingSource = RestingSource.Health)
        val withHealth = full.copy(environment = current.baselineEnvironment(readings))
        val saved = PlanFlow.save(current, lose, withHealth)
        val goal = GoalBook.driving(saved)!!
        val repository = saved.baselineInputs(lose.body.weightKgOrNull(), today, readings)!!
        assertEquals(repository, goal.baseline?.inputs)
        assertNull(GoalBaselines.recalculation(goal, repository))
        // Resting from the health app, and its activity: 1,900 + 650, less the half pound's 250.
        assertEquals(2300, saved.targets.energyKcal)
    }

    @Test
    fun theGoalsBand_isItsOwn_whateverRangeWasThere() {
        val narrow = Settings(targets = Targets.Default.step(com.opson.libraries.nutrition.RangeEnd.Low, up = true)!!.step(com.opson.libraries.nutrition.RangeEnd.Low, up = true)!!)
        val wide = Settings(targets = Targets.Default.step(com.opson.libraries.nutrition.RangeEnd.High, up = true)!!)
        val a = PlanFlow.save(narrow, lose, full).targets
        val b = PlanFlow.save(wide, lose, full).targets
        assertEquals(Triple(a.energyKcal, a.minKcal, a.maxKcal), Triple(b.energyKcal, b.minKcal, b.maxKcal))
        assertEquals(200, a.maxKcal - a.minKcal)
    }

    private fun context(preset: PresetId, overrides: Map<Metric, Visibility> = emptyMap()) = PlanContext(
        today = today,
        hard = false,
        policy = VisibilityPolicy(NumbersSettings(preset = preset, overrides = overrides), Clock.fixed(Instant.parse("2026-10-25T12:00:00Z")), TimeZone.UTC),
        fresh = true,
    )
}
