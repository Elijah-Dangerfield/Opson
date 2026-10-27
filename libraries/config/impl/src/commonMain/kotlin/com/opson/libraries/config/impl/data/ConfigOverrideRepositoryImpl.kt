package com.opson.libraries.config.impl.data

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.config.ConfigOverride
import com.opson.libraries.config.ConfigOverrideRepository
import com.opson.libraries.config.impl.serialization.ConfigJsonConverter
import com.opson.libraries.flowroutines.AppCoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class ConfigOverrideRepositoryImpl @Inject constructor(
    private val configCache: ConfigCache,
    private val converter: ConfigJsonConverter,
    appScope: AppCoroutineScope
) : ConfigOverrideRepository {

    private val logger = KLog.withTag("ConfigOverrideRepository")
    private val mutex = Mutex()

    // Null until the cache has been read. This class is built at app start (the
    // config repository is AutoInit), usually on the main thread, so it must not
    // read the cache while being constructed. Consumers wait for the first real
    // value instead, which arrives with the same disk read the config needs.
    private val overridesState = configCache.updates
        .map { snapshot -> decodeOverrides(snapshot.overridesJson) }
        .stateIn(
            scope = appScope,
            started = SharingStarted.Eagerly,
            initialValue = null,
        )

    override fun getOverridesFlow(): Flow<List<ConfigOverride<Any>>> = overridesState.filterNotNull()

    override suspend fun addOverride(override: ConfigOverride<Any>) {
        mutex.withLock {
            configCache.update { snapshot ->
                // Replace any existing override for the same path so toggling a
                // setting back doesn't pile up entries.
                val updated = decodeOverrides(snapshot.overridesJson).filter { it.path != override.path } + override
                converter.encodeOverrides(updated).fold(
                    onSuccess = { json ->
                        logger.d { "Persisting ${updated.size} overrides" }
                        snapshot.copy(overridesJson = json)
                    },
                    onFailure = { error ->
                        logger.e(error) { "Unable to persist overrides" }
                        snapshot
                    },
                )
            }
        }
    }

    override suspend fun clearAll() {
        mutex.withLock {
            logger.d { "Clearing all overrides" }
            configCache.update { snapshot -> snapshot.copy(overridesJson = null) }
        }
    }

    private fun decodeOverrides(raw: String?): List<ConfigOverride<Any>> {
        if (raw.isNullOrBlank()) return emptyList()
        return converter.decodeOverrides(raw)
            .onFailure { error -> logger.e(error) { "Unable to decode overrides" } }
            .getOrNull()
            ?.toList()
            ?: emptyList()
    }
}
