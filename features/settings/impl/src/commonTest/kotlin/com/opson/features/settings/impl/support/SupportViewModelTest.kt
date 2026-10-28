package com.opson.features.settings.impl.support

import com.opson.features.settings.impl.FakeAuthRepository
import com.opson.features.settings.impl.FakeDataExport
import com.opson.features.settings.impl.FakeDiarySync
import com.opson.features.settings.impl.FakeLinks
import com.opson.features.settings.impl.FakeSettingsRepository
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.Toast
import com.opson.features.settings.impl.eventsOf
import com.opson.features.settings.impl.findSupportFlag
import com.opson.features.settings.impl.providerSummaryFlag
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.core.DeviceRegion
import com.opson.libraries.diary.DataExport
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.identity.auth.DeleteAccountOutcome
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import kotlinx.coroutines.CancellationException
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_delete_done
import opson.libraries.resources.generated.resources.settings_export_ready
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Support: the outbound links, export (sync first, then fetch, then share), and delete (only
 * after the confirm step; success starts over, failure deletes nothing). What deletion wipes
 * locally is the auth orchestrator's (`SupabaseAuthRepositoryImplTest`); the CSV itself is
 * `ExportRoutesTest` on the server.
 */
class SupportViewModelTest : CoroutineTest() {

    private val csv = "record_type,day\r\nentry,2026-10-25\r\n".encodeToByteArray()

    private class Rig(
        val sync: FakeDiarySync = FakeDiarySync(),
        csv: Result<ByteArray> = Result.success(ByteArray(0)),
        val auth: FakeAuthRepository = FakeAuthRepository(),
        findSupport: Boolean = false,
        region: String? = "US",
        val links: FakeLinks = FakeLinks(),
        val settings: FakeSettingsRepository = FakeSettingsRepository(),
        providerSummary: Boolean = false,
    ) {
        val export = FakeDataExport(sync, csv)
        val vm = SupportViewModel(
            sync, export, auth, settings, findSupportFlag(findSupport), providerSummaryFlag(providerSummary), DeviceRegion { region }, links,
        )
    }

    private fun settingsIn(preset: PresetId, overrides: Map<Metric, Visibility> = emptyMap()) =
        FakeSettingsRepository(SettingsFixtures.settings(numbers = NumbersSettings(preset = preset, overrides = overrides)))

    @Test
    fun aModeThatHidesNumbers_hearsSoBeforeTheFileOpens() = runUnitTest {
        listOf(PresetId.NoNumbers, PresetId.MacrosOnly, PresetId.MacrosWithGrams).forEach { preset ->
            val rig = Rig(csv = Result.success(csv), settings = settingsIn(preset))
            val events = eventsOf(rig.vm)

            rig.vm.takeAction(SupportAction.ExportTapped)

            assertEquals(ExportStatus.ConfirmingNumbers, rig.vm.state.export, "for $preset")
            assertEquals(0, rig.export.calls, "nothing is fetched before the person says so, in $preset")
            assertTrue(events.isEmpty())

            rig.vm.takeAction(SupportAction.ExportConfirmed)

            assertEquals("opson-export.zip", assertIs<SupportEvent.ShareExport>(events[0]).name)
        }
    }

    @Test
    fun notNow_makesNoFile() = runUnitTest {
        val rig = Rig(csv = Result.success(csv), settings = settingsIn(PresetId.NoNumbers))
        val events = eventsOf(rig.vm)

        rig.vm.takeAction(SupportAction.ExportTapped)
        rig.vm.takeAction(SupportAction.ExportNotNow)

        assertEquals(ExportStatus.Idle, rig.vm.state.export)
        assertEquals(0, rig.export.calls)
        assertTrue(events.isEmpty())
    }

    /** All numbers with one number on Later, or softened, still has a number the file shows early. */
    @Test
    fun allNumbersWithOneNumberHeldBack_isWarnedToo() = runUnitTest {
        val later = Rig(settings = settingsIn(PresetId.Full, mapOf(Metric.Calories to Visibility.Delay)))
        later.vm.takeAction(SupportAction.ExportTapped)
        assertEquals(ExportStatus.ConfirmingNumbers, later.vm.state.export)

        val softWeight = Rig(settings = settingsIn(PresetId.Full, mapOf(Metric.Weight to Visibility.Soften)))
        softWeight.vm.takeAction(SupportAction.ExportTapped)
        assertEquals(ExportStatus.ConfirmingNumbers, softWeight.vm.state.export)
    }

