package com.opson.features.builder.impl

import com.opson.features.builder.BuilderProgramRoute
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.BuilderProgram
import com.opson.libraries.foods.BuilderResult
import com.opson.libraries.foods.BuilderStanding
import com.opson.libraries.foods.BuilderStatus
import com.opson.libraries.foods.BuilderTrust
import com.opson.libraries.foods.JoinBlock
import com.opson.libraries.ui.time.DateCopy
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import kotlin.time.Clock
import kotlin.time.Instant

/** What More › Builders draws. [page] is null while the first answer is on its way. */
data class BuilderProgramState(
    val page: BuilderPage? = null,
    /** The rules checkbox, which Join waits on. */
    val agreed: Boolean = false,
    /** The optional two-year photo consent, off unless they turn it on. */
    val retention: Boolean = false,
    val busy: Boolean = false,
    val problem: BuilderProblem? = null,
    val confirmingLeave: Boolean = false,
)

sealed interface BuilderPage {
    /** Not a Builder: what it is, the rules, Join. [block] says why Join isn't offered; [until] is when `too_new` lifts. */
    data class Join(val block: JoinBlock?, val until: String?, val rejoin: Boolean) : BuilderPage

    /** [proUntil] is a date to show; [proActive] whether it is still ahead. */
    data class Member(
        val trusted: Boolean,
        val accepted: Int,
        val needed: Int,
        val proUntil: String?,
        val proActive: Boolean,
        val retention: Boolean,
    ) : BuilderPage

    data object Removed : BuilderPage

    /** A server from before the program. */
    data object Unavailable : BuilderPage
}

enum class BuilderProblem { Offline, Failed }

sealed interface BuilderProgramAction {
    data object Load : BuilderProgramAction
    data object RetryTapped : BuilderProgramAction
    data class AgreeChanged(val agreed: Boolean) : BuilderProgramAction
    data class RetentionChanged(val keep: Boolean) : BuilderProgramAction
    data object JoinTapped : BuilderProgramAction
    data object LeaveTapped : BuilderProgramAction
    data object LeaveConfirmed : BuilderProgramAction
    data object LeaveCancelled : BuilderProgramAction
    data object AddFoodTapped : BuilderProgramAction
    data object TermsTapped : BuilderProgramAction
    data object BackTapped : BuilderProgramAction
}

sealed interface BuilderProgramEvent {
    data object Close : BuilderProgramEvent
    data object AddFood : BuilderProgramEvent
    data class OpenLink(val url: String) : BuilderProgramEvent
}

/**
 * More › Builders. Asks the server where the person stands each time the page opens, since a
 * join, a removal or a lapsed month can all have happened elsewhere. Joining needs the rules
 * ticked; the photo consent is separate and optional, and can be changed later.
 */
@Inject
class BuilderProgramViewModel(
    @Assisted private val route: BuilderProgramRoute,
    private val program: BuilderProgram,
    private val calendar: LocalCalendar,
    private val clock: Clock,
) : SEAViewModel<BuilderProgramState, BuilderProgramEvent, BuilderProgramAction>(
    initialStateArg = BuilderProgramState(page = program.standing.value?.let { BuilderPages.of(it, calendar, clock.now()) }),
) {
    init {
        takeAction(BuilderProgramAction.Load)
    }

    override suspend fun handleAction(action: BuilderProgramAction) {
        when (action) {
            BuilderProgramAction.Load, BuilderProgramAction.RetryTapped -> action.run { program.refresh() }
            is BuilderProgramAction.AgreeChanged -> action.updateState { it.copy(agreed = action.agreed) }
            is BuilderProgramAction.RetentionChanged -> when (stateFlow.value.page) {
                is BuilderPage.Member -> action.run { program.setPhotoRetention(action.keep) }
                else -> action.updateState { it.copy(retention = action.keep) }
            }
            BuilderProgramAction.JoinTapped -> {
                val state = stateFlow.value
                val page = state.page as? BuilderPage.Join ?: return
                if (!state.agreed || page.block != null || state.busy) return
                action.run { program.join(state.retention) }
            }
            BuilderProgramAction.LeaveTapped -> action.updateState { it.copy(confirmingLeave = true) }
            BuilderProgramAction.LeaveCancelled -> action.updateState { it.copy(confirmingLeave = false) }
            BuilderProgramAction.LeaveConfirmed -> {
                action.updateState { it.copy(confirmingLeave = false) }
                action.run { program.leave() }
            }
            BuilderProgramAction.AddFoodTapped -> sendEvent(BuilderProgramEvent.AddFood)
            BuilderProgramAction.TermsTapped -> sendEvent(BuilderProgramEvent.OpenLink(TERMS_URL))
            BuilderProgramAction.BackTapped -> sendEvent(BuilderProgramEvent.Close)
        }
    }

    private suspend fun BuilderProgramAction.run(call: suspend () -> BuilderResult) {
        updateState { it.copy(busy = true, problem = null) }
        val result = call()
        val now = clock.now()
        updateState { state ->
            when (result) {
                is BuilderResult.Done -> state.copy(busy = false, page = BuilderPages.of(result.standing, calendar, now), agreed = false)
                is BuilderResult.Blocked -> state.copy(
                    busy = false,
                    page = (state.page as? BuilderPage.Join)?.copy(block = result.block) ?: BuilderPage.Join(result.block, until = null, rejoin = false),
                )
                BuilderResult.Unavailable -> state.copy(busy = false, page = BuilderPage.Unavailable)
                is BuilderResult.Failed -> state.copy(busy = false, problem = if (result.offline) BuilderProblem.Offline else BuilderProblem.Failed)
            }
        }
    }

    companion object {
        /** The terms page, which carries the Builder rules in full (`legal/terms.md`). */
        const val TERMS_URL = "https://nightjarlabs.llc/opson/terms"
    }
}

/** A standing as the page it draws. Pure, for the tests and the previews. */
object BuilderPages {
    fun of(standing: BuilderStanding, calendar: LocalCalendar, now: Instant): BuilderPage = when (standing.status) {
        BuilderStatus.Active -> BuilderPage.Member(
            trusted = standing.trust == BuilderTrust.Trusted,
            accepted = standing.accepted30d,
            needed = standing.needed,
            proUntil = standing.proUntil?.let { date(it, calendar) },
            proActive = standing.proUntil?.let { it > now } == true,
            retention = standing.photoRetention,
        )
        BuilderStatus.Removed -> BuilderPage.Removed
        BuilderStatus.None, BuilderStatus.Left -> BuilderPage.Join(
            block = standing.joinBlock,
            until = standing.availableAt?.let { date(it, calendar) },
            rejoin = standing.status == BuilderStatus.Left,
        )
    }

    private fun date(at: Instant, calendar: LocalCalendar): String = DateCopy.longMonthDay(at.toLocalDateTime(calendar.timeZone()).date)
}
