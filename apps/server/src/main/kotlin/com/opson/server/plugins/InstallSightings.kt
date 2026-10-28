package com.opson.server.plugins

import com.opson.server.domain.InstallSighting
import com.opson.server.domain.UserInstallRepository
import com.opson.server.http.clientContext
import io.ktor.server.application.createRouteScopedPlugin
import io.ktor.server.application.install
import io.ktor.server.routing.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

/**
 * Records which build each signed-in install runs, into `user_installs` (V22): the version
 * history the admin lookup shows and the install ids the User detail board finds someone's app
 * events by.
 *
 * Every signed-in request says who and what it is, so the recording rides on all of them rather
 * than on one route: 0.1 included, which sends the same headers and was never changed. It is
 * written after the response, off the request, and at most once per [interval] for the same
 * user, install and build, so a sync burst costs one upsert. A failed write is logged and dropped;
 * the next sighting after [interval] tries again.
 */
@OptIn(ExperimentalTime::class)
class InstallSightingsConfig {
    var repository: UserInstallRepository? = null
    var clock: Clock = Clock.System
    var interval: Duration = 10.minutes
}

@OptIn(ExperimentalTime::class)
val InstallSightings = createRouteScopedPlugin("InstallSightings", ::InstallSightingsConfig) {
    val repository = pluginConfig.repository ?: return@createRouteScopedPlugin
    val clock = pluginConfig.clock
    val interval = pluginConfig.interval
    val lastWritten = ConcurrentHashMap<String, Long>()
    val logger = LoggerFactory.getLogger("InstallSightings")

    onCallRespond { call, _ ->
        val userId = call.userId() ?: return@onCallRespond
        val context = call.clientContext()
        val sighting = InstallSighting(
            userId = userId,
            installId = context.installId,
            platform = context.platform.wire,
            appVersion = context.appVersion,
            buildNumber = context.buildNumber,
        )
        val key = "${sighting.userId}|${sighting.installId}|${sighting.appVersion}|${sighting.buildNumber}"
        val now = clock.now()
        val previous = lastWritten[key]
        if (previous != null && now.toEpochMilliseconds() - previous < interval.inWholeMilliseconds) return@onCallRespond
        if (lastWritten.size > MAX_REMEMBERED) lastWritten.clear()
        lastWritten[key] = now.toEpochMilliseconds()

        call.application.launch(Dispatchers.IO) {
            try {
                repository.record(sighting, now)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                lastWritten.remove(key)
                logger.warn("install sighting not recorded ({})", e::class.simpleName)
            }
        }
    }
}

/** Installs the recorder on [this] route and everything under it. */
@OptIn(ExperimentalTime::class)
fun Route.recordInstallSightings(repository: UserInstallRepository, clock: Clock) {
    install(InstallSightings) {
        this.repository = repository
        this.clock = clock
    }
}

/** Remembered sightings before the throttle forgets them all and starts again. */
private const val MAX_REMEMBERED = 100_000