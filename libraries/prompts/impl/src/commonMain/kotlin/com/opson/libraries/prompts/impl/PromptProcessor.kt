@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.libraries.prompts.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.navigation.Route
import com.opson.libraries.opson.SessionTracker
import com.opson.libraries.prompts.PromptAccept
import com.opson.libraries.prompts.PromptCandidate
import com.opson.libraries.prompts.PromptDebug
import com.opson.libraries.prompts.PromptEvent
import com.opson.libraries.prompts.PromptEvents
import com.opson.libraries.prompts.PromptInspection
import com.opson.libraries.prompts.PromptPresentation
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC
import kotlin.time.Clock
import kotlin.time.Instant
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** A prompt the processor just put up. */
@HiddenFromObjC
data class PromptShowing(val id: String, val presentation: PromptPresentation, val trigger: PromptTrigger)

/**
 * The one place that decides whether Today shows a prompt, and which. Every candidate in the set
 * answers to [PromptRules]; of those that pass, the highest priority whose own
 * [PromptCandidate.isEligible] says yes is the one, and only one a session.
 *
 * The host asks ([next]) when Today settles; moments ([emit]) wait here until it does, and go
 * stale after [PromptRules.EVENT_FRESH_FOR]. A showing is written down before anything is drawn,
 * so a crash in between costs a prompt rather than repeating one, as the review coordinator
 * records an ask before it makes it. Each showing takes one answer, accepted or dismissed: a sheet
 * that closes after its button was tapped does not count as a Not now as well.
 */
