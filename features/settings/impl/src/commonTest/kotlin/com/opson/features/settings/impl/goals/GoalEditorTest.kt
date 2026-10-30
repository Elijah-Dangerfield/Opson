package com.opson.features.settings.impl.goals

import com.opson.features.settings.impl.SettingsFixtures
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.goals.GoalFixtures
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.BurnMeasure
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalKind
import com.opson.libraries.nutrition.GoalStatus
import com.opson.libraries.nutrition.GoalTargets
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The goal editor's draft, the goal it sets and what it draws, for every type. */
class GoalEditorTest {

    private val base = SettingsFixtures.settings().copy(heightCm = 180.0, birthYear = 1996, bodySex = BodySex.Male)

    private fun context(
        settings: Settings = base,
        preset: PresetId = PresetId.Full,
        existing: Goal? = null,
        weighedIn: Boolean = true,
    ) = GoalEditContext(
        today = GoalFixtures.today,
        settings = settings.copy(numbers = NumbersSettings(preset = preset)),
        policy = SettingsFixtures.policy(preset),
        inputs = GoalFixtures.inputs.takeIf { weighedIn },
        weightKg = GoalFixtures.WEIGHT_KG.takeIf { weighedIn },
        existing = existing,
    )

    private fun reduce(context: GoalEditContext, vararg edits: GoalEdit, from: GoalDraft = GoalDraft()) =
        edits.fold(from) { draft, edit -> GoalEditor.reduce(draft, edit, context) }

    @Test
    fun aNewGoal_listsTheTypesTheModeOffers() {
        assertEquals(GoalKind.entries - GoalKind.Unknown, GoalEditor.kinds(context()))
        assertEquals(listOf(GoalKind.TrackEveryDay, GoalKind.Meals), GoalEditor.kinds(context(base.copy(onboarding = OnboardingAnswers(hard = true)))))
        val none = GoalEditor.kinds(context(preset = PresetId.NoNumbers))
        assertFalse(GoalKind.Weight in none || GoalKind.EnergyBalance in none)
    }

    @Test
    fun underEighteen_onlyTheGentleTypes_andNoWeeklyAverage() {
        val teen = context(base.withBirthDate(kotlinx.datetime.LocalDate(2010, 6, 1)))
        assertEquals(listOf(GoalKind.TrackEveryDay, GoalKind.Meals), GoalEditor.kinds(teen))
        val draft = GoalEditor.draft(GoalFixtures.balance, null, teen)
        assertNull(GoalEditor.view(draft, teen).kind, "a deficit picked before isn't drawn")
        assertNull(GoalEditor.view(draft, teen).averaging)
    }

    @Test
    fun aWeightGoal_needsAGoalWeightThatPointsTheRightWay_andSetsATargetFromATypicalDay() {
        val context = context()
        val draft = reduce(context, GoalEdit.KindPicked(GoalKind.Weight), GoalEdit.TargetTyped("92"))
        assertNull(GoalEditor.goal(draft, context), "92 kg is more than 88: not a loss")
        assertTrue(GoalEditor.view(draft, context).weight!!.wrongWay)

        val losing = reduce(context, GoalEdit.TargetTyped("80"), from = draft)
        val goal = assertNotNull(GoalEditor.goal(losing, context))
        val type = assertIs<GoalType.Weight>(goal.type)
        assertEquals(GoalDirection.Lose, type.direction)
        assertEquals(80.0, type.targetKg)
        assertEquals(GoalFixtures.WEIGHT_KG, type.startKg)
        assertEquals(GoalFixtures.today, goal.startDate)
        assertNotNull(goal.baseline)

        val view = GoalEditor.view(losing, context)
        assertTrue(view.canSave)
        assertIs<Display.Shown>(view.target)
        assertNotNull(view.chart, "where it leads, from the latest weigh-in")
        assertNotNull(view.chart?.forecast?.middle)
    }

    @Test
    fun thePace_stepsAlongThePacesOnOffer() {
        val context = context()
        val draft = reduce(context, GoalEdit.KindPicked(GoalKind.Weight), GoalEdit.TargetTyped("80"))
        val start = GoalEditor.view(draft, context).weight!!
        assertEquals("0.2 kg", start.paceText)
        assertTrue(start.canSlower && start.canFaster)
        val faster = reduce(context, GoalEdit.PaceStepped(faster = true), from = draft)
        assertEquals("0.3 kg", GoalEditor.view(faster, context).weight!!.paceText)
    }

    @Test
    fun aGoalWeight_isTypedInThePersonsUnit() {
        val context = context(base.copy(weightUnit = WeightUnit.Lb))
        val draft = reduce(context, GoalEdit.KindPicked(GoalKind.Weight), GoalEdit.TargetTyped("176"))
        assertEquals(79.8, (GoalEditor.goal(draft, context)!!.type as GoalType.Weight).targetKg!!, 0.05)
    }

    @Test
    fun withNoWeighIn_aWeightGoalWaits_andSaysWhatItNeeds() {
        val context = context(weighedIn = false)
        val draft = reduce(context, GoalEdit.KindPicked(GoalKind.Weight), GoalEdit.TargetTyped("80"))
        val view = GoalEditor.view(draft, context)
        assertTrue(view.needsWeight)
        assertTrue(view.needsDetails)
        assertFalse(view.canSave)
    }

