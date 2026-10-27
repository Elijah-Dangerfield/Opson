package com.opson.libraries.ui.components.text

import kotlin.test.Test
import kotlin.test.assertEquals

class TypedTextTest {

    private val sent = mutableListOf<String>()
    private val typed = TypedText("").also { it.send = { value -> sent += value } }

    @Test
    fun `late echoes do not rewind fast typing`() {
        typed.onType("o")
        typed.onType("oa")
        typed.onType("oat")
        typed.onValue("o")
        typed.onValue("oa")

        assertEquals("oat", typed.text)
        assertEquals(listOf("o", "oa", "oat"), sent)
    }

    @Test
    fun `an echo that skips ahead still lets later echoes match`() {
        typed.onType("b")
        typed.onType("ba")
        typed.onType("ban")
        typed.onValue("ba")
        typed.onType("bana")
        typed.onValue("ban")
        typed.onValue("bana")

        assertEquals("bana", typed.text)
    }

    @Test
    fun `a value the field never sent replaces the text`() {
        typed.onType("rice")
        typed.onValue("rice")
        typed.onValue("")

        assertEquals("", typed.text)
    }

    @Test
    fun `a trimmed value from the view model wins`() {
        typed.onType("rice ")
        typed.onValue("rice")

        assertEquals("rice", typed.text)
    }

    @Test
    fun `an edit the view model refused is undone when typing settles`() {
        typed.onType("abcd")
        // The view model capped the length, kept "abc", and so sent nothing back.
        typed.settle("abc")

        assertEquals("abc", typed.text)
    }

    @Test
    fun `a keystroke a filter turned into no change is not sent`() {
        typed.onType("1.5")
        typed.onType("1.5")

        assertEquals(listOf("1.5"), sent)
    }
}