    @Test
    fun theProviderSummary_followsTheFlag_andComesFirstForSomeoneWithAProvider() = runUnitTest {
        assertEquals(ProviderSummaryOffer.None, Rig(providerSummary = false).vm.state.providerSummary)
        assertEquals(ProviderSummaryOffer.InYourData, Rig(providerSummary = true).vm.state.providerSummary)
        val withProvider = FakeSettingsRepository(
            SettingsFixtures.settings().copy(onboarding = OnboardingAnswers(why = listOf(OnboardingAnswers.WORKING_WITH_PROVIDER))),
        )
        val rig = Rig(providerSummary = true, settings = withProvider)
        assertEquals(ProviderSummaryOffer.First, rig.vm.state.providerSummary)

        val events = eventsOf(rig.vm)
        rig.vm.takeAction(SupportAction.ProviderSummaryTapped)
        assertEquals(listOf<SupportEvent>(SupportEvent.OpenProviderSummary), events)
    }

    @Test
    fun withTheFlagOff_theSummaryCannotBeOpened() = runUnitTest {
        val rig = Rig(providerSummary = false)
        val events = eventsOf(rig.vm)

        rig.vm.takeAction(SupportAction.ProviderSummaryTapped)

        assertTrue(events.isEmpty())
    }

    @Test
    fun theHelplineAndProviderOpenTheAlliance() = runUnitTest {
        val rig = Rig()
        val events = eventsOf(rig.vm)

        rig.vm.takeAction(SupportAction.HelplineTapped)
        rig.vm.takeAction(SupportAction.FindProviderTapped)

        assertEquals(
            listOf<SupportEvent>(SupportEvent.OpenLink(HELPLINE_URL), SupportEvent.OpenLink(PROVIDER_URL)),
            events,
        )
        assertEquals("https://www.allianceforeatingdisorders.com/find-treatment/", HELPLINE_URL)
    }

    @Test
    fun theAboutRowsOpenThePublishedLegalPages_andTheCreditsInTheApp() = runUnitTest {
        val rig = Rig()
        val events = eventsOf(rig.vm)

        rig.vm.takeAction(SupportAction.PrivacyTapped)
        rig.vm.takeAction(SupportAction.TermsTapped)
        rig.vm.takeAction(SupportAction.CreditsTapped)

        assertEquals(
            listOf(
                SupportEvent.OpenLink("https://nightjarlabs.llc/opson/privacy"),
                SupportEvent.OpenLink("https://nightjarlabs.llc/opson/terms"),
                SupportEvent.OpenCredits,
            ),
            events,
        )
    }

    @Test
    fun exportSyncsFirstThenSharesTheFile() = runUnitTest {
        val rig = Rig(csv = Result.success(csv))
        val events = eventsOf(rig.vm)

        rig.vm.takeAction(SupportAction.ExportTapped)

        assertEquals(1, rig.export.syncsBeforeFirstCall)
        val share = assertIs<SupportEvent.ShareExport>(events[0])
        assertEquals("opson-export.zip", share.name)
        assertContentEquals(csv, share.bytes)
        assertEquals(SupportEvent.ShowToast(Toast(Res.string.settings_export_ready, listOf("opson-export.zip"))), events[1])
        assertEquals(ExportStatus.Idle, rig.vm.state.export)
    }

    @Test
    fun exportOfflineAtTheSyncSharesNothing() = runUnitTest {
        val rig = Rig(sync = FakeDiarySync(Result.failure(IllegalStateException("offline"))))
        val events = eventsOf(rig.vm)

        rig.vm.takeAction(SupportAction.ExportTapped)

        assertEquals(0, rig.export.calls)
        assertEquals(ExportStatus.Failed, rig.vm.state.export)
        assertTrue(events.isEmpty())
    }

    @Test
    fun exportOfflineAtTheFetchSharesNothing() = runUnitTest {
        val rig = Rig(csv = Result.failure(IllegalStateException("offline")))
        val events = eventsOf(rig.vm)

        rig.vm.takeAction(SupportAction.ExportTapped)

        assertEquals(ExportStatus.Failed, rig.vm.state.export)
        assertTrue(events.isEmpty())
    }

    @Test
    fun aCancelledExport_isNotReportedAsOffline() = runUnitTest {
        val sync = FakeDiarySync()
        val cancelled = object : DataExport {
            override suspend fun everything(): Result<ByteArray> = throw CancellationException("left the page")
        }
        val vm = SupportViewModel(
            sync, cancelled, FakeAuthRepository(), FakeSettingsRepository(), findSupportFlag(false), providerSummaryFlag(false),
            DeviceRegion { "US" }, FakeLinks(),
        )

        vm.takeAction(SupportAction.ExportTapped)

        assertEquals(ExportStatus.Working, vm.state.export, "a cancellation is not a failed export")
    }

    @Test
    fun sendFeedbackOpensFeedback() = runUnitTest {
        val rig = Rig()
        val events = eventsOf(rig.vm)

        rig.vm.takeAction(SupportAction.SendFeedbackTapped)

        assertEquals(listOf<SupportEvent>(SupportEvent.OpenFeedback), events)
    }

    @Test
    fun deleteNeedsTheConfirmStep() = runUnitTest {
        val rig = Rig()

        rig.vm.takeAction(SupportAction.DeleteConfirmed)
        assertEquals(0, rig.auth.deletes)

        rig.vm.takeAction(SupportAction.DeleteTapped)
        assertEquals(DeleteStep.Confirming, rig.vm.state.delete)
        rig.vm.takeAction(SupportAction.KeepMyDataTapped)

        assertEquals(DeleteStep.Idle, rig.vm.state.delete)
        assertEquals(0, rig.auth.deletes)
    }

