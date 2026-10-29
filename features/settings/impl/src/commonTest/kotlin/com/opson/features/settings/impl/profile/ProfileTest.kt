package com.opson.features.settings.impl.profile

import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.libraries.core.DeviceRegion
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.HeightInput
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Instant

/**
 * More › Profile: the builder per mode and the view model per field. That Burned and the vitamin
 * and mineral references read these same settings is `RealBurnedRepositoryTest` and
 * `MicroReferencesTest`; nothing here keeps a copy of its own.
 */
class ProfileTest : CoroutineTest() {

    private val full = SettingsFixtures.policy(PresetId.Full)

    private fun build(
        settings: Settings = ProfileFixtures.filled,
        latest: WeighIn? = ProfileFixtures.weighIn,
        numbers: NumbersSettings = NumbersSettings(PresetId.Full),
        region: HeightUnit = HeightUnit.Cm,
    ) = ProfileStateBuilder.build(settings, latest, SettingsFixtures.policy(numbers), SettingsFixtures.timeZone, regionHeight = region)

    // --- builder ---

    @Test
    fun filledIn_allNumbers_showsTheWeighInBmiAndBodyFat() {
        val state = build()
        assertEquals("1990", state.birthYearText)
        assertEquals(BodySex.Female, state.sex)
        assertEquals(HeightInput(HeightUnit.Cm, cm = "168"), state.height)
        assertEquals(ProfileWeight.Logged(Display.Shown("64.2 kg"), onText = "Oct 25"), state.weight)
        assertEquals(Display.Shown("22.7"), state.bmi)
        assertEquals("24", state.bodyFatText)
    }

    @Test
    fun theWeighInFollowsTheWeightUnit() {
        val logged = assertIs<ProfileWeight.Logged>(build(ProfileFixtures.filled.copy(weightUnit = WeightUnit.Lb)).weight)
        assertEquals(Display.Shown("141.5 lb"), logged.display)
    }

    @Test
    fun neverFilledIn_emptyFields_noWeighIn_noBmi() {
        val state = build(Settings(), latest = null)
        assertEquals("", state.birthYearText)
        assertNull(state.sex)
        assertEquals(HeightInput(HeightUnit.Cm), state.height)
        assertEquals(ProfileWeight.None, state.weight)
        assertEquals(Display.Hidden, state.bmi)
        assertEquals("", state.bodyFatText, "the field is there, empty")
    }

    @Test
    fun theHeightUnit_isTheOnePicked_elsePoundsMeanFeet_elseTheRegions() {
        fun unit(settings: Settings, region: HeightUnit) = build(settings, region = region).height.unit

        val noUnit = ProfileFixtures.filled.copy(heightUnit = null)
        assertEquals(HeightUnit.FtIn, unit(noUnit, HeightUnit.FtIn), "a US phone")
        assertEquals(HeightUnit.Cm, unit(noUnit, HeightUnit.Cm))
        assertEquals(HeightUnit.FtIn, unit(noUnit.copy(weightUnit = WeightUnit.Lb), HeightUnit.Cm), "pounds kept inches")
        assertEquals(HeightUnit.Cm, unit(noUnit.copy(heightUnit = HeightUnit.Cm), HeightUnit.FtIn), "the person's pick wins")

        val stored = build(ProfileFixtures.filled.copy(heightCm = 177.8, heightUnit = HeightUnit.FtIn))
        assertEquals(HeightInput(HeightUnit.FtIn, feet = "5", inches = "10"), stored.height)
    }

    @Test
    fun whereWeightIsNotShownInFull_theWeighInSaysWhenButNotWhat_andBmiAndBodyFatGo() {
        val softened = NumbersSettings(PresetId.Full, overrides = mapOf(Metric.Weight to Visibility.Soften))
        listOf(NumbersSettings(PresetId.NoNumbers), softened).forEach { numbers ->
            val state = build(numbers = numbers)
            assertEquals(ProfileWeight.Logged(Display.Hidden, onText = "Oct 25"), state.weight, "in $numbers")
            assertEquals(Display.Hidden, state.bmi)
            assertNull(state.bodyFatText)
            assertEquals("1990", state.birthYearText, "the details stay")
        }
    }

    @Test
    fun afterNumbersHaveBeenHard_neverBmiOrBodyFat() {
        val state = build(ProfileFixtures.filled.copy(onboarding = OnboardingAnswers(hard = true)))
        assertEquals(ProfileWeight.Logged(Display.Shown("64.2 kg"), onText = "Oct 25"), state.weight)
        assertEquals(Display.Hidden, state.bmi)
        assertNull(state.bodyFatText)
    }

    @Test
    fun theBackLabelFollowsWhereItWasOpenedFrom() {
        assertEquals(ProfileBack.RestingEnergy, ProfileBack.fromWire("resting"))
        assertEquals(ProfileBack.Micros, ProfileBack.fromWire("micros"))
        assertEquals(ProfileBack.More, ProfileBack.fromWire("somewhere new"))
    }

    // --- view model ---

    private class FakeWeights(latest: WeighIn?) : WeightRepository {
        val weighIns = MutableStateFlow(listOfNotNull(latest))
        override fun observeWeighIns(since: Instant?): Flow<List<WeighIn>> = weighIns
        override suspend fun addWeighIn(kg: Double, measuredAt: Instant?): WeighIn = error("Profile never logs a weigh-in")
        override suspend fun removeWeighIn(id: String) = error("Profile never removes a weigh-in")
    }

