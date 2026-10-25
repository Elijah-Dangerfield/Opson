package com.opson.libraries.config.impl.repository

import com.opson.libraries.opson.AppEvent
import com.opson.libraries.opson.AppEvents
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.AppConfigRepository
import com.opson.libraries.config.ConfigOverrideRepository
import com.opson.libraries.config.impl.ConfigRefreshThrottleMs
import com.opson.libraries.config.impl.applyOverrides
import com.opson.libraries.config.impl.data.ConfigCache
import com.opson.libraries.config.impl.data.RemoteConfigDataSource
import com.opson.libraries.config.impl.model.BasicMapAppConfig
import com.opson.libraries.config.impl.model.FallbackConfigMap
import com.opson.libraries.config.impl.serialization.ConfigJsonConverter
import com.opson.libraries.core.AutoInit
import com.opson.libraries.core.ignoreValue
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.DispatcherProvider
import com.opson.libraries.flowroutines.childSupervisorScope
import com.opson.libraries.flowroutines.tryWithTimeout
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.Duration.Companion.seconds

private val ConfigRefreshTimeout = 5.seconds

/**
 * Disk-backed, **throttled-foreground** app config.
 *
 * Config carries the time-sensitive levers (kill switch, maintenance, forced
 * upgrade), so unlike the other session-aware caches it refreshes on **every app
 * foreground** — gated by [ConfigRefreshThrottleMs] so a quick app-switch doesn't
 * refetch. The throttle is itself a config value (meta, but it works): the
 * in-code default is 5 minutes and the dev database sets it to 0 so flag changes
 * show up on the next foreground while testing. Cold boot always fetches (no prior
 * fetch this process, and [AppEvent.OnForeground] fires on cold boot too). A user
 * who returns after the throttle window gets fresh flags instead of waiting for a
 * session rollover.
 *
 * Note this is a *transition* trigger: a user who keeps the app foregrounded
 * uninterrupted never re-fetches mid-session — that would need polling, which we
 * deliberately don't do. The first frame never blocks on the network: the cached
 * snapshot survives launches, and [cachedConfigFlow] starts from the bundled
 * fallback when there isn't one.
 *
 * Failure path: if the network fetch fails AND there's no prior cached snapshot,
 * the bundled fallback config is persisted so a later process starts from disk
 * rather than re-deciding. With a prior snapshot, the failure is logged and the
 * prior snapshot stays in place — the next foreground past the throttle tries
 * again.
 */
