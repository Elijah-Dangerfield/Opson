package com.opson.libraries.foods

import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.Serializable
import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * One food this person chose from search results for one query. [weight] is what the picks were
 * worth at [lastAtEpochMs]; it halves every [LearnedPicks.HALF_LIFE] after that.
 */
@Serializable
data class SearchPick(val query: String, val foodKey: String, val weight: Double, val lastAtEpochMs: Long)

/**
 * What someone keeps choosing for the words they type, learned on their phone: pick the fourth
 * result for `yogurt` twice and it comes first for them. Bounded, decayed, and never sent
 * anywhere. Pure, so the ranking is testable without a store.
 */
@Serializable
data class LearnedPicks(val picks: List<SearchPick> = emptyList()) {

    /**
     * How strongly [foodKey] is this person's answer to [query] at [now]: the decayed weight of
     * picks for the same words, plus half of picks for a query that one of them starts
     * ("straw" and "strawberry").
     */
    fun score(query: String, foodKey: String, now: Instant): Double {
        val typed = FoodQuery.normalize(query)
        if (typed.length < MIN_QUERY) return 0.0
        return picks.sumOf { pick ->
            if (pick.foodKey != foodKey) return@sumOf 0.0
            val relation = when {
                pick.query == typed -> 1.0
                related(pick.query, typed) -> RELATED
                else -> 0.0
            }
            if (relation == 0.0) 0.0 else relation * pick.decayed(now)
        }
    }

    /** [foodKey] chosen for [query] at [now], worth [weight]; the oldest and weakest go past the bounds. */
    fun picked(query: String, foodKey: String, now: Instant, weight: Double = 1.0): LearnedPicks {
        val typed = FoodQuery.normalize(query)
        if (typed.length < MIN_QUERY) return this
        val nowMs = now.toEpochMilliseconds()
        val earlier = picks.firstOrNull { it.query == typed && it.foodKey == foodKey }
        val updated = SearchPick(typed, foodKey, (earlier?.decayed(now) ?: 0.0) + weight, nowMs)
        val others = picks.filterNot { it.query == typed && it.foodKey == foodKey }
        val sameQuery = others.filter { it.query == typed }.sortedByDescending { it.decayed(now) }
        val dropped = sameQuery.drop(MAX_PER_QUERY - 1).toSet()
        val kept = (listOf(updated) + (others - dropped))
            .filter { it.decayed(now) >= FORGOTTEN }
            .sortedByDescending { it.decayed(now) }
            .take(MAX_PICKS)
        return LearnedPicks(kept)
    }

    private fun SearchPick.decayed(now: Instant): Double {
        val age = (now.toEpochMilliseconds() - lastAtEpochMs).coerceAtLeast(0)
        return weight * 0.5.pow(age.toDouble() / HALF_LIFE.inWholeMilliseconds)
    }

    private fun related(a: String, b: String): Boolean =
        minOf(a.length, b.length) >= MIN_QUERY && (a.startsWith(b) || b.startsWith(a))

    companion object {
        val HALF_LIFE: Duration = 30.days

        /** Two letters are a word on its way; nothing is learned for them. */
        const val MIN_QUERY = 3
        const val RELATED = 0.5
        const val MAX_PICKS = 500
        const val MAX_PER_QUERY = 8

        /** A pick decayed below this (a single one, five half-lives ago) is dropped. */
        const val FORGOTTEN = 0.03
    }
}

/**
 * [LearnedPicks] for the signed-in person, on this phone only: never synced, never in a log or a
 * report, and wiped when someone else signs in. A food chosen from results is recorded with the
 * words it was found by.
 */
interface SearchPicks {
    val learned: StateFlow<LearnedPicks>

    /** [foodKey] (a `FoodRef.key`) chosen from the results for [query]. */
    suspend fun picked(query: String, foodKey: String, weight: Double = 1.0)
}
