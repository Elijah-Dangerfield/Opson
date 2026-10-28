package com.opson.features.logger.impl

import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.Precision
import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.foods.FoodRef
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

class MissedBarcodePairingTest : CoroutineTest() {

    private var now = NOON
    private val clock = object : Clock {
        override fun now(): Instant = now
    }
    private val reporter = FakeBarcodeLinkReporter()
    private val pairing = RecentMissedBarcodePairing(reporter, clock)

    private fun pick(ref: FoodRef, source: PickSource = PickSource.Search) =
        LogPick(key = ref.key, ref = ref, draft = LogDraft(name = "Rolled oats", precision = Precision.Estimate), source = source)

    @Test
    fun aFoodFromSearchSoonAfterAMissIsReportedOnce() = runUnitTest {
        pairing.missed("012345678905")
        now += 4.minutes

        pairing.logged(listOf(pick(FoodRef.Custom("mine")), pick(FoodRef.Catalog("oats"))))
        pairing.logged(listOf(pick(FoodRef.Catalog("oats"))))

        assertEquals(listOf("012345678905" to "oats"), reporter.reports)
    }

    @Test
    fun pastTheWindowTheMissIsForgotten() = runUnitTest {
        pairing.missed("012345678905")
        now += MissedBarcodePairing.WINDOW + 1.minutes

        pairing.logged(listOf(pick(FoodRef.Catalog("oats"))))

        assertEquals(emptyList(), reporter.reports)
    }

    @Test
    fun onlyACatalogFoodFoundBySearchCounts() = runUnitTest {
        pairing.missed("012345678905")

        pairing.logged(listOf(pick(FoodRef.Catalog("recent"), source = PickSource.Recent)))
        pairing.logged(listOf(pick(FoodRef.Custom("mine"))))
        assertEquals(emptyList(), reporter.reports)

        pairing.logged(listOf(pick(FoodRef.Catalog("oats"))))
        assertEquals(listOf("012345678905" to "oats"), reporter.reports)
    }

    @Test
    fun nothingIsReportedWithoutAMiss() = runUnitTest {
        pairing.logged(listOf(pick(FoodRef.Catalog("oats"))))
        assertEquals(emptyList(), reporter.reports)
    }

    @Test
    fun theLoggerTellsThePairingWhatItLogged() = runUnitTest {
        val recording = RecordingPairing()
        val logger = FoodLogger(FakeDiaryRepository(), FakeFoodCatalog(), FakeRecentLogEvents(), RecordingTelemetry(), clock, recording)

        logger.log(LoggerFixtures.today, com.opson.libraries.diary.MealSlot.Lunch, listOf(pick(FoodRef.Catalog("oats"))))

        assertEquals(listOf(listOf("f:oats")), recording.logged.map { picks -> picks.map { it.key } })
    }
}
