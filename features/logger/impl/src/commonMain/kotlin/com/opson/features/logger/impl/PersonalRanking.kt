package com.opson.features.logger.impl

import com.opson.libraries.diary.LoggedFood
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.LearnedPicks
import kotlin.math.ln
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/** Why a food sits where it does in the results, said on its row: one reason, the strongest. */
sealed interface RowReason {
    data object Favorite : RowReason

    /** "Logged 3 times", or "Logged once". */
    data class Logged(val times: Int) : RowReason

    /** The person's own food, where no "Your food" badge says so already. */
    data object YourFood : RowReason
}

/** What the person's own use says about one food for one query. */
data class PersonalSignal(
    /** [LearnedPicks.score]: how often they chose it for these words lately. */
    val learned: Double = 0.0,
    val times: Int = 0,
    val lastLoggedAt: Instant? = null,
    val favorite: Boolean = false,
    val own: Boolean = false,
) {
    /**
     * Whether it goes above the catalog's order: a favorite, their own food, a food chosen for
     * these words twice, or one logged twice or in the last two weeks. One log long ago is not
     * enough to push the obvious food down.
     */
    fun promoted(now: Instant): Boolean =
        favorite || own || learned >= PersonalRanking.LEARNED_TO_PROMOTE || times >= 2 ||
            (times >= 1 && lastLoggedAt != null && now - lastLoggedAt <= PersonalRanking.RECENT)

    /** Among promoted foods: chosen for these words first, then favorites, then the most and latest logged. */
    fun score(now: Instant): Double {
        val age = lastLoggedAt?.let { now - it }
        val recency = when {
            age == null -> 0.0
            age <= 7.days -> 1.0
            age <= 30.days -> 0.5
            else -> 0.0
        }
        return learned * LEARNED_WEIGHT + (if (favorite) FAVORITE_WEIGHT else 0.0) + (if (own) 1.0 else 0.0) +
            ln(1.0 + times) + recency
    }

    private companion object {
        const val LEARNED_WEIGHT = 4.0
        const val FAVORITE_WEIGHT = 3.0
    }
}

/**
 * Search results in the person's order: their own foods first when their use says so
 * ([PersonalSignal.promoted]), best first, then everything else in the order it came, which is
 * the server's ranking. Everything here is on the phone; the server never hears what was chosen.
 */
internal class PersonalRanking(
    private val query: String,
    private val learned: LearnedPicks,
    private val logged: Map<String, LoggedFood>,
    private val favorites: Set<String>,
    private val now: Instant,
) {
    fun signal(pick: FoodPick): PersonalSignal {
        val use = logged[pick.key]
        return PersonalSignal(
            learned = learned.score(query, pick.key, now),
            times = use?.times ?: 0,
            lastLoggedAt = use?.lastLoggedAt,
            favorite = pick.key in favorites,
            own = pick.ref is FoodRef.Custom,
        )
    }

    fun order(picks: List<FoodPick>): List<FoodPick> {
        val signals = picks.map(::signal)
        val (promoted, rest) = picks.indices.partition { signals[it].promoted(now) }
        val first = promoted.sortedWith(compareByDescending<Int> { signals[it].score(now) }.thenBy { it })
        return (first + rest).map { picks[it] }
    }

    /** The hint on [pick]'s row; [badged] when a "Your food" badge already marks the person's own foods. */
    fun reason(pick: FoodPick, badged: Boolean): RowReason? {
        val signal = signal(pick)
        return when {
            signal.favorite -> RowReason.Favorite
            signal.times > 0 -> RowReason.Logged(signal.times)
            signal.own && !badged -> RowReason.YourFood
            else -> null
        }
    }

    companion object {
        /** Two picks for the same words within about a month. */
        const val LEARNED_TO_PROMOTE = 1.5
        val RECENT = 14.days
    }
}
