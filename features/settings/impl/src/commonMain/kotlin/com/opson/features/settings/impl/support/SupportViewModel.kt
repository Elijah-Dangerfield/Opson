package com.opson.features.settings.impl.support

import com.opson.features.settings.FindSupportEnabled
import com.opson.features.settings.ProviderSummaryEnabled
import com.opson.features.settings.impl.Toast
import com.opson.libraries.core.DeviceRegion
import com.opson.libraries.core.flatMap
import com.opson.libraries.diary.DataExport
import com.opson.libraries.diary.DiarySync
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.hidesAnyOf
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.DeleteAccountOutcome
import com.opson.libraries.navigation.WebLinkLauncher
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_delete_done
import opson.libraries.resources.generated.resources.settings_export_ready

// Checked on the Alliance's site on 2026-10-27. Find treatment names the helpline number, its
// hours, and 988 for anyone in crisis, since the helpline is not a 24/7 line. findEDhelp is the
// Alliance's own provider directory.
const val HELPLINE_URL = "https://www.allianceforeatingdisorders.com/find-treatment/"
const val PROVIDER_URL = "https://www.findedhelp.com/"

// The published legal pages (legal/*.md, synced to nightjarlabs.llc). Apple wants the privacy
// policy reachable inside the app as well as on the listing.
const val PRIVACY_URL = "https://nightjarlabs.llc/opson/privacy"
const val TERMS_URL = "https://nightjarlabs.llc/opson/terms"

/**
 * @property findSupport `release.findSupport`: "Find support near you" and the crisis row
 *   replace the 0.1 helpline links.
 * @property crisis the crisis line for the phone's country, null when there is no list for it
 *   (the row then opens Find support) or the flag is off.
 * @property couldNotDial the phone had no dialer: the row's number, said plainly.
 * @property providerSummary where "Make a summary for your provider" sits, if anywhere.
 * @property kindNotes the switch for Today's kind note when the log suggests a hard stretch
 *   (`Settings.hardStretchNotes`); null, and no row, with `release.findSupport` off, since the note
 *   only shows with it.
 */
data class SupportState(
    val export: ExportStatus = ExportStatus.Idle,
    val delete: DeleteStep = DeleteStep.Idle,
    val findSupport: Boolean = false,
    val crisis: CrisisLine? = null,
    val couldNotDial: String? = null,
    val providerSummary: ProviderSummaryOffer = ProviderSummaryOffer.None,
    val kindNotes: Boolean? = null,
)

object SupportStateBuilder {
    fun build(
        findSupport: Boolean,
        country: SupportCountry?,
        providerSummary: Boolean = false,
        workingWithProvider: Boolean = false,
        kindNotes: Boolean = true,
    ): SupportState = SupportState(
        findSupport = findSupport,
        crisis = country?.takeIf { findSupport }?.let { SupportDirectory.of(it).crisis },
        providerSummary = when {
            !providerSummary -> ProviderSummaryOffer.None
            workingWithProvider -> ProviderSummaryOffer.First
            else -> ProviderSummaryOffer.InYourData
        },
        kindNotes = kindNotes.takeIf { findSupport },
    )
}

/** `release.providerSummary`, and whether the person said in setup that they work with a provider. */
enum class ProviderSummaryOffer {
    None,

    /** A row in Your data, beside export. */
    InYourData,

    /** Its own card at the top of Support: they told us they work with a provider. */
    First,
}

sealed interface ExportStatus {
    data object Idle : ExportStatus

    /**
     * "Your export has every number": the mode keeps some of what the file holds out of sight, so
     * the person hears that before the file opens, and picks Make the file or Not now.
     */
    data object ConfirmingNumbers : ExportStatus
    data object Working : ExportStatus

    /** "Export needs a connection." Nothing was shared. */
    data object Failed : ExportStatus
}

sealed interface DeleteStep {
    data object Idle : DeleteStep

    /** "This removes every log and setting…" with Keep my data and Delete. */
    data object Confirming : DeleteStep
    data object Deleting : DeleteStep

    /** Nothing was deleted, here or on the server. [offline] picks the message. */
    data class Failed(val offline: Boolean) : DeleteStep
}

/**
 * Support and sharing. Export syncs first so changes still queued on the phone are in the file,
 * and first warns someone whose mode keeps any of the file's numbers out of sight.
 * Deleting goes through the account deletion in `:libraries:identity` (`DELETE /v1/me`), whose
 * success path wipes every local table, the outbox and the settings, and resets onboarding,
 * before it reports the signed-out state; on any failure nothing local is touched.
 */
