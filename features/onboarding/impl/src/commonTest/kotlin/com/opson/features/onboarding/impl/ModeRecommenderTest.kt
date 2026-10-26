package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every row of the `pickMode` port, and the rules for what else is offered. */
class ModeRecommenderTest {

    private fun recommend(
        why: Set<Why> = emptySet(),
        feel: NumbersFeel? = null,
        notice: Set<Notice> = emptySet(),
        hard: Boolean = false,
    ) = ModeRecommender.recommend(SetupAnswers(why, feel, notice, hard))

    @Test
    fun noAnswers_isDelayedReveal() {
        assertEquals(PresetId.Delayed, recommend())
    }

    @Test
    fun loveThem_isFull_orDelayedWhenNumbersHaveBeenHard() {
        assertEquals(PresetId.Full, recommend(feel = NumbersFeel.LoveThem))
        assertEquals(PresetId.Delayed, recommend(feel = NumbersFeel.LoveThem, hard = true))
    }

    @Test
    fun ratherNot_isBlind_orRhythmForEatingRegularly() {
        assertEquals(PresetId.Blind, recommend(feel = NumbersFeel.RatherNot))
        assertEquals(
            PresetId.Rhythm,
            recommend(why = setOf(Why.Curious, Why.EatingRegularly), feel = NumbersFeel.RatherNot),
        )
    }

    @Test
    fun someAreFine_isNutrient_orFuelForEatingEnough() {
        assertEquals(PresetId.Nutrient, recommend(feel = NumbersFeel.SomeAreFine))
        assertEquals(PresetId.Fuel, recommend(why = setOf(Why.EatingEnough), feel = NumbersFeel.SomeAreFine))
    }

    @Test
    fun howEatingFeels_isMindful_whenTheFeelAnswerDoesNotDecide() {
        assertEquals(PresetId.Mindful, recommend(notice = setOf(Notice.HowEatingFeels)))
        assertEquals(PresetId.Mindful, recommend(feel = NumbersFeel.NotSure, notice = setOf(Notice.HowEatingFeels)))
        assertEquals(PresetId.Mindful, recommend(notice = setOf(Notice.HowEatingFeels), hard = true))
    }

    @Test
    fun theFeelAnswer_winsOverWhatToNotice() {
        assertEquals(PresetId.Blind, recommend(feel = NumbersFeel.RatherNot, notice = setOf(Notice.HowEatingFeels)))
    }

    @Test
    fun otherwise_isBlindWhenHard_elseDelayed() {
        assertEquals(PresetId.Blind, recommend(hard = true))
        assertEquals(PresetId.Delayed, recommend(feel = NumbersFeel.NotSure, notice = setOf(Notice.Amounts)))
    }

    @Test
    fun hard_neverRecommendsEveryNumber() {
        val all = allAnswerCombinations().filter { it.hard }
        val picks = all.map { ModeRecommender.recommend(it) }.toSet()
        assertFalse(PresetId.Full in picks)
        assertFalse(PresetId.Sprint in picks)
    }

    @Test
    fun alternatives_areTheFirstFourInModesOrder_withoutTheChosenOne() {
        assertEquals(
            listOf(PresetId.Full, PresetId.Blind, PresetId.Nutrient, PresetId.Rhythm),
            ModeRecommender.alternatives(PresetId.Delayed, hard = false),
        )
        assertEquals(
            listOf(PresetId.Delayed, PresetId.Blind, PresetId.Nutrient, PresetId.Rhythm),
            ModeRecommender.alternatives(PresetId.Full, hard = false),
        )
    }

    @Test
    fun alternatives_whenHard_leaveOutFullAndSprint() {
        Presets.all.map { it.id }.forEach { chosen ->
            val alternatives = ModeRecommender.alternatives(chosen, hard = true)
            assertFalse(PresetId.Full in alternatives, "Full offered beside $chosen")
            assertFalse(PresetId.Sprint in alternatives, "Sprint offered beside $chosen")
        }
        assertEquals(
            listOf(PresetId.Delayed, PresetId.Nutrient, PresetId.Rhythm, PresetId.Explorer),
            ModeRecommender.alternatives(PresetId.Blind, hard = true),
        )
    }

    @Test
    fun alternatives_areAtMostFour_andNeverTheChosenOne() {
        Presets.all.map { it.id }.forEach { chosen ->
            listOf(false, true).forEach { hard ->
                val alternatives = ModeRecommender.alternatives(chosen, hard)
                assertTrue(alternatives.size in 1..4)
                assertFalse(chosen in alternatives)
                assertEquals(alternatives.distinct(), alternatives)
            }
        }
    }

    @Test
    fun isOffered_hidesOnlyFullAndSprint_andOnlyWhenHard() {
        Presets.all.map { it.id }.forEach { preset ->
            assertTrue(ModeRecommender.isOffered(preset, hard = false))
            assertEquals(preset != PresetId.Full && preset != PresetId.Sprint, ModeRecommender.isOffered(preset, hard = true))
        }
    }

    private fun allAnswerCombinations(): List<SetupAnswers> {
        val feels = listOf<NumbersFeel?>(null) + NumbersFeel.entries
        val whys = listOf(emptySet(), setOf(Why.EatingEnough), setOf(Why.EatingRegularly))
        val notices = listOf(emptySet(), setOf(Notice.HowEatingFeels))
        return feels.flatMap { feel ->
            whys.flatMap { why ->
                notices.flatMap { notice ->
                    listOf(false, true).map { hard -> SetupAnswers(why, feel, notice, hard) }
                }
            }
        }
    }
}
