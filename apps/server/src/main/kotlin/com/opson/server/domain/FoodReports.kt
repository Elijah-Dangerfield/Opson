package com.opson.server.domain

import com.opson.server.di.ServerScope
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * What someone says is wrong with a food. Wire values match the `food_reports.reason` CHECK (V29,
 * V33). Apps since V33 send [WrongNumbers] and [MissingInfo] in place of the first two, and may
 * pick several; the older codes stay valid for the apps that send them.
 */
enum class ReportReason(val wire: String) {
    WrongCalories("wrong_calories"),
    WrongNutrients("wrong_nutrients"),

    /** Calories or nutrients are wrong (V33). */
    WrongNumbers("wrong_numbers"),

    /** Something the pack gives is missing: a nutrient, the serving, ingredients (V33). */
    MissingInfo("missing_info"),
    WrongServing("wrong_serving"),
    WrongName("wrong_name"),
    Duplicate("duplicate"),
    NotFood("not_food"),
    Other("other"),

    /** The server's own: the energy guard ([FoodEnergy]) caught this food's calories out. Nobody sends it. */
    EnergyCheck("energy_check"),
    ;

    companion object {
        fun fromWire(value: String?): ReportReason? = entries.firstOrNull { it.wire == value }

        /** What a person may send. */
        fun fromPerson(value: String?): ReportReason? = fromWire(value)?.takeIf { it != EnergyCheck }
    }
}

enum class ReportStatus(val wire: String) {
    Open("open"),

    /** A reviewer applied a correction ([FoodReport.correctionId]). */
    Accepted("accepted"),
    Rejected("rejected"),

    /** The food is a copy of another ([FoodReport.duplicateOf], when the reviewer said which). */
    Duplicate("duplicate"),
    ;

