package com.opson.libraries.storage.impl.cache

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryCacheTest {

    /**
     * Real threads, and enough rounds to make the window certain to be hit.
     * Nothing in `InMemoryCache` suspends, so a read-then-write only loses a
     * write when two writers are genuinely running at once. A test dispatcher
     * would make the racy version look correct.
     */
    @Test
    fun concurrentWritersToDifferentFieldsBothSurvive() = runTest {
        val cache = InMemoryCache { Tally() }

        withContext(Dispatchers.Default) {
            listOf(
                async { repeat(WRITES) { cache.update { it.copy(a = it.a + 1) } } },
                async { repeat(WRITES) { cache.update { it.copy(b = it.b + 1) } } },
            ).awaitAll()
        }

        assertEquals(Tally(a = WRITES, b = WRITES), cache.get())
    }

    private companion object {
        const val WRITES = 10_000
    }
}

private data class Tally(val a: Int = 0, val b: Int = 0)
