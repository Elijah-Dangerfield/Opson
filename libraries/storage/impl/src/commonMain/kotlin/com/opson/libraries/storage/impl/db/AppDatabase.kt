package com.opson.libraries.storage.impl.db

import androidx.room.AutoMigration
import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import com.opson.libraries.diary.db.CustomFoodDao
import com.opson.libraries.diary.db.CustomFoodEntity
import com.opson.libraries.diary.db.DayRecordDao
import com.opson.libraries.diary.db.DayRecordEntity
import com.opson.libraries.diary.db.FastDao
import com.opson.libraries.diary.db.FastEntity
import com.opson.libraries.diary.db.LogEntryDao
import com.opson.libraries.diary.db.LogEntryEntity
import com.opson.libraries.diary.db.MealFeelingDao
import com.opson.libraries.diary.db.MealFeelingEntity
import com.opson.libraries.diary.db.OutboxDao
import com.opson.libraries.diary.db.OutboxEntity
import com.opson.libraries.diary.db.SavedMealDao
import com.opson.libraries.diary.db.SavedMealEntity
import com.opson.libraries.diary.db.SyncStateDao
import com.opson.libraries.diary.db.SyncStateEntity
import com.opson.libraries.diary.db.WeighInDao
import com.opson.libraries.diary.db.WeighInEntity
import com.opson.libraries.foods.db.FoodCacheDao
import com.opson.libraries.foods.db.FoodCacheEntity
import com.opson.libraries.opson.storage.db.ExampleUserDataDao
import com.opson.libraries.opson.storage.db.ExampleUserDataEntity

@Database(
    entities = [
        ExampleUserDataEntity::class,
        LogEntryEntity::class,
        DayRecordEntity::class,
        MealFeelingEntity::class,
        WeighInEntity::class,
        CustomFoodEntity::class,
        SavedMealEntity::class,
        OutboxEntity::class,
        SyncStateEntity::class,
        FoodCacheEntity::class,
        FastEntity::class,
    ],
    version = 9, // 6: the diary tables, the outbox, sync state and the food cache; 7: day_records.untracked and .feeling; 8: fasts; 9: day_records.checkIn
    /**
     * Every bump from [FIRST_APP_DATA_VERSION] on has to be listed here.
     *
     * Most additions are a new table or a defaulted column, which Room can
     * migrate on its own. A change it cannot, a renamed or retyped column,
     * fails the build here rather than at runtime, which is the point. When
     * the default is wrong for a new column, give the entry a
     * `spec = SomeBackfill::class`.
     */
    autoMigrations = [
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
        AutoMigration(from = 7, to = 8),
        AutoMigration(from = 8, to = 9),
    ],
    exportSchema = true
)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exampleUserDataDao(): ExampleUserDataDao

    abstract fun logEntryDao(): LogEntryDao

    abstract fun dayRecordDao(): DayRecordDao

    abstract fun mealFeelingDao(): MealFeelingDao

    abstract fun weighInDao(): WeighInDao

    abstract fun customFoodDao(): CustomFoodDao

    abstract fun savedMealDao(): SavedMealDao

    abstract fun outboxDao(): OutboxDao

    abstract fun syncStateDao(): SyncStateDao

    abstract fun foodCacheDao(): FoodCacheDao

    abstract fun fastDao(): FastDao

    companion object {
        /**
         * The first schema a shipped app can be sitting on, and therefore the
         * first one that may hold something a user would miss.
         *
         * Versions below it are this template's own history: 2 through 4 are
         * the demo `user` / `sessions` / `tasks` tables, which no generated app
         * ever stored real data in. They are the only ones
         * `RealAppDatabaseProvider` is allowed to drop.
         *
         * Raise this only if a generated app has genuinely never shipped a
         * release on the version below. Lowering it, or widening the drop
         * range to reach it, deletes user data on upgrade with no error.
         */
        const val FIRST_APP_DATA_VERSION = 5
    }
}

@Suppress("KotlinNoActualForExpect")
expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
