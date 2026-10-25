package com.opson.libraries.storage.impl.cache

import com.opson.libraries.storage.Cache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.updateAndGet

class InMemoryCache<T : Any>(
    private val initial: () -> T,
) : Cache<T> {
    private val state = MutableStateFlow(initial())

    override val updates = state

    override suspend fun get(): T = state.value

    override suspend fun set(value: T) { state.value = value }

    /**
     * Atomic, for the same reason the persistent cache overrides it.
     *
     * `updateAndGet` rather than update-then-read: a second read can see a
     * *later* writer's value, so the caller would be handed a result its own
     * transform never produced.
     */
    override suspend fun update(transform: (T) -> T): T = state.updateAndGet(transform)

    override suspend fun clear() {
        state.value = initial()
    }
}
