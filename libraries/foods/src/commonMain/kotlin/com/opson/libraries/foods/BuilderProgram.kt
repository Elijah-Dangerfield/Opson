package com.opson.libraries.foods

import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Instant

/** Where someone stands in Opson Builders. Wire values match the server's. */
enum class BuilderStatus(val wire: String) {
    None("none"),
    Active("active"),
    Left("left"),
    Removed("removed"),
    ;

    companion object {
        fun fromWire(value: String?): BuilderStatus = entries.firstOrNull { it.wire == value } ?: None
    }
}

/** How far Opson trusts someone's contributions. Only [Trusted] can be approved without a person. */
enum class BuilderTrust(val wire: String) {
    Anyone("anyone"),
    Builder("builder"),
    Trusted("trusted"),
    Removed("removed"),
    ;

    companion object {
        fun fromWire(value: String?): BuilderTrust = entries.firstOrNull { it.wire == value } ?: Anyone
    }
}

/** Why Join isn't offered. */
enum class JoinBlock(val wire: String) {
    /** A guest account. */
    Guest("guest"),

    /** No approved food yet and the account is under a week old; [BuilderStanding.availableAt] says when. */
    TooNew("too_new"),
    Removed("removed"),

    /** The program is at its cap for now. */
    Full("full"),
    ;

    companion object {
        fun fromWire(value: String?): JoinBlock? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * The person's place in the program, from `GET /v1/builders/me`. [accepted30d] of [needed]
 * accepted contributions in 30 days keeps Pro on, until [proUntil].
 */
data class BuilderStanding(
    val status: BuilderStatus,
    val trust: BuilderTrust,
    val joinedAt: Instant? = null,
    val proUntil: Instant? = null,
    val accepted30d: Int = 0,
    val needed: Int = 10,
    val photoRetention: Boolean = false,
    val canJoin: Boolean = false,
    val joinBlock: JoinBlock? = null,
    val availableAt: Instant? = null,
    val termsVersion: String = "",
) {
    val isActive: Boolean get() = status == BuilderStatus.Active
}

sealed interface BuilderResult {
    data class Done(val standing: BuilderStanding) : BuilderResult

    /** The server said no, and why. */
    data class Blocked(val block: JoinBlock) : BuilderResult

    /** A server from before the program. Nothing to retry. */
    data object Unavailable : BuilderResult

    data class Failed(val offline: Boolean) : BuilderResult
}

/**
 * Opson Builders (`release.builders`, `docs/plans/feedback-2026-10-29/builder-program.md`):
 * people who photograph packs they have in hand to add and check foods, and get Pro while
 * they do. [standing] is null until the server has answered for this account; it is refreshed
 * whenever the account becomes active and after every change made here.
 */
interface BuilderProgram {
    val standing: StateFlow<BuilderStanding?>

    /** Whether this account is an active Builder right now, as far as the phone knows. */
    val isBuilder: Boolean get() = standing.value?.isActive == true

    suspend fun refresh(): BuilderResult

    /** Joins with the rules accepted. [photoRetention] is the separate, optional consent. */
    suspend fun join(photoRetention: Boolean): BuilderResult

    suspend fun leave(): BuilderResult

    suspend fun setPhotoRetention(keep: Boolean): BuilderResult
}

/** What the phone read off a label photo, sent with a Builder's submission. Nothing here is logged. */
class SubmissionCapture(
    /** `vision` or `mlkit`. */
    val engine: String,
    val fromCamera: Boolean,
    val lines: List<LabelTextLine>,
    val draft: LabelFacts?,
    val signals: LabelSignals?,
    val score: Double?,
)

/** A Builder's check of a catalog food against the pack in their hand. */
sealed interface FoodCheckResult {
    /** Every headline number of the food is on the label. Counts toward Pro, once per food. */
    data object Confirmed : FoodCheckResult

    /** Some weren't found in the photo's text: [missing] are their field keys (`energy_kcal`). */
    data class Unconfirmed(val missing: List<String>) : FoodCheckResult

    /** Not a Builder on the server (left, or removed). */
    data object NotBuilder : FoodCheckResult
    data object TooMany : FoodCheckResult

    /** No such food any more, or a server from before checks. */
    data object Gone : FoodCheckResult
    data class Failed(val offline: Boolean) : FoodCheckResult
}

/**
 * `POST /v1/foods/{id}/verifications`: the label's text, read on the phone, goes to the server,
 * which looks for the food's numbers in it. No photo is sent. [id] is made once, so a retry is
 * the same check.
 */
interface FoodChecks {
    suspend fun check(id: String, foodId: String, engine: String, lines: List<LabelTextLine>): FoodCheckResult
}
