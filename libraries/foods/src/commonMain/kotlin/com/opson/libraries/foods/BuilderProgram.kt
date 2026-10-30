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

/** Why Apply isn't offered. */
enum class JoinBlock(val wire: String) {
    /** A guest account. */
    Guest("guest"),

    /** No approved food yet and the account is under a week old; [BuilderStanding.availableAt] says when. */
    TooNew("too_new"),
    Removed("removed"),

    /** The program is at its cap. Only a server from before applications says it. */
    Full("full"),

    /** An application is waiting for Opson. */
    Pending("pending"),

    /** Declined; [BuilderStanding.availableAt] is when they may apply again. */
    Declined("declined"),
    ;

    companion object {
        fun fromWire(value: String?): JoinBlock? = entries.firstOrNull { it.wire == value }
    }
}

/** Where one application to Builders is. Wire values match the server's. */
enum class ApplicationStatus(val wire: String) {
    Pending("pending"),
    Accepted("accepted"),
    Declined("declined"),
    Withdrawn("withdrawn"),
    ;

    companion object {
        fun fromWire(value: String?): ApplicationStatus? = entries.firstOrNull { it.wire == value }
    }
}

/** The person's newest application. [note] is Opson's, with a decision; [reapplyAt] follows a decline. */
data class BuilderApplication(
    val id: String,
    val status: ApplicationStatus,
    val submittedAt: Instant,
    val decidedAt: Instant? = null,
    val note: String? = null,
    val reapplyAt: Instant? = null,
)

/** How often someone buys packaged food. Wire values match the server's. */
enum class ShoppingFrequency(val wire: String) { Weekly("weekly"), Monthly("monthly"), Rarely("rarely") }

/** Where they shop, by the label format they will mostly photograph. Wire values match the server's. */
enum class LabelRegion(val wire: String) { Us("us"), Canada("ca"), Uk("uk"), Eu("eu"), AustraliaNz("au_nz"), Other("other") }

/** An application as the form sends it. [motivation] is optional; [via] is where they applied from. */
data class BuilderApplicationForm(
    val shopping: ShoppingFrequency,
    val region: LabelRegion,
    val motivation: String?,
    val photoRetention: Boolean,
    val via: String,
)

/**
 * The person's place in the program, from `GET /v1/builders/me`. [accepted30d] of [needed]
 * accepted contributions in 30 days keeps Pro on, until [proUntil]. [canApply] says Apply is open
 * to them and [joinBlock] why not; [application] is their newest. [foodsAdded], [checks] and
 * [rejected] are their record.
 */
data class BuilderStanding(
    val status: BuilderStatus,
    val trust: BuilderTrust,
    val joinedAt: Instant? = null,
    val proUntil: Instant? = null,
    val accepted30d: Int = 0,
    val needed: Int = 10,
    val photoRetention: Boolean = false,
    val canApply: Boolean = false,
    val joinBlock: JoinBlock? = null,
    val availableAt: Instant? = null,
    val termsVersion: String = "",
    val application: BuilderApplication? = null,
    val foodsAdded: Int = 0,
    val checks: Int = 0,
    val rejected: Int = 0,
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
 * they do. They apply, and Opson says yes or no. [standing] is null until the server has
 * answered for this account; it is refreshed whenever the account becomes active and after
 * every change made here.
 */
interface BuilderProgram {
    val standing: StateFlow<BuilderStanding?>

    /** Whether this account is an active Builder right now, as far as the phone knows. */
    val isBuilder: Boolean get() = standing.value?.isActive == true

    suspend fun refresh(): BuilderResult

    /** Sends an application with the rules accepted. Becoming a Builder waits for Opson's answer. */
    suspend fun sendApplication(form: BuilderApplicationForm): BuilderResult

    /** Takes back a pending application. */
    suspend fun withdraw(): BuilderResult

    suspend fun leave(): BuilderResult

    suspend fun setPhotoRetention(keep: Boolean): BuilderResult

    /** Whether this phone has shown Opson's decision on application [id], on the page or as a prompt. */
    suspend fun decisionSeen(id: String): Boolean

    suspend fun markDecisionSeen(id: String)
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

    /**
     * Every headline number is on the label, and the food shows as Verified once a second
     * Builder's check or a reviewer agrees. It counts toward Pro then.
     */
    data object AwaitingAgreement : FoodCheckResult

    /** Some weren't found in the photo's text: [missing] are their field keys (`energy_kcal`). */
    data class Unconfirmed(val missing: List<String>) : FoodCheckResult

    /** It waits for a reviewer with its photos and what the Builder said was wrong. */
    data object InReview : FoodCheckResult

    /** Not a Builder on the server (left, or removed). */
    data object NotBuilder : FoodCheckResult
    data object TooMany : FoodCheckResult

    /** No such food any more, or a server from before checks. */
    data object Gone : FoodCheckResult
    data class Failed(val offline: Boolean) : FoodCheckResult
}

/** A part of a food a Builder can say was wrong or missing. Wire values match the server's. */
enum class VerifyField(val wire: String) {
    Calories("calories"),
    Macros("macros"),
    Serving("serving"),
    NameBrand("name_brand"),
    Ingredients("ingredients"),
    Barcode("barcode"),
}

/**
 * The right values as the pack prints them, for [basisLabel] weighing [basisGrams] (a serving, or
 * 100 g). Every field is optional; the server turns the numbers into per 100 g.
 */
data class VerifyValues(
    val name: String? = null,
    val brand: String? = null,
    val barcode: String? = null,
    val basisLabel: String? = null,
    val basisGrams: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val servingLabel: String? = null,
    val servingGrams: Double? = null,
) {
    val isEmpty: Boolean
        get() = listOf(kcal, protein, carbs, fat, servingGrams).all { it == null } &&
            listOf(name, brand, barcode, servingLabel).all { it.isNullOrBlank() }
}

/** "Something was wrong": which parts were [wrong], which [missing], the right [values] and a [note]. */
data class VerifyProblem(
    val wrong: Set<VerifyField> = emptySet(),
    val missing: Set<VerifyField> = emptySet(),
    val values: VerifyValues? = null,
    val note: String? = null,
) {
    val isEmpty: Boolean get() = wrong.isEmpty() && missing.isEmpty() && (values?.isEmpty ?: true) && note.isNullOrBlank()
}

/**
 * One verification. [id] is made once, so a retry is the same check. [lines] are the label's text
 * as the phone read it. [front] and [label] are the two photos, JPEGs the device already shrank;
 * the server keeps them only when the check waits for a reviewer.
 */
class FoodVerification(
    val id: String,
    val foodId: String,
    val engine: String,
    val lines: List<LabelTextLine>,
    val problem: VerifyProblem? = null,
    val front: ByteArray? = null,
    val label: ByteArray? = null,
)

/**
 * `POST /v1/foods/{id}/verifications`: the label's text, read on the phone, goes to the server,
 * which looks for the food's numbers in it. With "Something was wrong", or numbers it could not
 * find, the photos and the Builder's answers wait for a reviewer ([FoodCheckResult.InReview]). A
 * server from before reviews never answers that; the caller files the problem as a report then.
 */
interface FoodChecks {
    suspend fun verify(verification: FoodVerification): FoodCheckResult
}
