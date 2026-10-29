package com.opson.libraries.health.impl

import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthWeighInImport
import com.opson.libraries.opson.AppEvent
import com.opson.libraries.opson.AppEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Whether the app is in front, which is the only time the health store is read. Health Connect
 * refuses a read from the background without a permission Opson does not ask for, and a refused
 * read must never stand in for a real one; on iOS the app is suspended anyway. Starts false: the
 * process can start in the background (a widget, a reminder), and the first foreground arrives
 * as an event.
 */
@SingleIn(AppScope::class)
@Inject
class HealthAppForeground {
    private val mutable = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = mutable.asStateFlow()

    internal fun set(foreground: Boolean) {
        mutable.value = foreground
    }
}

/**
 * Tells [HealthAppForeground] about every foreground and background, and on each foreground with
 * `release.health` on re-checks the connection, so a change made in the Health app or Health
 * Connect shows on every screen that reads [HealthRepository.status] (Today's Activity card,
 * Resting energy, More), then brings in new weigh-ins when the person has that on. The read loops
 * re-read on the same edge (`RealHealthRepository`).
 *
 * The providers are lazy so joining the listener set, which is resolved at launch, builds
 * nothing (and the flag's config repository, which listens for app events too, isn't a cycle):
 * with the flag off the store is never touched.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = AppEventListener::class, multibinding = true)
@Inject
class HealthForegroundRefresh(
    private val appScope: AppCoroutineScope,
    private val foreground: HealthAppForeground,
    flagProvider: () -> HealthReleaseFlag,
    healthProvider: () -> HealthRepository,
    weighInsProvider: () -> HealthWeighInImport,
) : AppEventListener {

    // The flag reads remote config, whose repository listens for app events itself: a cycle if built here.
    private val flag by lazy(flagProvider)
    private val health by lazy(healthProvider)
    private val weighIns by lazy(weighInsProvider)

    override fun onForeground(event: AppEvent.OnForeground) {
        foreground.set(true)
        if (!flag()) return
        appScope.launch {
            health.refresh()
            weighIns.importNew()
        }
    }

    override fun onBackground(event: AppEvent.OnBackground) {
        foreground.set(false)
    }
}
