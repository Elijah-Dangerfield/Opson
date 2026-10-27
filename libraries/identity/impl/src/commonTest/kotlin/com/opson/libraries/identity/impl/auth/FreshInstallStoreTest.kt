package com.opson.libraries.identity.impl.auth

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.opson.AppCache
import com.opson.libraries.opson.AppData
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.CacheJsonSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FreshInstallStoreTest : CoroutineTest() {

    @Test
    fun newInstall_isFreshOnce() = runUnitTest {
        val store = FreshInstallStore(InMemoryCacheFactory(), FakeAppCache(onboarded = false))

        assertTrue(store.claim())
        assertFalse(store.claim(), "the next load is a relaunch")
    }

    @Test
    fun anInstallThatFinishedOnboarding_isNeverFresh() = runUnitTest {
        // Everyone updating from a build without the marker: their session must survive.
        val store = FreshInstallStore(InMemoryCacheFactory(), FakeAppCache(onboarded = true))

        assertFalse(store.claim())
    }

    private class FakeAppCache(onboarded: Boolean) : AppCache {
        private val state = MutableStateFlow(AppData(hasUserOnboarded = onboarded))
        override val updates: Flow<AppData> = state
        override suspend fun get(): AppData = state.value
        override suspend fun set(value: AppData) { state.value = value }
        override suspend fun clear() { state.value = AppData() }
    }

    private class InMemoryCacheFactory : CacheFactory {
        override fun <T : Any> inMemory(defaultValue: () -> T): Cache<T> = FakeCache(defaultValue)

        override fun <T : Any> persistent(
            name: String,
            serializer: CacheJsonSerializer<T>,
            loadEagerly: Boolean,
        ): Cache<T> = FakeCache { kotlinx.coroutines.runBlocking { serializer.read(null) } }
    }

    private class FakeCache<T : Any>(private val initial: () -> T) : Cache<T> {
        private val state = MutableStateFlow(initial())
        override val updates: Flow<T> = state
        override suspend fun get(): T = state.value
        override suspend fun set(value: T) { state.value = value }
        override suspend fun clear() { state.value = initial() }
    }
}
