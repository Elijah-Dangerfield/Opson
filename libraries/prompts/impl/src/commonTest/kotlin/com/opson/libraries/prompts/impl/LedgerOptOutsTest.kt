package com.opson.libraries.prompts.impl

import com.opson.libraries.diary.Settings
import com.opson.libraries.prompts.OptOut
import com.opson.libraries.prompts.OptOuts
import com.opson.libraries.prompts.impl.LedgerOptOuts.Companion.without
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** "I'd rather not see these": offered on the first five showings, and each turns off its own switch. */
class LedgerOptOutsTest {

    @Test
    fun offered_onTheFirstFiveShowings_only() {
        assertFalse(LedgerOptOuts.offers(0), "nothing shown yet")
        (1..OptOuts.OFFERED_SHOWINGS).forEach { assertTrue(LedgerOptOuts.offers(it), "showing $it") }
        assertFalse(LedgerOptOuts.offers(OptOuts.OFFERED_SHOWINGS + 1))
    }

    @Test
    fun eachOptOut_turnsOffItsOwnSwitch_andNothingElse() {
        val on = Settings()
        assertEquals(on.copy(celebrationsEnabled = false), on.without(OptOut.Celebrations))
        assertEquals(on.copy(todayStreak = false), on.without(OptOut.Streak))
        assertEquals(on.copy(recapBanner = false), on.without(OptOut.RecapBanner))
        assertEquals(on.copy(tips = false), on.without(OptOut.Tips))
        assertEquals(on.copy(suggestions = false), on.without(OptOut.Suggestions))
    }

    @Test
    fun theWireNames_neverChange() {
        assertEquals(
            listOf("celebrations", "streak", "recap_banner", "tips", "suggestions"),
            OptOut.entries.map { it.wire },
        )
    }
}
