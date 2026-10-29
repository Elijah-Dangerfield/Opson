package com.opson.features.today.impl.celebration

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.diary.CelebrationStyle
import com.opson.libraries.diary.CelebrationsReleaseFlag
import com.opson.libraries.diary.DayMarks
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.Milestone
import com.opson.libraries.diary.MinuteTicker
import com.opson.libraries.diary.Progress
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.ShowingUp
import com.opson.libraries.opson.UserScopedClearer
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** Where someone stands and how celebrations speak to them; only while they are on. */
data class Celebrating(val progress: Progress, val style: CelebrationStyle)

/**
 * The run, the milestones and the style, for Today's streak, Your progress and Day close. Null
 * while `release.celebrations` is off or the person turned celebrations off, so every surface
 * that reads it simply draws nothing then.
 */
interface CelebrationsSource {
    fun observe(): Flow<Celebrating?>

    /** The milestone waiting to be celebrated on this phone, or null: reached lately and not shown here yet. */
    suspend fun pending(): Milestone?

    companion object {
        /** For tests and previews, and a default where a constructor must not grow a required argument. */
        val None: CelebrationsSource = object : CelebrationsSource {
            override fun observe(): Flow<Celebrating?> = flowOf(null)

            override suspend fun pending(): Milestone? = null
        }
    }
}

/**
 * [ShowingUp.compute] over every [DayMarks] date, recomputed when a day changes and at midnight
 * (the minute ticker's date). The flag is read once per subscription, like every release flag.
 */
@ContributesBinding(AppScope::class)
@Inject
class ShowingUpCelebrationsSource(
    private val flag: CelebrationsReleaseFlag,
    private val settings: SettingsRepository,
    private val marks: DayMarks,
    private val calendar: LocalCalendar,
    private val ticker: MinuteTicker,
    private val store: CelebrationStore,
) : CelebrationsSource {
    override fun observe(): Flow<Celebrating?> {
        if (!flag()) return flowOf(null)
        val today = ticker.ticks().map { calendar.today() }.distinctUntilChanged()
        val style = settings.settings.map { it.celebrationStyle }.distinctUntilChanged()
        return combine(marks.observe(), today, style) { marks, date, style ->
            if (style == CelebrationStyle.Off) null else Celebrating(ShowingUp.compute(marks, date), style)
        }.distinctUntilChanged()
    }

    override suspend fun pending(): Milestone? = observe().first()?.progress?.toCelebrate(store.celebrated())
}

/** Which milestones this phone has celebrated, and whether the way to turn them off was offered. */
@Serializable
data class CelebrationLedger(
    val celebrated: List<String> = emptyList(),
    /** The first celebration offers "I'd rather not see these"; later ones don't. */
    val offeredTurnOff: Boolean = false,
)

/**
 * The celebrations this phone has shown, kept on the phone and never synced: a second phone may
 * celebrate a fresh milestone once more, which costs nothing, and a milestone older than
 * [ShowingUp.FRESH_DAYS] is never celebrated anywhere. A user change wipes it.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = UserScopedClearer::class, multibinding = true)
@Inject
class CelebrationStore(cacheFactory: CacheFactory) : UserScopedClearer {
    private val cache: Cache<CelebrationLedger> = cacheFactory.persistent(
        name = "celebrations",
        serializer = versionedJsonSerializer(defaultValue = { CelebrationLedger() }),
    )

    suspend fun celebrated(): Set<Milestone> = cache.get().celebrated.mapNotNullTo(mutableSetOf(), Milestone::fromWire)

    suspend fun offeredTurnOff(): Boolean = cache.get().offeredTurnOff

    /** Writes [shown] down, with the lower milestones it covers, before it is drawn. */
    suspend fun shown(shown: Milestone) {
        cache.update { ledger ->
            val wires = (ledger.celebrated + ShowingUp.coveredBy(shown).map { it.wire }).distinct()
            ledger.copy(celebrated = wires, offeredTurnOff = true)
        }
    }

    override suspend fun clear(previousUserId: String) {
        cache.clear()
    }
}

/** Celebrations' app events (`docs/practices/app-events.md`, Celebrations). Ids and styles only. */
interface CelebrationTelemetry {
    fun shown(milestone: Milestone, style: CelebrationStyle, offeredTurnOff: Boolean)

    fun answered(milestone: Milestone, answer: CelebrationAnswer)

    fun progressOpened(style: CelebrationStyle)

    companion object {
        val None: CelebrationTelemetry = object : CelebrationTelemetry {
            override fun shown(milestone: Milestone, style: CelebrationStyle, offeredTurnOff: Boolean) = Unit

            override fun answered(milestone: Milestone, answer: CelebrationAnswer) = Unit

            override fun progressOpened(style: CelebrationStyle) = Unit
        }
    }
}

/** How a celebration was left. [wire] is the `answer` attribute. */
enum class CelebrationAnswer(val wire: String) {
    CanDoThis("can_do_this"),
    TurnedOff("turned_off"),
    Back("back"),
}

val CelebrationStyle.wire: String
    get() = when (this) {
        CelebrationStyle.Counts -> "counts"
        CelebrationStyle.Gentle -> "gentle"
        CelebrationStyle.Off -> "off"
    }

@ContributesBinding(AppScope::class)
@Inject
class AppEventsCelebrationTelemetry : CelebrationTelemetry {
    private val logger = KLog.withTag("Celebrations")

    override fun shown(milestone: Milestone, style: CelebrationStyle, offeredTurnOff: Boolean) {
        logger.logEvent("celebration.shown", "milestone" to milestone.wire, "style" to style.wire, "offered_turn_off" to offeredTurnOff)
    }

    override fun answered(milestone: Milestone, answer: CelebrationAnswer) {
        logger.logEvent("celebration.answered", "milestone" to milestone.wire, "answer" to answer.wire)
    }

    override fun progressOpened(style: CelebrationStyle) {
        logger.logEvent("celebration.progress_opened", "style" to style.wire)
    }
}
