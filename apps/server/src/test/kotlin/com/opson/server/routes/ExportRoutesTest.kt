package com.opson.server.routes

import com.opson.server.domain.ExportRecord
import com.opson.server.domain.ExportRepository
import com.opson.server.domain.LogMethod
import com.opson.server.domain.MealSlot
import com.opson.server.domain.Nutrients
import com.opson.server.domain.Precision
import com.opson.server.domain.UserId
import com.opson.server.plugins.installAuthentication
import com.opson.server.plugins.installRateLimits
import com.opson.server.plugins.installSerialization
import com.opson.server.plugins.installStatusPages
import com.opson.server.testing.TestJwt
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Instant

class ExportRoutesTest {

    private val userId = UserId(UUID.fromString("33333333-3333-3333-3333-333333333333"))

    private class FakeExportRepository(private val records: List<ExportRecord>) : ExportRepository {
        val users = mutableListOf<UserId>()
        override suspend fun export(userId: UserId, sink: suspend (ExportRecord) -> Unit) {
            users += userId
            records.forEach { sink(it) }
        }
    }

    private val records = listOf(
        ExportRecord.Entry(
            day = LocalDate.parse("2026-10-25"),
            loggedAt = Instant.parse("2026-10-25T08:30:00Z"),
            slot = MealSlot.Breakfast,
            name = "Toast, with \"butter\", lots",
            amountText = "2 slices",
            grams = 60.0,
            nutrients = Nutrients(kcal = 180.0, protein = 6.5),
            precision = Precision.Estimate,
            method = LogMethod.Describe,
        ),
        ExportRecord.Entry(
            day = LocalDate.parse("2026-10-25"),
            loggedAt = Instant.parse("2026-10-25T12:00:00Z"),
            slot = MealSlot.Lunch,
            name = "=HYPERLINK(\"x\")",
            amountText = null,
            grams = null,
            nutrients = Nutrients(),
            precision = Precision.Named,
            method = LogMethod.QuickNumber,
        ),
        ExportRecord.Weight(Instant.parse("2026-10-24T07:00:00Z"), 70.4),
        ExportRecord.Water(LocalDate.parse("2026-10-25"), 1500),
    )

    @Test
    fun streamsTheCallersRowsAsCsv() = runTest {
        val repo = FakeExportRepository(records)
        testApplication {
            application {
                installSerialization()
                installStatusPages()
                installRateLimits()
                installAuthentication(TestJwt.verification)
                routing { exportRoutes(repo) }
            }
            val resp = client.get("/v1/export.csv") { header(HttpHeaders.Authorization, TestJwt.bearer(userId)) }

            assertEquals(HttpStatusCode.OK, resp.status)
            assertEquals(ContentType.Text.CSV, resp.contentType()?.withoutParameters())
            assertTrue(resp.headers[HttpHeaders.ContentDisposition].orEmpty().contains("opson-export.csv"))
            val lines = resp.bodyAsText().split("\r\n").filter { it.isNotEmpty() }
            assertEquals(
                listOf(
                    EXPORT_HEADER.joinToString(","),
                    "entry,2026-10-25,2026-10-25T08:30:00Z,breakfast,\"Toast, with \"\"butter\"\", lots\",2 slices,60,180,6.5,,,,estimate,describe,,",
                    "entry,2026-10-25,2026-10-25T12:00:00Z,lunch,\"'=HYPERLINK(\"\"x\"\")\",,,,,,,,named,quick_number,,",
                    "weight,2026-10-24,2026-10-24T07:00:00Z,,,,,,,,,,,,70.4,",
                    "water,2026-10-25,,,,,,,,,,,,,,1500",
                ),
                lines,
            )
            assertEquals(listOf(userId), repo.users)
        }
    }

    @Test
    fun exportsAreLimitedPerHour() = runTest {
        val repo = FakeExportRepository(records)
        testApplication {
            application {
                installSerialization()
                installStatusPages()
                installRateLimits()
                installAuthentication(TestJwt.verification)
                routing { exportRoutes(repo) }
            }
            val statuses = (1..11).map {
                client.get("/v1/export.csv") { header(HttpHeaders.Authorization, TestJwt.bearer(userId)) }.status
            }
            assertEquals(List(10) { HttpStatusCode.OK } + HttpStatusCode.TooManyRequests, statuses)
        }
    }

    @Test
    fun requiresAuth() = runTest {
        val repo = FakeExportRepository(records)
        testApplication {
            application {
                installSerialization()
                installStatusPages()
                installRateLimits()
                installAuthentication(TestJwt.verification)
                routing { exportRoutes(repo) }
            }
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/export.csv").status)
            assertTrue(repo.users.isEmpty())
        }
    }
}
