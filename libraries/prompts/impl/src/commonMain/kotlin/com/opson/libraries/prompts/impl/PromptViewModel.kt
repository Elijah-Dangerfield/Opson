@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.prompts.impl

import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.navigation.Route
import com.opson.libraries.prompts.PromptAccept
import com.opson.libraries.prompts.PromptContent
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/**
 * A prompt's dialog or sheet. [content] is null for an id no candidate has any more, which closes
 * at once. [offersOptOut]: "I'd rather not see these" under the two buttons.
 */
@HiddenFromObjC
data class PromptState(val content: PromptContent?, val offersOptOut: Boolean = false)

@HiddenFromObjC
sealed interface PromptAction {
    data object AcceptTapped : PromptAction

    data object NotNowTapped : PromptAction

    /** "I'd rather not see these": suggestions off, and this one closes. */
    data object TurnOffTapped : PromptAction

    /** Gone by a swipe, a tap outside or Back, after the animation. Counts as Not now unless already answered. */
    data object Gone : PromptAction
}

@HiddenFromObjC
sealed interface PromptUiEvent {
    /** Give way to [route], so Back from there lands on Today. */
    data class Open(val route: Route) : PromptUiEvent

    data class OpenLink(val url: String) : PromptUiEvent

    /** Animate away. */
    data object Close : PromptUiEvent

    /** Animate away after suggestions were turned off; a line says where to turn them back on. */
    data object TurnedOff : PromptUiEvent
}

@HiddenFromObjC
@Inject
class PromptViewModel(
    @Assisted private val id: String,
    private val processor: PromptProcessor,
) : SEAViewModel<PromptState, PromptUiEvent, PromptAction>(
    initialStateArg = PromptState(content = processor.candidate(id)?.content, offersOptOut = processor.offersOptOut(id)),
) {
    private var leaving = false

    override suspend fun handleAction(action: PromptAction) {
        when (action) {
            PromptAction.AcceptTapped -> {
                if (leaving) return
                leaving = true
                sendEvent(
                    when (val accept = processor.accepted(id)) {
                        is PromptAccept.Open -> PromptUiEvent.Open(accept.route)
                        is PromptAccept.OpenLink -> PromptUiEvent.OpenLink(accept.url)
                        PromptAccept.Done, null -> PromptUiEvent.Close
                    },
                )
            }
            PromptAction.NotNowTapped -> {
                if (leaving) return
                leaving = true
                processor.dismissed(id)
                sendEvent(PromptUiEvent.Close)
            }
            PromptAction.TurnOffTapped -> {
                if (leaving) return
                leaving = true
                processor.turnedOff(id)
                sendEvent(PromptUiEvent.TurnedOff)
            }
            PromptAction.Gone -> processor.dismissed(id)
        }
    }
}
