package com.opson.features.settings.impl.energy

import com.opson.features.settings.impl.FakeBurnedRepository
import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.integrations.FakeCalendar
import com.opson.features.settings.impl.integrations.FakeHealthRepository
import com.opson.features.settings.impl.integrations.healthFlag
import com.opson.libraries.core.DeviceRegion
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.nutrition.BodyDetail
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.HeightInput
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.WeightUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** Resting energy (design sync 4, 13.2d): the builder per branch and the view model per action. */
class RestingEnergyTest : CoroutineTest() {

    private val choices = RestingChoices(1610, emptySet(), 1560, HealthSource.HealthConnect, 72.0)
    private val missing = choices.copy(calculatedKcal = null, missing = setOf(BodyDetail.Height, BodyDetail.Weight))

    private fun build(settings: Settings = Settings(), choices: RestingChoices = this.choices, numbers: NumbersSettings = NumbersSettings(PresetId.Full)) =
        RestingEnergyStateBuilder.build(settings, choices, SettingsFixtures.policy(numbers))

    // --- builder ---

    @Test
    fun eachSourceShowsWhatItWouldGive() {
        val state = build()
        assertEquals(RestingSource.Calculate, state.source)
        assertEquals(Display.Shown("1,610"), state.calculated)
        assertEquals(HealthSource.HealthConnect, state.health)
        assertEquals(Display.Shown("1,560"), state.healthKcal)
        assertNull(state.details, "nothing missing")
    }

    @Test
    fun calculatingWithDetailsMissing_asksForThem() {
        val details = assertNotNull(build(Settings(birthYear = 1990, bodySex = BodySex.Male), missing).details)
        assertEquals("1990", details.birthYearText)
        assertEquals(HeightInput(HeightUnit.Cm), details.height)
        assertEquals(BodySex.Male, details.sex)
        assertEquals(true, details.weightMissing)
        assertNull(build(Settings(restingSource = RestingSource.Own), missing).details, "only while calculating")
    }

    @Test
    fun theHeightUnit_isTheOnePicked_elsePoundsMeanFeet_elseTheRegions() {
        fun unit(settings: Settings, region: HeightUnit) =
            assertNotNull(RestingEnergyStateBuilder.build(settings, missing, SettingsFixtures.policy(PresetId.Full), regionHeight = region).details).height.unit

        assertEquals(HeightUnit.FtIn, unit(Settings(), HeightUnit.FtIn), "a US phone")
        assertEquals(HeightUnit.Cm, unit(Settings(), HeightUnit.Cm))
        assertEquals(HeightUnit.FtIn, unit(Settings(weightUnit = WeightUnit.Lb), HeightUnit.Cm), "pounds kept inches")
        assertEquals(HeightUnit.Cm, unit(Settings(heightUnit = HeightUnit.Cm), HeightUnit.FtIn), "the person's pick wins")

        val stored = assertNotNull(build(Settings(heightCm = 177.8, heightUnit = HeightUnit.FtIn), missing).details)
        assertEquals(HeightInput(HeightUnit.FtIn, feet = "5", inches = "10"), stored.height)
    }

