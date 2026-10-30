package com.opson.libraries.diary.impl

import com.opson.libraries.diary.Recipe
import com.opson.libraries.diary.RecipeDraft
import com.opson.libraries.diary.RecipeLinks
import com.opson.libraries.diary.RecipeRepository
import com.opson.libraries.diary.RecipeSharing
import com.opson.libraries.diary.SyncEntity
import com.opson.libraries.diary.db.RecipeEntity
import com.opson.libraries.diary.impl.wire.RecipeRowDto
import com.opson.libraries.diary.impl.wire.WireJson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Recipes in Room, each change queued on the diary outbox like a saved meal. The sharing columns
 * are written only by [markShared] and by a pull: an edit keeps whatever the server last said.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
@Inject
class RecipeRepositoryImpl(
    private val tables: DiaryTables,
    private val writer: DiaryWriter,
) : RecipeRepository {

    override fun observeAll(): Flow<List<Recipe>> =
        tables.recipes.observeAll().map { rows -> rows.map { it.toDomain() } }

    override fun observe(id: String): Flow<Recipe?> = tables.recipes.observe(id).map { it?.toDomain() }

    override suspend fun get(id: String): Recipe? = tables.recipes.get(id)?.takeUnless { it.deleted }?.toDomain()

    override suspend fun byShareCode(code: String): Recipe? = tables.recipes.byPublicCode(code)?.toDomain()

    override suspend fun create(draft: RecipeDraft): Recipe {
        val valid = draft.validated()
        return writer.write {
            val row = valid.toEntity(newId(), stamp(null), kept = null)
            put(row, base = null)
            row.toDomain()
        }
    }

    override suspend fun update(id: String, draft: RecipeDraft) {
        val valid = draft.validated()
        writer.write {
            val existing = tables.recipes.get(id)?.takeUnless { it.deleted } ?: return@write
            val next = valid.toEntity(id, stamp(existing.clientUpdatedAtEpochMs), kept = existing)
            if (next.copy(clientUpdatedAtEpochMs = existing.clientUpdatedAtEpochMs) == existing) return@write
            put(next, existing)
        }
    }

    override suspend fun delete(id: String) = writer.write {
        val existing = tables.recipes.get(id)?.takeUnless { it.deleted } ?: return@write
        put(existing.copy(deleted = true, clientUpdatedAtEpochMs = stamp(existing.clientUpdatedAtEpochMs)), existing)
    }

    override suspend fun markShared(id: String, sharing: RecipeSharing) {
        val existing = tables.recipes.get(id) ?: return
        tables.recipes.upsert(
            listOf(existing.copy(publicCode = sharing.code ?: existing.publicCode, publicState = sharing.state.wire, publicShowName = sharing.showName)),
        )
    }

    private suspend fun DiaryWriter.WriteScope.put(row: RecipeEntity, base: RecipeEntity?) {
        tables.recipes.upsert(listOf(row))
        enqueue(SyncEntity.Recipe, row.id, row.json(), base?.json())
    }

    private fun RecipeEntity.json() = WireJson.encodeToString(RecipeRowDto.serializer(), toRow())

    /** The server's limits (`DiaryRules`, `RecipeRules`): a recipe past them would be refused whole, so it is held to them here. */
    private fun RecipeDraft.validated(): RecipeDraft {
        require(name.isNotBlank()) { "A recipe needs a name" }
        require(servings.isFinite() && servings >= Recipe.MIN_SERVINGS && servings <= Recipe.MAX_SERVINGS) { "servings is out of range" }
        return copy(
            name = name.trim().take(Recipe.MAX_NAME),
            ingredients = ingredients.take(Recipe.MAX_INGREDIENTS),
            notes = notes?.trim()?.take(Recipe.MAX_NOTES)?.takeIf { it.isNotEmpty() },
            savedFrom = savedFrom?.takeIf(RecipeLinks::isCode),
        )
    }
}
