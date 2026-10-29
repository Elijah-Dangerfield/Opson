package com.opson.libraries.prompts.impl

import com.opson.libraries.prompts.PromptBlock
import com.opson.libraries.prompts.PromptCandidate
import com.opson.libraries.prompts.PromptEvent
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/** Why the processor is asking. */
sealed interface PromptTrigger {
    val wire: String

    /** Today settled with nothing over it: a launch, a foreground, coming back from a screen. */
    data object TodayOpen : PromptTrigger {
        override val wire = "today"
    }

    /** Today settled within [PromptRules.EVENT_FRESH_FOR] of [event]. */
    data class After(val event: PromptEvent) : PromptTrigger {
        override val wire = event.wire
    }

    /** The QA menu's Show next. Past every rule. */
    data object Forced : PromptTrigger {
        override val wire = "qa"
    }
}

/** Who Today belongs to right now, as the rules need it. */
data class PromptAudience(
    val userId: String?,
    val onboarded: Boolean,
    /** "Numbers or tracking have been hard for me before", from onboarding. */
    val numbersHard: Boolean,
)

/**
 * The rules every prompt answers to, as pure functions of what is stored and what time it is.
 * Ported from Cards' Home arbiter (one pure pass decides, in one place, instead of each surface
 * racing its own flag) and its review coordinator (a quiet start and a floor between asks).
 *
 * In the order [check] applies them, so the reason it reports is the first one that holds:
 *
 * 1. Only a signed-in account that finished onboarding sees prompts. Sign-in and onboarding are
 *    their own routes, so the host never asks there; this catches the gap around them.
 * 2. The prompt's own record: accepted once ends it, [PromptFrequency.maxDismissals] Not nows end
 *    it, [PromptFrequency.maxShows] showings end it, and two showings are at least
 *    [PromptFrequency.minGap] apart. Nothing skips these, events included.
 * 3. The trigger must be one the prompt asked for.
 * 4. Someone who said numbers have been hard sees only prompts that opted in.
 * 5. At most one prompt a session ([com.opson.libraries.opson.SessionTracker]), events included.
 * 6. On Today opening only: none in the first [QUIET_START] of an account on this phone, and
 *    [MIN_GAP] between any two prompts ([MIN_GAP_NUMBERS_HARD] after numbers have been hard). A
 *    moment ([PromptTrigger.After]) skips both, because it does not come back.
 */
object PromptRules {
    /** Between any two prompts, so a week holds at most seven and usually far fewer. */
    val MIN_GAP: Duration = 24.hours

    /** Fewer prompts for someone who said numbers have been hard. */
    val MIN_GAP_NUMBERS_HARD: Duration = 72.hours

    /** An account's first days on this phone are for settling in, not for being asked things. */
    val QUIET_START: Duration = 2.days

    /** A moment older than this is gone; a prompt about it would arrive out of context. */
    val EVENT_FRESH_FOR: Duration = 10.minutes

    fun check(
        candidate: PromptCandidate,
        trigger: PromptTrigger,
        account: AccountLedger,
        audience: PromptAudience,
        now: Instant,
        shownThisSession: Boolean,
    ): PromptBlock? {
        if (audience.userId == null) return PromptBlock.NotSignedIn
        if (!audience.onboarded) return PromptBlock.NotOnboarded
        if (trigger == PromptTrigger.Forced) return null

        val record = account.prompts[candidate.id] ?: PromptRecord()
        val frequency = candidate.frequency
        when {
            record.accepted -> return PromptBlock.Accepted
            record.dismissals >= frequency.maxDismissals -> return PromptBlock.Dismissed
            record.shows >= frequency.maxShows -> return PromptBlock.ShownEnough
            record.lastShownAtMs > 0 && now - Instant.fromEpochMilliseconds(record.lastShownAtMs) < frequency.minGap ->
                return PromptBlock.ShownRecently
        }

        val asked = when (trigger) {
            PromptTrigger.TodayOpen -> candidate.onTodayOpen
            is PromptTrigger.After -> trigger.event in candidate.events
            PromptTrigger.Forced -> true
        }
        if (!asked) return PromptBlock.NotThisMoment
        if (audience.numbersHard && !candidate.offerWhenNumbersHard) return PromptBlock.NumbersHard
        if (shownThisSession) return PromptBlock.ShownThisSession

        if (trigger == PromptTrigger.TodayOpen) {
            if (account.firstSeenAtMs > 0 && now - Instant.fromEpochMilliseconds(account.firstSeenAtMs) < QUIET_START) {
                return PromptBlock.SettlingIn
            }
            val gap = if (audience.numbersHard) MIN_GAP_NUMBERS_HARD else MIN_GAP
            if (account.lastPromptAtMs > 0 && now - Instant.fromEpochMilliseconds(account.lastPromptAtMs) < gap) {
                return PromptBlock.PromptedRecently
            }
        }
        return null
    }

    /** Highest priority first; a tie goes by id, so the order never depends on the set's. */
    val order: Comparator<PromptCandidate> = compareByDescending<PromptCandidate> { it.priority }.thenBy { it.id }
}
