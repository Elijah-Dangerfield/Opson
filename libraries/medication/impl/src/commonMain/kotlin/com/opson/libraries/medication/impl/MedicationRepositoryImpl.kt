package com.opson.libraries.medication.impl

import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.logging.logEvent
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.medication.DoseDraft
import com.opson.libraries.medication.DoseUnit
import com.opson.libraries.medication.DoseVia
import com.opson.libraries.medication.InjectionSite
import com.opson.libraries.medication.MedicationDose
import com.opson.libraries.medication.MedicationDrug
import com.opson.libraries.medication.MedicationEnabled
import com.opson.libraries.medication.MedicationPlan
import com.opson.libraries.medication.MedicationProduct
import com.opson.libraries.medication.MedicationRepository
import com.opson.libraries.medication.Severity
import com.opson.libraries.medication.Symptom
import com.opson.libraries.medication.SymptomDay
import com.opson.libraries.medication.SymptomLog
import com.opson.libraries.medication.db.MedicationDoseDao
import com.opson.libraries.medication.db.MedicationDoseEntity
import com.opson.libraries.medication.db.SymptomDayDao
import com.opson.libraries.medication.db.SymptomDayEntity
import com.opson.libraries.opson.UserScopedClearer
import com.opson.libraries.storage.Cache
import com.opson.libraries.storage.CacheFactory
import com.opson.libraries.storage.versionedJsonSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * The plan in a device cache and the doses and side effects in Room, all on this phone. Nothing
 * is queued for `/v1/sync`: the prod server has no medication tables yet, and a mutation it
 * cannot place would sit in the outbox for good. The rows already carry what a sync entity needs
 * (uuid ids, `clientUpdatedAt`, tombstones), so adding one is an outbox `enqueue` here and a
 * server migration, not a data migration.
 *
 * A user change wipes all of it: the tables through `ClearableDao`, the plan here
 * ([UserScopedClearer]).
 *
 * Telemetry: `medication.dose_logged` with where it came from, `medication.dose_deleted`, and
 * `medication.tracking` with whether it is on. Never a drug, dose, site, note or side effect.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = MedicationRepository::class)
