package com.opson.libraries.review.impl

import com.opson.libraries.core.AppState
import com.opson.libraries.core.AutoInit
import com.opson.libraries.core.logging.EXTRA_APP_EVENT
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.LogEntry
import com.opson.libraries.core.logging.LogId
import com.opson.libraries.core.logging.LogLevel
import com.opson.libraries.core.logging.LogTree
import com.opson.libraries.flowroutines.AppCoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * When something last went wrong that the person may have seen, so a rating is never asked for
 * right after it. Planted as a log tree at boot: it notes any line logged at error, and the app
 * events that mean a step failed ([isTrouble]). Going offline counts too.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class RecentTrouble(
    private val clock: Clock,
    appState: AppState,
    appScope: AppCoroutineScope,
) : AutoInit {

    @Volatile
    private var lastAt: Instant? = null

    private val tree = object : LogTree() {
        override fun isLoggable(level: LogLevel, tag: String?): Boolean = level >= LogLevel.Info

        override fun log(entry: LogEntry): LogId? {
            if (isTrouble(entry)) note()
            return null
        }
    }

    init {
        KLog.plant(tree)
        appState.isOffline.filter { it }.onEach { note() }.launchIn(appScope)
    }

    /** Something went wrong now. */
    fun note() {
        lastAt = clock.now()
    }

    /** True when something went wrong in the last [WINDOW]. */
    fun recent(): Boolean = lastAt?.let { clock.now() - it < WINDOW } == true

    companion object {
        val WINDOW: Duration = 30.minutes

        /** App events that mean a step the person took failed or came up empty. */
        private val TROUBLE_EVENTS = setOf(
            "net.backend_unreachable",
            "photo.failed",
            "barcode.miss",
            "onboarding.sign_in_failed",
        )

        internal fun isTrouble(entry: LogEntry): Boolean =
            entry.level >= LogLevel.Error || entry.context.extras[EXTRA_APP_EVENT] in TROUBLE_EVENTS
    }
}
