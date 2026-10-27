package com.opson.libraries.widgets.impl

import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.GoalsAndEnergyEnabled
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.opson.AppEvent
import com.opson.libraries.opson.AppEvents
import com.opson.libraries.widgets.WidgetCopy
import com.opson.libraries.widgets.WidgetInputs
import com.opson.libraries.widgets.WidgetSnapshot
import com.opson.libraries.widgets.WidgetSnapshotBuilder
import com.opson.libraries.widgets.WidgetSnapshotSource
import com.opson.libraries.widgets.WidgetStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

/**
 * Today's widget snapshot, rebuilt whenever an input moves: today's day (a log, an "ate, no
 * details", untracked), the settings (mode, targets, meal groups) once they are the
 * user's own, the policy, and the date (each local midnight, and every foreground in case the
 * clock or zone moved while the app slept). Equal snapshots are dropped, so a widget only reloads
 * when what it shows changed.
 *
 * Cold: nothing is read until someone collects, and only [WidgetRefresher] with `release.widgets`
 * on, or the Widgets page, ever does.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = WidgetSnapshotSource::class)
@Inject
class RealWidgetSnapshotSource(
    private val settingsRepository: SettingsRepository,
    private val diary: DiaryRepository,
    private val policies: VisibilityPolicySource,
    private val calendar: LocalCalendar,
    private val clock: Clock,
    private val appEvents: AppEvents,
    private val strings: WidgetStrings,
    private val burnedRepository: BurnedRepository,
    private val goalsAndEnergy: GoalsAndEnergyEnabled,
) : WidgetSnapshotSource {

    override fun observe(): Flow<WidgetSnapshot> = flow {
        val copy = WidgetCopy.resolve(strings)
        emitAll(
            combine(knownSettings(), today().flatMapLatest(diary::observeDay), policies.policy, burned()) { settings, day, policy, burned ->
                WidgetSnapshotBuilder.build(WidgetInputs(today = day, settings = settings, burnedKcal = burned?.totalKcal), policy, copy)
            },
        )
    }.distinctUntilChanged()

    /** Today's Burned, what a set deficit or surplus aims at. With `release.goalsAndEnergy` off nothing reads it. */
    private fun burned(): Flow<Burned?> =
        if (goalsAndEnergy()) today().flatMapLatest(burnedRepository::burned) else flowOf(null)

    /** The user's own settings: before the first read, the defaults would show the wrong mode. */
    private fun knownSettings(): Flow<Settings> =
        combine(settingsRepository.settings, settingsRepository.isKnown) { settings, known -> settings.takeIf { known } }
            .filterNotNull()

    private fun today(): Flow<LocalDate> =
        merge(appEvents.live().filterIsInstance<AppEvent.OnForeground>().map { }, midnights())
            .onStart { emit(Unit) }
            .map { calendar.today() }
            .distinctUntilChanged()

    /** Emits at each local midnight, so today's snapshot rolls over while the app is open. */
    private fun midnights(): Flow<Unit> = flow {
        while (true) {
            val next = calendar.today().plus(DatePeriod(days = 1)).atStartOfDayIn(calendar.timeZone())
            delay((next - clock.now()).coerceAtLeast(1.seconds))
            emit(Unit)
        }
    }
}
