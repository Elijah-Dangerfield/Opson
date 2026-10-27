package com.opson.libraries.diary.impl

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.diary.ExerciseDraft
import com.opson.libraries.diary.ExerciseEntry
import com.opson.libraries.diary.ExerciseRepository
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.db.ExerciseEntity
import com.opson.libraries.diary.impl.wire.ExerciseRowDto
import com.opson.libraries.diary.impl.wire.WireJson
import com.opson.libraries.nutrition.ExerciseActivities
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Instant

/**
 * Logged exercise in Room, each write queued for `/v1/sync` in the same transaction. A delete is
 * a tombstone, so it reaches the user's other phones.
 *
 * Telemetry: `exercise.logged`, `exercise.edited` and `exercise.deleted`, with whether the
 * activity came from the list and whether the number was typed. Never the activity, its length
 * or its energy: those are health data.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class ExerciseRepositoryImpl(
    private val tables: DiaryTables,
    private val writer: DiaryWriter,
) : ExerciseRepository {

    private val logger = KLog.withTag("Exercise")

    override fun observeDay(day: LocalDate): Flow<List<ExerciseEntry>> =
        tables.exercises.observeDay(day.toString()).map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): ExerciseEntry? = tables.exercises.get(id)?.takeUnless { it.deleted }?.toDomain()

    override suspend fun add(draft: ExerciseDraft): ExerciseEntry {
        require(draft.problem() == null) { "exercise ${draft.problem()} is invalid" }
        val entry = writer.write {
            val row = draft.toEntity(id = newId(), clientUpdatedAtEpochMs = stamp(null))
            put(row, base = null)
            row.toDomain()
        }
        logger.logEvent("exercise.logged", *draft.attributes())
        return entry
    }

    override suspend fun update(id: String, draft: ExerciseDraft) {
        require(draft.problem() == null) { "exercise ${draft.problem()} is invalid" }
        val updated = writer.write {
            val existing = tables.exercises.get(id)?.takeUnless { it.deleted } ?: return@write false
            put(draft.toEntity(id = id, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs)), base = existing)
            true
        }
        if (updated) logger.logEvent("exercise.edited", *draft.attributes())
    }

    override suspend fun delete(id: String) {
        val deleted = writer.write {
            val existing = tables.exercises.get(id)?.takeUnless { it.deleted } ?: return@write false
            put(existing.copy(deleted = true, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs)), base = existing)
            true
        }
        if (deleted) logger.logEvent("exercise.deleted")
    }

    private suspend fun DiaryWriter.WriteScope.put(row: ExerciseEntity, base: ExerciseEntity?) {
        tables.exercises.upsert(listOf(row))
        enqueue(SyncEntity.Exercise, row.id, row.json(), base?.json(), day = row.day)
    }

    private fun ExerciseEntity.json() = WireJson.encodeToString(ExerciseRowDto.serializer(), toRow())

    private fun ExerciseDraft.attributes(): Array<Pair<String, String>> = arrayOf(
        ATTR_LISTED to (activity != ExerciseActivities.OTHER).toString(),
        ATTR_TYPED to kcalTyped.toString(),
    )

    private companion object {
        const val ATTR_LISTED = "listed"
        const val ATTR_TYPED = "typed"
    }
}

internal fun ExerciseDraft.toEntity(id: String, clientUpdatedAtEpochMs: Long): ExerciseEntity = ExerciseEntity(
    id = id,
    day = day.toString(),
    startedAtEpochMs = startedAt.toEpochMilliseconds(),
    activity = activity,
    name = name?.trim()?.takeIf { it.isNotEmpty() },
    minutes = minutes,
    kcal = kcal,
    kcalTyped = kcalTyped,
    notOnWatch = notOnWatch,
    clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
    deleted = false,
)

internal fun ExerciseEntity.toDomain(): ExerciseEntry = ExerciseEntry(
    id = id,
    day = LocalDate.parse(day),
    startedAt = Instant.fromEpochMilliseconds(startedAtEpochMs),
    activity = activity,
    name = name,
    minutes = minutes,
    kcal = kcal,
    kcalTyped = kcalTyped,
    notOnWatch = notOnWatch,
)

internal fun ExerciseEntity.toRow(): ExerciseRowDto = ExerciseRowDto(
    id = id,
    day = day,
    startedAtEpochMs = startedAtEpochMs,
    activity = activity,
    name = name,
    minutes = minutes,
    kcal = kcal,
    kcalTyped = kcalTyped,
    notOnWatch = notOnWatch,
    clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
    deleted = deleted,
)

internal fun ExerciseRowDto.toEntity(): ExerciseEntity = ExerciseEntity(
    id = id,
    day = day,
    startedAtEpochMs = startedAtEpochMs,
    activity = activity,
    name = name,
    minutes = minutes,
    kcal = kcal,
    kcalTyped = kcalTyped,
    notOnWatch = notOnWatch,
    clientUpdatedAtEpochMs = clientUpdatedAtEpochMs,
    deleted = deleted,
)
