package com.opson.features.settings.impl.energy

import com.opson.features.settings.impl.FakeBurnedRepository
import com.opson.features.settings.impl.FakePolicySource
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.integrations.FakeHealthRepository
import com.opson.features.settings.impl.integrations.healthFlag
import com.opson.libraries.diary.Settings
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.nutrition.BodyDetail
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Resting energy (design sync 4, 13.2d): the builder per branch and the view model per action. The details it reads are `ProfileTest`'s. */
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
        assertEquals(emptySet(), state.profileMissing, "calculating, with nothing missing")
    }

    @Test
    fun calculating_linksTheProfile_sayingWhatItStillNeeds() {
        assertEquals(setOf(BodyDetail.Height, BodyDetail.Weight), build(Settings(), missing).profileMissing)
        assertNull(build(Settings(restingSource = RestingSource.Own), missing).profileMissing, "only while calculating")
        assertNull(build(Settings(restingSource = RestingSource.Health), missing).profileMissing)
    }

    @Test
    fun everydayActivity_isAskedOnlyWhileNoHealthAppCountsTheDay() {
        // Connected with activity counted, Burned takes movement from the health app and leaves
        // everyday activity out, so asking would change nothing.
        assertFalse(RestingEnergyStateBuilder.build(Settings(), choices, SettingsFixtures.policy(PresetId.Full), activitySource = HealthSource.AppleHealth).asksEveryday)
        assertTrue(build(Settings()).asksEveryday, "no health app")
        val off = RestingEnergyStateBuilder.build(Settings(activityInBurned = false), choices, SettingsFixtures.policy(PresetId.Full), activitySource = HealthSource.HealthConnect)
        assertTrue(off.asksEveryday, "activity off: the everyday baseline counts again")
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
    ) = RestingEnergyViewModel(
        repository,
        burned,
        FakePolicySource(SettingsFixtures.policy(PresetId.Full)),
        health,
        healthFlag(true),
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
    fun activityInBurnedToggled_isSaved() = runUnitTest {
        val repository = FakeSettingsRepository()
        val vm = viewModel(repository)
        vm.takeAction(RestingEnergyAction.ActivityInBurnedToggled(false))
        assertFalse(repository.state.value.activityInBurned)
        assertFalse(vm.state.activityInBurned)
    }

    @Test
    fun profileActivityAndBack_navigate() = runUnitTest {
        val vm = viewModel(FakeSettingsRepository())
        val events = eventsOf(vm)
        vm.takeAction(RestingEnergyAction.ProfileTapped)
        vm.takeAction(RestingEnergyAction.ActivityTapped)
        vm.takeAction(RestingEnergyAction.BackTapped)
        assertEquals(listOf(RestingEnergyEvent.OpenProfile, RestingEnergyEvent.OpenIntegrations, RestingEnergyEvent.Close), events)
    }
}
