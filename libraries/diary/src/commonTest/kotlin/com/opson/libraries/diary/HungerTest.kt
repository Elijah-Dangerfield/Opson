package com.opson.libraries.diary

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HungerTest {

    @Test
    fun everyStepReadsBackFromItsWireValue() {
        Hunger.entries.forEach { assertEquals(it, Hunger.fromWire(it.wire)) }
    }

    /** Rows from the four-step scale, and the app versions that wrote them, keep meaning what they said. */
    @Test
    fun theOldScalesValuesStillRead() {
        assertEquals(Hunger.Starving, Hunger.fromWire("very_hungry"))
        assertEquals(Hunger.Hungry, Hunger.fromWire("hungry"))
        assertEquals(Hunger.Hungry, Hunger.fromWire("peckish"))
        assertEquals(Hunger.Neutral, Hunger.fromWire("not_hungry"))
    }

    @Test
    fun anUnknownValueIsNoAnswer() {
        assertNull(Hunger.fromWire("ravenous"))
        assertNull(Hunger.fromWire(null))
    }
}