    @Test
    fun anEnergyBalance_isTypedOrStepped_andComesToATargetUnderTheBurn() {
        val context = context()
        val draft = reduce(context, GoalEdit.KindPicked(GoalKind.EnergyBalance), GoalEdit.AmountTyped("4"))
        assertNull(GoalEditor.goal(draft, context), "4 isn't a balance yet")
        val stepped = reduce(context, GoalEdit.AmountTyped("420"), GoalEdit.AmountStepped(up = true), from = draft)
        assertEquals("450", stepped.balanceText, "a step lands on the next 50")
        val goal = assertNotNull(GoalEditor.goal(stepped, context))
        assertEquals(GoalType.EnergyBalance(-450), goal.type)
        assertEquals(GoalTargets.of(goal)!!.burnKcal - 450, GoalTargets.of(goal)!!.aimKcal)
        assertNotNull(GoalEditor.view(stepped, context).balance?.pace)

        val over = reduce(context, GoalEdit.SidePicked(under = false), from = stepped)
        assertEquals(GoalType.EnergyBalance(450), GoalEditor.goal(over, context)!!.type)
        assertNull(GoalEditor.view(over, context).balance?.pace, "a surplus has no loss pace")
    }

    @Test
    fun burnStepsAndMeals_setNoCalorieTarget() {
        val context = context()
        val burn = reduce(context, GoalEdit.KindPicked(GoalKind.Burn), GoalEdit.MeasurePicked(BurnMeasure.Total))
        assertEquals("2500", burn.burnText, "a whole day starts at a typical day's burn, to the step")
        assertEquals(GoalType.Burn(2500, BurnMeasure.Total), GoalEditor.goal(burn, context)!!.type)

        val steps = reduce(context, GoalEdit.KindPicked(GoalKind.Steps), GoalEdit.AmountStepped(up = true))
        assertEquals(GoalType.Steps(8500), GoalEditor.goal(steps, context)!!.type)

        val meals = reduce(context, GoalEdit.KindPicked(GoalKind.Meals), GoalEdit.AmountTyped("9"))
        assertNull(GoalEditor.goal(meals, context), "at most six")
        val view = GoalEditor.view(reduce(context, GoalEdit.AmountTyped("4"), from = meals), context)
        assertNull(view.averaging)
        assertNull(view.toleranceKcal)
        assertEquals(Display.Hidden, view.target)
        assertNull(view.chart)
    }

    @Test
    fun aCalorieGoal_offersAWeeklyAverage_andABand_whereCaloriesShow() {
        val context = context()
        val draft = reduce(
            context,
            GoalEdit.KindPicked(GoalKind.EnergyBalance),
            GoalEdit.AveragingPicked(GoalAveraging.Weekly),
            GoalEdit.BandPicked(150),
            GoalEdit.BandPicked(175),
        )
        val view = GoalEditor.view(draft, context)
        assertEquals(GoalAveraging.Weekly, view.averaging)
        assertEquals(150, view.toleranceKcal, "only the bands on offer")
        val goal = GoalEditor.goal(draft, context)!!
        assertEquals(GoalAveraging.Weekly, goal.averaging)
        assertEquals(300, GoalTargets.of(goal)!!.let { it.maxKcal - it.minKcal })

        val grams = GoalEditor.view(draft, context(preset = PresetId.MacrosWithGrams))
        assertNull(grams.averaging)
        assertNull(grams.toleranceKcal)
    }

    @Test
    fun changingAGoal_keepsItsIdStartAndHistory_andAnotherCalorieGoalPausesIt() {
        val existing = GoalFixtures.lose
        val settings = GoalFixtures.settings(existing, base = base)
        val context = context(settings, existing = existing)
        val draft = GoalEditor.draft(existing, null, context)
        assertEquals("80", draft.targetText)
        val goal = GoalEditor.goal(draft, context)!!
        assertEquals(existing.id to existing.startDate, goal.id to goal.startDate)
        assertEquals(90.0, (goal.type as GoalType.Weight).startKg)

        val fresh = context(settings)
        val balance = reduce(fresh, GoalEdit.KindPicked(GoalKind.EnergyBalance))
        assertEquals(GoalKind.Weight, GoalEditor.view(balance, fresh).replaces)
        val saved = GoalEditor.save(settings, GoalEditor.goal(balance, fresh)!!)
        assertEquals(GoalStatus.Paused, GoalBook.weight(saved)?.status)
        assertEquals(GoalKind.EnergyBalance, GoalBook.driving(saved)?.type?.kind)
        assertEquals(GoalTargets.of(GoalBook.driving(saved)!!)!!.aimKcal, saved.targets.energyKcal)
    }

    @Test
    fun aGoalWithNoTypicalDay_isKept_andWaits() {
        val context = context().copy(inputs = null)
        val goal = GoalEditor.goal(reduce(context, GoalEdit.KindPicked(GoalKind.EnergyBalance)), context)!!
        assertNull(goal.baseline)
        val saved = GoalEditor.save(base, goal)
        assertEquals(base.targets, saved.targets)
        assertEquals(goal, GoalBook.calorieGoal(saved))
    }
}
