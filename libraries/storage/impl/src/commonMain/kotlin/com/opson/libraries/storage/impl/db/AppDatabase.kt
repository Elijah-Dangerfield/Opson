package com.opson.libraries.storage.impl.db

import androidx.room.ConstructedBy
import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.RoomDatabaseConstructor
import androidx.room.TypeConverters
import com.opson.libraries.opson.storage.db.ExampleUserDataDao
import com.opson.libraries.opson.storage.db.ExampleUserDataEntity

@Database(
    entities = [
        ExampleUserDataEntity::class,
    ],
    version = 5, // Bumped: demo User/Session tables replaced by the example table
    /**
     * Every bump from [FIRST_APP_DATA_VERSION] on has to be listed here.
     *
     * The list is empty because nothing has been bumped since. It is declared
     * anyway, because the moment someone adds a table or a column the choice
     * they face is "add a line here" or "let the builder drop the database",
     * and only one of those is written down anywhere.
     *
     * Most additions are a new table or a defaulted column, which Room can
     * migrate on its own. A change it cannot, a renamed or retyped column,
     * fails the build here rather than at runtime, which is the point. When
     * the default is wrong for a new column, give the entry a
     * `spec = SomeBackfill::class`.
     */
    autoMigrations = [],
    exportSchema = true
)
@TypeConverters(CoreTypeConverters::class)
@ConstructedBy(AppDatabaseConstructor::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun exampleUserDataDao(): ExampleUserDataDao

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