@ContributesBinding(AppScope::class, boundType = SymptomLog::class)
@ContributesBinding(AppScope::class, boundType = UserScopedClearer::class, multibinding = true)
@Inject
class MedicationRepositoryImpl(
    private val doses: MedicationDoseDao,
    private val symptomDays: SymptomDayDao,
    cacheFactory: CacheFactory,
    private val calendar: LocalCalendar,
    private val clock: Clock,
    private val config: AppConfigFlow,
) : MedicationRepository, SymptomLog, UserScopedClearer {

    private val logger = KLog.withTag("Medication")

    private val cache: Cache<MedicationPlan> = cacheFactory.persistent(
        name = "medication_plan",
        serializer = versionedJsonSerializer(defaultValue = { MedicationPlan() }),
    )

    override val plan: Flow<MedicationPlan> get() = cache.updates

    override suspend fun currentPlan(): MedicationPlan = cache.get()

    override suspend fun updatePlan(change: (MedicationPlan) -> MedicationPlan): MedicationPlan {
        var before = MedicationPlan()
        cache.update { current ->
            before = current
            change(current).copy(updatedAtEpochMs = clock.now().toEpochMilliseconds())
        }
        val after = cache.get()
        if (after.tracking != before.tracking) logger.logEvent("medication.tracking", "on" to after.tracking)
        return after
    }

    override fun observeDoses(): Flow<List<MedicationDose>> = doses.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observeDoses(from: LocalDate, to: LocalDate): Flow<List<MedicationDose>> =
        doses.observeRange(from.toString(), to.toString()).map { rows -> rows.map { it.toDomain() } }

    override suspend fun dose(id: String): MedicationDose? = doses.get(id)?.takeUnless { it.deleted }?.toDomain()

    override suspend fun logDose(draft: DoseDraft, via: DoseVia): MedicationDose? {
        require(draft.problem() == null) { "dose ${draft.problem()} is invalid" }
        val medication = cache.get().medications.firstOrNull { it.id == draft.medicationId } ?: return null
        val row = MedicationDoseEntity(
            id = Uuid.random().toString(),
            medicationId = medication.id,
            product = medication.product.wire,
            drug = medication.drug.wire,
            brand = medication.brand?.trim()?.takeIf { medication.product.named && it.isNotEmpty() },
            dose = draft.dose,
            doseUnit = draft.unit.wire,
            takenAtEpochMs = draft.takenAt.toEpochMilliseconds(),
            day = day(draft.takenAt).toString(),
            site = draft.site?.wire?.takeIf { medication.injected },
            note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
            clientUpdatedAtEpochMs = stamp(null),
            deleted = false,
        )
        doses.upsert(listOf(row))
        logger.logEvent("medication.dose_logged", "via" to via.wire)
        return row.toDomain()
    }

    override suspend fun updateDose(id: String, draft: DoseDraft): Boolean {
        require(draft.problem() == null) { "dose ${draft.problem()} is invalid" }
        val existing = doses.get(id)?.takeUnless { it.deleted } ?: return false
        doses.upsert(
            listOf(
                existing.copy(
                    dose = draft.dose,
                    doseUnit = draft.unit.wire,
                    takenAtEpochMs = draft.takenAt.toEpochMilliseconds(),
                    day = day(draft.takenAt).toString(),
                    site = draft.site?.wire,
                    note = draft.note?.trim()?.takeIf { it.isNotEmpty() },
                    clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs),
                ),
            ),
        )
        return true
    }

    override suspend fun deleteDose(id: String): Boolean {
        val existing = doses.get(id)?.takeUnless { it.deleted } ?: return false
        doses.upsert(listOf(existing.copy(deleted = true, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs))))
        logger.logEvent("medication.dose_deleted")
        return true
    }

    override suspend fun restoreDose(id: String): Boolean {
        val existing = doses.get(id)?.takeIf { it.deleted } ?: return false
        doses.upsert(listOf(existing.copy(deleted = false, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs))))
        return true
    }

    // SymptomLog

    override val asking: Flow<Boolean>
        get() = combine(config.map(MedicationEnabled::isOn).distinctUntilChanged(), plan) { on, plan -> on && plan.tracking }
            .distinctUntilChanged()

    override fun observeDay(day: LocalDate): Flow<Map<Symptom, Severity>> =
        symptomDays.observeDay(day.toString()).map { row -> row?.let { decodeSymptoms(it.symptoms) }.orEmpty() }

    override fun observeRange(from: LocalDate, to: LocalDate): Flow<List<SymptomDay>> =
        symptomDays.observeRange(from.toString(), to.toString()).map { rows ->
            rows.map { SymptomDay(LocalDate.parse(it.day), decodeSymptoms(it.symptoms)) }.filter { it.symptoms.isNotEmpty() }
        }

    override suspend fun set(day: LocalDate, symptoms: Map<Symptom, Severity>) {
        val existing = symptomDays.get(day.toString())
        symptomDays.upsert(
            listOf(
                SymptomDayEntity(
                    day = day.toString(),
                    symptoms = encodeSymptoms(symptoms),
                    clientUpdatedAtEpochMs = stamp(existing?.clientUpdatedAtEpochMs),
                    // An emptied day stays as a row with nothing in it, so a later sync can say so.
                    deleted = false,
                ),
            ),
        )
    }

    override suspend fun clear(previousUserId: String) {
        cache.clear()
    }

    private fun day(at: Instant): LocalDate = at.toLocalDateTime(calendar.timeZone()).date

    /** Strictly after the row's last stamp, so a later sync's last-writer-wins never goes backwards. */
    private fun stamp(previous: Long?): Long = maxOf(clock.now().toEpochMilliseconds(), (previous ?: 0) + 1)
}

internal fun MedicationDoseEntity.toDomain(): MedicationDose = MedicationDose(
    id = id,
    medicationId = medicationId,
    product = MedicationProduct.fromWire(product),
    drug = MedicationDrug.fromWire(drug),
    brand = brand,
    dose = dose,
    unit = DoseUnit.fromWire(doseUnit),
    takenAt = Instant.fromEpochMilliseconds(takenAtEpochMs),
    day = LocalDate.parse(day),
    site = InjectionSite.fromWire(site),
    note = note,
)

private val SymptomJson = Json { ignoreUnknownKeys = true }

/** `{"nausea":"mild"}`, in [Symptom] order. */
internal fun encodeSymptoms(symptoms: Map<Symptom, Severity>): String =
    SymptomJson.encodeToString(
        JsonObject.serializer(),
        JsonObject(Symptom.entries.mapNotNull { symptom -> symptoms[symptom]?.let { symptom.wire to JsonPrimitive(it.wire) } }.toMap()),
    )

/** A name this build does not know is dropped rather than guessed at. */
internal fun decodeSymptoms(json: String): Map<Symptom, Severity> =
    Catching { SymptomJson.parseToJsonElement(json).jsonObject }.getOrNull().orEmpty()
        .mapNotNull { (key, value) ->
            val symptom = Symptom.fromWire(key) ?: return@mapNotNull null
            val severity = Severity.fromWire(value.jsonPrimitive.contentOrNull) ?: return@mapNotNull null
            symptom to severity
        }.toMap()
