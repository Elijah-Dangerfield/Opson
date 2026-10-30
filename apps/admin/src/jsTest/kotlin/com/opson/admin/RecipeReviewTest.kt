package com.opson.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecipeReviewTest {

    @Test
    fun reasonsReadMostReportedFirst() {
        assertEquals("2 spam, 1 unsafe eating", RecipeReview.reasons(mapOf("unsafe" to 1, "spam" to 2)))
        assertEquals("1 something_new", RecipeReview.reasons(mapOf("something_new" to 1)))
    }

    @Test
    fun onlyAPublicRecipeCanBeRemoved_andOnlyAHiddenOrRemovedOneRestored() {
        assertTrue(RecipeReview.canRemove("live"))
        assertTrue(RecipeReview.canRemove("hidden"))
        assertFalse(RecipeReview.canRemove("removed"))
        assertTrue(RecipeReview.canRestore("removed"))
        assertFalse(RecipeReview.canRestore("live"))
    }

    @Test
    fun theLinkIsTheOnePeopleShare() {
        assertEquals("https://www.opsontracker.com/r/abcd2345", RecipeReview.link("abcd2345"))
        assertEquals(null, RecipeReview.link(null))
    }

    @Test
    fun aListDecodesFromTheServersShape() {
        val body = """{"filter":"reported","recipes":[{"id":"r","code":"abcd2345","name":"Chili","ownerId":"u","showName":false,""" +
            """"state":"hidden","servings":6.0,"ingredientCount":4,"openReports":3,"reasons":{"spam":3},"updatedAtEpochMs":1,"saveCount":0}]}"""
        val list = adminJson.decodeFromString(AdminRecipeListDto.serializer(), body)
        assertEquals("Hidden, waiting for a look", RecipeReview.state(list.recipes.single().state))
    }
}