@HiddenFromObjC
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = PromptEvents::class)
@ContributesBinding(AppScope::class, boundType = PromptDebug::class)
@Inject
class PromptProcessor(
    candidates: Set<PromptCandidate>,
    private val ledger: PromptLedgerStore,
    private val audience: PromptAudienceSource,
    private val sessions: SessionTracker,
    private val telemetry: PromptTelemetry,
    private val clock: Clock,
) : PromptEvents, PromptDebug {

    private val logger = KLog.withTag("Prompts")
    private val ordered: List<PromptCandidate> = candidates.sortedWith(PromptRules.order)
    private val mutex = Mutex()

    private val pendingEvents = MutableStateFlow<Map<PromptEvent, Instant>>(emptyMap())
    private val forced = MutableStateFlow<String?>(null)
    private val wakes = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    /** The session (its uuid) in which a prompt last showed. In memory: a new process is a new session. */
    private var promptedInSession: String? = null

    /** The session in which a prompt followed someone's answer to another ([PromptRules.isFollowUp]). */
    private var followedUpInSession: String? = null

    /** Prompts answered since they last showed. */
    private val answered = mutableSetOf<String>()

    private val _card = MutableStateFlow<PromptShowing?>(null)

    /** The prompt drawn as a card in Today's list, while it is up. */
    val card: StateFlow<PromptShowing?> = _card.asStateFlow()

    /** Ticks when there may be something new to show: a moment, or the QA menu's Show next. */
    val wakeUps: Flow<Unit> = wakes.asSharedFlow()

    fun candidate(id: String): PromptCandidate? = ordered.firstOrNull { it.id == id }

    override fun emit(event: PromptEvent) {
        pendingEvents.update { it + (event to clock.now()) }
        wakes.tryEmit(Unit)
    }

    override fun showNext(id: String) {
        forced.value = id
        wakes.tryEmit(Unit)
    }

    /**
     * The prompt to put up now, or null. Called by the host once Today has settled with nothing
     * over it. Moments are used up whatever the answer: one that could not show is gone.
     */
    suspend fun next(): PromptShowing? = mutex.withLock {
        val now = clock.now()
        val moments = pendingEvents.getAndUpdate { emptyMap() }
            .filterValues { at -> now - at <= PromptRules.EVENT_FRESH_FOR }
            .entries.sortedByDescending { it.value }
            .map { PromptTrigger.After(it.key) }
        val who = audience.current()
        val userId = who.userId ?: return@withLock null
        val session = sessions.current.uuid
        if (promptedInSession != session) _card.value = null

        var account = ledger.account(userId)
        if (account.firstSeenAtMs == 0L) {
            account = ledger.update(userId) { it.copy(firstSeenAtMs = now.toEpochMilliseconds()) }
        }

        forced.getAndUpdate { null }?.let(::candidate)?.let { wanted ->
            if (PromptRules.check(wanted, PromptTrigger.Forced, account, who, now, shownThisSession = false) == null) {
                return@withLock show(wanted, PromptTrigger.Forced, userId, session, now)
            }
        }

        val eligibility = mutableMapOf<Pair<String, PromptEvent?>, Boolean>()
        for (trigger in moments + PromptTrigger.TodayOpen) {
            val moment = (trigger as? PromptTrigger.After)?.event
            for (candidate in ordered) {
                val shownThisSession = promptedInSession == session
                val followUpTaken = followedUpInSession == session
                val block = PromptRules.check(candidate, trigger, account, who, now, shownThisSession, followUpTaken)
                if (block != null) continue
                val eligible = eligibility.getOrPut(candidate.id to moment) { isEligible(candidate, moment) }
                if (eligible) {
                    if (shownThisSession) followedUpInSession = session
                    return@withLock show(candidate, trigger, userId, session, now)
                }
            }
        }
        null
    }

    /** The main button. Returns where to go, or null when [id] was already answered. */
    suspend fun accepted(id: String): PromptAccept? {
        val candidate = candidate(id) ?: return null
        val first = answer(id) { it.copy(accepted = true) }
        if (!first) return null
        if (!candidate.sensitive) telemetry.accepted(id)
        return Catching { candidate.accept() }
            .logOnFailure { "Prompt $id failed to accept" }
            .getOrNull() ?: PromptAccept.Done
    }

    /**
     * A [PromptPresentation.System] prompt's showing: its [PromptCandidate.accept] asks the
     * system, which draws whatever it draws. Counted as shown already; never as an answer.
     */
    suspend fun launch(id: String) {
        val candidate = candidate(id) ?: return
        Catching { candidate.accept() }.logOnFailure { "Prompt $id failed to launch" }
    }

    /** Where a [PromptPresentation.Screen] prompt draws itself, or null when it has nowhere to go. */
    suspend fun screen(id: String): Route? {
        val candidate = candidate(id) ?: return null
        return Catching { candidate.screen() }
            .logOnFailure { "Prompt $id failed to name its screen" }
            .getOrNull()
    }

    /** Not now, a swipe, a tap outside, Back. A second answer to the same showing is ignored. */
    suspend fun dismissed(id: String) {
        val candidate = candidate(id) ?: return
        if (answer(id) { it.copy(dismissals = it.dismissals + 1) } && !candidate.sensitive) telemetry.dismissed(id)
    }

    private suspend fun answer(id: String, change: (PromptRecord) -> PromptRecord): Boolean = mutex.withLock {
        if (!answered.add(id)) return@withLock false
        if (_card.value?.id == id) _card.value = null
        val userId = audience.current().userId ?: return@withLock true
        ledger.update(userId) { it.with(id, change) }
        true
    }

    private suspend fun show(candidate: PromptCandidate, trigger: PromptTrigger, userId: String, session: String, now: Instant): PromptShowing {
        val at = now.toEpochMilliseconds()
        ledger.update(userId) { account ->
            account.copy(lastPromptAtMs = at).with(candidate.id) { it.copy(shows = it.shows + 1, lastShownAtMs = at) }
        }
        promptedInSession = session
        answered -= candidate.id
        val showing = PromptShowing(candidate.id, candidate.presentation, trigger)
        if (candidate.presentation == PromptPresentation.Card) _card.value = showing
        if (!candidate.sensitive) {
            telemetry.shown(candidate.id, candidate.presentation, trigger)
            logger.i { "Prompt ${candidate.id} shown (${trigger.wire})" }
        }
        return showing
    }

    private suspend fun isEligible(candidate: PromptCandidate, moment: PromptEvent? = null): Boolean =
        Catching { candidate.isEligible(moment) }
            .logOnFailure { "Prompt ${candidate.id} failed its eligibility check" }
            .getOrNull() == true

    override suspend fun inspect(): List<PromptInspection> {
        val who = audience.current()
        val account = who.userId?.let { ledger.account(it) } ?: AccountLedger()
        val now = clock.now()
        val shownThisSession = mutex.withLock { promptedInSession == sessions.current.uuid }
        return ordered.map { candidate ->
            val record = account.record(candidate.id)
            PromptInspection(
                id = candidate.id,
                title = candidate.content.title,
                presentation = candidate.presentation,
                priority = candidate.priority,
                blockedBy = PromptRules.check(candidate, PromptTrigger.TodayOpen, account, who, now, shownThisSession),
                eligible = isEligible(candidate),
                shows = record.shows,
                dismissals = record.dismissals,
                accepted = record.accepted,
            )
        }
    }

    override suspend fun reset(id: String?) {
        val userId = audience.current().userId ?: return
        val now = clock.now()
        mutex.withLock {
            promptedInSession = null
            followedUpInSession = null
            if (id == null || _card.value?.id == id) _card.value = null
            ledger.update(userId) { account ->
                // The quiet start is over too, or a reset would hide everything for two days.
                val settled = now - PromptRules.QUIET_START
                account.copy(
                    firstSeenAtMs = minOf(account.firstSeenAtMs.takeIf { it > 0 } ?: Long.MAX_VALUE, settled.toEpochMilliseconds()),
                    lastPromptAtMs = 0L,
                    prompts = if (id == null) emptyMap() else account.prompts - id,
                )
            }
        }
    }
}
