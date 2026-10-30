package com.opson.integration.recipes

import com.opson.integration.helpers.IntegrationTest
import com.opson.integration.helpers.randomUserId
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.RecipeDraft
import com.opson.libraries.diary.RecipeLinks
import com.opson.libraries.diary.RecipeShareState
import com.opson.libraries.diary.valueOrNull
import com.opson.libraries.foods.PublicRecipeLookup
import com.opson.libraries.foods.PublishResult
import com.opson.libraries.foods.RecipeActionResult
import com.opson.libraries.foods.RecipeReportReason
import kotlinx.coroutines.flow.first
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * **A recipe, shared and found (`release.recipes`).** Made on one phone, it syncs to the owner's
 * other phone; shared, anyone with the code reads it (the website with no account at all), with
 * its numbers worked out from the catalog; someone else saves and logs it, and reports it; the
 * owner stops sharing and the code goes quiet.
 */
class RecipeJourneyTest : IntegrationTest() {

    @Test
    fun aRecipeIsMadeSharedFoundSavedLoggedReportedAndStopped() = integration {
        val alice = randomUserId()
        val bob = randomUserId()
        val phone = client(alice)
        val tablet = client(alice)
        val beans = server.seedFood("Beans, black, canned", kcalPer100g = 91.0)

        // Made on the phone, with the creator's own (wrong) number for the beans.
        val recipe = phone.recipes.create(
            RecipeDraft(
                name = "Weeknight chili",
                servings = 6.0,
                ingredients = listOf(
                    LogDraft("Black beans", Precision.Weighed, foodId = beans, amountText = "2 cans", grams = 800.0, kcal = 1000.0),
                    LogDraft("Ground beef", Precision.Weighed, amountText = "1 lb", grams = 454.0, kcal = 1150.0),
                ),
                notes = "Simmer for an hour.",
            ),
        )
        phone.syncNow().getOrThrow()
        tablet.syncNow().getOrThrow()
        assertEquals("Weeknight chili", tablet.recipes.get(recipe.id)?.name, "the owner's other phone has it")

        // Shared, with the person's say-so (the app's consent sheet is where that happens).
        val shared = assertIs<PublishResult.Published>(phone.publicRecipes.publish(phone.recipes.get(recipe.id)!!, showName = false))
        val code = assertNotNull(shared.sharing.code)
        assertEquals(RecipeShareState.Live, shared.sharing.state)
        phone.recipes.markShared(recipe.id, shared.sharing)
        tablet.syncNow().getOrThrow()
        assertEquals(code, tablet.recipes.get(recipe.id)?.sharing?.code, "the other phone learns it's shared")

        // The website reads it with no account. (java.net.http: HttpURLConnection drops an Origin header.)
        val web = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder(URI("${server.baseUrl}/v1/recipes/public/$code")).header("Origin", "https://www.opsontracker.com").build(),
            HttpResponse.BodyHandlers.ofString(),
        )
        assertEquals(200, web.statusCode())
        assertEquals("*", web.headers().firstValue("Access-Control-Allow-Origin").orElse(null))
        assertTrue("Weeknight chili" in web.body())

        // Someone else finds it by its link and saves and logs it.
        val other = client(bob)
        val found = assertIs<PublicRecipeLookup.Found>(other.publicRecipes.get(RecipeLinks.codeFrom(RecipeLinks.web(code))!!)).recipe
        assertEquals(1, found.checkedCount)
        assertEquals(728.0, found.ingredients.first().nutrients.kcal!!, 0.01, "the catalog's numbers, not the creator's")
        assertTrue(other.publicRecipes.search("chili").any { it.code == code })
        val copy = other.recipes.create(found.toDraft())
        other.publicRecipes.countSave(code)
        assertEquals(code, copy.savedFrom)
        val logged = other.diary.addItems(other.today(), MealSlot.Dinner, listOf(copy.servingDraft()), LogMethod.Saved).valueOrNull()
        assertEquals("Weeknight chili", logged?.single()?.name)
        assertEquals((728.0 + 1150.0) / 6, logged!!.single().kcal!!, 0.01)
        other.syncNow().getOrThrow()
        assertEquals(1L, server.countFor("recipes", bob))
        assertEquals(listOf(1), server.query("SELECT save_count FROM recipes WHERE public_code = ?", code).map { it["save_count"] })

        // Reported, then the owner stops sharing: the code reads as not shared.
        assertEquals(RecipeActionResult.Done, other.publicRecipes.report(code, UUID.randomUUID().toString(), RecipeReportReason.WrongNutrition, "Beans look off"))
        assertEquals(RecipeActionResult.Done, phone.publicRecipes.unpublish(recipe.id))
        assertEquals(PublicRecipeLookup.NotShared, other.publicRecipes.get(code))
        assertTrue(other.recipes.observeAll().first().any { it.id == copy.id }, "a saved copy stays its saver's")
    }
}
