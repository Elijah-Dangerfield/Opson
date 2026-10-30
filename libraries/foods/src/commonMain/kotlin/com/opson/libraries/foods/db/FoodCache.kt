package com.opson.libraries.foods.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.opson.libraries.opson.storage.db.ClearableDao
import kotlinx.coroutines.flow.Flow

/**
 * Catalog foods this user has picked or starred, so Favorites, Recent and an
 * offline search have something to show. [json] is the whole `Food`; the other
 * columns exist to be queried. User-scoped: what someone eats is theirs, so a
 * user change wipes it. Bounded: the 500 most recently used besides favorites
 * ([FoodCacheDao.trim]).
 */
@Entity(
    tableName = "food_cache",
    indices = [Index("lastUsedAtEpochMs"), Index("barcode")],
)
data class FoodCacheEntity(
    @PrimaryKey val id: String,
    val json: String,
    /** Lowercased name and brand, for the offline search. */
    val searchText: String,
    val barcode: String?,
    val lastUsedAtEpochMs: Long?,
    val favorite: Boolean,
)

/** A [FoodCacheEntity] without its JSON: what the offline search matches on. */
data class FoodCacheSearchRow(
    val id: String,
    val searchText: String,
    val favorite: Boolean,
    val lastUsedAtEpochMs: Long?,
)

@Dao
interface FoodCacheDao : ClearableDao {
    @Upsert
    suspend fun upsert(row: FoodCacheEntity)

    @Query("SELECT * FROM food_cache WHERE id = :id")
    suspend fun get(id: String): FoodCacheEntity?

    @Query("SELECT * FROM food_cache WHERE barcode = :barcode LIMIT 1")
    suspend fun getByBarcode(barcode: String): FoodCacheEntity?

    /**
     * What the offline search reads to choose, without each food's JSON: favorites, then the most
     * recently used. The cache is bounded ([trim]), so this is a few hundred short rows.
     */
    @Query("SELECT id, searchText, favorite, lastUsedAtEpochMs FROM food_cache ORDER BY favorite DESC, lastUsedAtEpochMs DESC")
    suspend fun searchable(): List<FoodCacheSearchRow>

    @Query("SELECT * FROM food_cache WHERE id IN (:ids)")
    suspend fun getAll(ids: List<String>): List<FoodCacheEntity>

    /**
     * Keeps the [keep] most recently used foods besides favorites and deletes the rest, oldest
     * first; a food kept from a scan and never logged goes before any logged one.
     */
    @Query(
        "DELETE FROM food_cache WHERE favorite = 0 AND id NOT IN " +
            "(SELECT id FROM food_cache WHERE favorite = 0 ORDER BY lastUsedAtEpochMs DESC LIMIT :keep)",
    )
    suspend fun trim(keep: Int)

    @Query("SELECT * FROM food_cache WHERE favorite = 1 ORDER BY searchText")
    fun observeFavorites(): Flow<List<FoodCacheEntity>>

    @Query("SELECT * FROM food_cache WHERE lastUsedAtEpochMs IS NOT NULL ORDER BY lastUsedAtEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<FoodCacheEntity>>

    @Query("DELETE FROM food_cache")
    override suspend fun deleteAll()

    /**
     * The QA menu's "Clear caches": Recent and the offline search go, Favorites stay. Favorites
     * live only here, so this is the most a cache clear may take.
     */
    @Query("DELETE FROM food_cache WHERE favorite = 0")
    suspend fun deleteAllButFavorites()
}
