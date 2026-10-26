package com.opson.libraries.storage.impl.cache

import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.DefaultDispatcherProvider
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheJsonSerializer
import com.opson.libraries.storage.FileManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import kotlin.random.Random
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DataStoreCacheTest {

    private val fileManager = TempDirFileManager()
    private val factory = DataStoreCacheFactory(
        scope = AppCoroutineScope(DefaultDispatcherProvider()),
        fileManager = fileManager,
    )

    @AfterTest
    fun deleteTempFiles() {
        fileManager.deleteRoot()
    }

    /**
     * Runs on real threads on purpose. The interleaving this is about only
     * exists because `get()` and `set()` suspend independently, and a test
     * dispatcher that never actually parks a coroutine would make a
     * read-then-write look atomic.
     */
    @Test
    fun concurrentWritersToDifferentFieldsBothSurvive() = runTest {
        val cache = newCache("update-race")

        withContext(Dispatchers.Default) {
            listOf(
                async { repeat(WRITES) { cache.update { it.copy(a = it.a + 1) } } },
                async { repeat(WRITES) { cache.update { it.copy(b = it.b + 1) } } },
            ).awaitAll()
        }

        assertEquals(Counters(a = WRITES, b = WRITES), cache.get())
    }

    @Test
    fun clearResetsTheStoredValueToTheSerializerDefault() = runTest {
        val cache = newCache("clear-get")
        cache.set(Counters(a = 7, b = 9))

        cache.clear()

        assertEquals(Counters(), cache.get())
    }

    /**
     * The half that correcting the file name would not have fixed: `DataStore`
     * serves readers from memory, so a reader still on the old value is the
     * symptom even once the file on disk is gone.
     */
    @Test
    fun clearIsVisibleToAReaderOfUpdates() = runTest {
        val cache = newCache("clear-updates")
        cache.set(Counters(a = 7, b = 9))

        cache.clear()

        assertEquals(Counters(), cache.updates.first())
    }

    private fun newCache(name: String): Cache<Counters> =
        factory.persistent(name, CountersSerializer, loadEagerly = false)

    private companion object {
        const val WRITES = 50
    }
}

private data class Counters(val a: Int = 0, val b: Int = 0)

/** Two ints and a comma. Enough to round-trip; nothing here is about parsing. */
private object CountersSerializer : CacheJsonSerializer<Counters> {
    override suspend fun read(bytes: ByteArray?): Counters {
        val parts = bytes?.decodeToString()?.split(",") ?: return Counters()
        return Counters(a = parts[0].toInt(), b = parts[1].toInt())
    }

    override suspend fun write(value: Counters): ByteArray =
        "${value.a},${value.b}".encodeToByteArray()
}

private class TempDirFileManager : FileManager {
    private val root: Path =
        FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "cache-test-${Random.nextInt(Int.MAX_VALUE)}"

    init {
        FileSystem.SYSTEM.createDirectories(root)
    }

    override fun createFile(name: String): Path = root / name

    fun deleteRoot() {
        FileSystem.SYSTEM.deleteRecursively(root, mustExist = false)
    }
}
