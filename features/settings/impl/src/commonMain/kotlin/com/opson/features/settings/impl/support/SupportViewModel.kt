package com.opson.features.settings.impl.support

import com.opson.features.settings.impl.Toast
import com.opson.libraries.diary.DataExport
import com.opson.libraries.diary.DiarySync
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.auth.DeleteAccountOutcome
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

data class SupportState(
    val export: ExportStatus = ExportStatus.Idle,
    val delete: DeleteStep = DeleteStep.Idle,
)

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
) : SEAViewModel<SupportState, SupportEvent, SupportAction>(initialStateArg = SupportState()) {

    override suspend fun handleAction(action: SupportAction) {
        when (action) {
            SupportAction.HelplineTapped -> sendEvent(SupportEvent.OpenLink(HELPLINE_URL))
            SupportAction.FindProviderTapped -> sendEvent(SupportEvent.OpenLink(PROVIDER_URL))
            SupportAction.ExportTapped -> export(action)
            SupportAction.SendFeedbackTapped -> sendEvent(SupportEvent.OpenFeedback)
            SupportAction.DeleteTapped -> action.updateState { it.copy(delete = DeleteStep.Confirming) }
            SupportAction.KeepMyDataTapped -> action.updateState { it.copy(delete = DeleteStep.Idle) }
            SupportAction.DeleteConfirmed -> delete(action)
            SupportAction.BackTapped -> sendEvent(SupportEvent.Close)
        }
    }

    private suspend fun export(action: SupportAction) {
        if (state.export == ExportStatus.Working) return
        action.updateState { it.copy(export = ExportStatus.Working) }
        val bytes = diarySync.syncNow().mapCatching { dataExport.csv().getOrThrow() }.getOrNull()
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

    /** Everything is gone; start again from onboarding with nothing behind it. */
    data object GoToOnboarding : SupportEvent
    data object Close : SupportEvent
}

sealed interface SupportAction {
    data object HelplineTapped : SupportAction
    data object FindProviderTapped : SupportAction
    data object ExportTapped : SupportAction
    data object SendFeedbackTapped : SupportAction
    data object DeleteTapped : SupportAction
    data object KeepMyDataTapped : SupportAction
    data object DeleteConfirmed : SupportAction
    data object BackTapped : SupportAction
}
