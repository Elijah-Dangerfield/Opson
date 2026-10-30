package com.opson.features.onboarding.impl

import com.opson.libraries.core.fixed
import com.opson.libraries.diary.GoalBook
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.BurnMeasure
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalKind
import com.opson.libraries.nutrition.GoalTargets
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.SexChoice
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.WeightGoal
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Onboarding's goal question (owner feedback, 2026-10-30, docs/plans/onboarding-v4.md): a goal is
 * optional, the body is asked only for a goal that needs an energy estimate, each kind saves the
 * engine's goal, and a streak is asked only after a goal.
 */
class GoalQuestionTest {

    private val full = onboarding(PresetId.Full)
    private val body = OnboardingFixtures.ImperialBody

    @Test
    fun notNow_endsAtTheQuestion_withNothingAboutABodyAsked() {
        val answers = PlanAnswers(wants = false)
        assertEquals(listOf(PlanStep.Ask), PlanFlow.steps(answers, full))
        assertEquals(listOf(PlanStep.Ask), PlanFlow.steps(PlanAnswers(), full), "nothing past the question until it is answered")
    }

    @Test
    fun onlyTheKindsThatSetACalorieTarget_askAboutTheBody() {
        fun steps(pick: GoalKind) = PlanFlow.steps(PlanAnswers(wants = true, pick = pick), full)
        val tail = listOf(PlanStep.Streak, PlanStep.Commit)
        assertEquals(
            listOf(PlanStep.Ask, PlanStep.Kind, PlanStep.Profile, PlanStep.Activity, PlanStep.Goal, PlanStep.Overview) + tail,
            steps(GoalKind.Weight),
        )
        assertEquals(
            listOf(PlanStep.Ask, PlanStep.Kind, PlanStep.Profile, PlanStep.Activity, PlanStep.Balance, PlanStep.Overview) + tail,
            steps(GoalKind.EnergyBalance),
        )
        assertEquals(listOf(PlanStep.Ask, PlanStep.Kind, PlanStep.Burn) + tail, steps(GoalKind.Burn))
        assertEquals(listOf(PlanStep.Ask, PlanStep.Kind, PlanStep.MealCount) + tail, steps(GoalKind.Meals))
        assertEquals(listOf(PlanStep.Ask, PlanStep.Kind) + tail, steps(GoalKind.TrackEveryDay))
        assertEquals(listOf(PlanStep.Ask, PlanStep.Kind), PlanFlow.steps(PlanAnswers(wants = true), full), "the rest waits for a kind")
    }

    @Test
    fun theStreak_isOnlyAskedWhileCelebrationsAreOn() {
        val steps = PlanFlow.steps(PlanAnswers(wants = true, pick = GoalKind.TrackEveryDay), full.copy(streakOffered = false))
        assertEquals(listOf(PlanStep.Ask, PlanStep.Kind, PlanStep.Commit), steps)
    }

    @Test
    fun theKinds_followTheMode_andTheGentleSide() {
        val all = listOf(GoalKind.Weight, GoalKind.EnergyBalance, GoalKind.Burn, GoalKind.TrackEveryDay, GoalKind.Meals)
        assertEquals(all, PlanFlow.picks(full))
        assertEquals(listOf(GoalKind.Weight, GoalKind.TrackEveryDay, GoalKind.Meals), PlanFlow.picks(onboarding(PresetId.MacrosWithGrams)))
        assertEquals(listOf(GoalKind.TrackEveryDay, GoalKind.Meals), PlanFlow.picks(onboarding(PresetId.NoNumbers)))
        assertEquals(listOf(GoalKind.TrackEveryDay, GoalKind.Meals), PlanFlow.picks(full.copy(hard = true)), "after numbers have been hard, or 16 and 17")
        val notOffered = PlanFlow.reduce(PlanAnswers(wants = true), PlanAction.GoalPicked(GoalKind.Weight), full.copy(hard = true))
        assertNull(notOffered.pick)
    }

