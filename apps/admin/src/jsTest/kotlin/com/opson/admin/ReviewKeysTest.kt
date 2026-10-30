package com.opson.admin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewKeysTest {

    @Test
    fun movingKeysWorkWithOrWithoutAnOpenSubmission() {
        assertEquals(ReviewCommand.Next, ReviewKeys.command("j", modified = false, hasSelection = false))
        assertEquals(ReviewCommand.Next, ReviewKeys.command("ArrowDown", modified = false, hasSelection = true))
        assertEquals(ReviewCommand.Previous, ReviewKeys.command("k", modified = false, hasSelection = true))
        assertEquals(ReviewCommand.Legend, ReviewKeys.command("?", modified = false, hasSelection = false))
    }

    @Test
    fun decisionKeysNeedAnOpenSubmission() {
        listOf("a", "r", "s", "t", "Escape").forEach { key ->
            assertNull(ReviewKeys.command(key, modified = false, hasSelection = false), key)
        }
        assertEquals(ReviewCommand.Approve, ReviewKeys.command("a", modified = false, hasSelection = true))
        assertEquals(ReviewCommand.FocusReject, ReviewKeys.command("r", modified = false, hasSelection = true))
        assertEquals(ReviewCommand.Save, ReviewKeys.command("s", modified = false, hasSelection = true))
        assertEquals(ReviewCommand.ToggleText, ReviewKeys.command("t", modified = false, hasSelection = true))
        assertEquals(ReviewCommand.Close, ReviewKeys.command("Escape", modified = false, hasSelection = true))
    }

    @Test
    fun openOnlyWhenNothingIsOpenAndEnterLeavesButtonsAlone() {
        assertEquals(ReviewCommand.Open, ReviewKeys.command("o", modified = false, hasSelection = false))
        assertEquals(ReviewCommand.Open, ReviewKeys.command("Enter", modified = false, hasSelection = false))
        assertNull(ReviewKeys.command("Enter", modified = false, hasSelection = true))
        assertNull(ReviewKeys.command("Enter", modified = false, hasSelection = false, onControl = true))
    }

    @Test
    fun aModifiedKeyBelongsToTheBrowser() {
        assertNull(ReviewKeys.command("a", modified = true, hasSelection = true))
        assertNull(ReviewKeys.command("x", modified = false, hasSelection = true))
    }

    @Test
    fun movingStaysInsideTheQueue() {
        val ids = listOf("a", "b", "c")
        assertEquals("a", ReviewKeys.move(ids, null, ReviewCommand.Next))
        assertEquals("b", ReviewKeys.move(ids, "a", ReviewCommand.Next))
        assertEquals("c", ReviewKeys.move(ids, "c", ReviewCommand.Next))
        assertEquals("a", ReviewKeys.move(ids, "a", ReviewCommand.Previous))
        assertEquals("a", ReviewKeys.move(ids, "gone", ReviewCommand.Previous))
        assertNull(ReviewKeys.move(emptyList(), null, ReviewCommand.Next))
    }

    @Test
    fun afterADecisionTheNextOneOpens() {
        val ids = listOf("a", "b", "c")
        assertEquals("b", ReviewKeys.afterDecision(ids, "a"))
        assertEquals("b", ReviewKeys.afterDecision(ids, "c"), "the last one goes back to the one above")
        assertNull(ReviewKeys.afterDecision(listOf("a"), "a"))
    }

    @Test
    fun typingInAFieldIsNotACommand() {
        assertTrue(ReviewKeys.isTextTarget("input", editable = false))
        assertTrue(ReviewKeys.isTextTarget("TEXTAREA", editable = false))
        assertTrue(ReviewKeys.isTextTarget("SELECT", editable = false))
        assertTrue(ReviewKeys.isTextTarget("DIV", editable = true))
        assertFalse(ReviewKeys.isTextTarget("BUTTON", editable = false))
        assertFalse(ReviewKeys.isTextTarget(null, editable = false))
    }
}
