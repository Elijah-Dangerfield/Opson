package com.opson.libraries.navigation.impl

import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AccessRestoredDateTest {

    @Test
    fun theServersTimestampReadsAsADateOnThePhonesCalendar() {
        assertEquals("Aug 1, 2026", accessRestoredDate("2026-08-01T12:00:00Z", TimeZone.UTC))
        assertEquals("Jul 31, 2026", accessRestoredDate("2026-08-01T00:00:00Z", TimeZone.of("America/New_York")))
    }

    @Test
    fun aTimestampThatDoesNotParseIsLeftOut() {
        assertNull(accessRestoredDate("next week", TimeZone.UTC))
    }
}
