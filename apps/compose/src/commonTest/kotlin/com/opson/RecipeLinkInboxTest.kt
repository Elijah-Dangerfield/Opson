package com.opson

import com.opson.features.foods.PublicRecipeRoute
import com.opson.features.foods.impl.RecipeLinkRoutesImpl
import com.opson.libraries.diary.RecipeLinks
import com.opson.libraries.diary.RecipesRelease
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Where [RecipeLinkInbox] sends a recipe link: never nowhere. Over the real `RecipeLinkRoutesImpl`, with
 * `release.recipes` switched by [Release.on]. App.kt's own wiring
 * (reading onboarding and auth, resuming when Today is on top) is not covered here.
 */
class RecipeLinkInboxTest {
    private val release = Release()
    private val inbox = RecipeLinkInbox(RecipeLinkRoutesImpl(release))
    private val link = "https://www.opsontracker.com/r/abcd2345"

    @Test
    fun aReadyAppOpensTheRecipeInTheApp() {
        assertEquals(RecipeLinkInbox.Step.Open(PublicRecipeRoute("abcd2345")), inbox.receive(link, ready = true))
        assertNull(inbox.resume(), "nothing was held")
    }

    @Test
    fun withRecipesOffTheLinkOpensTheWebsitePageNotNothing() {
        release.on = false

        assertEquals(RecipeLinkInbox.Step.Web(RecipeLinks.page("abcd2345")), inbox.receive(link, ready = true))
    }

    @Test
    fun beforeSignInTheWebsitePageOpensAndTheAppPageWaitsForThem() {
        assertEquals(RecipeLinkInbox.Step.Web(RecipeLinks.page("abcd2345")), inbox.receive(link, ready = false))

        assertEquals(PublicRecipeRoute("abcd2345"), inbox.resume())
        assertNull(inbox.resume(), "it opens once")
    }

    @Test
    fun aHeldLinkTheAppCantShowAfterSignInIsLetGo() {
        inbox.receive(link, ready = false)
        release.on = false

        assertNull(inbox.resume())
        release.on = true
        assertNull(inbox.resume(), "already let go")
    }

    @Test
    fun anyOtherLinkIsLeftToTheCaller() {
        assertNull(inbox.receive("opson://auth/confirmed", ready = true))
        assertNull(inbox.receive("https://www.opsontracker.com/privacy", ready = false))
        assertNull(inbox.resume())
    }

    @Test
    fun theWebsitePageIsAnAddressTheAppDoesNotClaim() {
        val page = RecipeLinks.page("abcd2345")

        assertNull(RecipeLinks.codeFrom(page), "opening it from the app must not come back to the app")
        assertEquals("https://www.opsontracker.com/recipe/?c=abcd2345", page)
    }

    /** `release.recipes` as a switch, under the real routes. */
    private class Release : RecipesRelease {
        var on = true

        override fun isOn(): Boolean = on
    }
}
