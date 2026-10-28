package com.opson.server.routes

import com.opson.server.domain.DayRecord
import com.opson.server.domain.DiaryRow
import com.opson.server.domain.ExportRepository
import com.opson.server.domain.LogEntry
import com.opson.server.domain.LogMethod
import com.opson.server.domain.MealSlot
import com.opson.server.domain.Nutrients
import com.opson.server.domain.Precision
import com.opson.server.domain.UserId
import com.opson.server.domain.UserSettings
import com.opson.server.domain.WeighIn
import com.opson.server.plugins.installAuthentication
import com.opson.server.plugins.installRateLimits
import com.opson.server.plugins.installSerialization
import com.opson.server.plugins.installStatusPages
import com.opson.server.testing.TestJwt
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsBytes
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.routing.routing
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import java.time.LocalDate
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class ExportRoutesTest {

    private val userId = UserId(UUID.fromString("33333333-3333-3333-3333-333333333333"))
    private val t0 = Instant.parse("2026-10-25T08:30:00Z")

    private class FakeExportRepository(private val rows: List<DiaryRow>) : ExportRepository {
        val users = mutableListOf<UserId>()
        override suspend fun export(userId: UserId, sink: suspend (DiaryRow) -> Unit) {
            users += userId
            rows.forEach { sink(it) }
        }
    }

    private fun entry(name: String, slot: MealSlot, at: Instant, nutrients: Nutrients, amount: String?, grams: Double?, precision: Precision, method: LogMethod) =
        LogEntry(
            id = UUID.randomUUID(), day = LocalDate.parse("2026-10-25"), slot = slot, loggedAt = at, foodId = null, customFoodId = null,
            name = name, amountText = amount, quantity = null, unit = null, grams = grams, precision = precision, nutrients = nutrients,
            micros = null, method = method, clientUpdatedAt = at, deleted = false,
        )

    private val rows = listOf(
        entry(
            "Toast, with \"butter\", lots", MealSlot.Breakfast, t0, Nutrients(kcal = 180.0, protein = 6.5), "2 slices", 60.0,
            Precision.Estimate, LogMethod.Describe,
        ),
        entry(
            "=HYPERLINK(\"x\")", MealSlot.Lunch, Instant.parse("2026-10-25T12:00:00Z"), Nutrients(), null, null,
            Precision.Named, LogMethod.QuickNumber,
        ),
        WeighIn(UUID.randomUUID(), Instant.parse("2026-10-24T07:00:00Z"), 70.4, t0, false),
        DayRecord(LocalDate.parse("2026-10-25"), 1500, null, "A note", emptyMap(), null, t0, false),
        DayRecord(LocalDate.parse("2026-10-26"), null, null, "No water today", emptyMap(), null, t0, false),
        UserSettings("""{"numbers":{"preset":"no_numbers"}}""", null, t0, false),
    )

    private fun ApplicationTestBuilder.serve(repo: ExportRepository) {
        application {
            installSerialization()
            installStatusPages()
            installRateLimits()
            installAuthentication(TestJwt.verification)
            routing { exportRoutes(repo, clock = object : Clock { override fun now() = t0 }) }
        }
    }

    @Test
    fun theZipHoldsEveryFile_withLocalTimesInThePhonesZone() = runTest {
        val repo = FakeExportRepository(rows)
        testApplication {
            serve(repo)
            val resp = client.get("/v1/export.zip") {
                header(HttpHeaders.Authorization, TestJwt.bearer(userId))
                header(TIME_ZONE_HEADER, "America/New_York")
            }

            assertEquals(HttpStatusCode.OK, resp.status)
            assertEquals(ContentType.Application.Zip, resp.contentType()?.withoutParameters())
            assertTrue(resp.headers[HttpHeaders.ContentDisposition].orEmpty().contains("opson-export.zip"))
            val files = unzip(resp.bodyAsBytes())
            assertEquals(
                setOf(EXPORT_README, EXPORT_SETTINGS) + ExportFile.entries.map { it.fileName },
                files.keys,
            )
            val entries = files.getValue("entries.csv").csvLines()
            assertEquals(ExportFile.Entries.header.joinToString(","), entries.first())
            assertTrue(entries[1].startsWith("2026-10-25,04:30,America/New_York,2026-10-25T08:30:00Z,breakfast,"), entries[1])
            assertTrue(files.getValue(EXPORT_README).contains("America/New_York"))
            assertTrue(files.getValue(EXPORT_SETTINGS).contains("\"no_numbers\""))
            assertEquals(listOf(userId), repo.users)
        }
    }

    @Test
    fun anUnknownZone_fallsBackToUtc() = runTest {
        testApplication {
            serve(FakeExportRepository(rows))
            val resp = client.get("/v1/export.zip") {
                header(HttpHeaders.Authorization, TestJwt.bearer(userId))
                header(TIME_ZONE_HEADER, "Mars/Olympus_Mons")
            }
            val entries = unzip(resp.bodyAsBytes()).getValue("entries.csv").csvLines()
            assertTrue(entries[1].startsWith("2026-10-25,08:30,UTC,"), entries[1])
        }
    }

    /** What a 0.1 phone's Export button gets: unchanged, and none of the kinds it never had. */
    @Test
    fun theCsvStaysThe01Shape() = runTest {
        val repo = FakeExportRepository(rows)
        testApplication {
            serve(repo)
            val resp = client.get("/v1/export.csv") { header(HttpHeaders.Authorization, TestJwt.bearer(userId)) }

            assertEquals(HttpStatusCode.OK, resp.status)
            assertEquals(ContentType.Text.CSV, resp.contentType()?.withoutParameters())
            assertTrue(resp.headers[HttpHeaders.ContentDisposition].orEmpty().contains("opson-export.csv"))
            assertEquals(
                listOf(
                    EXPORT_HEADER.joinToString(","),
                    "entry,2026-10-25,2026-10-25T08:30:00Z,breakfast,\"Toast, with \"\"butter\"\", lots\",2 slices,60,180,6.5,,,,estimate,describe,,",
                    "entry,2026-10-25,2026-10-25T12:00:00Z,lunch,\"'=HYPERLINK(\"\"x\"\")\",,,,,,,,named,quick_number,,",
                    "weight,2026-10-24,2026-10-24T07:00:00Z,,,,,,,,,,,,70.4,",
                    "water,2026-10-25,,,,,,,,,,,,,,1500",
                ),
                resp.bodyAsText().csvLines(),
            )
            assertEquals(listOf(userId), repo.users)
        }
    }

    @Test
    fun exportsAreLimitedPerHour_acrossBothRoutes() = runTest {
        testApplication {
            serve(FakeExportRepository(rows))
            val statuses = (1..11).map { i ->
                client.get(if (i % 2 == 0) "/v1/export.csv" else "/v1/export.zip") {
                    header(HttpHeaders.Authorization, TestJwt.bearer(userId))
                }.status
            }
            assertEquals(List(10) { HttpStatusCode.OK } + HttpStatusCode.TooManyRequests, statuses)
        }
    }

    @Test
    fun requiresAuth() = runTest {
        val repo = FakeExportRepository(rows)
        testApplication {
            serve(repo)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/export.csv").status)
            assertEquals(HttpStatusCode.Unauthorized, client.get("/v1/export.zip").status)
            assertTrue(repo.users.isEmpty())
        }
    }
}
