package com.opson.features.settings.impl.support

import com.opson.libraries.core.DeviceRegion
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.navigation.WebLinkLauncher
import me.tatarka.inject.annotations.Inject

/**
 * Find support: the crisis line and eating-disorder lines for one country, then one line from
 * each of the others. No numbers about the person, ever.
 *
 * @property country null when the phone's region has no list; the page asks for a country.
 * @property couldNotOpen the phone had nothing to open a tap with: the page repeats the number.
 */
data class FindSupportState(
    val country: SupportCountry? = null,
    val pickingCountry: Boolean = false,
    val crisis: CrisisLine? = null,
    val lines: List<SupportLine> = emptyList(),
    val elsewhere: List<SupportLine> = emptyList(),
    val couldNotOpen: CouldNotOpen? = null,
)

/** What to tell someone whose phone couldn't open a tap: the number, or the web address. */
data class CouldNotOpen(val shown: String, val isLink: Boolean)

object FindSupportStateBuilder {
    fun build(country: SupportCountry?): FindSupportState {
        val here = country?.let(SupportDirectory::of)
        return FindSupportState(
            country = country,
            crisis = here?.crisis,
            lines = here?.lines.orEmpty(),
            elsewhere = SupportCountry.entries
                .filter { it != country }
                .map { SupportDirectory.of(it).lines.first() },
        )
    }
}

/**
 * Starts from the phone's region and lets the person pick another country, which lasts only
 * while the page is open. Taps go straight to the dialer, messages or the browser through
 * [WebLinkLauncher], never the router: the router logs a link it fails to open, and nothing
 * about this page is ever logged, synced or shared. A failure only shows the number.
 */
@Inject
class FindSupportViewModel(
    region: DeviceRegion,
    private val links: WebLinkLauncher,
) : SEAViewModel<FindSupportState, FindSupportEvent, FindSupportAction>(
    initialStateArg = FindSupportStateBuilder.build(SupportCountry.of(region.countryCode())),
) {

    override suspend fun handleAction(action: FindSupportAction) {
        when (action) {
            FindSupportAction.CountryTapped -> action.updateState { it.copy(pickingCountry = true) }
            FindSupportAction.CountryPickerDismissed -> action.updateState { it.copy(pickingCountry = false) }
            is FindSupportAction.CountryPicked -> action.updateState { FindSupportStateBuilder.build(action.country) }
            FindSupportAction.CrisisCallTapped -> state.crisis?.let { open(action, it.call.telUrl, CouldNotOpen(it.call.shown, isLink = false)) }
            FindSupportAction.CrisisTextTapped -> state.crisis?.let { open(action, it.text.smsUrl, CouldNotOpen(it.text.shown, isLink = false)) }
            is FindSupportAction.LineTapped -> {
                val line = action.line
                open(action, line.url, CouldNotOpen(line.shown, isLink = line.reach is Reach.Web))
            }
            FindSupportAction.DirectoryTapped ->
                open(action, FIND_A_HELPLINE_URL, CouldNotOpen(FIND_A_HELPLINE_URL.removePrefix("https://").removeSuffix("/"), isLink = true))
            FindSupportAction.BackTapped -> sendEvent(FindSupportEvent.Close)
        }
    }

    private suspend fun open(action: FindSupportAction, url: String, failure: CouldNotOpen) {
        val opened = links.open(url).isSuccess
        action.updateState { it.copy(couldNotOpen = if (opened) null else failure) }
    }
}

sealed interface FindSupportEvent {
    data object Close : FindSupportEvent
}

sealed interface FindSupportAction {
    data object CountryTapped : FindSupportAction
    data object CountryPickerDismissed : FindSupportAction
    data class CountryPicked(val country: SupportCountry) : FindSupportAction
    data object CrisisCallTapped : FindSupportAction
    data object CrisisTextTapped : FindSupportAction
    data class LineTapped(val line: SupportLine) : FindSupportAction

    /** Find A Helpline, for a country with no list of its own. */
    data object DirectoryTapped : FindSupportAction
    data object BackTapped : FindSupportAction
}
