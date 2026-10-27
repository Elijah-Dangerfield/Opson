package com.opson.server.db

import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.ColumnType
import org.jetbrains.exposed.sql.Expression
import org.jetbrains.exposed.sql.ExpressionWithColumnType
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.QueryBuilder
import org.jetbrains.exposed.sql.Table
import org.postgresql.util.PGobject

/**
 * Exposed column type that maps a Postgres `JSONB` column to a Kotlin
 * `String`. Writes wrap the value in a `PGobject(type="jsonb")` so the
 * JDBC driver sends it as the right SQL type (text→jsonb has no
 * implicit cast in Postgres); reads pull `PGobject.value` back out as
 * a plain string. Callers serialize/deserialize at the application
 * layer.
 */
private class JsonbColumnType : ColumnType<String>() {
    override fun sqlType(): String = "JSONB"

    override fun valueFromDB(value: Any): String = when (value) {
        is PGobject -> value.value.orEmpty()
        is String -> value
        else -> value.toString()
    }

    override fun notNullValueToDB(value: String): Any =
        PGobject().apply {
            type = "jsonb"
            this.value = value
        }
}

/**
 * Register a `JSONB` column whose values are exchanged as `String`.
 * Use this for any column declared `JSONB` in Flyway — `text(...)`
 * works for read-only projections but the JDBC driver rejects writes
 * because there's no text→jsonb implicit cast.
 */
fun Table.jsonb(name: String): Column<String> = registerColumn(name, JsonbColumnType())

/**
 * `stored || incoming`: Postgres's shallow JSONB merge. Keys in [incoming]
 * win; keys only in [stored] are kept. Used where an older client may send
 * an object without fields a newer client added (see the settings upsert).
 */
fun jsonbMerge(stored: Column<String>, incoming: Expression<String>): Expression<String> =
    object : ExpressionWithColumnType<String>() {
        override val columnType: IColumnType<String> = stored.columnType

        override fun toQueryBuilder(queryBuilder: QueryBuilder) {
            queryBuilder.append("(")
            queryBuilder.append(stored)
            queryBuilder.append(" || ")
            queryBuilder.append(incoming)
            queryBuilder.append(")")
        }
    }
