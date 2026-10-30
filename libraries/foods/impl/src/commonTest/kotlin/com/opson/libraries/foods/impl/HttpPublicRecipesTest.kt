package com.opson.libraries.foods.impl

import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Recipe
import com.opson.libraries.diary.RecipeShareState
import com.opson.libraries.diary.RecipeSharing
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.PublicRecipeLookup
import com.opson.libraries.foods.PublishResult
import com.opson.libraries.foods.RecipeActionResult
import com.opson.libraries.foods.RecipeReportReason
import com.opson.libraries.networking.InternalNetworkingApi
import com.opson.libraries.networking.NetworkClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

@OptIn(InternalNetworkingApi::class)
class HttpPublicRecipesTest : CoroutineTest() {

    private class FakeNetworkClient(handler: MockRequestHandler) : NetworkClient {
        private val http = HttpClient(MockEngine(handler)) {
            expectSuccess = true
            install(HttpTimeout) { requestTimeoutMillis = 30_000 }
            // Strict, as a debug build decodes: a response field the DTOs leave out fails here.
            install(ContentNegotiation) { json(Json { explicitNulls = false }) }
        }
        override val client: HttpClient get() = http
        override val authenticatedClient: HttpClient get() = http
        override suspend fun awaitAuthReady() = Unit
    }

    private fun recipes(status: HttpStatusCode, body: String = "{}", seen: MutableList<String>? = null) = HttpPublicRecipes(
        FakeNetworkClient { request ->
            seen?.add(request.url.encodedPath)
            (request.body as? OutgoingContent.ByteArrayContent)?.let { seen?.add(it.bytes().decodeToString()) }
            respond(body, status, headersOf(HttpHeaders.ContentType, "application/json"))
        },
    )

    private val chili = Recipe(
        id = "00000000-0000-4000-8000-0000000000a1",
        name = "Chili",
        servings = 6.0,
        ingredients = listOf(LogDraft(name = "Black beans", precision = Precision.Weighed, foodId = "f1", grams = 800.0, kcal = 728.0)),
        updatedAtEpochMs = 1_000,
    )

    private val shared = """
        {"code":"abcd2345","name":"Chili","servings":6.0,"by":null,
         "ingredients":[{"name":"Black beans","amountText":"2 cans","grams":800.0,"nutrients":{"kcal":728.0},"foodId":"f1","checked":true}],
         "total":{"kcal":728.0},"perServing":{"kcal":121.33},"checkedCount":1,"updatedAtEpochMs":1000}
    """.trimIndent()

    @Test
    fun aSharedRecipeReadsBack_andItsServingIsASixth() = runUnitTest {
        val found = assertIs<PublicRecipeLookup.Found>(recipes(HttpStatusCode.OK, shared).get("abcd2345"))
        assertEquals("Chili", found.recipe.name)
        assertEquals(1, found.recipe.checkedCount)
        assertEquals(728.0 / 6, found.recipe.servingDraft().kcal!!, 1e-9)
        val draft = found.recipe.toDraft()
        assertEquals("abcd2345", draft.savedFrom)
        assertEquals("f1", draft.ingredients.single().foodId)
    }

    @Test
    fun goneAndUnknownCodesSaySo() = runUnitTest {
        assertEquals(PublicRecipeLookup.NotShared, recipes(HttpStatusCode.Gone).get("abcd2345"))
        assertEquals(PublicRecipeLookup.NotFound, recipes(HttpStatusCode.NotFound).get("abcd2345"))
        assertEquals(PublicRecipeLookup.Failed(offline = false), recipes(HttpStatusCode.ServiceUnavailable).get("abcd2345"))
    }

    @Test
    fun sharingSendsTheRowWithItsStamp() = runUnitTest {
        val seen = mutableListOf<String>()
        val result = recipes(HttpStatusCode.OK, """{"code":"abcd2345","state":"live"}""", seen).publish(chili, showName = true)

        assertEquals(PublishResult.Published(RecipeSharing("abcd2345", RecipeShareState.Live, showName = true)), result)
        assertEquals("/v1/recipes/${chili.id}/publish", seen[0])
        val body = Json.parseToJsonElement(seen[1]).jsonObject
        val row = body.getValue("recipe").jsonObject
        assertEquals("1000", row.getValue("clientUpdatedAtEpochMs").jsonPrimitive.content)
        assertEquals("Black beans", row.getValue("items").jsonArray.single().jsonObject.getValue("name").jsonPrimitive.content)
        assertEquals("true", body.getValue("showName").jsonPrimitive.content)
    }

    @Test
    fun aRemovedRecipeIsToldApartFromARestrictedAccount() = runUnitTest {
        val removed = """{"error":{"code":"removed","message":"x"}}"""
        val restricted = """{"error":{"code":"contributions_restricted","message":"x"}}"""
        assertEquals(PublishResult.Removed, recipes(HttpStatusCode.Forbidden, removed).publish(chili, false))
        assertEquals(PublishResult.Restricted, recipes(HttpStatusCode.Forbidden, restricted).publish(chili, false))
        assertEquals(PublishResult.TooMany, recipes(HttpStatusCode.TooManyRequests).publish(chili, false))
    }

    @Test
    fun aReportGoesToTheCodesRoute() = runUnitTest {
        val seen = mutableListOf<String>()
        val result = recipes(HttpStatusCode.Created, seen = seen).report("abcd2345", "r1", RecipeReportReason.Unsafe, "  ")
        assertEquals(RecipeActionResult.Done, result)
        assertEquals("/v1/recipes/public/abcd2345/reports", seen[0])
        val body = Json.parseToJsonElement(seen[1]).jsonObject
        assertEquals("unsafe", body.getValue("reason").jsonPrimitive.content)
        assertTrue("note" !in body, "a blank note is left out")
    }

    @Test
    fun aShortQueryNeverAsks() = runUnitTest {
        val seen = mutableListOf<String>()
        assertTrue(recipes(HttpStatusCode.OK, """{"query":"c","recipes":[]}""", seen).search(" c ").isEmpty())
        assertTrue(seen.isEmpty())
    }
}
