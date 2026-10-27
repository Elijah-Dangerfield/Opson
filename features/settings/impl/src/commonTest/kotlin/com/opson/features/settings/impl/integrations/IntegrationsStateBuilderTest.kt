package com.opson.features.settings.impl.integrations

import com.opson.features.settings.impl.SettingsFixtures
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * Integrations' state for each connection state and each way energy out can be seen or not.
 * Energy out has no soften word, so Show is the only level that puts an amount on the page.
 */
class IntegrationsStateBuilderTest {

    private val full = SettingsFixtures.policy(PresetId.Full)

    private fun build(
        policy: VisibilityPolicy = full,
        status: HealthStatus = HealthStatus.Connected,
        energy: EnergyExpended? = IntegrationsFixtures.energy,
        connecting: Boolean = false,
        denied: Boolean = false,
        source: HealthSource = HealthSource.HealthConnect,
    ) = IntegrationsStateBuilder.build(IntegrationsFixtures.inputs(status, source, energy), policy, connecting, denied)

    @Test
    fun full_showsEachPartAndTheTotal() {
        val card = assertIs<ExpendedCard.Shown>(build().expended)
        assertEquals(listOf("1,580 kcal", "260 kcal", "150 kcal"), card.rows.map { (it.display as Display.Shown).text })
        assertEquals(Display.Shown("1,990 kcal"), card.total)
    }

    @Test
    fun aPartTheStoreDidNotHave_isLeftOut() {
        val card = assertIs<ExpendedCard.Shown>(build(energy = EnergyExpended(restingKcal = 1580.0, activeKcal = null, workoutKcal = null)).expended)
        assertEquals(1, card.rows.size)
        assertEquals(Display.Shown("1,580 kcal"), card.total)
    }

    @Test
    fun connectedWithNothingYet_isEmpty_notZero() {
        assertEquals(ExpendedCard.Empty, build(energy = null).expended)
    }

    @Test
    fun everyPresetThatDoesNotShowEnergyOut_hidesTheWholeCard() {
        PresetId.entries.filter { policy(it).level(Metric.EnergyOut) != Visibility.Show }.forEach { preset ->
            assertEquals(ExpendedCard.Hidden, build(policy = policy(preset)).expended, "in $preset")
        }
    }

    @Test
    fun blind_hidesIt() {
        assertEquals(ExpendedCard.Hidden, build(policy = SettingsFixtures.policy(PresetId.NoNumbers)).expended)
    }

    @Test
    fun softened_hidesIt_becauseEnergyOutHasNoSoftWord() {
        val soft = SettingsFixtures.policy(NumbersSettings(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Soften)))
        assertEquals(ExpendedCard.Hidden, build(policy = soft).expended)
    }

    @Test
    fun fullWithEnergyOutHidden_hidesIt() {
        val hidden = SettingsFixtures.policy(NumbersSettings(PresetId.Full, overrides = mapOf(Metric.EnergyOut to Visibility.Hide)))
        assertEquals(ExpendedCard.Hidden, build(policy = hidden).expended)
    }

    @Test
    fun delayed_hidesItBeforeTheReveal_andShowsItAfter() {
        assertEquals(ExpendedCard.Hidden, build(policy = SettingsFixtures.policy(PresetId.Later)).expended)
        val evening = VisibilityPolicy(
            NumbersSettings(PresetId.Later),
            object : Clock {
                override fun now() = Instant.parse("2026-10-25T21:30:00Z")
            },
            SettingsFixtures.timeZone,
        )
        assertIs<ExpendedCard.Shown>(build(policy = evening).expended)
    }

    @Test
    fun notConnected_hasNoCard_andOffersConnect() {
        val state = build(status = HealthStatus.NotConnected)
        assertEquals(Connection.NotConnected, state.connection)
        assertNull(state.expended)
        assertFalse(state.loading)
    }

    @Test
    fun connecting_showsWhileThePromptIsUp() {
        assertEquals(Connection.Connecting, build(status = HealthStatus.NotConnected, connecting = true).connection)
    }

    @Test
    fun theStoreStates_mapAcross() {
        assertEquals(Connection.Unsupported, build(status = HealthStatus.Unsupported).connection)
        assertEquals(Connection.NeedsInstall, build(status = HealthStatus.NeedsInstall).connection)
        assertEquals(Connection.Connected, build().connection)
    }

    @Test
    fun denied_onlyShowsWhileStillNotConnected() {
        assertTrue(build(status = HealthStatus.NotConnected, denied = true).denied)
        assertFalse(build(status = HealthStatus.Connected, denied = true).denied)
    }

    @Test
    fun onlyHealthConnect_hasItsOwnScreenToOpen() {
        assertTrue(build().canOpenSource)
        assertFalse(build(source = HealthSource.AppleHealth).canOpenSource)
    }

    private fun policy(preset: PresetId) = SettingsFixtures.policy(preset)
}
