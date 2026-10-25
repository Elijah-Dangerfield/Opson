package com.opson.libraries.config.impl.data

import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import kotlinx.serialization.Serializable
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@Serializable
data class ConfigCacheSnapshot(
    val configJson: String? = null,
    val overridesJson: String? = null
)

interface ConfigCache : Cache<ConfigCacheSnapshot>

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ConfigCache::class)
class ConfigCacheImpl @Inject constructor(
    cacheFactory: CacheFactory
) : ConfigCache,
    Cache<ConfigCacheSnapshot> by cacheFactory.persistent(
        name = "app_config_cache",
        serializer = versionedJsonSerializer(defaultValue = { ConfigCacheSnapshot() })
    )
