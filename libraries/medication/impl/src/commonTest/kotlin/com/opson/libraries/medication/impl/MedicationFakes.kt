package com.opson.libraries.medication.impl

import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.config.AppConfigMap
import com.opson.libraries.config.AppConfigRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.medication.db.MedicationDoseDao
import com.opson.libraries.medication.db.MedicationDoseEntity
import com.opson.libraries.medication.db.SymptomDayDao
import com.opson.libraries.medication.db.SymptomDayEntity
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.CacheJsonSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.Instant

class FakeDoseDao : MedicationDoseDao {
    val rows = MutableStateFlow<Map<String, MedicationDoseEntity>>(emptyMap())

    override suspend fun upsert(rows: List<MedicationDoseEntity>) {
        this.rows.value = this.rows.value + rows.associateBy { it.id }
    }

    override suspend fun get(id: String): MedicationDoseEntity? = rows.value[id]

    override fun observeAll(): Flow<List<MedicationDoseEntity>> =
        rows.map { all -> all.values.filterNot { it.deleted }.sortedByDescending { it.takenAtEpochMs } }

    override fun observeRange(from: String, to: String): Flow<List<MedicationDoseEntity>> =
        rows.map { all -> all.values.filter { !it.deleted && it.day >= from && it.day <= to }.sortedBy { it.takenAtEpochMs } }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }
}

class FakeSymptomDao : SymptomDayDao {
    val rows = MutableStateFlow<Map<String, SymptomDayEntity>>(emptyMap())

    override suspend fun upsert(rows: List<SymptomDayEntity>) {
        this.rows.value = this.rows.value + rows.associateBy { it.day }
    }

    override suspend fun get(day: String): SymptomDayEntity? = rows.value[day]

    override fun observeDay(day: String): Flow<SymptomDayEntity?> = rows.map { it[day]?.takeUnless { row -> row.deleted } }

    override fun observeRange(from: String, to: String): Flow<List<SymptomDayEntity>> =
        rows.map { all -> all.values.filter { !it.deleted && it.day >= from && it.day <= to }.sortedBy { it.day } }

    override suspend fun deleteAll() {
        rows.value = emptyMap()
    }
}

/** Caches held in memory, each starting from its serializer's default, as a fresh install's would. */
class FakeCacheFactory : CacheFactory {
    override fun <T : Any> inMemory(defaultValue: () -> T): Cache<T> = MemoryCache { defaultValue() }

    override fun <T : Any> persistent(name: String, serializer: CacheJsonSerializer<T>, loadEagerly: Boolean): Cache<T> =
        MemoryCache { serializer.read(null) }

    private class MemoryCache<T : Any>(private val default: suspend () -> T) : Cache<T> {
        private val value = MutableStateFlow<T?>(null)

        override val updates: Flow<T> = flow {
            get()
            emitAll(value.filterNotNull())
        }

        override suspend fun get(): T = value.value ?: default().also { value.value = it }

        override suspend fun set(value: T) {
            this.value.value = value
        }

        override suspend fun clear() {
            value.value = default()
        }

        override suspend fun update(transform: (T) -> T): T = transform(get()).also { set(it) }
    }
}

class FixedCalendar(private val date: LocalDate, private val zone: TimeZone = TimeZone.UTC) : LocalCalendar {
    override fun timeZone(): TimeZone = zone
    override fun today(): LocalDate = date
}

class FixedClock(var now: Instant) : Clock {
    override fun now(): Instant = now
}

/** `release.medication` from a flow the test flips. */
fun medicationConfig(flag: MutableStateFlow<Boolean>): AppConfigFlow {
    fun map(on: Boolean) = object : AppConfigMap() {
        override val map: Map<String, *> = mapOf("release" to mapOf("medication" to on))
    }
    val repository = object : AppConfigRepository {
        override fun config(): AppConfigMap = map(flag.value)
        override fun configStream(): Flow<AppConfigMap> = flag.map(::map)
    }
    return AppConfigFlow(repository)
}