    private fun viewModel(
        repository: FakeSettingsRepository = FakeSettingsRepository(Settings()),
        weights: FakeWeights = FakeWeights(null),
        region: String? = "GB",
        back: ProfileBack = ProfileBack.More,
    ) = ProfileViewModel(back, repository, weights, FakePolicySource(full), FakeCalendar(SettingsFixtures.today), DeviceRegion { region })

    @Test
    fun theLatestWeighInIsTheOneShown() = runUnitTest {
        val earlier = ProfileFixtures.weighIn.copy(id = "w0", measuredAt = Instant.parse("2026-10-01T07:00:00Z"), kg = 66.0)
        val weights = FakeWeights(ProfileFixtures.weighIn)
        weights.weighIns.value = listOf(ProfileFixtures.weighIn, earlier)
        val vm = viewModel(FakeSettingsRepository(ProfileFixtures.filled), weights)

        assertEquals(ProfileWeight.Logged(Display.Shown("64.2 kg"), onText = "Oct 25"), vm.state.weight)
    }

    @Test
    fun detailsTyped_saveOnceTheyMakeSense() = runUnitTest {
        val repository = FakeSettingsRepository(Settings())
        val vm = viewModel(repository)

        vm.takeAction(ProfileAction.BirthYearTyped("199"))
        assertNull(repository.state.value.birthYear)
        assertEquals("199", vm.state.birthYearText, "kept as typed")
        vm.takeAction(ProfileAction.BirthYearTyped("1990"))
        assertEquals(1990, repository.state.value.birthYear)
        vm.takeAction(ProfileAction.BirthYearTyped("2024"))
        assertEquals(1990, repository.state.value.birthYear, "too young for the equation")

        vm.takeAction(ProfileAction.HeightCmTyped("17"))
        assertNull(repository.state.value.heightCm)
        vm.takeAction(ProfileAction.HeightCmTyped("172"))
        assertEquals(172.0, repository.state.value.heightCm)

        vm.takeAction(ProfileAction.SexPicked(BodySex.Unsaid))
        assertEquals(BodySex.Unsaid, repository.state.value.bodySex)
    }

    @Test
    fun feetAndInches_areSavedInCentimetres_andTheUnitIsKept() = runUnitTest {
        val repository = FakeSettingsRepository(Settings())
        val vm = viewModel(repository)

        vm.takeAction(ProfileAction.HeightUnitPicked(HeightUnit.FtIn))
        assertEquals(HeightUnit.FtIn, repository.state.value.heightUnit)
        vm.takeAction(ProfileAction.HeightFeetTyped("5"))
        vm.takeAction(ProfileAction.HeightInchesTyped("11,5"))

        assertEquals(181.61, repository.state.value.heightCm!!, 1e-9)
        assertEquals(HeightInput(HeightUnit.FtIn, feet = "5", inches = "11.5"), vm.state.height)

        vm.takeAction(ProfileAction.HeightInchesTyped("13"))
        assertEquals(181.61, repository.state.value.heightCm!!, 1e-9, "13 inches is a typo, so the last height stays")
    }

    @Test
    fun switchingUnits_keepsTheHeightTyped_andWritesNoNewHeight() = runUnitTest {
        val repository = FakeSettingsRepository(Settings(heightCm = 180.0))
        val vm = viewModel(repository)
        assertEquals(HeightInput(HeightUnit.Cm, cm = "180"), vm.state.height)

        vm.takeAction(ProfileAction.HeightUnitPicked(HeightUnit.FtIn))
        assertEquals(HeightInput(HeightUnit.FtIn, feet = "5", inches = "11"), vm.state.height)
        assertEquals(180.0, repository.state.value.heightCm)
    }

    @Test
    fun aUsPhone_opensInFeetAndInches() = runUnitTest {
        assertEquals(HeightUnit.FtIn, viewModel(region = "US").state.height.unit)
    }

    @Test
    fun bodyFat_savesASensibleShare_andEmptyingItClearsIt() = runUnitTest {
        val repository = FakeSettingsRepository(Settings())
        val vm = viewModel(repository)

        vm.takeAction(ProfileAction.BodyFatTyped("2"))
        assertNull(repository.state.value.bodyFat, "2% is half-typed")
        vm.takeAction(ProfileAction.BodyFatTyped("22,5"))
        assertEquals(22.5, repository.state.value.bodyFat)
        assertEquals("22.5", vm.state.bodyFatText)

        vm.takeAction(ProfileAction.BodyFatTyped(""))
        assertNull(repository.state.value.bodyFat)
        assertEquals(0.0, repository.state.value.bodyFatPercent, "zero, not null, so the clear reaches the server")
    }

    @Test
    fun weightAndBack_navigate() = runUnitTest {
        val vm = viewModel(back = ProfileBack.Micros)
        val events = eventsOf(vm)
        assertEquals(ProfileBack.Micros, vm.state.back)

        vm.takeAction(ProfileAction.WeightTapped)
        vm.takeAction(ProfileAction.BackTapped)

        assertEquals(listOf(ProfileEvent.OpenWeight, ProfileEvent.Close), events)
    }
}
