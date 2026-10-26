package com.opson.libraries.storage.impl.cache

import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.core.okio.OkioSerializer
import androidx.datastore.core.okio.OkioStorage
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.CacheJsonSerializer
import com.opson.libraries.storage.FileManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import me.tatarka.inject.annotations.Inject
import okio.BufferedSink
import okio.BufferedSource
import okio.FileSystem
import okio.SYSTEM
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, CacheFactory::class)
@Inject
class DataStoreCacheFactory (
    private val scope: AppCoroutineScope,
    private val fileManager: FileManager,
) : CacheFactory {

    private val persistentCaches: MutableMap<String, Cache<*>> = mutableMapOf()

    override fun <T : Any> inMemory(
        defaultValue: () -> T,
    ): Cache<T> = InMemoryCache(defaultValue)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> persistent(
        name: String,
        serializer: CacheJsonSerializer<T>,
        loadEagerly: Boolean
        ): Cache<T> {
        return persistentCaches.getOrPut(name) {
            createPersistentCache(name, serializer, loadEagerly)
        } as Cache<T>
    }
    
    private fun <T : Any> createPersistentCache(
        name: String,
        serializer: CacheJsonSerializer<T>,
        loadEagerly: Boolean
    ): Cache<T> {
        val storage = OkioStorage(
            fileSystem = FileSystem.SYSTEM,
            serializer = serializer.toOkioSerializer(),
            producePath = { fileManager.createFile("$name.json") },
        )

        val corruptionHandler = ReplaceFileCorruptionHandler<T> { ex ->
            KLog.e("File Corrupted", ex)
            runBlocking { serializer.read(null) }
        }

        val dataStore: DataStore<T> = DataStoreFactory.create(
            storage = storage,
            scope = scope,
            corruptionHandler = corruptionHandler
        )

        if (loadEagerly) {
            scope.launch {
                dataStore.data.first()
            }
        }

        return DataStoreCache(
            dataStore,
            defaultValue = { serializer.read(null) },
        )
    }

    private fun <T : Any> CacheJsonSerializer<T>.toOkioSerializer(): OkioSerializer<T> =
        object : OkioSerializer<T> {
            override val defaultValue: T
                get() = runBlocking { read(null) }

            override suspend fun readFrom(source: BufferedSource): T =
                read(source.readByteArray())

            override suspend fun writeTo(t: T, sink: BufferedSink) {
                val bytes = write(t)
                sink.write(bytes)
            }
        }
}

private class DataStoreCache<T : Any>(
    private val dataStore: DataStore<T>,
    private val defaultValue: suspend () -> T,
) : Cache<T> {

    override val updates: Flow<T> = dataStore.data

    override suspend fun get(): T = dataStore.data.first()

    override suspend fun set(value: T) {
        dataStore.updateData { value }
    }

    /**
     * Overridden because the interface default is `set(transform(get()))`, and
     * a read-then-write is not atomic. Two writers that overlap each hold a
     * snapshot taken before the other's write, so whichever lands second
     * silently reverts the first. `AppData` has several writers live at once
     * on a cold start.
     *
     * `DataStore.updateData` serialises the read and the write, so the
     * transform always sees the latest value.
     */
    override suspend fun update(transform: (T) -> T): T = dataStore.updateData(transform)

    /**
     * Writes the default back rather than deleting the file.
     *
     * Deleting was both wrong and invisible. Wrong because the path passed to
     * `deleteFile` was `name` while the file is written as `"$name.json"`, so
     * it removed nothing; invisible because `deleteRecursively()` on a path
     * that does not exist returns false without throwing, so the
     * `Catching {}.logOnFailure` inside the file manager never fired.
     *
     * Correcting the name would not have been enough either: `DataStore` holds
     * the value in memory and serves readers from there, so even a
     * correctly-named delete leaves every reader on the old value until the
     * process dies. `updateData` is the only write path `DataStore` observes,
     * which makes it the only one `clear` can use.
     *
     * This is the user-isolation mechanism. `UserScopedProfileCacheCleaner`
     * calls it on a user change against a persistent cache, so a broken
     * `clear` leaves the previous account's profile on a shared device.
     */
    override suspend fun clear() {
        val fresh = defaultValue()
        dataStore.updateData { fresh }
    }
}
