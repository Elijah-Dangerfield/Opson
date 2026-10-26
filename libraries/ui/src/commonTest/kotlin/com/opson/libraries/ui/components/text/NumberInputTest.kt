package com.opson.libraries.ui.components.text

import kotlin.test.Test
import kotlin.test.assertEquals

class NumberInputTest {

    @Test
    fun keepsDigitsAndOnePoint() {
        assertEquals("72.6", NumberInput.sanitize("72.6", allowDecimal = true))
        assertEquals("72.65", NumberInput.sanitize("72.6.5", allowDecimal = true))
        assertEquals(".5", NumberInput.sanitize(".5", allowDecimal = true))
    }

    @Test
    fun aCommaIsADecimalPoint() {
        assertEquals("1.5", NumberInput.sanitize("1,5 kg", allowDecimal = true))
    }

    @Test
    fun wholeNumbersDropThePoint() {
        assertEquals("2505", NumberInput.sanitize("250.5", allowDecimal = false))
        assertEquals("1000", NumberInput.sanitize("1,000 ml", allowDecimal = false))
    }

    @Test
    fun dropsEverythingElse() {
        assertEquals("", NumberInput.sanitize("abc", allowDecimal = true))
        assertEquals("12", NumberInput.sanitize(" -1 2 ", allowDecimal = true))
        assertEquals("12", NumberInput.sanitize("١٢12", allowDecimal = true))
    }
}
