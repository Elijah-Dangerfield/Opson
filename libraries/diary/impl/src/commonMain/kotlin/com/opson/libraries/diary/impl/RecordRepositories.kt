package com.opson.libraries.diary.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.SavedMeal
import com.opson.libraries.diary.SavedMealRepository
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.diary.db.CustomFoodEntity
import com.opson.libraries.diary.db.SavedMealEntity
import com.opson.libraries.diary.db.WeighInEntity
import com.opson.libraries.diary.impl.wire.CustomFoodRowDto
import com.opson.libraries.diary.impl.wire.SavedMealRowDto
import com.opson.libraries.diary.impl.wire.WeighInRowDto
import com.opson.libraries.diary.impl.wire.WireJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import kotlin.time.Instant

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class WeightRepositoryImpl(
    private val tables: DiaryTables,
    private val writer: DiaryWriter,
) : WeightRepository {

    override fun observeWeighIns(since: Instant?): Flow<List<WeighIn>> =
        tables.weighIns.observeSince(since?.toEpochMilliseconds() ?: Long.MIN_VALUE)
            .map { rows -> rows.map { it.toDomain() } }

    override suspend fun addWeighIn(kg: Double, measuredAt: Instant?): WeighIn {
        require(kg.isFinite() && kg > 0.0 && kg <= MAX_KG) { "kg is out of range" }
        return writer.write {
            val row = WeighInEntity(
                id = newId(),
                measuredAtEpochMs = measuredAt?.toEpochMilliseconds() ?: nowEpochMs,
                kg = kg,
                clientUpdatedAtEpochMs = stamp(null),
                deleted = false,
            )
            put(row, base = null)
            row.toDomain()
        }
    }

    override suspend fun removeWeighIn(id: String) = writer.write {
        val existing = tables.weighIns.get(id)?.takeUnless { it.deleted } ?: return@write
        put(existing.copy(deleted = true, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs)), existing)
    }

    private suspend fun DiaryWriter.WriteScope.put(row: WeighInEntity, base: WeighInEntity?) {
        tables.weighIns.upsert(listOf(row))
        enqueue(SyncEntity.WeighIn, row.id, row.json(), base?.json())
    }

    private fun WeighInEntity.json() = WireJson.encodeToString(WeighInRowDto.serializer(), toRow())

    private companion object {
        const val MAX_KG = 700.0
    }
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class CustomFoodRepositoryImpl(
    private val tables: DiaryTables,
    private val writer: DiaryWriter,
) : CustomFoodRepository {

    override fun observeAll(): Flow<List<CustomFood>> =
        tables.customFoods.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): CustomFood? =
        tables.customFoods.get(id)?.takeUnless { it.deleted }?.toDomain()

    override suspend fun create(draft: CustomFoodDraft): CustomFood {
        val food = CustomFood(
            id = newId(),
            name = draft.name,
            servingAmount = draft.servingAmount,
            servingUnit = draft.servingUnit,
            brand = draft.brand,
            kcal = draft.kcal,
            protein = draft.protein,
            carbs = draft.carbs,
            fat = draft.fat,
            fiber = draft.fiber,
        ).validated()
        return writer.write {
            val row = food.toEntity(stamp(null))
            put(row, base = null)
            row.toDomain()
        }
    }

    override suspend fun update(food: CustomFood) {
        val valid = food.validated()
        writer.write {
            val existing = tables.customFoods.get(food.id)?.takeUnless { it.deleted } ?: return@write
            val next = valid.toEntity(stamp(existing.clientUpdatedAtEpochMs))
            if (next.copy(clientUpdatedAtEpochMs = existing.clientUpdatedAtEpochMs) == existing) return@write
            put(next, existing)
        }
    }

    override suspend fun delete(id: String) = writer.write {
        val existing = tables.customFoods.get(id)?.takeUnless { it.deleted } ?: return@write
        put(existing.copy(deleted = true, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs)), existing)
    }

    private suspend fun DiaryWriter.WriteScope.put(row: CustomFoodEntity, base: CustomFoodEntity?) {
        tables.customFoods.upsert(listOf(row))
        enqueue(SyncEntity.CustomFood, row.id, row.json(), base?.json())
    }

    private fun CustomFoodEntity.json() = WireJson.encodeToString(CustomFoodRowDto.serializer(), toRow())

    private fun CustomFood.validated(): CustomFood {
        require(name.isNotBlank()) { "A custom food needs a name" }
        require(servingUnit.isNotBlank()) { "A custom food needs a serving unit" }
        require(servingAmount.isFinite() && servingAmount > 0.0 && servingAmount <= MAX_AMOUNT) {
            "servingAmount is out of range"
        }
        return copy(
            name = name.trim().take(MAX_NAME),
            brand = brand?.trim()?.takeIf(String::isNotEmpty)?.take(MAX_NAME),
            servingUnit = servingUnit.trim().take(MAX_UNIT),
            kcal = kcal.clean(),
            protein = protein.clean(),
            carbs = carbs.clean(),
            fat = fat.clean(),
            fiber = fiber.clean(),
        )
    }

    private fun Double?.clean(): Double? = this?.takeIf { it.isFinite() && it >= 0.0 && it <= MAX_AMOUNT }

    private companion object {
        const val MAX_NAME = 200
        const val MAX_UNIT = 32
        const val MAX_AMOUNT = 100_000.0
    }
}

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class SavedMealRepositoryImpl(
    private val tables: DiaryTables,
    private val writer: DiaryWriter,
) : SavedMealRepository {

    override fun observeAll(): Flow<List<SavedMeal>> =
        tables.savedMeals.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): SavedMeal? =
        tables.savedMeals.get(id)?.takeUnless { it.deleted }?.toDomain()

    override suspend fun create(name: String, usualSlot: MealSlot?, items: List<LogDraft>): SavedMeal {
        val meal = SavedMeal(id = newId(), name = name, usualSlot = usualSlot, items = items).validated()
        return writer.write {
            val row = meal.toEntity(stamp(null))
            put(row, base = null)
            row.toDomain()
        }
    }

    override suspend fun update(meal: SavedMeal) {
        val valid = meal.validated()
        writer.write {
            val existing = tables.savedMeals.get(meal.id)?.takeUnless { it.deleted } ?: return@write
            val next = valid.toEntity(stamp(existing.clientUpdatedAtEpochMs))
            if (next.copy(clientUpdatedAtEpochMs = existing.clientUpdatedAtEpochMs) == existing) return@write
            put(next, existing)
        }
    }

    override suspend fun delete(id: String) = writer.write {
        val existing = tables.savedMeals.get(id)?.takeUnless { it.deleted } ?: return@write
        put(existing.copy(deleted = true, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs)), existing)
    }

    private suspend fun DiaryWriter.WriteScope.put(row: SavedMealEntity, base: SavedMealEntity?) {
        tables.savedMeals.upsert(listOf(row))
        enqueue(SyncEntity.SavedMeal, row.id, row.json(), base?.json())
    }

    private fun SavedMealEntity.json() = WireJson.encodeToString(SavedMealRowDto.serializer(), toRow())

    private fun SavedMeal.validated(): SavedMeal {
        require(name.isNotBlank()) { "A saved meal needs a name" }
        return copy(name = name.trim().take(MAX_NAME))
    }

    private companion object {
        const val MAX_NAME = 200
    }
}