    @Test
    fun continueOnTheKind_waitsForOne() {
        assertFalse(PlanFlow.canContinue(PlanStep.Kind, PlanAnswers(wants = true), full))
        assertTrue(PlanFlow.canContinue(PlanStep.Kind, PlanAnswers(wants = true, pick = GoalKind.Meals), full))
    }

    @Test
    fun notNow_savesNoGoal_asAnAnswer_soTodayDoesNotAskAgain() {
        val saved = PlanFlow.save(Settings(), PlanAnswers(wants = false), full)
        assertTrue(GoalBook.goals(saved).isEmpty())
        assertEquals(GoalDirection.JustTrack, saved.weightGoal.direction)
        assertEquals(Targets.Default, saved.targets)
        assertNull(saved.heightCm)
    }

    @Test
    fun aGoalWithItsOwnNumber_savesTheEnginesGoal_andNoBody() {
        // A body typed for a weight goal and then left for another kind isn't kept.
        val typed = PlanAnswers(body = body, wants = true)
        val burn = PlanFlow.save(Settings(), typed.copy(pick = GoalKind.Burn, burnKcal = 400), full)
        assertEquals(GoalType.Burn(400, BurnMeasure.Active), GoalBook.goals(burn).single().type)
        assertNull(burn.heightCm, "the profile wasn't one of the steps")
        assertNull(burn.bodySex)
        assertEquals(Targets.Default, burn.targets)

        val meals = PlanFlow.save(Settings(), typed.copy(pick = GoalKind.Meals, meals = 4), full)
        assertEquals(GoalType.Meals(4), GoalBook.goals(meals).single().type)

        val showUp = PlanFlow.save(Settings(), typed.copy(pick = GoalKind.TrackEveryDay), full)
        assertEquals(GoalType.TrackEveryDay, GoalBook.goals(showUp).single().type)
        assertEquals(GoalDirection.JustTrack, showUp.weightGoal.direction, "no weight goal to offer later")
    }

    @Test
    fun anEnergyBalance_setsTheTarget_fromATypicalDay_judgedHowItWasPicked() {
        val answers = OnboardingFixtures.balance(weekly = true).copy(wants = true)
        val saved = PlanFlow.save(Settings(), answers, full)
        val goal = assertNotNull(GoalBook.driving(saved))
        assertEquals(GoalType.EnergyBalance(-400), goal.type)
        assertEquals(GoalAveraging.Weekly, goal.averaging)
        val target = assertNotNull(GoalTargets.of(goal))
        assertEquals(target.aimKcal, saved.targets.energyKcal)
        assertEquals(-400, target.deltaKcal)
        assertEquals(SexChoice.Male.sex, saved.bodySex, "the profile was asked, so it is kept")
    }

    @Test
    fun aDeficit_neverReachesUnderTheLineEveryoneIsHeldTo() {
        val small = PlanAnswers(body = OnboardingFixtures.Body, wants = true, pick = GoalKind.EnergyBalance)
        val burn = assertNotNull(PlanFlow.burnKcal(small, full))
        val max = PlanFlow.balanceMax(small, full)
        assertTrue(burn - max >= GoalTargets.WORKED_OUT_FLOOR_KCAL, "$burn less $max is under the floor")
        val stepped = (1..40).fold(small) { answers, _ -> PlanFlow.reduce(answers, PlanAction.BalanceStepped(more = true), full) }
        assertEquals(max, stepped.balance.kcal)
        val over = PlanFlow.reduce(stepped, PlanAction.BalanceSidePicked(under = false), full)
        assertTrue(PlanFlow.balanceMax(over, full) >= over.balance.kcal, "a surplus has its own reach")
    }

    @Test
    fun aBalance_stepsAway_whilePregnant() {
        val pregnant = OnboardingFixtures.balance().copy(wants = true, body = body.copy(sex = SexChoice.Pregnant))
        assertFalse(PlanStep.Balance in PlanFlow.steps(pregnant, full))
        assertNull(PlanFlow.goal(pregnant, full, null))
    }

