package com.opson.features.settings.impl.micros

import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.goalsAndEnergyFlag
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ReferenceBasis
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Vitamins and minerals: the page and the sheet per branch, and the sheet's writes. */
class MicroTargetsTest : CoroutineTest() {

    private val today = SettingsFixtures.today
    private val woman = SettingsFixtures.settings().copy(birthYear = 1990, bodySex = BodySex.Female)

    private fun page(settings: Settings, goalsAndEnergy: Boolean = true) =
        MicroTargetsStateBuilder.build(settings, SettingsFixtures.policy(settings.numbers), today, goalsAndEnergy = goalsAndEnergy)

    private fun sheet(settings: Settings, micro: Micronutrient, typed: String? = null) =
        MicroTargetsStateBuilder.sheet(settings, SettingsFixtures.policy(settings.numbers), today, micro, typed)

    private fun rows(settings: Settings) = page(settings).groups.flatMap { it.rows }.associateBy { it.micro }

    // --- builder ---

    @Test
    fun withAgeAndSex_theReferencesAreTheirs() {
        val state = page(woman)
        assertEquals(ReferenceBasis.AgeAndSex, state.basis)
        assertFalse(state.canAddDetails)
        assertEquals("18 mg", rows(woman).getValue(Micronutrient.Iron).reference)
        assertEquals("2,300 mg limit", rows(woman).getValue(Micronutrient.Sodium).reference)
    }

    @Test
    fun withNothingKnown_theyAreTheDailyValues_andResting_energyCanTakeTheDetails() {
        val state = page(SettingsFixtures.settings())
        assertEquals(ReferenceBasis.DailyValue, state.basis)
        assertTrue(state.canAddDetails)
        assertEquals("900 µg", rows(SettingsFixtures.settings()).getValue(Micronutrient.VitaminA).reference)
    }

    /** Resting energy only asks for them while it calculates, and only with release.goalsAndEnergy. */
    @Test
    fun theDetailsLink_onlyWhereRestingEnergyAsks() {
        assertFalse(page(SettingsFixtures.settings().copy(restingSource = RestingSource.Own)).canAddDetails)
        assertFalse(page(SettingsFixtures.settings(), goalsAndEnergy = false).canAddDetails)
    }

    @Test
    fun theirOwnNumber_andTodaysFour_areMarked() {
        val settings = woman.withMicroTarget(Micronutrient.VitaminD, 25.0)
        val rows = rows(settings)
        assertTrue(rows.getValue(Micronutrient.VitaminD).own)
        assertEquals("25 µg", rows.getValue(Micronutrient.VitaminD).reference)
        assertEquals(
            setOf(Micronutrient.Sodium, Micronutrient.Potassium, Micronutrient.Calcium, Micronutrient.Iron),
            rows.values.filter { it.onToday }.map { it.micro }.toSet(),
        )
    }

    @Test
    fun whereTheModeHidesMicros_thePageIsOff() {
        listOf(PresetId.MacrosOnly, PresetId.NoNumbers).forEach { preset ->
            val settings = SettingsFixtures.settings(NumbersSettings(preset = preset))
            assertTrue(page(settings).off)
            assertEquals(emptyList(), page(settings).groups)
            assertTrue(sheet(settings, Micronutrient.Iron).off)
            assertEquals("", sheet(settings, Micronutrient.Iron).suggested)
        }
        assertTrue(page(SettingsFixtures.settings(NumbersSettings(overrides = mapOf(Metric.Micros to Visibility.Hide)))).off)
    }

    @Test
    fun sheet_showsTheSuggestion_andTheirOwnPlainly() {
        val plain = sheet(woman, Micronutrient.Calcium)
        assertEquals("1,000 mg", plain.suggested)
        assertEquals("1000", plain.suggestedNumber)
        assertEquals("", plain.ownText)
        assertFalse(plain.own)
        val own = sheet(woman.withMicroTarget(Micronutrient.Copper, 0.85), Micronutrient.Copper)
        assertEquals("0.85", own.ownText)
        assertTrue(own.own)
        assertEquals("2,300 mg limit", sheet(woman, Micronutrient.Sodium).suggested)
    }

