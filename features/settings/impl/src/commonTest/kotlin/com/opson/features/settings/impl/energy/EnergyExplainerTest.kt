package com.opson.features.settings.impl.energy

import com.opson.features.settings.EnergyExplainerRoute
import com.opson.features.settings.impl.FakeBurnedRepository
import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Heights
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingEnergy
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Units
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The (i) sheets: each topic's working from the person's own numbers, and what a mode that hides
 * them leaves out. How the words render, and that no calorie amount reaches a hidden mode, is the
 * leak test's (`energy-explainer-*` surfaces).
 */
class EnergyExplainerTest : CoroutineTest() {

    /** The owner: 5 ft 11 in, born 1998, male, 170 lb, on 2026-10-25. */
    private val settings = Settings(heightCm = Heights.cm(5, 11.0), birthYear = 1998, bodySex = BodySex.Male, weightUnit = WeightUnit.Lb)
    private val choices = RestingChoices(1760, emptySet(), 1840, HealthSource.AppleHealth, Units.lbToKg(170.0))
    private val burned = Burned(RestingEnergy(1760, RestingFrom.Calculated), everydayKcal = 350, exerciseKcal = 200, foodKcal = 150)

    private fun build(
        topic: EnergyTopic,
        settings: Settings = this.settings,
        numbers: NumbersSettings = NumbersSettings(PresetId.Full),
        burned: Burned? = this.burned,
    ) = EnergyExplainerStateBuilder.build(
        topic,
        EnergyExplainerInputs(settings.copy(numbers = numbers), choices, burned, SettingsFixtures.today),
        SettingsFixtures.policy(numbers),
    )

    @Test
    fun resting_showsMifflinStJeorTermByTerm() {
        val working = assertNotNull(build(EnergyTopic.Resting).working)

        assertEquals(Display.Shown("77.1 kg"), working.weight, "the equation's kilograms, whatever the profile shows")
        assertEquals(180, working.heightCm)
        assertEquals(28, working.age)
        assertEquals(Display.Shown("771"), working.weightTerm)
        assertEquals(Display.Shown("1,127"), working.heightTerm)
        assertEquals(Display.Shown("140"), working.ageTerm)
        assertEquals(Display.Shown("5"), working.sexTerm)
        assertTrue(working.sexAdds)
        assertEquals(Display.Shown("1,760 kcal"), working.total)
    }

    @Test
    fun resting_withoutEnergyOutOrWeightOnShow_hasNoWorking() {
        assertNull(build(EnergyTopic.Resting, numbers = NumbersSettings(PresetId.NoNumbers)).working)
        val weightHidden = NumbersSettings(PresetId.Full, overrides = mapOf(Metric.Weight to Visibility.Hide))
        assertNull(build(EnergyTopic.Resting, numbers = weightHidden).working, "ten times the weight would give it away")
        assertNull(build(EnergyTopic.Resting, settings = settings.copy(bodySex = null)).working)
    }

    @Test
    fun resting_namesTheNumberInUse_whenItIsNotTheCalculation() {
        assertNull(build(EnergyTopic.Resting).inUse)
        val health = assertNotNull(build(EnergyTopic.Resting, settings = settings.copy(restingSource = RestingSource.Health)).inUse)
        assertEquals(RestingFrom.Health, health.from)
        assertEquals(HealthSource.AppleHealth, health.source)
        assertEquals(Display.Shown("1,840 kcal"), health.amount)
    }

    @Test
    fun burned_addsUpTheParts_andLeavesDigestingOutWhereCaloriesHide() {
        val state = build(EnergyTopic.Burned)
        val working = assertNotNull(state.burned)
        assertEquals(Display.Shown("1,760"), working.resting)
        assertEquals(Display.Shown("350"), working.everyday)
        assertEquals(Display.Shown("200"), working.exercise)
        assertEquals(Display.Shown("150"), working.food)
        assertEquals(Display.Shown("2,460 kcal"), working.total)
        assertTrue(state.digesting)

        val energyOnly = NumbersSettings(PresetId.MacrosWithGrams, overrides = mapOf(Metric.EnergyOut to Visibility.Show))
        val hidden = build(EnergyTopic.Burned, numbers = energyOnly)
        assertFalse(hidden.digesting, "a tenth of what was eaten is the day's calories")
        assertEquals(Display.Hidden, hidden.burned?.food)
        assertEquals(Display.Shown("2,310 kcal"), hidden.burned?.total)

        assertNull(build(EnergyTopic.Burned, numbers = NumbersSettings(PresetId.NoNumbers)).burned)
        assertNull(build(EnergyTopic.Burned, burned = null).burned)
    }

    @Test
    fun balance_showsTodaysGoalFromBurned_forASetDeficit() {
        val deficit = settings.copy(targets = SettingsFixtures.rangeTargets.copy(style = TargetStyle.Deficit(kcal = 500, startingKcal = 500)))
        val state = build(EnergyTopic.Balance, settings = deficit)
        val working = assertNotNull(state.balance)

        assertFalse(working.surplus)
        assertEquals(Display.Shown("2,460"), working.burned)
        assertEquals(Display.Shown("500"), working.amount)
        assertEquals(Display.Shown("1,960 kcal"), working.goal)
        assertFalse(working.held)
        assertTrue(state.ruleNumbers)
        assertEquals(WeightUnit.Lb, state.weightUnit)
    }

    @Test
    fun balance_hasNoWorkingForARange_andNoRuleNumbersWhereCaloriesHide() {
        assertNull(build(EnergyTopic.Balance, settings = settings.copy(targets = SettingsFixtures.rangeTargets)).balance)
        val noNumbers = build(EnergyTopic.Balance, settings = settings.copy(targets = SettingsFixtures.deficitTargets), numbers = NumbersSettings(PresetId.NoNumbers))
        assertNull(noNumbers.balance)
        assertFalse(noNumbers.ruleNumbers, "about 3,500 calories is a calorie amount")
    }

    @Test
    fun anUnknownTopic_opensRestingEnergy() {
        assertEquals(EnergyTopic.Burned, EnergyTopic.fromWire(EnergyExplainerRoute.TOPIC_BURNED))
        assertEquals(EnergyTopic.Resting, EnergyTopic.fromWire("something else"))
    }

    @Test
    fun theViewModel_buildsItsTopic_andItsLinksNavigate() = runUnitTest {
        val vm = EnergyExplainerViewModel(
            EnergyTopic.Burned,
            FakeSettingsRepository(settings),
            FakeBurnedRepository(burned = burned, choices = choices),
            FakePolicySource(SettingsFixtures.policy(PresetId.Full)),
            FakeCalendar(SettingsFixtures.today),
        )
        val events = eventsOf(vm)
        assertEquals(Display.Shown("2,460 kcal"), vm.state.burned?.total)

        vm.takeAction(EnergyExplainerAction.ProfileTapped)
        vm.takeAction(EnergyExplainerAction.RestingEnergyTapped)
        vm.takeAction(EnergyExplainerAction.DoneTapped)
        assertEquals(listOf(EnergyExplainerEvent.OpenProfile, EnergyExplainerEvent.OpenRestingEnergy, EnergyExplainerEvent.Dismiss), events)
    }
}
