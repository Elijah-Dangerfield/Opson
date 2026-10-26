package com.opson.features.settings.impl.support

import com.opson.features.settings.FindSupportEnabled
import com.opson.features.settings.impl.Toast
import com.opson.libraries.core.flatMap
import com.opson.libraries.diary.DataExport
import com.opson.libraries.diary.DiarySync
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.DeleteAccountOutcome
import com.opson.libraries.navigation.WebLinkLauncher
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_delete_done
import opson.libraries.resources.generated.resources.settings_export_ready

// TODO(launch): verify both links before launch. The handoff gives only the Alliance's home
// page; point each at the right page once someone has checked them. Do not guess a path.
const val HELPLINE_URL = "https://www.allianceforeatingdisorders.com"
const val PROVIDER_URL = "https://www.allianceforeatingdisorders.com"

/** The file the share sheet offers, matching the server's Content-Disposition. */
const val EXPORT_FILE_NAME = "opson-export.csv"
const val EXPORT_MIME_TYPE = "text/csv"

/**
 * @property findSupport `release.findSupport`: "Find support near you" and the crisis row
 *   replace the 0.1 helpline links.
 * @property crisis the crisis line for the phone's country, null when there is no list for it
 *   (the row then opens Find support) or the flag is off.
 * @property couldNotDial the phone had no dialer: the row's number, said plainly.
 */
data class SupportState(
    val export: ExportStatus = ExportStatus.Idle,
    val delete: DeleteStep = DeleteStep.Idle,
    val findSupport: Boolean = false,
    val crisis: CrisisLine? = null,
    val couldNotDial: String? = null,
)

object SupportStateBuilder {
    fun build(findSupport: Boolean, country: SupportCountry?): SupportState = SupportState(
        findSupport = findSupport,
        crisis = country?.takeIf { findSupport }?.let { SupportDirectory.of(it).crisis },
    )
}

sealed interface ExportStatus {
    data object Idle : ExportStatus
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
 * Support and sharing. Export syncs first so changes still queued on the phone are in the file.
 * Deleting goes through the account deletion in `:libraries:identity` (`DELETE /v1/me`), whose
 * success path wipes every local table, the outbox and the settings, and resets onboarding,
 * before it reports the signed-out state; on any failure nothing local is touched.
 */
@Inject
class SupportViewModel(
    private val diarySync: DiarySync,
    private val dataExport: DataExport,
    private val authRepository: AuthRepository,
    findSupport: FindSupportEnabled,
    region: DeviceRegion,
    private val links: WebLinkLauncher,
) : SEAViewModel<SupportState, SupportEvent, SupportAction>(
    initialStateArg = SupportStateBuilder.build(findSupport(), SupportCountry.of(region.countryCode())),
) {

    override suspend fun handleAction(action: SupportAction) {
        when (action) {
            SupportAction.HelplineTapped -> sendEvent(SupportEvent.OpenLink(HELPLINE_URL))
            SupportAction.FindProviderTapped -> sendEvent(SupportEvent.OpenLink(PROVIDER_URL))
            SupportAction.FindSupportTapped -> sendEvent(SupportEvent.OpenFindSupport)
            SupportAction.CrisisTapped -> crisis(action)
            SupportAction.ExportTapped -> export(action)
            SupportAction.SendFeedbackTapped -> sendEvent(SupportEvent.OpenFeedback)
            SupportAction.DeleteTapped -> action.updateState { it.copy(delete = DeleteStep.Confirming) }
            SupportAction.KeepMyDataTapped -> action.updateState { it.copy(delete = DeleteStep.Idle) }
            SupportAction.DeleteConfirmed -> delete(action)
            SupportAction.BackTapped -> sendEvent(SupportEvent.Close)
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

    private suspend fun export(action: SupportAction) {
        if (state.export == ExportStatus.Working) return
        action.updateState { it.copy(export = ExportStatus.Working) }
        // flatMap, not mapCatching, which would catch the export's cancellation as a failure.
        val bytes = diarySync.syncNow().flatMap { dataExport.csv() }.getOrNull()
        if (bytes == null) {
            action.updateState { it.copy(export = ExportStatus.Failed) }
            return
        }
        action.updateState { it.copy(export = ExportStatus.Idle) }
        sendEvent(SupportEvent.ShareExport(EXPORT_FILE_NAME, bytes))
        sendEvent(SupportEvent.ShowToast(Toast(Res.string.settings_export_ready, listOf(EXPORT_FILE_NAME))))
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
    data object SendFeedbackTapped : SupportAction
    data object DeleteTapped : SupportAction
    data object KeepMyDataTapped : SupportAction
    data object DeleteConfirmed : SupportAction
    data object BackTapped : SupportAction
}
