package com.opson.libraries.storage.impl.db

import androidx.room.Room
import androidx.room.RoomDatabase
import com.opson.libraries.storage.impl.NoBackupDirectory
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosAppDatabaseBuilderFactory @Inject constructor() : AppDatabaseBuilderFactory {

    override fun create(): RoomDatabase.Builder<AppDatabase> {
        val dbUrl = NoBackupDirectory().adopt(DB_NAME, companions = SQLITE_SIDE_FILES.map { DB_NAME + it })
        return Room.databaseBuilder<AppDatabase>(
            name = requireNotNull(dbUrl.path),
        )
    }

    private companion object {
        const val DB_NAME = "opson.db"

        // Writes SQLite hasn't folded into the database yet live in the -wal, so it moves with
        // the database or the newest entries are lost.
        val SQLITE_SIDE_FILES = listOf("-wal", "-shm", "-journal")
    }
}
