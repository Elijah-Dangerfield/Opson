package com.opson.libraries.identity.impl.auth

import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.CacheJsonSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking

/**
 * A [CacheFactory] whose persistent caches live in memory, keyed by name, so
 * two stores built from one factory see the same file the way they would
 * across a relaunch.
 */
internal class TestCacheFactory : CacheFactory {
    private val caches = mutableMapOf<String, Cache<*>>()

    override fun <T : Any> inMemory(defaultValue: () -> T): Cache<T> = MemoryCache(defaultValue)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> persistent(
        name: String,
        serializer: CacheJsonSerializer<T>,
        loadEagerly: Boolean,
    ): Cache<T> = caches.getOrPut(name) { MemoryCache { runBlocking { serializer.read(null) } } } as Cache<T>

    private class MemoryCache<T : Any>(private val initial: () -> T) : Cache<T> {
        private val state = MutableStateFlow(initial())
        override val updates: Flow<T> = state
        override suspend fun get(): T = state.value
        override suspend fun set(value: T) {
            state.value = value
        }
        override suspend fun clear() {
            state.value = initial()
        }
    }
}
