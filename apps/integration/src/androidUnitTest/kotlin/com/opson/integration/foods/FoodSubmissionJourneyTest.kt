package com.opson.integration.foods

import com.opson.features.logger.impl.LogPick
import com.opson.features.logger.impl.PickSource
import com.opson.features.logger.impl.RecentMissedBarcodePairing
import com.opson.integration.helpers.Harness
import com.opson.integration.helpers.InProcessServer
import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.awaitUntil
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.Precision
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.FoodSubmission
import com.opson.libraries.foods.LabelCheckCode
import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.LabelParse
import com.opson.libraries.foods.SubmissionOrigin
import com.opson.libraries.foods.SubmitResult
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * **Adding a food from its pack.** A scan misses; the person photographs the
 * pack; the server reads the label (a local stand-in for Anthropic's API,
 * through the real SDK and the real conversions and checks); the person
 * submits; the food is theirs at once and nobody else's; a reviewer approves
 * it through the admin API, photos and all; then another person's scan finds
 * it. And the other way a barcode gets attached: a miss followed by a search
 * pick, counted across people, attached by a reviewer.
 */
class FoodSubmissionJourneyTest : IntegrationTest() {

    private val json = Json { ignoreUnknownKeys = true }

    /** Enough of a JPEG for the server's type sniffing; the stand-in model never decodes it. */
    private val front = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte()) + Random.nextBytes(64)
    private val label = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE1.toByte()) + Random.nextBytes(64)

    @Test
    fun aMissedBarcodeBecomesEveryonesFoodOnceAReviewerApprovesIt() = integration {
        val code = randomEan13()
        val alice = client()
        val bob = client()

        assertNull(alice.foods.barcode(code).getOrThrow(), "nobody knows the code yet")
        alice.barcodeMisses.report(code)

        val read = assertIs<LabelParse.Read>(alice.foodLabels.parse(front, label))
        assertEquals("Oats & honey granola bar", read.facts.name)
        assertEquals(190.0, read.facts.perServing[LabelNutrient.Energy])
        assertEquals(0.6, read.confidence["protein_g"])
        assertEquals(emptyList(), read.checks, "the stand-in's label adds up")

        // The person changes the calories, which then no longer add up, and keeps their figure through the check.
        val corrected = read.facts.copy(perServing = read.facts.perServing + (LabelNutrient.Energy to 240.0))
        val submitted = assertIs<SubmitResult.Submitted>(
            alice.foodLabels.submit(
                FoodSubmission(
                    id = UUID.randomUUID().toString(),
                    parseId = read.parseId,
                    origin = SubmissionOrigin.Barcode,
                    barcode = code,
                    front = front,
                    label = label,
                    facts = corrected,
                    acknowledged = listOf(LabelCheckCode.EnergyMacrosMismatch),
                ),
            ),
        )

        // Theirs at once: their own scan finds it, and only theirs.
        assertEquals(submitted.foodId, alice.foods.barcode(code).getOrThrow()?.id)
        assertNull(bob.foods.barcode(code).getOrThrow(), "pending is the submitter's alone")
        assertTrue(bob.foods.detail(submitted.foodId).isFailure)
        assertTrue(bob.foods.search("honey granola bar").getOrThrow().foods.none { it.id == submitted.foodId })

        // The reviewer sees it, with both photos and the model's own reading.
        val queue = adminGet("/v1/admin/food-submissions?status=pending&limit=200")
        val row = queue["submissions"]!!.jsonArray.map { it.jsonObject }.single { it["id"]!!.jsonPrimitive.content == submitted.submissionId }
        assertEquals(listOf("energy_macros_mismatch"), row["failedChecks"]!!.jsonArray.map { it.jsonPrimitive.content })
        val detail = adminGet("/v1/admin/food-submissions/${submitted.submissionId}")
        assertEquals("4", detail["parsed"]!!.jsonObject["facts"]!!.jsonObject["perServing"]!!.jsonObject["protein_g"]!!.jsonPrimitive.content.substringBefore('.'))
        assertContentEquals(label, adminPhoto(submitted.submissionId, "label"))
        assertContentEquals(front, adminPhoto(submitted.submissionId, "front"))

        // Approved with the calories the label really says, and the protein written as 4.5.
        val fixed = """{"food":{"name":"Oats & honey granola bar","brand":"Hillside","servingSize":40.0,"servingUnit":"g","basis":"us",
            "perServing":{"energy_kcal":190.0,"protein_g":4.5,"carbs_g":29.0,"fat_g":7.0,"sodium_mg":150.0}}}"""
        val approved = adminPost("/v1/admin/food-submissions/${submitted.submissionId}/approve", fixed)
        assertEquals(HttpStatusCode.OK, approved.first, approved.second)

        val found = assertNotNull(bob.foods.barcode(code).getOrThrow(), "another person's scan finds it now")
        assertEquals(submitted.foodId, found.id)
        assertEquals(475.0, found.per100g.kcal, "190 kcal in 40 g")
        assertEquals(11.25, found.per100g.protein, "the reviewer's protein")
        assertTrue(found.verified)
        // A fresh phone: Bob's own search above is still in his five-minute cache.
        assertTrue(client().foods.search("honey granola bar").getOrThrow().foods.any { it.id == submitted.foodId })
    }

    @Test
    fun aMissThenASearchPickIsSuggestedAndAttachedByAReviewer() = integration {
        val code = randomEan13()
        val oats = server.seedFood("Rolled oats ${UUID.randomUUID().toString().take(8)}", kcalPer100g = 379.0, source = "usda_branded")
        val alice = client()
        val bob = client()
        val carol = client()

        listOf(alice, bob).forEach { phone ->
            // The logger's own pairing, over the app's real reporter: a miss, then oats from search.
            val pairing = RecentMissedBarcodePairing(phone.barcodeLinks, Clock.System)
            assertNull(phone.foods.barcode(code).getOrThrow())
            pairing.missed(code)
            val ref = FoodRef.Catalog(oats)
            pairing.logged(listOf(LogPick(ref.key, ref, LogDraft(name = "Rolled oats", precision = Precision.Estimate), PickSource.Search)))
        }

        val gtin = code.padStart(14, '0')
        awaitUntil { pairings(gtin) == 2 }
        assertNull(carol.foods.barcode(code).getOrThrow(), "nothing is attached on its own")

        val attach = adminPost("/v1/admin/barcode-links/attach", """{"gtin":"$gtin","foodId":"$oats"}""")
        assertEquals(HttpStatusCode.NoContent, attach.first, attach.second)

        assertEquals(oats, carol.foods.barcode(code).getOrThrow()?.id)
        val rows = server.query("SELECT pairings, status FROM barcode_link_suggestions WHERE gtin = ?", gtin).single()
        assertEquals("attached", rows["status"])
        assertTrue(server.query("SELECT column_name FROM information_schema.columns WHERE table_name = 'barcode_link_suggestions'").none { "user" in (it["column_name"] as String) })
    }

    @Test
    fun withNoModelKeyTheReadIsUnavailableAndASubmissionStillWorks() = integration(photoEstimates = false) {
        val phone = client()

        assertEquals(LabelParse.Unavailable, phone.foodLabels.parse(front, label))
        val result = phone.foodLabels.submit(
            FoodSubmission(
                id = UUID.randomUUID().toString(),
                parseId = null,
                origin = SubmissionOrigin.Search,
                barcode = null,
                front = front,
                label = label,
                facts = com.opson.libraries.foods.LabelFacts(name = "Rye crispbread", servingSize = 10.0, servingUnit = com.opson.libraries.foods.ServingUnit.Gram),
                acknowledged = emptyList(),
            ),
        )
        assertIs<SubmitResult.Submitted>(result)
        assertEquals(0, server.upstreams.labelReads.get())
    }

    private suspend fun Harness.pairings(gtin: String): Int = adminGet("/v1/admin/barcode-links")["links"]!!.jsonArray
        .map { it.jsonObject }
        .firstOrNull { it["gtin"]!!.jsonPrimitive.content == gtin }
        ?.get("pairings")?.jsonPrimitive?.int ?: 0

    private suspend fun Harness.adminGet(path: String) = http.get("${server.baseUrl}$path") {
        header("X-Admin-Token", InProcessServer.ADMIN_TOKEN)
    }.let { response ->
        check(response.status == HttpStatusCode.OK) { "admin GET $path answered ${response.status}: ${response.bodyAsText()}" }
        json.parseToJsonElement(response.bodyAsText()).jsonObject
    }

    private suspend fun Harness.adminPhoto(id: String, kind: String): ByteArray =
        http.get("${server.baseUrl}/v1/admin/food-submissions/$id/photos/$kind") {
            header("X-Admin-Token", InProcessServer.ADMIN_TOKEN)
        }.readRawBytes()

    private suspend fun Harness.adminPost(path: String, body: String): Pair<HttpStatusCode, String> =
        http.post("${server.baseUrl}$path") {
            header("X-Admin-Token", InProcessServer.ADMIN_TOKEN)
            header("X-Admin-Actor", "integration")
            contentType(ContentType.Application.Json)
            setBody(body)
        }.let { it.status to it.bodyAsText() }

    private fun randomEan13(): String {
        val body = "2" + (1..11).map { Random.nextInt(10) }.joinToString("")
        val total = body.reversed().withIndex().sumOf { (i, c) -> (c - '0') * if (i % 2 == 0) 3 else 1 }
        return body + (10 - total % 10) % 10
    }
}
