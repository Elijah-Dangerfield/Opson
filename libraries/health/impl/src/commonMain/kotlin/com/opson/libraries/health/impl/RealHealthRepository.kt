package com.opson.libraries.health.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.EnergyExpended
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.opson.AppCache
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * [HealthRepository] over the platform's [HealthReader].
 *
 * The only thing kept is [com.opson.libraries.opson.AppData.healthConnected], the person's
 * choice. Values are read when a screen collects [energy] and held nowhere else. Telemetry
 * carries the source and nothing more.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class RealHealthRepository(
    private val reader: HealthReader,
    private val appCache: AppCache,
    private val clock: Clock,
) : HealthRepository {

    /** How often a collected [energy] re-reads. Exposed for tests. */
    internal var refreshEvery: Duration = REFRESH_EVERY

    private val logger = KLog.withTag("Health")
    private val mutableStatus = MutableStateFlow<HealthStatus>(HealthStatus.NotConnected)

    override val source: HealthSource get() = reader.source

    override val status: StateFlow<HealthStatus> = mutableStatus.asStateFlow()

    override suspend fun refresh() {
        mutableStatus.value = check()
    }

    override suspend fun connect(): ConnectOutcome {
        when (reader.availability()) {
            HealthAvailability.Unsupported -> {
                mutableStatus.value = HealthStatus.Unsupported
                return ConnectOutcome.Unsupported
            }
            HealthAvailability.NeedsInstall -> {
                mutableStatus.value = HealthStatus.NeedsInstall
                return ConnectOutcome.NeedsInstall
            }
            HealthAvailability.Available -> Unit
        }
        val granted = guarded("request access") { reader.requestAccess() } ?: false
        return if (granted) {
            appCache.update { it.copy(healthConnected = true) }
            mutableStatus.value = HealthStatus.Connected
            logger.logEvent(EVENT_CONNECTED, ATTR_SOURCE to source.key)
            ConnectOutcome.Connected
        } else {
            mutableStatus.value = HealthStatus.NotConnected
            logger.logEvent(EVENT_DENIED, ATTR_SOURCE to source.key)
            ConnectOutcome.Denied
        }
    }

    override suspend fun disconnect() {
        appCache.update { it.copy(healthConnected = false) }
        guarded("revoke access") { reader.revokeAccess() }
        mutableStatus.value = check()
        logger.logEvent(EVENT_DISCONNECTED, ATTR_SOURCE to source.key)
    }

    override fun energy(day: LocalDate, timeZone: TimeZone): Flow<EnergyExpended?> = flow {
        refresh()
        emitAll(
            mutableStatus.flatMapLatest { status ->
                if (status != HealthStatus.Connected) flowOf(null) else readEvery(day, timeZone)
            },
        )
    }

    override fun openSourceSettings(): Boolean = reader.openSettings()

    private fun readEvery(day: LocalDate, timeZone: TimeZone): Flow<EnergyExpended?> = flow {
        while (true) {
            emit(read(day, timeZone))
            delay(refreshEvery)
        }
    }

    /** Midnight to now for today, midnight to midnight for a past day, nothing for a future one. */
    private suspend fun read(day: LocalDate, timeZone: TimeZone): EnergyExpended? {
        val from = day.atStartOfDayIn(timeZone)
        val to = minOf(day.plus(DatePeriod(days = 1)).atStartOfDayIn(timeZone), clock.now())
        if (to <= from) return null
        return guarded("read energy") { reader.read(from, to) }
    }

    private suspend fun check(): HealthStatus = when (guarded("availability") { reader.availability() }) {
        HealthAvailability.Unsupported, null -> HealthStatus.Unsupported
        HealthAvailability.NeedsInstall -> HealthStatus.NeedsInstall
        HealthAvailability.Available -> when {
            !appCache.get().healthConnected -> HealthStatus.NotConnected
            // Revoked in Health Connect's own screen since: the choice stands, access does not.
            guarded("check access") { reader.hasAccess() } == false -> HealthStatus.NotConnected
            else -> HealthStatus.Connected
        }
    }

    /** A store that throws is logged at warn, never crashes a screen, and never reads as zero. */
    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> guarded(what: String, block: suspend () -> T): T? = try {
        block()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        logger.w(e, "Health store: $what failed (${source.key})")
        null
    }

    companion object {
        val REFRESH_EVERY: Duration = 5.minutes

        const val EVENT_CONNECTED = "health.connected"
        const val EVENT_DISCONNECTED = "health.disconnected"
        const val EVENT_DENIED = "health.permission_denied"
        const val ATTR_SOURCE = "source"
    }
}
