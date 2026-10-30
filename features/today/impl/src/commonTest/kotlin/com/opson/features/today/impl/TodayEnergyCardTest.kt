package com.opson.features.today.impl

import com.opson.libraries.diary.MainNumberChoice
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.diary.rememberMainNumber
import com.opson.libraries.nutrition.BodyDetail
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_card_to_range_line
import opson.libraries.resources.generated.resources.today_compact_deficit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Design sync 4's summary card (13.1, 13.2, 13.6) over the fixture day: 1,245 eaten against
 * 2,180 burned. The presets, the main number's default per goal style and its flip (the gap, the
 * balance, eaten; fewer after "numbers have been hard"), the sticky bar following it, and what the
 * levels hide. Which readings a mode allows is `TodayFlipsTest`.
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

    // --- Burned that can't be worked out: said where the line goes, never left out ---

    @Test
    fun burnedUnknown_theCardSaysWhy_inEveryPreset() {
        val gap = BurnedGap(setOf(BodyDetail.Weight))
        SummaryCardPreset.entries.forEach { preset ->
            val card = assertNotNull(
                (
                    TodayFixtures.todayState(
                        policy(PresetId.Full),
                        settings = Settings(todayCard = preset),
                        summaryOn = true,
                        goalsAndEnergyOn = true,
                        burnedGap = gap,
                    ).summary as TodaySummary.Energy
                    ).card,
            )
            assertNull(card.burned, "$preset")
            assertEquals(gap, card.burnedUnavailable, "$preset")
        }
    }

    @Test
    fun burnedUnknown_withNothingToSayWhy_stillSaysItIsUnavailable() {
        val card = assertNotNull(card(burned = false))
        assertEquals(BurnedGap(), card.burnedUnavailable)
        assertEquals(BurnedFix.RestingEnergy, card.burnedUnavailable?.fix)
    }

    @Test
    fun burnedKnown_hasNoUnavailableRow() {
        assertNull(assertNotNull(card()).burnedUnavailable)
    }

    /** A mode that hides energy out hides Burned on purpose: nothing to fix, so no row. */
    @Test
    fun energyOutHidden_hasNoUnavailableRow() {
        val hidden = policy(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide))
        assertNull(assertNotNull(card(policy = hidden, burned = false)).burnedUnavailable)
    }

    @Test
    fun theFix_opensWeightForAWeighIn_ProfileForTheRest() {
        assertEquals(BurnedFix.Weight, BurnedGap(setOf(BodyDetail.Weight)).fix)
        assertEquals(BurnedFix.Profile, BurnedGap(setOf(BodyDetail.Height)).fix)
        assertEquals(BurnedFix.Profile, BurnedGap(setOf(BodyDetail.Weight, BodyDetail.Sex)).fix)
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
        assertEquals(MainNumberChoice.Eaten, card.next)
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
        assertEquals(MainNumberChoice.Eaten, balance.next)
    }

    @Test
    fun theMainNumberSetting_isWhatTheCardStartsOn_andEatenJoinsTheToggle() {
        val eaten = assertNotNull(card(Settings(todayMainNumber = MainNumberChoice.Eaten)))
        assertEquals(MainKind.Eaten, eaten.main.kind)
        assertEquals("1,245", eaten.main.display.text())
        assertEquals(MainNumberChoice.Goal, eaten.next)
    }

    /** The owner's ask: "1,800 to range" flips to what was eaten, and round again. */
    @Test
    fun theMainNumber_flipsFromTheGap_toTheBalance_toEaten_andBack() {
        val readings = generateSequence(Settings()) { s -> card(s)?.next?.let(s::rememberMainNumber) }
            .take(4)
            .map { assertNotNull(card(it)) }
            .toList()
        assertEquals(listOf(MainKind.ToRange, MainKind.Deficit, MainKind.Eaten, MainKind.ToRange), readings.map { it.main.kind })
        assertEquals(listOf("655", "935", "1,245", "655"), readings.map { it.main.display.text() })
        assertEquals(listOf(MainKind.Deficit, MainKind.Eaten, MainKind.ToRange, MainKind.Deficit), readings.map { it.nextKind })
    }

    @Test
    fun theMainNumberWithItsUnit_isWhatTheStickyBarLeadsWith() {
        fun sticky(settings: Settings) = TodayFixtures.todayState(
            policy(PresetId.Full),
            settings = settings,
            summaryOn = true,
            goalsAndEnergyOn = true,
            burned = TodayFixtures.burned,
        ).compact?.pages?.first()?.parts?.first() as CompactPart.Amount

        val gap = sticky(Settings())
        assertEquals("655 kcal", gap.display.text())
        assertEquals(Res.string.today_card_to_range_line, gap.phrase)
        val deficit = sticky(Settings().rememberMainNumber(MainNumberChoice.Balance))
        assertEquals("935 kcal", deficit.display.text())
        assertEquals(Res.string.today_compact_deficit, deficit.phrase)
        val eaten = sticky(Settings().rememberMainNumber(MainNumberChoice.Eaten))
        assertEquals("1,245 kcal", eaten.display.text())
        assertNull(eaten.phrase, "eaten leads bare, as the bar always did")
    }

    @Test
    fun afterNumbersHaveBeenHard_theMainNumberNeverFlipsIntoADeficitTheyDidNotSet() {
        val hard = Settings(onboarding = OnboardingAnswers(hard = true))
        val card = assertNotNull(card(hard))
        assertEquals(MainKind.ToRange, card.main.kind)
        assertEquals(MainNumberChoice.Eaten, card.next)
        assertEquals(MainNumberChoice.Goal, card(hard.rememberMainNumber(MainNumberChoice.Eaten))?.next)
        assertEquals(MainKind.ToRange, card(hard.rememberMainNumber(MainNumberChoice.Balance))?.main?.kind, "a stored deficit falls back")
        // A set deficit is their own goal, so its balance stays.
        val deficitGoal = assertNotNull(card(TodayFixtures.deficit500.copy(onboarding = OnboardingAnswers(hard = true))))
        assertEquals(MainKind.Deficit, deficitGoal.main.kind)
    }

    @Test
    fun withoutBurned_theresNoDeficit_noBurnedBar_andTheToggleIsGoalAndEaten() {
        val card = assertNotNull(card(Settings(todayMainNumber = MainNumberChoice.Balance), burned = false))
        assertEquals(MainKind.ToRange, card.main.kind)
        assertNull(card.burned)
        assertEquals(MainNumberChoice.Eaten, card.next)
        assertEquals(MainNumberChoice.Goal, card(Settings().rememberMainNumber(MainNumberChoice.Eaten), burned = false)?.next)
    }

    @Test
    fun withWatchEnergyHidden_burnedAndTheDeficitGo_theCardStays() {
        val card = assertNotNull(card(TodayFixtures.deficit500, policy(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide))))
        assertNull(card.burned)
        assertEquals(MainNumberChoice.Eaten, card.next, "eaten, never the deficit")
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
    }

    @Test
    fun theNutrientsSwitch_turnsTheNutrientsPageOff() {
        fun energy(settings: Settings) = TodayFixtures.todayState(
            policy(PresetId.Full),
            settings = settings,
            summaryOn = true,
            goalsAndEnergyOn = true,
            burned = TodayFixtures.burned,
        ).summary as TodaySummary.Energy
        assertNotNull(energy(Settings()).nutrientsPage)
        assertNull(energy(Settings(todayNutrientsLink = false)).nutrientsPage)
    }

    @Test
    fun outsideFullNumbers_theCardComesFromTheMode() {
        listOf(PresetId.MacrosWithGrams, PresetId.MacrosOnly, PresetId.NoNumbers).forEach { preset ->
            assertNull(card(policy = policy(preset)), "$preset")
        }
        assertNull(card(policy = policy(NumbersSettings.later())), "Later")
        assertNull(card(policy = policy(NumbersSettings.later(), at = TodayFixtures.evening)), "Later, after its reveal")
    }

    @Test
    fun theListsRows_turnOff_withOrWithoutTheFlag() {
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
        // More › Today screen is reachable whatever the flags say, so its switches always count.
        val flagOff = TodayFixtures.todayState(policy(PresetId.Full), settings = off, summaryOn = true, weighIns = TodayFixtures.weighIns)
        assertNull(flagOff.water)
        assertNull(flagOff.closeDay)
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