    companion object {
        fun fromWire(value: String?): ReportStatus? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * The numbers someone says are right, as they read them: for the amount the
 * detail showed ([basisLabel], [basisGrams] of it), or per 100 when there is
 * no basis. [servingLabel] and [servingGrams] are the serving they say the
 * pack gives. Every field is optional.
 */
data class SuggestedValues(
    val basisLabel: String? = null,
    val basisGrams: Double? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val servingLabel: String? = null,
    val servingGrams: Double? = null,
) {
    val isEmpty: Boolean get() = listOf(kcal, protein, carbs, fat, servingGrams).all { it == null } && servingLabel == null

    /** As a correction would store it: the nutrients per 100 g (or ml). */
    fun toCorrection(): CorrectionValues {
        val factor = basisGrams?.takeIf { it > 0 }?.let { PER / it } ?: 1.0
        fun per100(value: Double?) = value?.let { Math.round(it * factor * ROUND) / ROUND }
        return CorrectionValues(
            kcal = per100(kcal),
            protein = per100(protein),
            carbs = per100(carbs),
            fat = per100(fat),
            servingLabel = servingLabel,
            servingGrams = servingGrams,
        )
    }

    private companion object {
        const val PER = 100.0
        const val ROUND = 100.0
    }
}

/**
 * What a correction sets, per 100 g (or ml, as the food's own numbers are).
 * Null leaves that field as the source has it. A serving goes first among the
 * food's portions, so it is what the app logs by default.
 */
data class CorrectionValues(
    val name: String? = null,
    val brand: String? = null,
    val kcal: Double? = null,
    val protein: Double? = null,
    val carbs: Double? = null,
    val fat: Double? = null,
    val servingLabel: String? = null,
    val servingGrams: Double? = null,
) {
    val isEmpty: Boolean get() = this == CorrectionValues()

    /** These over [older]: a second correction keeps what the first fixed unless it changes it too. */
    fun over(older: CorrectionValues?): CorrectionValues = if (older == null) {
        this
    } else {
        CorrectionValues(
            name = name ?: older.name,
            brand = brand ?: older.brand,
            kcal = kcal ?: older.kcal,
            protein = protein ?: older.protein,
            carbs = carbs ?: older.carbs,
            fat = fat ?: older.fat,
            servingLabel = servingLabel ?: older.servingLabel,
            servingGrams = if (servingLabel != null) servingGrams else older.servingGrams,
        )
    }
}

/** A food's values as someone saw them: what a report was about, and what a correction replaced. */
data class FoodSnapshot(
    val name: String,
    val brand: String?,
    val source: String,
    val per100g: Nutrients,
    val serving: Portion?,
) {
    companion object {
        fun of(food: Food) = FoodSnapshot(food.name, food.brand, food.source.wire, food.per100g, food.portions.firstOrNull())
    }
}

/** Who corrected a food and when, carried on the served [Food]. */
@OptIn(ExperimentalTime::class)
data class CorrectionMark(val id: UUID, val at: Instant, val by: String)

/**
 * A reviewer's fix to a food, kept apart from the imported row (`food_corrections`,
 * V29) and laid over it whenever the food is served. A re-import of USDA or
 * Open Food Facts rewrites the row and never this, so a human fix survives
 * it. [base] is the food as served just before, for the diff and the undo.
 * One correction per food is live at a time: a newer one supersedes it, and
 * reverting the newer brings the older back.
 */
@OptIn(ExperimentalTime::class)
data class FoodCorrection(
    val id: UUID,
    val foodId: UUID,
    val createdAt: Instant,
    val createdBy: String,
    val values: CorrectionValues,
    val base: FoodSnapshot,
    val revertedAt: Instant? = null,
    val revertedBy: String? = null,
    val supersededBy: UUID? = null,
) {
    val live: Boolean get() = revertedAt == null && supersededBy == null

    fun applyTo(food: Food): Food {
        val v = values
        val portions = v.servingLabel?.let { label ->
            listOf(Portion(label, v.servingGrams)) + food.portions.filterNot { it.label.equals(label, ignoreCase = true) }
        } ?: food.portions
        return food.copy(
            name = v.name ?: food.name,
            brand = v.brand ?: food.brand,
            per100g = food.per100g.copy(
                kcal = v.kcal ?: food.per100g.kcal,
                protein = v.protein ?: food.per100g.protein,
                carbs = v.carbs ?: food.per100g.carbs,
                fat = v.fat ?: food.per100g.fat,
            ),
            portions = portions,
            correction = CorrectionMark(id, createdAt, createdBy),
        )
    }
}

/**
 * A food as the catalog hands it out: a reviewer's [correction] over the
 * stored row, then the energy guard, unless the correction itself set the
 * calories (a person checked them; the guard does not second-guess that).
 */
object ServedFood {
    /** [verified]: a Builder's verification of it was confirmed or accepted ([FoodCheck]). */
    fun of(stored: Food, correction: FoodCorrection?, verified: Boolean = false): Food {
        val corrected = correction?.applyTo(stored) ?: stored
        val portioned = Portions.served(corrected)
        val guarded = if (correction?.values?.kcal != null) portioned else FoodEnergy.served(portioned)
        return guarded.copy(checked = FoodCheck.checked(stored, correction, verified))
    }
}

/**
 * What "verified" means (docs/decisions.md, 2026-10-30): a person checked the food against its
 * pack, or a reviewer stood behind it. A Builder's verification confirmed by the label's text or
 * accepted by a reviewer, a reviewer's live correction, or a food someone added from its pack
 * that a reviewer approved. USDA's and NEVO's reference data is not "checked": there is no pack.
 */
object FoodCheck {
    fun checked(stored: Food, correction: FoodCorrection?, verified: Boolean): Boolean =
        verified ||
            (correction?.live == true) ||
            (stored.source == CatalogSource.UserSubmitted && stored.reviewStatus == SubmissionStatus.Approved)
}

class ReportPhoto(val bytes: ByteArray, val type: ImageType)

/** Which side of the pack a report's or a verification's photo shows (V33). */
enum class PackSide(val wire: String) {
    Front("front"),
    Label("label"),
    ;

    companion object {
        fun fromWire(value: String?): PackSide? = entries.firstOrNull { it.wire == value }
    }
}

/** A report as the route validated it. [userId] is null only for the server's own energy check. */
data class NewFoodReport(
    val id: UUID,
    val userId: UserId?,
    val foodId: UUID,
    val reason: ReportReason,
    val suggested: SuggestedValues?,
    val note: String?,
    val snapshot: FoodSnapshot,
    /** The label (the back of the pack). */
    val photo: ReportPhoto?,
    /** Every reason picked, [reason] first. */
    val reasons: List<ReportReason> = listOf(reason),
    val frontPhoto: ReportPhoto? = null,
)

@OptIn(ExperimentalTime::class)
data class FoodReport(
    val id: UUID,
    val foodId: UUID,
    val userId: UserId?,
    val createdAt: Instant,
    val reason: ReportReason,
    val suggested: SuggestedValues?,
    val note: String?,
    val snapshot: FoodSnapshot,
    val status: ReportStatus,
    val resolvedAt: Instant? = null,
    val resolvedBy: String? = null,
    val resolution: String? = null,
    val correctionId: UUID? = null,
    val duplicateOf: UUID? = null,
    /** A photo of the label. */
    val hasPhoto: Boolean = false,
    /** Every reason the person picked, [reason] first. */
    val reasons: List<ReportReason> = listOf(reason),
    val hasFrontPhoto: Boolean = false,
)

sealed interface ReportOutcome {
    data class Created(val report: FoodReport) : ReportOutcome

    /** The same id again from the same person: the first report, unchanged. */
    data class Existing(val report: FoodReport) : ReportOutcome

    /** The id is someone else's. */
    data object Conflict : ReportOutcome
}

sealed interface CorrectionOutcome {
    data class Applied(val correction: FoodCorrection) : CorrectionOutcome

    /** A report named is not open on this food. */
    data object NotOpen : CorrectionOutcome
    data object NothingToChange : CorrectionOutcome
}

sealed interface RevertOutcome {
    /** [restored] is the older correction that is live again, if this one had superseded it. */
    data class Reverted(val correction: FoodCorrection, val restored: FoodCorrection?) : RevertOutcome
    data object NotFound : RevertOutcome

    /** Only the live correction can be undone; this one was reverted or superseded already. */
    data object NotLive : RevertOutcome
}

@OptIn(ExperimentalTime::class)
interface FoodReportRepository {
    /** Stores a report and its photo in one transaction. Idempotent on the id. It never changes the food. */
    suspend fun report(report: NewFoodReport): ReportOutcome

    /**
     * The server's own report for a food the energy guard flagged, with its
     * estimate as the suggestion. At most one per food, ever: once a reviewer
     * has decided, the same flag never comes back. True when one was added.
     */
    suspend fun flagEnergy(food: Food): Boolean

    /** Open reports, oldest first, at most [limit]. */
    suspend fun open(limit: Int): List<FoodReport>

    suspend fun countOpen(): Long

    /** Every report on [foodId], newest first. */
    suspend fun forFood(foodId: UUID): List<FoodReport>

    suspend fun photo(reportId: UUID, side: PackSide = PackSide.Label): StoredPhoto?

    /** The live correction per food, for laying over what the catalog serves. */
    suspend fun liveCorrections(foodIds: Collection<UUID>): Map<UUID, FoodCorrection>

    /** Every correction on [foodId], newest first. */
    suspend fun corrections(foodId: UUID): List<FoodCorrection>

    /**
     * Lays [values] over the food (merged over any live correction, which it
     * supersedes) and closes [reportIds] as accepted. [base] is the food as
     * it was served before.
     */
    suspend fun applyCorrection(foodId: UUID, values: CorrectionValues, base: FoodSnapshot, reportIds: List<UUID>, by: String): CorrectionOutcome

    /** Closes [reportIds] as rejected. Returns how many were open. */
    suspend fun reject(reportIds: List<UUID>, reason: String?, by: String): Int

    /** Closes [reportIds]: the food is a copy, of [duplicateOf] when the reviewer said which. */
    suspend fun markDuplicate(reportIds: List<UUID>, duplicateOf: UUID?, by: String): Int

    suspend fun revert(correctionId: UUID, by: String): RevertOutcome

    /** Deletes the photos of reports decided before [before]. */
    suspend fun purgeDecidedPhotos(before: Instant): Int
}

/** Limits on `POST /v1/foods/{id}/reports`. */
object FoodReportLimits {
    /** Two photos of up to [MAX_IMAGE_BYTES], base64 (4/3), and the rest: checked before the body is read. */
    const val MAX_BODY_BYTES = 12L * 1024 * 1024
    const val MAX_IMAGE_BYTES = PhotoLimits.MAX_IMAGE_BYTES
    const val MAX_NOTE = 1_000
    const val MAX_LABEL = 80
    const val MAX_NAME = 200
    const val MAX_AMOUNT = 100_000.0
    const val MAX_GRAMS = 5_000.0
    const val MAX_KCAL_PER_100 = 900.0
    const val MAX_MACRO_PER_100 = 100.0
}

/**
 * Who may change a food directly, without review. For them
 * `POST /v1/foods/{id}/reports` applies the suggested values as a correction
 * straight away, recorded under their account id and reversible like any
 * other; everyone else's report waits for a reviewer.
 */
fun interface CorrectionAuthority {
    suspend fun canApplyDirectly(userId: UserId): Boolean
}

/** Owners and admins ([Permission.FoodEditDirect]), read on every report, so a role change applies at once. */
@SingleIn(ServerScope::class)
@ContributesBinding(ServerScope::class)
@Inject
class RoleCorrectionAuthority(private val permissions: Permissions) : CorrectionAuthority {
    override suspend fun canApplyDirectly(userId: UserId): Boolean = permissions.can(userId, Permission.FoodEditDirect)
}

/**
 * How often one person may report foods, keyed on the JWT's user id like
 * [FoodLabelLimiter]: a pantry's worth of fixes in a sitting is fine, a
 * script is not. In memory, so it resets on deploy and counts per machine.
 */
@OptIn(ExperimentalTime::class)
@SingleIn(ServerScope::class)
@Inject
class FoodReportLimiter(private val clock: Clock) {
    private val calls = ConcurrentHashMap<UserId, ArrayDeque<Instant>>()

    fun tryReport(userId: UserId): Boolean {
        val now = clock.now()
        val history = calls.computeIfAbsent(userId) { ArrayDeque() }
        synchronized(history) {
            while (history.isNotEmpty() && now - history.first() >= DAY) history.removeFirst()
            if (history.size >= DAILY) return false
            if (history.count { now - it < BURST_WINDOW } >= BURST) return false
            history.addLast(now)
            return true
        }
    }

    companion object {
        const val BURST = 10
        const val DAILY = 30
        val BURST_WINDOW: Duration = 10.minutes
        private val DAY: Duration = 1.days
    }
}
