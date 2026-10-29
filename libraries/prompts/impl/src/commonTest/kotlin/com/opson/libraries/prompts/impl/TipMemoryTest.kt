package com.opson.libraries.prompts.impl

import com.opson.libraries.prompts.Tips
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Which tip a screen visit shows: the most useful one on screen that this account hasn't seen. */
class TipMemoryTest {

    @Test
    fun theFirstTipInTheRegistry_thatIsOnScreen_wins() {
        val onToday = listOf(Tips.PullToRefresh.id, Tips.FoodDetails.id, Tips.FlipFigure.id)
        assertEquals(Tips.FlipFigure.id, TipMemory.choose(onToday, seen = emptySet()))
    }

    @Test
    fun aTipSeenOnce_neverShowsAgain_andTheNextOneTakesItsTurn() {
        val onToday = listOf(Tips.PullToRefresh.id, Tips.FlipFigure.id)
        assertEquals(Tips.PullToRefresh.id, TipMemory.choose(onToday, seen = setOf(Tips.FlipFigure.id)))
        assertNull(TipMemory.choose(onToday, seen = setOf(Tips.FlipFigure.id, Tips.PullToRefresh.id)))
    }

    @Test
    fun anIdNotInTheRegistry_isNeverShown() {
        assertNull(TipMemory.choose(listOf("made_up"), seen = emptySet()))
    }

    @Test
    fun everyTip_hasItsOwnId() {
        assertTrue(Tips.all.map { it.id }.toSet().size == Tips.all.size)
    }
}
