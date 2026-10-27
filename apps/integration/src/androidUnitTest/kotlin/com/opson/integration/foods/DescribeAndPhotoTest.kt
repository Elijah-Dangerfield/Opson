package com.opson.integration.foods

import com.opson.integration.helpers.FakeUpstreams.PhotoFood
import com.opson.integration.helpers.IntegrationTest
import com.opson.libraries.foods.ParsedItem
import com.opson.libraries.foods.PhotoEstimate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * **"Describe it" and photo estimates.** Typed text comes back as lines; a
 * meal photo, read by the model (a local stand-in for Anthropic's API, through
 * the real SDK), comes back as the same rows matched against the same catalog;
 * a server with no model key says the feature is unavailable rather than
 * failing.
 */
class DescribeAndPhotoTest : IntegrationTest() {

    private val word = (1..10).map { 'a' + Random.nextInt(26) }.joinToString("")

    /** Enough of a JPEG for the server's type sniffing; the stand-in model never decodes it. */
    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10) + ByteArray(64)

    @Test
    fun describe_turnsTextIntoLines() = integration {
        val phone = client()

        val items = phone.foods.describe("2 eggs and a slice of toast").getOrThrow()

        assertEquals(2, items.size, "one line per food: $items")
        assertEquals(2.0, items.first().quantity)
    }

    @Test
    fun describe_bringsTheFoodAndWhatToSwapTo() = integration {
        val plain = server.seedFood("$word cake, plain", kcalPer100g = 300.0, portionsJson = """[{"label":"1 piece","grams":80}]""")
        val iced = server.seedFood("$word cake, iced", kcalPer100g = 380.0, portionsJson = """[{"label":"1 piece","grams":90}]""")
        val phone = client()

        val item = phone.foods.describe("2 pieces of $word cake").getOrThrow().single()

        assertEquals(plain, item.food?.id, "the food itself, so the amount can change on the phone")
        assertEquals(listOf("1 piece"), item.food?.portions?.map { it.label })
        assertEquals(listOf(iced), item.alternatives.map { it.id })
        assertEquals(160.0, item.grams)
    }

    @Test
    fun photo_comesBackAsTheSameRowsDescribeGives() = integration {
        val broccoli = server.seedFood("$word broccoli", kcalPer100g = 35.0, portionsJson = """[{"label":"1 cup","grams":156}]""")
        server.upstreams.photoFoods = listOf(PhotoFood("$word broccoli", quantity = 1.0, unit = "cup", grams = 150.0))
        val phone = client()

        val described = phone.foods.describe("1 cup $word broccoli").getOrThrow().single()
        val estimate = assertIs<PhotoEstimate.Items>(phone.photoEstimator.estimate(jpeg, note = "steamed"))
        val photographed = estimate.items.single()

        assertEquals(broccoli, photographed.foodId, "matched to the catalog food")
        assertEquals(156.0, photographed.grams, "the catalog's cup, not the model's guess")
        assertEquals(described.shape(), photographed.shape())
    }

    @Test
    fun photo_withNoModelConfigured_isUnavailable() = integration(photoEstimates = false) {
        val phone = client()

        assertEquals(PhotoEstimate.Unavailable, phone.photoEstimator.estimate(jpeg, note = null))
    }

    /** What a row means, leaving out the words it was asked with. */
    private fun ParsedItem.shape() = listOf(foodId, name, quantity, unit, portionLabel, grams, kcal, protein)
}