@ContributesBinding(AppScope::class, boundType = AppConfigRepository::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@SingleIn(AppScope::class)
class OfflineFirstAppConfigRepository @Inject constructor(
    private val dispatcherProvider: DispatcherProvider,
    private val remoteConfigDataSource: RemoteConfigDataSource,
    private val configCache: ConfigCache,
    private val converter: ConfigJsonConverter,
    private val fallbackConfig: FallbackConfigMap,
    private val configOverrideRepository: ConfigOverrideRepository,
    private val appEvents: AppEvents,
    private val clock: Clock,
    private val appScope: AppCoroutineScope,
) : AppConfigRepository, AutoInit {

    private val logger = KLog.withTag("AppConfigRepository")
    private var refreshJob: Job? = null
    private val refreshJobMutex = Mutex()
    private var lastFetchAtMs: Long? = null

    /**
     * The current config, starting from the bundled fallback rather than from
     * nothing.
     *
     * This used to be `mapNotNull`, which meant a device with no cached snapshot
     * emitted **nothing at all** until a fetch either succeeded or failed hard
     * enough to persist the fallback. [com.opson.libraries.config.EnsureAppConfigLoaded]
     * awaits `configStream().first()`, so on a fresh install the first frame
     * waited out the entire retry chain against a config server a brand-new
     * project has not deployed yet — measured downstream at 10 seconds cold,
     * 3.3 after this change.
     *
     * This file's own KDoc already promised the first frame never blocks on the
     * network. It was true from the second launch onwards, which is why it
     * survived: on a dev machine you launch the app twice and the second one is
     * fine.
     *
     * A corrupt snapshot takes the same path: [decodeConfig] returns null, and
     * an unreadable file is exactly as good a reason to start from the fallback
     * as an absent one.
     */
    private val cachedConfigFlow = configCache.updates
        .map { snapshot -> snapshot.configJson?.let(::decodeConfig) ?: fallbackConfig }

    private val configStream: SharedFlow<AppConfigMap> = combine(
        configOverrideRepository.getOverridesFlow(),
        cachedConfigFlow,
    ) { overrides, config -> config.applyOverrides(overrides) }
        .distinctUntilChanged()
        .onEach { logger.d { "Config emitted" } }
        .shareIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            replay = 1,
        )

    // The refresh throttle is itself a config value, read from this repo's own
    // current snapshot. Constructed (not injected) against [config] to avoid the
    // cycle a ConfiguredValue → AppConfigMap → AppConfigRepository injection
    // would create. Resolves live: each read sees the latest merged config.
    private val refreshThrottleMs = ConfigRefreshThrottleMs(config())

    init {
        observeForegroundForRefresh()
    }

    override fun config(): AppConfigMap = LazyAppConfigMap()

    override fun configStream(): Flow<AppConfigMap> = configStream

    private fun observeForegroundForRefresh() {
        appEvents
            .filterIsInstance<AppEvent.OnForeground>()
            .onEach { maybeRefreshOnForeground() }
            .launchIn(appScope.childSupervisorScope(dispatcherProvider.io))
    }

    /**
     * Refresh on app foreground, at most once per [ConfigRefreshThrottleMs]. The
     * timestamp is stamped at the *attempt*, so a failed fetch doesn't retry on
     * every rapid resume — the next foreground past the window does. A throttle of
     * 0 (the dev default) means every foreground refetches.
     */
    private suspend fun maybeRefreshOnForeground() {
        val now = clock.now().toEpochMilliseconds()
        val last = lastFetchAtMs
        if (last != null && now - last < refreshThrottleMs.value) {
            logger.d { "Foreground within throttle window — skipping config refresh" }
            return
        }
        lastFetchAtMs = now
        refreshConfig().join()
    }

    private suspend fun refreshConfig(): Job = refreshJobMutex.withLock {
        val currentJob = refreshJob
        if (currentJob != null && currentJob.isActive) {
            currentJob
        } else {
            appScope.childSupervisorScope(dispatcherProvider.io).launch {
                tryWithTimeout(ConfigRefreshTimeout) {
                    remoteConfigDataSource.getConfig()
                }
                    .onSuccess { config ->
                        logger.d { "Config refresh succeeded" }
                        persistConfig(config)
                    }
                    .onFailure { throwable ->
                        if (!hasCachedConfig()) {
                            logger.w(throwable) { "No cached config available, using fallback" }
                            persistConfig(fallbackConfig)
                        } else {
                            logger.w(throwable) { "Falling back to cached config" }
                        }
                    }
                    .ignoreValue()
            }.also { job -> refreshJob = job }
        }
    }

    private suspend fun persistConfig(config: AppConfigMap) {
        converter.encodeMap(config.map)
            .onSuccess { json ->
                configCache.update { snapshot -> snapshot.copy(configJson = json) }
            }
            .onFailure { error ->
                logger.e(error) { "Unable to persist config" }
            }
    }

    private suspend fun hasCachedConfig(): Boolean = configCache.get().configJson != null

    private fun decodeConfig(raw: String): AppConfigMap? =
        converter.decodeToMap(raw)
            .onFailure { error -> logger.e(error) { "Unable to decode cached config" } }
            .getOrNull()
            ?.let { BasicMapAppConfig(it) }

    private inner class LazyAppConfigMap : AppConfigMap() {
        // Synchronous + non-blocking by design. A `ConfiguredValue` read can land
        // on the main thread (e.g. `progressionConfig.levelCurve()` during
        // composition), so this must never block: on a cold/fresh start the
        // stream has not emitted yet (it is one dispatch away, not zero), and
        // `runBlocking { configStream.first() }` here deadlocks the app — the
        // main thread parks while the K/N worker pool is full of the same
        // blocking read, so nothing can produce the config (scene-create
        // watchdog → SIGKILL). Return the latest emitted config if present, else
        // the shipped [fallbackConfig]; every `ConfiguredValue` already falls
        // back to its own default for any missing key, and reactive readers
        // (App.kt `remember(config)`) recompose once the real config arrives.
        override val map: Map<String, *>
            get() = configStream.replayCache.firstOrNull()?.map ?: fallbackConfig.map
    }
}
