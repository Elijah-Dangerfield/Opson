package com.opson.libraries.review.impl

import com.opson.libraries.flowroutines.testing.CoroutineTest
import com.opson.libraries.opson.AppData
import com.opson.libraries.review.ReviewMoment
import com.opson.libraries.review.ReviewVariant
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ConfiguredReviewExperimentTest : CoroutineTest() {

    private val config = FakeConfig("release.reviewAsk" to true)
    private val cache = FakeAppCache()
    private val telemetry = RecordingReviewTelemetry()
    private val experiment = experiment(config, cache, telemetry)

    @Test
    fun offWithTheReleaseFlag() = runUnitTest {
        config["release.reviewAsk"] = false

        assertNull(experiment.variant())
        assertEquals("", cache.get().reviewVariant, "nothing assigned while off")
    }

    @Test
    fun offWhenEveryWeightIsZero() = runUnitTest {
        config["review.weightCelebration"] = 0
        config["review.weightDayFeltGood"] = 0
        config["review.weightShowingUp"] = 0

        assertNull(experiment.variant())
    }

    @Test
    fun assignsOnce_andKeepsIt() = runUnitTest {
        config["review.weightCelebration"] = 0
        config["review.weightShowingUp"] = 0

        val first = experiment.variant()
        assertEquals(ReviewVariant(ReviewMoment.DayFeltGood, "moments-1"), first)
        assertEquals("day_felt_good", cache.get().reviewVariant)
        assertEquals(listOf(first), telemetry.assigned)

        config["review.weightShowingUp"] = 5
        assertEquals(first, experiment.variant(), "a new weight moves only installs not yet assigned")
        assertEquals(1, telemetry.assigned.size)
    }

    @Test
    fun renamingTheExperiment_assignsAgain() = runUnitTest {
        cache.state.value = AppData(reviewVariant = "celebration", reviewExperiment = "moments-1")
        config["review.weightCelebration"] = 0
        config["review.weightDayFeltGood"] = 0
        config["review.experiment"] = "moments-2"

        assertEquals(ReviewVariant(ReviewMoment.ShowingUp, "moments-2"), experiment.variant())
        assertEquals("moments-2", cache.get().reviewExperiment)
    }

    @Test
    fun aMomentTurnedOff_givesItsInstallsAnother() = runUnitTest {
        cache.state.value = AppData(reviewVariant = "celebration", reviewExperiment = "moments-1")
        config["review.weightCelebration"] = 0
        config["review.weightShowingUp"] = 0

        assertEquals(ReviewMoment.DayFeltGood, experiment.variant()?.moment)
    }

    @Test
    fun foodApprovedIsOffByDefault() = runUnitTest {
        val seen = (0 until 60).map { seed ->
            val fresh = experiment(config, FakeAppCache(), telemetry).apply { random = Random(seed) }
            fresh.variant()?.moment
        }.toSet()

        assertEquals(setOf(ReviewMoment.Celebration, ReviewMoment.DayFeltGood, ReviewMoment.ShowingUp), seen)
    }

    @Test
    fun pick_walksTheWeightsInOrder() {
        val shares = mapOf(ReviewMoment.Celebration to 1, ReviewMoment.ShowingUp to 3)

        assertEquals(ReviewMoment.Celebration, ConfiguredReviewExperiment.pick(shares, roll = 0))
        assertEquals(ReviewMoment.ShowingUp, ConfiguredReviewExperiment.pick(shares, roll = 1))
        assertEquals(ReviewMoment.ShowingUp, ConfiguredReviewExperiment.pick(shares, roll = 3))
    }
}
