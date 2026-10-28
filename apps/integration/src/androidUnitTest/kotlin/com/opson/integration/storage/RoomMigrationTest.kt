package com.opson.integration.storage

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import androidx.sqlite.execSQL
import com.opson.libraries.flowroutines.DefaultDispatcherProvider
import com.opson.libraries.storage.impl.db.AppDatabase
import com.opson.libraries.storage.impl.db.AppDatabaseBuilderFactory
import com.opson.libraries.storage.impl.db.AppDatabaseConstructor
import com.opson.libraries.storage.impl.db.RealAppDatabaseProvider
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/**
 * **An update keeps what is on the phone.** For every schema a shipped app can be sitting on
 * ([AppDatabase.FIRST_APP_DATA_VERSION] up), a database file is built from that version's exported
 * schema with a row in every table, then opened through the app's own [RealAppDatabaseProvider]
 * (its auto-migrations and its drop policy). Every row has to come through, and Room has to accept
 * the result as the current schema.
 */
class RoomMigrationTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun fromVersion5_everyRowSurvives() = migratesKeepingEveryRow(5)

    @Test
    fun fromVersion6_everyRowSurvives() = migratesKeepingEveryRow(6)

    @Test
    fun fromVersion7_everyRowSurvives() = migratesKeepingEveryRow(7)

    @Test
    fun fromVersion8_everyRowSurvives() = migratesKeepingEveryRow(8)

    @Test
    fun fromVersion9_everyRowSurvives() = migratesKeepingEveryRow(9)

    @Test
    fun theOldestMigratedVersionIsTheFirstWithAppData() {
        assertEquals(5, AppDatabase.FIRST_APP_DATA_VERSION, "a version below 5 would be dropped, not migrated; add a test for it")
    }

    /**
     * The tests above are one per version so a failure names it, and this is what stops the
     * next version shipping without one: every exported schema older than the current one has
     * to be in [TESTED_VERSIONS], and the current one has to be [Schema.CURRENT].
     */
    @Test
    fun everyExportedSchemaHasAMigrationTest() {
        val exported = Schema.exportedVersions()
        assertEquals(Schema.CURRENT, exported.max(), "AppDatabase moved to ${exported.max()}: update Schema.CURRENT")
        assertEquals(
            exported.filter { it >= AppDatabase.FIRST_APP_DATA_VERSION && it < Schema.CURRENT }.toSet(),
            TESTED_VERSIONS,
            "add a fromVersionN_everyRowSurvives test for every version a shipped app can be on",
        )
    }

    private fun migratesKeepingEveryRow(from: Int) = runTest {
        val schema = Schema.read(from)
        val file = File(folder.root, "opson-$from.db")
        BundledSQLiteDriver().open(file.path).use { connection ->
            schema.create(connection)
            schema.tables.forEach { connection.execSQL(it.insertSql()) }
            connection.execSQL("PRAGMA user_version = $from")
        }

        val database = RealAppDatabaseProvider(FileAppDatabase(file), DefaultDispatcherProvider()).database
        try {
            // The first query opens the file, which runs the migrations and Room's schema check.
            val entry = database.logEntryDao().get("id")
            if (from >= LOG_ENTRIES_SINCE) {
                assertEquals("name", assertNotNull(entry, "the logged food is still there").name)
                val day = assertNotNull(database.dayRecordDao().get("day"))
                assertEquals("note", day.note)
                if (from < UNTRACKED_SINCE) assertFalse(day.untracked, "a column added later takes its default")
            }
        } finally {
            database.close()
        }

        BundledSQLiteDriver().open(file.path).use { connection ->
            assertEquals(Schema.CURRENT, connection.single("PRAGMA user_version").toInt())
            schema.tables.forEach { table ->
                assertEquals(1L, connection.single("SELECT COUNT(*) FROM `${table.name}`"), "${table.name} keeps its row")
                table.columns.forEach { column ->
                    assertEquals(
                        column.value,
                        connection.text("SELECT quote(`${column.name}`) FROM `${table.name}`"),
                        "${table.name}.${column.name} is unchanged",
                    )
                }
            }
        }
    }

    private companion object {
        const val LOG_ENTRIES_SINCE = 6
        const val UNTRACKED_SINCE = 7

        /** The versions the `fromVersionN` tests above cover. */
        val TESTED_VERSIONS = setOf(5, 6, 7, 8, 9)
    }
}

