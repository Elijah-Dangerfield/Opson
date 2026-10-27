package com.opson.features.today.impl

import com.opson.libraries.diary.MainNumberChoice
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.diary.rememberMainNumber
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Design sync 4's summary card (13.1, 13.2, 13.6) over the fixture day: 1,245 eaten against
 * 2,180 burned. The presets, the main number's default per goal style and its toggle, and what
 * the levels hide.
 */
class TodayEnergyCardTest {

    private fun card(
        settings: Settings = Settings(),
        policy: VisibilityPolicy = policy(PresetId.Full),
        burned: Boolean = true,
        on: Boolean = true,
    ): EnergyCard? = (
        TodayFixtures.todayState(
            policy,
            settings = settings,
            summaryOn = true,
            goalsAndEnergyOn = on,
            burned = TodayFixtures.burned.takeIf { burned },
        ).summary as? TodaySummary.Energy
        )?.card

    @Test
    fun theDefault_isInAndOut_toYourRange() {
        val card = assertNotNull(card())
        assertEquals(SummaryCardPreset.InOut, card.preset)
        assertEquals(MainKind.ToRange, card.main.kind)
        assertEquals("655", card.main.display.text())
        assertEquals("1,900 to 2,300", (card.goal as GoalLine.Range).range.text())
        assertEquals("2,180", card.burned?.total?.text())
        assertEquals("1,610", card.burned?.resting?.text())
        assertEquals("570", card.burned?.active?.text())
        assertEquals(MainNumberChoice.Balance, card.next, "the label switches to Deficit")
    }

    @Test
    fun withTheFlagOff_thereIsNoCard() {
        assertNull(card(on = false))
    }

    @Test
    fun aSetDeficit_showsDeficit_andItsTargetAgainstBurned() {
        val card = assertNotNull(card(TodayFixtures.deficit500))
        assertEquals(MainKind.Deficit, card.main.kind)
        assertEquals(Metric.EnergyOut, card.main.metric)
        assertEquals("935", card.main.display.text())
        assertEquals("500", (card.goal as GoalLine.DeficitGoal).kcal.text())
        val target = assertNotNull(card.target)
        assertEquals("1,680", target.target.text())
        assertEquals("435 kcal", target.left?.text())
        assertEquals(MainNumberChoice.Goal, card.next)
    }

    @Test
    fun aSetSurplus_showsToYourSurplus_andTheTargetFollowsTheBurn() {
        val card = assertNotNull(card(TodayFixtures.surplus300))
        assertEquals(MainKind.ToSurplus, card.main.kind)
        assertEquals("1,235", card.main.display.text())
        assertEquals("300", (card.goal as GoalLine.SurplusGoal).kcal.text())
        assertTrue(assertNotNull(card.target).surplus)
        assertEquals("2,480", card.target?.target?.text())
    }

    @Test
    fun oneNumber_showsLeftToTarget() {
        val card = assertNotNull(card(TodayFixtures.oneNumber))
        assertEquals(MainKind.LeftToTarget, card.main.kind)
        assertEquals("855", card.main.display.text())
        assertEquals("2,100", (card.goal as GoalLine.Target).kcal.text())
    }

    @Test
    fun theToggle_isRememberedPerGoalStyle() {
        val balance = assertNotNull(card(Settings().rememberMainNumber(MainNumberChoice.Balance)))
        assertEquals(MainKind.Deficit, balance.main.kind)
        assertEquals("935", balance.main.display.text())
        assertEquals(MainNumberChoice.Goal, balance.next)
    }

    @Test
    fun theMainNumberSetting_isWhatTheCardStartsOn_andEatenJoinsTheToggle() {
        val eaten = assertNotNull(card(Settings(todayMainNumber = MainNumberChoice.Eaten)))
        assertEquals(MainKind.Eaten, eaten.main.kind)
        assertEquals("1,245", eaten.main.display.text())
        assertEquals(MainNumberChoice.Goal, eaten.next)
    }

    @Test
    fun withoutBurned_theresNoDeficit_noBurnedBar_andNoToggle() {
        val card = assertNotNull(card(Settings(todayMainNumber = MainNumberChoice.Balance), burned = false))
        assertEquals(MainKind.ToRange, card.main.kind)
        assertNull(card.burned)
        assertNull(card.next)
    }

    @Test
    fun withWatchEnergyHidden_burnedAndTheDeficitGo_theCardStays() {
        val card = assertNotNull(card(TodayFixtures.deficit500, policy(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide))))
        assertNull(card.burned)
        assertNull(card.next)
        assertTrue(card.main.kind != MainKind.Deficit)
    }

    @Test
    fun rings_needASingleTarget_equation_needsBurned() {
        assertEquals(SummaryCardPreset.InOut, card(Settings(todayCard = SummaryCardPreset.Rings))?.preset)
        assertEquals(SummaryCardPreset.Rings, card(TodayFixtures.oneNumber.copy(todayCard = SummaryCardPreset.Rings))?.preset)
        assertEquals(SummaryCardPreset.Equation, card(Settings(todayCard = SummaryCardPreset.Equation))?.preset)
        assertEquals(SummaryCardPreset.InOut, card(Settings(todayCard = SummaryCardPreset.Equation), burned = false)?.preset)
        assertEquals(SummaryCardPreset.RangeBar, card(Settings(todayCard = SummaryCardPreset.RangeBar))?.preset)
    }

    @Test
    fun theOnTheCardSwitches() {
        val card = assertNotNull(card(Settings(todayBurnedBreakdown = false, todayMealsCount = true, todayNutrientsLink = false)))
        assertEquals(false, card.burned?.breakdown)
        assertEquals(MealCount(3, 4), card.meals)
        assertEquals(false, card.nutrientsLink)
    }

    @Test
    fun outsideFullNumbers_theCardComesFromTheMode() {
        listOf(PresetId.Fuel, PresetId.Blind, PresetId.Rhythm, PresetId.Nutrient).forEach { preset ->
            assertNull(card(policy = policy(preset)), "$preset")
        }
    }

    @Test
    fun theListsRows_turnOff_onlyWithTheFlag() {
        val off = Settings(todayWater = false, todayWeight = false, todayCloseDay = false)
        val state = TodayFixtures.todayState(
            policy(PresetId.Full),
            settings = off,
            summaryOn = true,
            weighIns = TodayFixtures.weighIns,
            goalsAndEnergyOn = true,
        )
        assertNull(state.water)
        assertNull(state.weight)
        assertNull(state.closeDay)
        val flagOff = TodayFixtures.todayState(policy(PresetId.Full), settings = off, summaryOn = true, weighIns = TodayFixtures.weighIns)
        assertNotNull(flagOff.water)
        assertNotNull(flagOff.closeDay)
    }

    @Test
    fun aSetDeficitOnToday_aimsAtBurned_withNoActivityAddedTwice() {
        val settings = TodayFixtures.deficit500.copy(exerciseRange = ExerciseAdjustment.All)
        val range = todayRange(settings, TodayFixtures.day(), TodayFixtures.today, TodayFixtures.expended, TodayFixtures.burned)
        assertEquals(1680, range.targetKcal)
        assertTrue(range.single)
        assertEquals(0, range.activityKcal)
    }
}