    @Test
    fun sheet_saysWhenATypedNumberIsNotADailyAmount() {
        assertTrue(sheet(woman, Micronutrient.VitaminD, typed = "9999").invalid)
        assertFalse(sheet(woman, Micronutrient.VitaminD, typed = "25").invalid)
        assertFalse(sheet(woman, Micronutrient.VitaminD, typed = "").invalid)
    }

    @Test
    fun sheet_today_fullAndLast() {
        assertTrue(sheet(woman, Micronutrient.Zinc).todayFull)
        assertFalse(sheet(woman, Micronutrient.Iron).todayFull)
        var one = woman
        listOf(Micronutrient.Sodium, Micronutrient.Potassium, Micronutrient.Calcium).forEach { one = one.withTodayNutrient(it, false) }
        assertTrue(sheet(one, Micronutrient.Iron).todayLast)
        assertFalse(sheet(one, Micronutrient.Zinc).todayFull)
    }

    // --- view models ---

    private fun sheetVm(repository: FakeSettingsRepository, micro: Micronutrient = Micronutrient.VitaminD) = MicroTargetViewModel(
        micro,
        repository,
        FakePolicySource(SettingsFixtures.policy(PresetId.Full)),
        FakeCalendar(today),
    )

    @Test
    fun typing_savesOnceItIsADailyAmount_andEmptyGoesBackToTheSuggestion() = runUnitTest {
        val repository = FakeSettingsRepository(woman)
        val vm = sheetVm(repository)

        vm.takeAction(MicroTargetAction.OwnTyped("9999"))
        assertEquals(emptyMap(), repository.state.value.microTargets)
        assertTrue(vm.state.invalid)
        assertEquals("9999", vm.state.ownText)

        vm.takeAction(MicroTargetAction.OwnTyped("25"))
        assertEquals(mapOf("vitamin_d" to 25.0), repository.state.value.microTargets)

        vm.takeAction(MicroTargetAction.OwnTyped(""))
        assertEquals(emptyMap(), repository.state.value.microTargets)
    }

    @Test
    fun useTheSuggestion_clearsTheirOwn() = runUnitTest {
        val repository = FakeSettingsRepository(woman.withMicroTarget(Micronutrient.VitaminD, 25.0))
        val vm = sheetVm(repository)
        vm.takeAction(MicroTargetAction.UseSuggestedTapped)
        assertEquals(emptyMap(), repository.state.value.microTargets)
        assertEquals("", vm.state.ownText)
    }

    @Test
    fun todayToggle_putsItOnTheCard() = runUnitTest {
        val repository = FakeSettingsRepository(woman.withTodayNutrient(Micronutrient.Sodium, false))
        val vm = sheetVm(repository)
        vm.takeAction(MicroTargetAction.TodayToggled(true))
        assertEquals(listOf("potassium", "calcium", "iron", "vitamin_d"), repository.state.value.todayNutrients)
        assertTrue(vm.state.onToday)
    }

    @Test
    fun page_opensANutrient_andCloses() = runUnitTest {
        val vm = MicroTargetsViewModel(
            backToTodayScreen = true,
            settingsRepository = FakeSettingsRepository(woman),
            policySource = FakePolicySource(SettingsFixtures.policy(PresetId.Full)),
            calendar = FakeCalendar(today),
            goalsAndEnergy = goalsAndEnergyFlag(true),
        )
        val events = eventsOf(vm)
        assertTrue(vm.state.backToTodayScreen)
        assertNull(vm.state.groups.flatMap { it.rows }.firstOrNull { it.micro == Micronutrient.Iron && !it.onToday })
        vm.takeAction(MicroTargetsAction.NutrientTapped(Micronutrient.Zinc))
        vm.takeAction(MicroTargetsAction.BackTapped)
        assertEquals(listOf(MicroTargetsEvent.OpenNutrient(Micronutrient.Zinc), MicroTargetsEvent.Close), events)
    }
}
