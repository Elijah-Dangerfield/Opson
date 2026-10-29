package com.opson.libraries.prompts.impl

import com.opson.libraries.navigation.Route
import com.opson.libraries.opson.Session
import com.opson.libraries.opson.SessionStartReason
import com.opson.libraries.opson.SessionTracker
import com.opson.libraries.prompts.PromptAccept
import com.opson.libraries.prompts.PromptCandidate
import com.opson.libraries.prompts.PromptContent
import com.opson.libraries.prompts.PromptEvent
import com.opson.libraries.prompts.PromptFrequency
import com.opson.libraries.prompts.PromptPresentation
import com.opson.libraries.ui.components.icon.AppIcons
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Instant
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.prompt_checkin_accept
import opson.libraries.resources.generated.resources.prompt_checkin_body
import opson.libraries.resources.generated.resources.prompt_checkin_title
import opson.libraries.resources.generated.resources.prompt_not_now

internal val FAKE_CONTENT = PromptContent(
    icon = AppIcons.Mood,
    title = Res.string.prompt_checkin_title,
    body = Res.string.prompt_checkin_body,
    accept = Res.string.prompt_checkin_accept,
    dismiss = Res.string.prompt_not_now,
)

internal class FakeRoute : Route()

internal class FakeCandidate(
    override val id: String,
    override val priority: Int = 200,
    override val presentation: PromptPresentation = PromptPresentation.Dialog,
    override val frequency: PromptFrequency = PromptFrequency(),
    override val onTodayOpen: Boolean = true,
    override val events: Set<PromptEvent> = emptySet(),
    override val offerWhenNumbersHard: Boolean = false,
    var eligible: Boolean = true,
    var throws: Boolean = false,
    val acceptsWith: PromptAccept = PromptAccept.Open(FakeRoute()),
    val screenRoute: Route? = null,
) : PromptCandidate {
    override val content = FAKE_CONTENT
    var eligibilityChecks = 0
        private set

    override suspend fun isEligible(): Boolean {
        eligibilityChecks++
        if (throws) error("eligibility blew up")
        return eligible
    }

    override suspend fun accept(): PromptAccept = acceptsWith

    override suspend fun screen(): Route? = screenRoute
}

internal class InMemoryLedger : PromptLedgerStore {
    val accounts = mutableMapOf<String, AccountLedger>()

    override suspend fun account(userId: String): AccountLedger = accounts[userId] ?: AccountLedger()

    override suspend fun update(userId: String, change: (AccountLedger) -> AccountLedger): AccountLedger {
        val next = change(account(userId))
        accounts[userId] = next
        return next
    }
}

internal class FakeAudience(var audience: PromptAudience = PromptAudience(userId = "user-1", onboarded = true, numbersHard = false)) :
    PromptAudienceSource {
    override suspend fun current(): PromptAudience = audience
}

internal class FakeSessions : SessionTracker {
    private var count = 1L
    private val flow = MutableStateFlow(session(count))
    override val current: Session get() = flow.value

    override fun observe(): Flow<Session> = flow

    /** A background long enough to roll the session. */
    fun roll() {
        count++
        flow.value = session(count)
    }

    private fun session(id: Long) = Session(id = id, startedAtMs = 0L, reason = SessionStartReason.ColdBoot, uuid = "session-$id")
}

internal class RecordingTelemetry : PromptTelemetry {
    val events = mutableListOf<String>()

    override fun shown(id: String, presentation: PromptPresentation, trigger: PromptTrigger) {
        events += "shown:$id:${presentation.wire}:${trigger.wire}"
    }

    override fun dismissed(id: String) {
        events += "dismissed:$id"
    }

    override fun accepted(id: String) {
        events += "accepted:$id"
    }
}

internal class MutableClock(var at: Instant) : Clock {
    fun advance(by: Duration) {
        at += by
    }

    override fun now(): Instant = at
}
