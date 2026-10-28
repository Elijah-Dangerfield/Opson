package com.opson.features.logger.impl

import com.opson.libraries.foods.BarcodeLinkReporter
import com.opson.libraries.foods.FoodRef
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * A barcode nothing knew, followed soon after by the person finding the food
 * by searching: that pairing is the best evidence there is that the catalog
 * food is the product, so it is reported for a reviewer to attach
 * (`barcode_link_suggestions`, V25). Never attached here.
 *
 * Only the last miss is held, in memory, for [WINDOW]: a process death or a
 * new session forgets it, which is what "the same session" means here. Only
 * the code and the food go to the server, never what was typed into search.
 */
interface MissedBarcodePairing {
    /** The scanner found nothing for [code]. */
    fun missed(code: String)

    /** [picks] were just logged. A catalog food picked from search within [WINDOW] of a miss is reported, once. */
    suspend fun logged(picks: List<LogPick>)

    /** For tests and anywhere pairing does not apply. */
    object Off : MissedBarcodePairing {
        override fun missed(code: String) = Unit
        override suspend fun logged(picks: List<LogPick>) = Unit
    }

    companion object {
        /** Long enough to back out of the scanner, search and pick; short enough that it is still that pack. */
        val WINDOW: Duration = 10.minutes
    }
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class RecentMissedBarcodePairing(
    private val reporter: BarcodeLinkReporter,
    private val clock: Clock,
) : MissedBarcodePairing {

    private class Miss(val code: String, val at: Instant)

    private var last: Miss? = null

    override fun missed(code: String) {
        last = Miss(code, clock.now())
    }

    override suspend fun logged(picks: List<LogPick>) {
        val miss = last ?: return
        if (clock.now() - miss.at > MissedBarcodePairing.WINDOW) {
            last = null
            return
        }
        val food = picks.firstNotNullOfOrNull { pick ->
            (pick.ref as? FoodRef.Catalog)?.takeIf { pick.source == PickSource.Search }
        } ?: return
        last = null
        reporter.report(miss.code, food.id)
    }
}