@Inject
class SupportViewModel(
    private val diarySync: DiarySync,
    private val dataExport: DataExport,
    private val authRepository: AuthRepository,
    private val settings: SettingsRepository,
    findSupport: FindSupportEnabled,
    providerSummary: ProviderSummaryEnabled,
    region: DeviceRegion,
    private val links: WebLinkLauncher,
) : SEAViewModel<SupportState, SupportEvent, SupportAction>(
    initialStateArg = SupportStateBuilder.build(
        findSupport = findSupport(),
        country = SupportCountry.of(region.countryCode()),
        providerSummary = providerSummary(),
        workingWithProvider = settings.settings.value.onboarding.workingWithProvider,
        kindNotes = settings.settings.value.hardStretchNotes,
    ),
) {

    override suspend fun handleAction(action: SupportAction) {
        when (action) {
            SupportAction.HelplineTapped -> sendEvent(SupportEvent.OpenLink(HELPLINE_URL))
            SupportAction.FindProviderTapped -> sendEvent(SupportEvent.OpenLink(PROVIDER_URL))
            SupportAction.FindSupportTapped -> sendEvent(SupportEvent.OpenFindSupport)
            SupportAction.CrisisTapped -> crisis(action)
            SupportAction.ExportTapped -> exportTapped(action)
            SupportAction.ExportConfirmed -> if (state.export == ExportStatus.ConfirmingNumbers) export(action)
            SupportAction.ExportNotNow -> if (state.export == ExportStatus.ConfirmingNumbers) {
                action.updateState { it.copy(export = ExportStatus.Idle) }
            }
            SupportAction.ProviderSummaryTapped -> if (state.providerSummary != ProviderSummaryOffer.None) {
                sendEvent(SupportEvent.OpenProviderSummary)
            }
            SupportAction.SendFeedbackTapped -> sendEvent(SupportEvent.OpenFeedback)
            SupportAction.PrivacyTapped -> sendEvent(SupportEvent.OpenLink(PRIVACY_URL))
            SupportAction.TermsTapped -> sendEvent(SupportEvent.OpenLink(TERMS_URL))
            SupportAction.CreditsTapped -> sendEvent(SupportEvent.OpenCredits)
            SupportAction.DeleteTapped -> action.updateState { it.copy(delete = DeleteStep.Confirming) }
            SupportAction.KeepMyDataTapped -> action.updateState { it.copy(delete = DeleteStep.Idle) }
            SupportAction.DeleteConfirmed -> delete(action)
            SupportAction.BackTapped -> sendEvent(SupportEvent.Close)
            SupportAction.KindNotesToggled -> if (state.kindNotes != null) {
                val next = settings.update { it.copy(hardStretchNotes = !it.hardStretchNotes) }
                action.updateState { it.copy(kindNotes = next.hardStretchNotes) }
            }
        }
    }

    /**
     * Straight to the dialer, never through the router, which would log a failure. With no list
     * for the phone's region the row opens Find support instead.
     */
    private suspend fun crisis(action: SupportAction) {
        val line = state.crisis ?: return sendEvent(SupportEvent.OpenFindSupport)
        val dialed = links.open(line.call.telUrl).isSuccess
        action.updateState { it.copy(couldNotDial = if (dialed) null else line.call.shown) }
    }

    /** Settings not read yet count as No numbers, as everywhere else: the warning is the safe side. */
    private suspend fun exportTapped(action: SupportAction) {
        if (state.export == ExportStatus.Working || state.export == ExportStatus.ConfirmingNumbers) return
        val hides = !settings.isKnown.value || settings.settings.value.numbers.hidesAnyOf(DataExport.METRICS)
        if (hides) action.updateState { it.copy(export = ExportStatus.ConfirmingNumbers) } else export(action)
    }

    private suspend fun export(action: SupportAction) {
        if (state.export == ExportStatus.Working) return
        action.updateState { it.copy(export = ExportStatus.Working) }
        // flatMap, not mapCatching, which would catch the export's cancellation as a failure.
        val bytes = diarySync.syncNow().flatMap { dataExport.everything() }.getOrNull()
        if (bytes == null) {
            action.updateState { it.copy(export = ExportStatus.Failed) }
            return
        }
        action.updateState { it.copy(export = ExportStatus.Idle) }
        sendEvent(SupportEvent.ShareExport(DataExport.FILE_NAME, bytes))
        sendEvent(SupportEvent.ShowToast(Toast(Res.string.settings_export_ready, listOf(DataExport.FILE_NAME))))
    }

    private suspend fun delete(action: SupportAction) {
        if (state.delete != DeleteStep.Confirming) return
        action.updateState { it.copy(delete = DeleteStep.Deleting) }
        when (authRepository.deleteAccount()) {
            DeleteAccountOutcome.Success -> {
                action.updateState { it.copy(delete = DeleteStep.Idle) }
                sendEvent(SupportEvent.ShowToast(Toast(Res.string.settings_delete_done)))
                sendEvent(SupportEvent.GoToOnboarding)
            }
            is DeleteAccountOutcome.NetworkError ->
                action.updateState { it.copy(delete = DeleteStep.Failed(offline = true)) }
            DeleteAccountOutcome.NotConfigured,
            DeleteAccountOutcome.NotSignedIn,
            is DeleteAccountOutcome.Unknown,
            -> action.updateState { it.copy(delete = DeleteStep.Failed(offline = false)) }
        }
    }
}

sealed interface SupportEvent {
    data class OpenLink(val url: String) : SupportEvent

    /** Bytes rather than a path: the router writes the file where each platform's share sheet can reach it. */
    class ShareExport(val name: String, val bytes: ByteArray) : SupportEvent

    data class ShowToast(val toast: Toast) : SupportEvent
    data object OpenFeedback : SupportEvent
    data object OpenFindSupport : SupportEvent
    data object OpenProviderSummary : SupportEvent

    /** In the app rather than the web page, so the licence texts ship with the binary. */
    data object OpenCredits : SupportEvent

    /** Everything is gone; start again from onboarding with nothing behind it. */
    data object GoToOnboarding : SupportEvent
    data object Close : SupportEvent
}

sealed interface SupportAction {
    data object HelplineTapped : SupportAction
    data object FindProviderTapped : SupportAction
    data object FindSupportTapped : SupportAction
    data object CrisisTapped : SupportAction
    data object ExportTapped : SupportAction
    data object ExportConfirmed : SupportAction
    data object ExportNotNow : SupportAction
    data object ProviderSummaryTapped : SupportAction
    data object SendFeedbackTapped : SupportAction
    data object PrivacyTapped : SupportAction
    data object TermsTapped : SupportAction
    data object CreditsTapped : SupportAction
    data object DeleteTapped : SupportAction
    data object KeepMyDataTapped : SupportAction
    data object DeleteConfirmed : SupportAction
    data object BackTapped : SupportAction

    /** The kind note's switch. */
    data object KindNotesToggled : SupportAction
}