/** One exported Room schema, as `libraries/storage/impl/schemas` holds it. */
private class Schema(json: JsonObject) {
    val tables: List<Table> = json.getValue("entities").jsonArray.map { Table(it.jsonObject) }
    private val setupQueries = json.getValue("setupQueries").jsonArray.map { it.jsonPrimitive.content }

    fun create(connection: SQLiteConnection) {
        tables.forEach { table -> table.createSql.forEach(connection::execSQL) }
        setupQueries.forEach(connection::execSQL)
    }

    companion object {
        /** `AppDatabase`'s version; a migrated file has to end up here. */
        const val CURRENT = 10

        fun read(version: Int): Schema {
            val file = File(directory(), "$version.json")
            val database = Json.parseToJsonElement(file.readText()).jsonObject.getValue("database").jsonObject
            check(database.getValue("version").jsonPrimitive.int == version) { "$file is not version $version" }
            return Schema(database)
        }

        /** Every version Room has exported a schema for. */
        fun exportedVersions(): List<Int> =
            directory().listFiles().orEmpty().mapNotNull { it.name.removeSuffix(".json").toIntOrNull() }
                .also { check(it.isNotEmpty()) { "no exported schemas under ${directory()}" } }

        private fun directory(): File {
            val root = System.getProperty("repoRoot") ?: error("repoRoot is unset; apps/integration/build.gradle.kts should supply it")
            return File(root, "libraries/storage/impl/schemas/com.opson.libraries.storage.impl.db.AppDatabase")
        }
    }
}

private class Table(json: JsonObject) {
    val name: String = json.getValue("tableName").jsonPrimitive.content
    val columns: List<Column> = json.getValue("fields").jsonArray.map { field ->
        val column = field.jsonObject
        Column(
            name = column.getValue("columnName").jsonPrimitive.content,
            affinity = column.getValue("affinity").jsonPrimitive.content,
        )
    }
    val createSql: List<String> = buildList {
        add(json.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
        json["indices"]?.jsonArray?.forEach { index ->
            add(index.jsonObject.getValue("createSql").jsonPrimitive.content.replace("\${TABLE_NAME}", name))
        }
    }

    /** Every column filled, so a migration that drops or rewrites one shows up. */
    fun insertSql(): String =
        "INSERT INTO `$name` (${columns.joinToString { "`${it.name}`" }}) VALUES (${columns.joinToString { it.value }})"
}

/** A value for [name] as SQLite's `quote()` prints it back: the column's own name for text. */
private class Column(val name: String, affinity: String) {
    val value: String = when (affinity) {
        "INTEGER" -> "1"
        "REAL" -> "1.5"
        else -> "'$name'"
    }
}

/** The app's database in a file, as on a phone; `RealAppDatabaseProvider` adds the driver and the policy. */
private class FileAppDatabase(private val file: File) : AppDatabaseBuilderFactory {
    override fun create(): RoomDatabase.Builder<AppDatabase> =
        Room.databaseBuilder<AppDatabase>(FileContext(), file.path, factory = { AppDatabaseConstructor.initialize() })
            // AUTOMATIC asks the context for the ActivityManager, which a host JVM has none of.
            .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)

    /** Room only asks it where the file is, and the name is already a full path. */
    private class FileContext : ContextWrapper(null) {
        override fun getApplicationContext(): Context = this

        override fun getDatabasePath(name: String): File = File(name)
    }
}

private fun SQLiteConnection.single(sql: String): Long = prepare(sql).use { statement ->
    check(statement.step()) { "no row for $sql" }
    statement.getLong(0)
}

private fun SQLiteConnection.text(sql: String): String = prepare(sql).use { statement ->
    check(statement.step()) { "no row for $sql" }
    statement.getText(0)
}
