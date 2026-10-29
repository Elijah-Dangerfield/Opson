package com.opson.features.builder.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.builder.BuilderProgramRoute
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.ApplicationStatus
import com.opson.libraries.foods.BuilderProgram
import com.opson.libraries.foods.BuilderResult
import com.opson.libraries.foods.BuilderStanding
import com.opson.libraries.foods.BuilderStatus
import com.opson.libraries.foods.BuilderTrust
import com.opson.libraries.foods.JoinBlock
import com.opson.libraries.ui.time.DateCopy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import kotlin.math.roundToInt
import kotlin.time.Clock
import kotlin.time.Instant

/** What More › Builders draws. [page] is null while the first answer is on its way. */
data class BuilderProgramState(
    val page: BuilderPage? = null,
    val busy: Boolean = false,
    val problem: BuilderProblem? = null,
    val confirming: BuilderConfirm? = null,
)

/**
 * The page's states, one per place someone can stand: not applied, waiting, declined, a
 * Builder, removed. Dates arrive as text to show.
 */
sealed interface BuilderPage {
    /**
     * Not a Builder and nothing waiting: what Builders do, three steps and Apply. [block] says
     * why Apply isn't open, with [until] for `too_new`; [returning] for someone who left.
     */
    data class Intro(val block: JoinBlock?, val until: String?, val returning: Boolean) : BuilderPage

    /** An application is with Opson. [sentOn] is the day it went. */
    data class Applied(val sentOn: String) : BuilderPage

    /**
     * Opson said no. [note] is theirs if they wrote one; [againOn] is when Apply opens again,
     * or null once it has.
     */
    data class Declined(val note: String?, val againOn: String?) : BuilderPage

    /**
     * A Builder's home. [welcome] the first time this phone shows it after Opson said yes.
     * [acceptedPercent] is null until something they sent has been decided.
     */
    data class Home(
        val welcome: Boolean,
        val trusted: Boolean,
        val accepted: Int,
        val needed: Int,
        val proUntil: String?,
        val proActive: Boolean,
        val foodsAdded: Int,
        val checks: Int,
        val acceptedPercent: Int?,
        val retention: Boolean,
    ) : BuilderPage

    data object Removed : BuilderPage

    /** A server from before the program. */
    data object Unavailable : BuilderPage
}

enum class BuilderProblem { Offline, Failed }

/** A dialog asking before something that can't be taken back in a tap. */
enum class BuilderConfirm { Leave, Withdraw }

sealed interface BuilderProgramAction {
    data object Load : BuilderProgramAction
    data class StandingArrived(val standing: BuilderStanding) : BuilderProgramAction
    data object RetryTapped : BuilderProgramAction
    data object ApplyTapped : BuilderProgramAction
    data object RulesTapped : BuilderProgramAction
    data object WithdrawTapped : BuilderProgramAction
    data object LeaveTapped : BuilderProgramAction
    data object Confirmed : BuilderProgramAction
    data object ConfirmCancelled : BuilderProgramAction
    data class RetentionChanged(val keep: Boolean) : BuilderProgramAction
    data object AddFoodTapped : BuilderProgramAction
    data object CheckFoodTapped : BuilderProgramAction
    data object BackTapped : BuilderProgramAction
}

sealed interface BuilderProgramEvent {
    data object Close : BuilderProgramEvent
    data class OpenApply(val via: String) : BuilderProgramEvent
    data object OpenRules : BuilderProgramEvent
    data object AddFood : BuilderProgramEvent

    /** The scanner, checking packs, with [dayIso] as today. */
    data class CheckFood(val dayIso: String) : BuilderProgramEvent
}

/**
 * More › Builders. Follows [BuilderProgram.standing], so an application sent from the Apply
 * sheet or a decision Opson made elsewhere shows as soon as it is known, and asks the server
 * again each time the page opens. The first time a decision is drawn here it is marked seen, so
 * Today's prompt doesn't tell it twice; a welcome stays up while this page is open.
 */
