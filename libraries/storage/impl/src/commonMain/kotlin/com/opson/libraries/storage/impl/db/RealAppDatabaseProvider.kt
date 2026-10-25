package com.opson.libraries.storage.impl.db

import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.opson.libraries.flowroutines.DispatcherProvider
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class RealAppDatabaseProvider @Inject constructor(
    private val builderFactory: AppDatabaseBuilderFactory,
    private val dispatcherProvider: DispatcherProvider
) : AppDatabaseProvider {

    override val database: AppDatabase by lazy {
        builderFactory
            .create()
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(dispatcherProvider.io)
            // Only the pre-app template schemas may be dropped. Everything from
            // AppDatabase.FIRST_APP_DATA_VERSION up has to migrate: this file on
            // the device may be the only copy of what the user has. See
            // AppDatabase for what belongs in the autoMigrations list.
            .fallbackToDestructiveMigrationFrom(
                dropAllTables = true,
                *DROPPABLE_TEMPLATE_SCHEMA_VERSIONS,
            )
            .build()
    }
}

private val DROPPABLE_TEMPLATE_SCHEMA_VERSIONS = intArrayOf(1, 2, 3, 4)
