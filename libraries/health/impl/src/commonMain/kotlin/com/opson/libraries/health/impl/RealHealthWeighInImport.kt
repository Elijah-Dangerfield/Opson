package com.opson.libraries.health.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.health.HealthRecords
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.health.HealthWeighIn
import com.opson.libraries.health.HealthWeighInImport
import com.opson.libraries.opson.AppCache
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes

/**
 * [HealthWeighInImport]: reads the store's weigh-ins and adds the ones the log does not have
 * through [WeightRepository], so they sync like typed ones. Only ever while the person has it on
 * ([com.opson.libraries.opson.AppData.healthWeighIns]) and the store is connected. The ids of
 * those brought in are kept, so deleting one from the log is final.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class RealHealthWeighInImport(
    private val health: HealthRepository,
    private val records: HealthRecords,
    private val weights: WeightRepository,
    private val appCache: AppCache,
    private val clock: Clock,
) : HealthWeighInImport {

    private val logger = KLog.withTag("Health")

    /** One import at a time: turning it on while a foreground import runs must not add a weigh-in twice. */
    private val running = Mutex()

    override val on: Flow<Boolean> = appCache.updates.map { it.healthWeighIns }.distinctUntilChanged()

    override suspend fun turnOn(): Int? {
        appCache.update { it.copy(healthWeighIns = true) }
        logger.logEvent(EVENT_ON)
        return importNew()
    }

    override suspend fun turnOff() {
        appCache.update { it.copy(healthWeighIns = false) }
        logger.logEvent(EVENT_OFF)
    }

    override suspend fun importNew(): Int? = running.withLock {
        if (!appCache.get().healthWeighIns || health.status.value != HealthStatus.Connected) return@withLock null
        val samples = records.weighIns(since = clock.now() - LOOK_BACK) ?: return@withLock null
        val existing = weights.observeWeighIns(since = clock.now() - LOOK_BACK - WeighInMerge.SAME_WEIGH_IN).first()
        val fresh = WeighInMerge.newOnes(samples, existing, appCache.get().healthImportedWeighIns.toSet())
        fresh.forEach { weights.addWeighIn(it.kg, it.measuredAt) }
        if (fresh.isNotEmpty()) {
            appCache.update { data ->
                data.copy(healthImportedWeighIns = (data.healthImportedWeighIns + fresh.map { it.id }).distinct().takeLast(WeighInMerge.KEEP_IDS))
            }
        }
        fresh.size
    }

    companion object {
        /** How far back a first import reaches, and every later one looks for late arrivals. */
        val LOOK_BACK: Duration = 90.days

        const val EVENT_ON = "health.weigh_ins_on"
        const val EVENT_OFF = "health.weigh_ins_off"
    }
}

/** Which of the store's weigh-ins the log should gain. Pure, so the rules are tested on their own. */
object WeighInMerge {
    /** Two weigh-ins this close are one weigh-in typed twice: once in Opson, once on a scale or in another app. */
    val SAME_WEIGH_IN: Duration = 10.minutes

    /** A weight the log takes; anything else is a stray reading. */
    private val PLAUSIBLE_KG = 20.0..400.0

    /** Enough for several weigh-ins a day across [RealHealthWeighInImport.LOOK_BACK]. */
    const val KEEP_IDS = 1000

    /**
     * [samples] less any already brought in ([importedIds]), any within [SAME_WEIGH_IN] of a
     * weigh-in the log has, any implausible weight, and repeats within [SAME_WEIGH_IN] of each
     * other (a scale and its app writing the same reading), oldest first.
     */
    fun newOnes(samples: List<HealthWeighIn>, existing: List<WeighIn>, importedIds: Set<String>): List<HealthWeighIn> {
        val taken = existing.map { it.measuredAt }.toMutableList()
        return samples.sortedBy { it.measuredAt }.filter { sample ->
            val keep = sample.id !in importedIds &&
                sample.kg in PLAUSIBLE_KG &&
                taken.none { abs((it - sample.measuredAt).inWholeSeconds) < SAME_WEIGH_IN.inWholeSeconds }
            if (keep) taken += sample.measuredAt
            keep
        }
    }
}