    @Test
    fun theStreak_answer_setsCelebrations_andTheGoalsOwnStreak_onlyWhereMetIsNeverEatingLess() {
        val kept = PlanFlow.save(Settings(celebrationsEnabled = false), PlanAnswers(wants = true, pick = GoalKind.TrackEveryDay, streak = true), full)
        assertTrue(kept.celebrationsEnabled)
        assertTrue(kept.goalStreakEnabled)
        val declined = PlanFlow.save(Settings(), PlanAnswers(wants = true, pick = GoalKind.TrackEveryDay, streak = false), full)
        assertFalse(declined.celebrationsEnabled)
        assertFalse(declined.goalStreakEnabled)

        val balance = OnboardingFixtures.balance().copy(wants = true, streak = true)
        assertFalse(PlanFlow.goalStreakOffered(balance, full))
        val showingUpOnly = PlanFlow.save(Settings(celebrationsEnabled = false), balance, full)
        assertTrue(showingUpOnly.celebrationsEnabled)
        assertFalse(showingUpOnly.goalStreakEnabled, "never a streak for an energy balance")
        assertTrue(PlanFlow.goalStreakOffered(PlanAnswers(wants = true, pick = GoalKind.Burn), full))
        val notAsked = PlanFlow.save(Settings(), PlanAnswers(wants = true, pick = GoalKind.TrackEveryDay, streak = false), full.copy(streakOffered = false))
        assertTrue(notAsked.celebrationsEnabled, "a streak never asked leaves celebrations as they were")
    }

    @Test
    fun aWeightGoal_inOnboarding_listsNoJustTrack() {
        assertEquals(listOf(GoalDirection.Lose, GoalDirection.Maintain, GoalDirection.Gain), PlanFlow.goalDirections(full))
        val tracking = PlanAnswers(body = body, goal = GoalAnswer(GoalDirection.JustTrack), wants = true, pick = GoalKind.Weight)
        assertFalse(PlanFlow.canContinue(PlanStep.Goal, tracking, full))
        assertEquals(WeightGoal.None.direction, PlanFlow.weightGoal(tracking.copy(goal = GoalAnswer()), full).direction)
    }

    @Test
    fun aTypedAmount_isKeptAsTyped_andCountsOnceItIsInRange() {
        val meals = PlanAnswers(wants = true, pick = GoalKind.Meals)
        val four = PlanFlow.reduce(meals, PlanAction.MealsTyped("4"), full)
        assertEquals(4, four.meals)
        assertEquals("4", PlanViews.mealCount(four).text)
        val cleared = PlanFlow.reduce(four, PlanAction.MealsTyped(""), full)
        assertEquals(4, cleared.meals, "an empty box keeps the amount")
        assertEquals("", PlanViews.mealCount(cleared).text)
        val tooMany = PlanFlow.reduce(cleared, PlanAction.MealsTyped("12"), full)
        assertEquals(4, tooMany.meals, "past the range waits to be fixed")
        val stepped = PlanFlow.reduce(tooMany, PlanAction.MealsStepped(more = true), full)
        assertEquals("5", PlanViews.mealCount(stepped).text)

        val burn = PlanFlow.reduce(PlanAnswers(wants = true, pick = GoalKind.Burn), PlanAction.BurnTyped("450"), full)
        assertEquals(450, burn.burnKcal)
    }

    @Test
    fun aWeeklyAverage_isOnlyKept_whereItIsOffered() {
        val weekly = PlanFlow.reduce(OnboardingFixtures.balance().copy(wants = true), PlanAction.AveragingPicked(GoalAveraging.Weekly), full)
        assertEquals(GoalAveraging.Weekly, PlanFlow.goal(weekly, full, null)?.averaging)
        val macros = onboarding(PresetId.MacrosWithGrams)
        assertEquals(GoalAveraging.Daily, PlanFlow.averaging(weekly, macros))
    }

    private fun onboarding(preset: PresetId) = PlanContext(
        today = DEFAULT_TODAY,
        hard = false,
        policy = VisibilityPolicy(NumbersSettings(preset = preset), Clock.fixed(Instant.parse("2026-10-25T12:00:00Z")), TimeZone.UTC),
        fresh = true,
        asks = true,
        streakOffered = true,
        birthdayAsked = true,
    )
}