@Inject
class BuilderProgramViewModel(
    @Assisted private val route: BuilderProgramRoute,
    private val program: BuilderProgram,
    private val calendar: LocalCalendar,
    private val clock: Clock,
) : SEAViewModel<BuilderProgramState, BuilderProgramEvent, BuilderProgramAction>(
    initialStateArg = BuilderProgramState(page = program.standing.value?.let { BuilderPages.of(it, calendar, clock.now(), welcome = false) }),
) {
    /** The application whose welcome this page is showing, so a refresh doesn't take it down. */
    private var welcomed: String? = null

    init {
        viewModelScope.launch { program.standing.filterNotNull().collect { takeAction(BuilderProgramAction.StandingArrived(it)) } }
        takeAction(BuilderProgramAction.Load)
    }

    override suspend fun handleAction(action: BuilderProgramAction) {
        when (action) {
            BuilderProgramAction.Load, BuilderProgramAction.RetryTapped -> action.run { program.refresh() }
            is BuilderProgramAction.StandingArrived -> arrived(action, action.standing)
            BuilderProgramAction.ApplyTapped -> {
                val page = stateFlow.value.page
                val open = (page is BuilderPage.Intro && page.block == null) || (page is BuilderPage.Declined && page.againOn == null)
                if (open) sendEvent(BuilderProgramEvent.OpenApply(route.via))
            }
            BuilderProgramAction.RulesTapped -> sendEvent(BuilderProgramEvent.OpenRules)
            BuilderProgramAction.WithdrawTapped -> action.updateState { it.copy(confirming = BuilderConfirm.Withdraw) }
            BuilderProgramAction.LeaveTapped -> action.updateState { it.copy(confirming = BuilderConfirm.Leave) }
            BuilderProgramAction.ConfirmCancelled -> action.updateState { it.copy(confirming = null) }
            BuilderProgramAction.Confirmed -> {
                val confirming = stateFlow.value.confirming ?: return
                action.updateState { it.copy(confirming = null) }
                when (confirming) {
                    BuilderConfirm.Leave -> action.run { program.leave() }
                    BuilderConfirm.Withdraw -> action.run { program.withdraw() }
                }
            }
            is BuilderProgramAction.RetentionChanged -> if (stateFlow.value.page is BuilderPage.Home) {
                action.run { program.setPhotoRetention(action.keep) }
            }
            BuilderProgramAction.AddFoodTapped -> sendEvent(BuilderProgramEvent.AddFood)
            BuilderProgramAction.CheckFoodTapped -> sendEvent(BuilderProgramEvent.CheckFood(calendar.today().toString()))
            BuilderProgramAction.BackTapped -> sendEvent(BuilderProgramEvent.Close)
        }
    }

    private suspend fun arrived(action: BuilderProgramAction, standing: BuilderStanding) {
        val decided = standing.application?.takeIf { it.status == ApplicationStatus.Accepted || it.status == ApplicationStatus.Declined }
        if (decided != null && welcomed != decided.id && !program.decisionSeen(decided.id)) {
            program.markDecisionSeen(decided.id)
            if (decided.status == ApplicationStatus.Accepted && standing.isActive) welcomed = decided.id
        }
        val welcome = welcomed != null && welcomed == standing.application?.id && standing.isActive
        action.updateState { it.copy(page = BuilderPages.of(standing, calendar, clock.now(), welcome)) }
    }

    /** The answer itself arrives through [BuilderProgram.standing]; this handles everything else. */
    private suspend fun BuilderProgramAction.run(call: suspend () -> BuilderResult) {
        updateState { it.copy(busy = true, problem = null) }
        val result = call()
        updateState { state ->
            when (result) {
                is BuilderResult.Done -> state.copy(busy = false)
                is BuilderResult.Blocked -> state.copy(busy = false)
                BuilderResult.Unavailable -> state.copy(busy = false, page = BuilderPage.Unavailable)
                is BuilderResult.Failed -> state.copy(busy = false, problem = if (result.offline) BuilderProblem.Offline else BuilderProblem.Failed)
            }
        }
    }
}

/** A standing as the page it draws. Pure, for the tests and the previews. */
object BuilderPages {
    fun of(standing: BuilderStanding, calendar: LocalCalendar, now: Instant, welcome: Boolean): BuilderPage {
        val application = standing.application
        return when {
            standing.status == BuilderStatus.Active -> BuilderPage.Home(
                welcome = welcome,
                trusted = standing.trust == BuilderTrust.Trusted,
                accepted = standing.accepted30d,
                needed = standing.needed,
                proUntil = standing.proUntil?.let { date(it, calendar) },
                proActive = standing.proUntil?.let { it > now } == true,
                foodsAdded = standing.foodsAdded,
                checks = standing.checks,
                acceptedPercent = acceptedPercent(standing.foodsAdded, standing.rejected),
                retention = standing.photoRetention,
            )
            standing.status == BuilderStatus.Removed || standing.joinBlock == JoinBlock.Removed -> BuilderPage.Removed
            application?.status == ApplicationStatus.Pending || standing.joinBlock == JoinBlock.Pending ->
                BuilderPage.Applied(sentOn = date(application?.submittedAt ?: now, calendar))
            application?.status == ApplicationStatus.Declined -> BuilderPage.Declined(
                note = application.note,
                againOn = if (standing.canApply) null else (standing.availableAt ?: application.reapplyAt)?.let { date(it, calendar) },
            )
            else -> BuilderPage.Intro(
                block = standing.joinBlock?.takeUnless { it == JoinBlock.Declined },
                until = standing.availableAt?.let { date(it, calendar) },
                returning = standing.status == BuilderStatus.Left,
            )
        }
    }

    /** Approved out of decided, as a whole percent; null with nothing decided yet. */
    fun acceptedPercent(approved: Int, rejected: Int): Int? {
        val decided = approved + rejected
        return if (decided == 0) null else (approved * PERCENT.toDouble() / decided).roundToInt()
    }

    private fun date(at: Instant, calendar: LocalCalendar): String = DateCopy.longMonthDay(at.toLocalDateTime(calendar.timeZone()).date)

    private const val PERCENT = 100
}
