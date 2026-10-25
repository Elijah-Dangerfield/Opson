package com.opson.server.db

import kotlinx.coroutines.test.runTest
import org.jetbrains.exposed.sql.selectAll
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Schema smoke test: proves Flyway created the table *and* that the Exposed
 * [ProfilesTable] projection matches the real columns (the `selectAll()`
 * references every declared column, so a drifted column name fails here). Add a
 * line per new table.
 */
class DatabaseSchemaTest : DatabaseTest() {

    @Test
    fun migrationsCreateProfilesTable() = runTest {
        val rows = database.transaction {
            ProfilesTable.selectAll().toList()
        }
        assertTrue(rows.isEmpty())
    }
}
