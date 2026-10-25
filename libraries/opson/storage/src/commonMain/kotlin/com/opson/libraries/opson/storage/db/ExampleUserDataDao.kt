package com.opson.libraries.opson.storage.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Reference user-scoped Room table. Replace with your app's real tables —
 * the pattern to copy is: an `@Entity`, a `@Dao` extending [ClearableDao],
 * a `ProvideXxxDao` binding in `:libraries:storage:impl` that ALSO
 * contributes the DAO into the ClearableDao multibinding set, and the
 * entity registered in `AppDatabase`.
 */
@Entity(tableName = "example_user_data")
data class ExampleUserDataEntity(
    @PrimaryKey val key: String,
    val value: String,
)

@Dao
interface ExampleUserDataDao : ClearableDao {
    @Upsert
    suspend fun upsert(row: ExampleUserDataEntity)

    @Query("SELECT * FROM example_user_data WHERE key = :key")
    fun observe(key: String): Flow<ExampleUserDataEntity?>

    @Query("DELETE FROM example_user_data")
    override suspend fun deleteAll()
}