    @Test
    fun withWatchEnergyHidden_noAmountShows_notEvenYourOwn() {
        val numbers = NumbersSettings(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide))
        val state = build(Settings(restingOwnKcal = 1600), numbers = numbers)
        assertEquals(Display.Hidden, state.calculated)
        assertEquals(Display.Hidden, state.healthKcal)
        assertNull(state.ownText)
    }

    // --- view model ---

    private fun viewModel(
        repository: FakeSettingsRepository,
        health: FakeHealthRepository = FakeHealthRepository(HealthStatus.Connected),
        burned: FakeBurnedRepository = FakeBurnedRepository(choices = choices),
        region: String? = "GB",
    ) = RestingEnergyViewModel(
        repository,
        burned,
        FakePolicySource(SettingsFixtures.policy(PresetId.Full)),
        health,
        healthFlag(true),
        FakeCalendar(),
        DeviceRegion { region },
    )

    @Test
    fun everydayActivity_showsWithoutReleaseExercise_andIsSaved() = runUnitTest {
        // Everyday activity is Burned's movement with no health app, so a deficit works without
        // one; it does not wait for logged exercise.
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)
        assertEquals(EverydayActivity.MostlySitting, vm.state.everyday, "the default")

        vm.takeAction(RestingEnergyAction.EverydayPicked(EverydayActivity.OnMyFeet))

        assertEquals(EverydayActivity.OnMyFeet, repository.state.value.everydayActivity)
        assertEquals(EverydayActivity.OnMyFeet, vm.state.everyday)
    }

    @Test
    fun sourcePicked_isSaved() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)
        vm.takeAction(RestingEnergyAction.SourcePicked(RestingSource.Health))
        assertEquals(RestingSource.Health, repository.state.value.restingSource)
        assertEquals(RestingSource.Health, vm.state.source)
        assertEquals(HealthSource.HealthConnect, vm.state.activitySource)
    }

    @Test
    fun ownTyped_savesOnlyASensibleNumber_andPicksIt() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)
        val writes = repository.writes

        vm.takeAction(RestingEnergyAction.OwnTyped("16"))
        assertEquals(writes, repository.writes, "16 kcal is half-typed, not a resting energy")
        assertEquals("16", vm.state.ownText)

        vm.takeAction(RestingEnergyAction.OwnTyped("1,600"))
        assertEquals(1600, repository.state.value.restingOwnKcal)
        assertEquals(RestingSource.Own, repository.state.value.restingSource)
    }

    @Test
    fun detailsTyped_saveOnceTheyMakeSense() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)

        vm.takeAction(RestingEnergyAction.BirthYearTyped("199"))
        assertNull(repository.state.value.birthYear)
        vm.takeAction(RestingEnergyAction.BirthYearTyped("1990"))
        assertEquals(1990, repository.state.value.birthYear)
        vm.takeAction(RestingEnergyAction.BirthYearTyped("2024"))
        assertEquals(1990, repository.state.value.birthYear, "too young for the equation")

        vm.takeAction(RestingEnergyAction.HeightCmTyped("17"))
        assertNull(repository.state.value.heightCm)
        vm.takeAction(RestingEnergyAction.HeightCmTyped("172"))
        assertEquals(172.0, repository.state.value.heightCm)

        vm.takeAction(RestingEnergyAction.SexPicked(BodySex.Unsaid))
        assertEquals(BodySex.Unsaid, repository.state.value.bodySex)
    }

    @Test
    fun feetAndInches_areSavedInCentimetres_andTheUnitIsKept() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository, burned = FakeBurnedRepository(choices = missing))

        vm.takeAction(RestingEnergyAction.HeightUnitPicked(HeightUnit.FtIn))
        assertEquals(HeightUnit.FtIn, repository.state.value.heightUnit)
        vm.takeAction(RestingEnergyAction.HeightFeetTyped("5"))
        vm.takeAction(RestingEnergyAction.HeightInchesTyped("11,5"))

        assertEquals(181.61, repository.state.value.heightCm!!, 1e-9)
        assertEquals(HeightInput(HeightUnit.FtIn, feet = "5", inches = "11.5"), vm.state.details?.height)

        vm.takeAction(RestingEnergyAction.HeightInchesTyped("13"))
        assertEquals(181.61, repository.state.value.heightCm!!, 1e-9, "13 inches is a typo, so the last height stays")
    }

    @Test
    fun theHeightFields_stayUp_whileTyping_evenOnceNothingIsMissing() = runUnitTest {
        // Five feet is already a height. If the details went away when it was saved, the inches
        // field would vanish before the person reached it.
        val repository = FakeSettingsRepository(Settings(heightUnit = HeightUnit.FtIn))
        val vm = viewModel(repository, burned = FakeBurnedRepository(choices = choices))
        assertNull(vm.state.details, "nothing missing, nothing typed")

        vm.takeAction(RestingEnergyAction.HeightFeetTyped("5"))
        assertEquals(152.4, repository.state.value.heightCm!!, 1e-9)
        assertNotNull(vm.state.details)
    }

    @Test
    fun switchingUnits_keepsTheHeightTyped() = runUnitTest {
        val repository = FakeSettingsRepository(Settings(heightCm = 180.0))
        val vm = viewModel(repository, burned = FakeBurnedRepository(choices = missing))
        assertEquals(HeightInput(HeightUnit.Cm, cm = "180"), vm.state.details?.height)

        vm.takeAction(RestingEnergyAction.HeightUnitPicked(HeightUnit.FtIn))
        assertEquals(HeightInput(HeightUnit.FtIn, feet = "5", inches = "11"), vm.state.details?.height)
        assertEquals(180.0, repository.state.value.heightCm, "switching alone writes no new height")
    }

    @Test
    fun aUsPhone_opensInFeetAndInches() = runUnitTest {
        val vm = viewModel(FakeSettingsRepository(), burned = FakeBurnedRepository(choices = missing), region = "US")
        assertEquals(HeightUnit.FtIn, vm.state.details?.height?.unit)
    }

    @Test
    fun activityInBurnedToggled_isSaved() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)
        vm.takeAction(RestingEnergyAction.ActivityInBurnedToggled(false))
        assertFalse(repository.state.value.activityInBurned)
        assertFalse(vm.state.activityInBurned)
    }

    @Test
    fun countingDigestingFood_isOffUntilSwitchedOn_andSaved() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)
        assertFalse(vm.state.thermicEffectInBurned)

        vm.takeAction(RestingEnergyAction.ThermicEffectToggled(true))

        assertTrue(repository.state.value.thermicEffectInBurned)
        assertTrue(vm.state.thermicEffectInBurned)
    }

    @Test
    fun activityAndBack_navigate() = runUnitTest {
        val vm = viewModel(FakeSettingsRepository())
        val events = eventsOf(vm)
        vm.takeAction(RestingEnergyAction.ActivityTapped)
        vm.takeAction(RestingEnergyAction.BackTapped)
        assertEquals(listOf(RestingEnergyEvent.OpenIntegrations, RestingEnergyEvent.Close), events)
    }
}
