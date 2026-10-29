package com.opson.server.domain

import java.util.UUID
import kotlin.math.roundToLong
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** A Builder's check of a catalog food against the pack in their hand. */
enum class VerificationOutcome(val wire: String) {
    /** Every headline number of the food was found in the label's text: it counts as accepted. */
    Confirmed("confirmed"),

    /** Some weren't. Nothing changes and nothing counts; a real difference goes through Report a problem. */
    Unconfirmed("unconfirmed"),
}

@OptIn(ExperimentalTime::class)
data class FoodVerification(
    val id: UUID,
    val foodId: UUID,
    val userId: UserId,
    val createdAt: Instant,
    val outcome: VerificationOutcome,
    val grounded: Int,
    val groundable: Int,
    val engine: String?,
)

sealed interface VerifyOutcome {
    data class Recorded(val verification: FoodVerification) : VerifyOutcome

    /** The same id again from the same person: the first answer. */
    data class Existing(val verification: FoodVerification) : VerifyOutcome
    data object Conflict : VerifyOutcome
}

interface FoodVerificationRepository {
    /**
     * Records the check. A second confirmation of the same food by the same person is kept as
     * unconfirmed, so checking one pack twice never counts twice toward Pro.
     */
    suspend fun record(verification: FoodVerification): VerifyOutcome
}

/**
 * Whether a label's text shows a catalog food's numbers. The catalog keeps per 100 g; a label
 * may print per 100, per serving, or both, so each headline value is looked for per 100 and,
 * when the food has a serving in grams, per serving too. Rounded as a label would print them
 * (whole kcal, grams to a tenth), which [LabelGrounding] already allows for.
 */
object VerificationCheck {
    /** Energy, protein, carbs and fat: a food missing one can't be confirmed. */
    const val HEADLINE = 4

    fun ground(per100: Nutrients, servingGrams: Double?, lines: List<CaptureLine>): Grounding {
        val values = listOf(
            NutrientKey.Energy.wire to per100.kcal,
            NutrientKey.Protein.wire to per100.protein,
            NutrientKey.Carbs.wire to per100.carbs,
            NutrientKey.Fat.wire to per100.fat,
        )
        val missing = mutableListOf<String>()
        var groundable = 0
        for ((key, value) in values) {
            value ?: continue
            groundable++
            val tokens = LabelGrounding.tokensFor(key, lines)
            val forms = listOfNotNull(value, servingGrams?.takeIf { it > 0 }?.let { printed(value * it / 100.0) })
            if (forms.none { LabelGrounding.grounded(it, tokens, isEnergy = key == NutrientKey.Energy.wire) }) missing += key
        }
        return Grounding(groundable - missing.size, groundable, missing)
    }

    private fun printed(value: Double): Double = if (value >= 10) value.roundToLong().toDouble() else (value * 10).roundToLong() / 10.0
}
