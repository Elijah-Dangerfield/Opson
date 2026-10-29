package com.opson.server.db

import org.jetbrains.exposed.sql.BooleanColumnType
import org.jetbrains.exposed.sql.IColumnType
import org.jetbrains.exposed.sql.IntegerColumnType
import org.jetbrains.exposed.sql.TextColumnType
import org.jetbrains.exposed.sql.UUIDColumnType
import org.jetbrains.exposed.sql.javatime.JavaInstantColumnType
import org.jetbrains.exposed.sql.statements.StatementType
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/*
 * Bound-parameter SQL for code that reads views, `auth.users` or SQL functions Exposed doesn't
 * model (moderation, provenance, devices). Call inside `database.transaction { }`. Every value is
 * a bound argument, never spliced into the text.
 */

internal typealias SqlArg = Pair<IColumnType<*>, Any?>

internal fun uuidArg(value: UUID?): SqlArg = UUIDColumnType() to value

internal fun textArg(value: String?): SqlArg = TextColumnType() to value

internal fun intArg(value: Int): SqlArg = IntegerColumnType() to value

internal fun boolArg(value: Boolean): SqlArg = BooleanColumnType() to value

@OptIn(ExperimentalTime::class)
internal fun instantArg(value: Instant?): SqlArg = JavaInstantColumnType() to value?.toJavaInstant()

/** Rows of a SELECT, or of any statement with `RETURNING`. */
internal fun <T> queryRows(sql: String, args: List<SqlArg> = emptyList(), row: (ResultSet) -> T): List<T> {
    val out = mutableListOf<T>()
    TransactionManager.current().exec(stmt = sql, args = args, explicitStatementType = StatementType.SELECT) { rs ->
        while (rs.next()) out += row(rs)
    }
    return out
}

/** A statement with no result. */
internal fun execute(sql: String, args: List<SqlArg> = emptyList()) {
    TransactionManager.current().exec(stmt = sql, args = args)
}

@OptIn(ExperimentalTime::class)
internal fun ResultSet.instantOrNull(column: String): Instant? =
    getObject(column, OffsetDateTime::class.java)?.toInstant()?.toKotlinInstant()

internal fun ResultSet.uuidOrNull(column: String): UUID? = getObject(column, UUID::class.java)
