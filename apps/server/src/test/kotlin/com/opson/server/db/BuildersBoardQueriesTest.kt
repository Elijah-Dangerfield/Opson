package com.opson.server.db

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jetbrains.exposed.sql.transactions.TransactionManager
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Every query on the Builders board (`ops/grafana/builders.json`) and in its alert rules
 * (`ops/grafana/alerts/builders.json`) runs, as `grafana_ro`, against the migrated schema. A
 * renamed column or a view the role can't read fails here instead of in Grafana, where it only
 * shows as an error on a panel nobody is looking at, or an alert that never fires.
 */
class BuildersBoardQueriesTest : DatabaseTest() {

    @Test
    fun everyPanelAndAlertQueryRunsAsGrafana() {
        val queries = sqlIn(file("builders.json")) + sqlIn(file("alerts/builders.json"))
        assertTrue(queries.size >= MINIMUM_QUERIES, "only ${queries.size} queries found, so the files moved or parsed as empty")
        val failures = queries.mapNotNull { query ->
            val runnable = query.replace(TIME_FILTER) { "(${it.groupValues[1]}) > now() - interval '30 days'" }
            runCatching {
                database.blockingTransaction {
                    TransactionManager.current().exec("SET LOCAL ROLE grafana_ro")
                    TransactionManager.current().exec(runnable) { rs -> while (rs.next()) Unit }
                }
            }.exceptionOrNull()?.let { "$query\n  ${it.message?.lineSequence()?.firstOrNull()}" }
        }
        assertTrue(failures.isEmpty(), "These queries fail as grafana_ro:\n" + failures.joinToString("\n"))
    }

    private fun file(name: String): File {
        // Gradle runs tests from apps/server.
        val f = File("../../ops/grafana/$name")
        check(f.isFile) { "no ${f.absolutePath}" }
        return f
    }

    private fun sqlIn(file: File): List<String> {
        val out = mutableListOf<String>()
        fun walk(element: JsonElement) {
            when (element) {
                is JsonObject -> element.forEach { (key, value) ->
                    if (key == "rawSql" && value is JsonPrimitive) out += value.content else walk(value)
                }
                is JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(Json.parseToJsonElement(file.readText()))
        return out
    }

    private companion object {
        val TIME_FILTER = Regex("""\${'$'}__timeFilter\(([^()]*(?:\([^()]*\))?[^()]*)\)""")
        const val MINIMUM_QUERIES = 30
    }
}