    @Test
    fun aConfirmedDeleteStartsOver() = runUnitTest {
        val rig = Rig()
        val events = eventsOf(rig.vm)

        rig.vm.takeAction(SupportAction.DeleteTapped)
        rig.vm.takeAction(SupportAction.DeleteConfirmed)

        assertEquals(1, rig.auth.deletes)
        assertEquals(
            listOf(SupportEvent.ShowToast(Toast(Res.string.settings_delete_done)), SupportEvent.GoToOnboarding),
            events,
        )
    }

    @Test
    fun aDeleteThatCannotReachTheServerDeletesNothing() = runUnitTest {
        val rig = Rig(auth = FakeAuthRepository(DeleteAccountOutcome.NetworkError(IllegalStateException("offline"))))
        val events = eventsOf(rig.vm)

        rig.vm.takeAction(SupportAction.DeleteTapped)
        rig.vm.takeAction(SupportAction.DeleteConfirmed)

        assertEquals(DeleteStep.Failed(offline = true), rig.vm.state.delete)
        assertTrue(events.isEmpty())
    }

    @Test
    fun anyOtherDeleteFailureSaysSomethingWentWrong() = runUnitTest {
        listOf(
            DeleteAccountOutcome.NotConfigured,
            DeleteAccountOutcome.NotSignedIn,
            DeleteAccountOutcome.Unknown(IllegalStateException("500")),
        ).forEach { outcome ->
            val rig = Rig(auth = FakeAuthRepository(outcome))

            rig.vm.takeAction(SupportAction.DeleteTapped)
            rig.vm.takeAction(SupportAction.DeleteConfirmed)

            assertEquals(DeleteStep.Failed(offline = false), rig.vm.state.delete, "for $outcome")
        }
    }

    @Test
    fun backCloses() = runUnitTest {
        val rig = Rig()
        val events = eventsOf(rig.vm)

        rig.vm.takeAction(SupportAction.BackTapped)

        assertEquals(listOf<SupportEvent>(SupportEvent.Close), events)
    }

    // --- design sync 2: Find support and the crisis row (release.findSupport) ---

    @Test
    fun withTheFlagOffSupportKeepsItsLinksAndNoCrisisRow() = runUnitTest {
        val rig = Rig(findSupport = false)

        assertEquals(false, rig.vm.state.findSupport)
        assertEquals(null, rig.vm.state.crisis)
    }

    @Test
    fun theCrisisRowFollowsThePhonesCountry() = runUnitTest {
        assertEquals(SupportDirectory.of(SupportCountry.UnitedStates).crisis, Rig(findSupport = true, region = "US").vm.state.crisis)
        assertEquals(SupportDirectory.of(SupportCountry.UnitedKingdom).crisis, Rig(findSupport = true, region = "GB").vm.state.crisis)
        assertEquals(SupportDirectory.of(SupportCountry.NewZealand).crisis, Rig(findSupport = true, region = "nz").vm.state.crisis)
        assertEquals(null, Rig(findSupport = true, region = "FR").vm.state.crisis)
        assertEquals(null, Rig(findSupport = true, region = null).vm.state.crisis)
    }

    @Test
    fun tappingTheCrisisRowDialsStraightAway() = runUnitTest {
        val rig = Rig(findSupport = true, region = "GB")
        val events = eventsOf(rig.vm)

        rig.vm.takeAction(SupportAction.CrisisTapped)

        assertEquals(listOf("tel:116123"), rig.links.opened)
        assertTrue(events.isEmpty())
        assertEquals(null, rig.vm.state.couldNotDial)
    }

    @Test
    fun withNoDialerTheRowSaysTheNumber() = runUnitTest {
        val rig = Rig(findSupport = true, region = "US", links = FakeLinks(opens = false))

        rig.vm.takeAction(SupportAction.CrisisTapped)

        assertEquals("988", rig.vm.state.couldNotDial)
    }

    @Test
    fun withNoListForTheRegionTheCrisisRowOpensFindSupport() = runUnitTest {
        val rig = Rig(findSupport = true, region = "FR")
        val events = eventsOf(rig.vm)

        rig.vm.takeAction(SupportAction.CrisisTapped)

        assertTrue(rig.links.opened.isEmpty())
        assertEquals(listOf<SupportEvent>(SupportEvent.OpenFindSupport), events)
    }

    @Test
    fun findSupportNearYouOpensThePage() = runUnitTest {
        val rig = Rig(findSupport = true)
        val events = eventsOf(rig.vm)

        rig.vm.takeAction(SupportAction.FindSupportTapped)

        assertEquals(listOf<SupportEvent>(SupportEvent.OpenFindSupport), events)
    }
}
