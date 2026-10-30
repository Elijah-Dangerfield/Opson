package com.opson.features.upgrade.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class OfflinePhasesTest : CoroutineTest() {

    private val offline = MutableStateFlow(false)
    private val seen = mutableListOf<OfflinePhase>()

    private fun TestScope.watch() {
        backgroundScope.launch {
            offlinePhases(offline, showAfter = 3.seconds, backOnlineFor = 2.seconds).collect { seen += it }
        }
        runCurrent()
    }

    @Test
    fun aBlipShorterThanTheWait_showsNothing() = runUnitTest {
        watch()
        offline.value = true
        advanceTimeBy(2.seconds)
        offline.value = false
        advanceTimeBy(10.seconds)

        assertEquals(listOf(OfflinePhase.Hidden), seen)
    }

    @Test
    fun offlinePastTheWait_showsTheStrip_thenBackOnline_thenHides() = runUnitTest {
        watch()
        offline.value = true
        advanceTimeBy(2999.milliseconds)
        assertEquals(listOf(OfflinePhase.Hidden), seen, "not before the wait is over")
        advanceTimeBy(2.milliseconds)
        assertEquals(OfflinePhase.Offline, seen.last())

        offline.value = false
        runCurrent()
        assertEquals(OfflinePhase.BackOnline, seen.last(), "back online at once")
        advanceTimeBy(2001.milliseconds)

        assertEquals(listOf(OfflinePhase.Hidden, OfflinePhase.Offline, OfflinePhase.BackOnline, OfflinePhase.Hidden), seen)
    }

    @Test
    fun droppingAgainWhileBackOnlineShows_goesStraightBackToOffline() = runUnitTest {
        watch()
        offline.value = true
        advanceTimeBy(4.seconds)
        offline.value = false
        advanceTimeBy(1.seconds)

        offline.value = true
        runCurrent()

        assertEquals(listOf(OfflinePhase.Hidden, OfflinePhase.Offline, OfflinePhase.BackOnline, OfflinePhase.Offline), seen)
    }

    @Test
    fun bootingOffline_showsTheStripAfterTheWait() = runUnitTest {
        offline.value = true
        watch()
        advanceTimeBy(3001.milliseconds)

        assertEquals(listOf(OfflinePhase.Hidden, OfflinePhase.Offline), seen)
    }
}
