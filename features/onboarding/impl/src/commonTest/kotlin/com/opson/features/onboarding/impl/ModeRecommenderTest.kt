package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Every row of the `pickMode` port with v3's default, and the rules for what else is offered. */
class ModeRecommenderTest {

    private fun answers(
        why: Set<Why> = emptySet(),
        feel: NumbersFeel? = null,
        notice: Notice? = null,
        hard: Boolean = false,
    ) = SetupAnswers(why, feel, notice, if (hard) StatementAnswer.Agree else null)

    private fun recommend(
        why: Set<Why> = emptySet(),
        feel: NumbersFeel? = null,
        notice: Notice? = null,
        hard: Boolean = false,
    ) = ModeRecommender.recommend(answers(why, feel, notice, hard))

    @Test
    fun noAnswers_isFullNumbers_likeSkippingSetup() {
        assertEquals(PresetId.Full, recommend())
        assertEquals(PresetId.Full, ModeRecommender.recommend(SetupAnswers(statement = StatementAnswer.NotReally)))
    }

    @Test
    fun agreeingWithTheStatementAlone_isBlind() {
        assertEquals(PresetId.Blind, recommend(hard = true))
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
        assertEquals(PresetId.Mindful, recommend(notice = Notice.HowEatingFeels))
        assertEquals(PresetId.Mindful, recommend(feel = NumbersFeel.NotSure, notice = Notice.HowEatingFeels))
        assertEquals(PresetId.Full, recommend(feel = NumbersFeel.LoveThem, notice = Notice.HowEatingFeels))
    }

    @Test
    fun anAnswerThatDecidesNothing_isDelayed_orBlindWhenHard() {
        assertEquals(PresetId.Delayed, recommend(feel = NumbersFeel.NotSure))
        assertEquals(PresetId.Delayed, recommend(why = setOf(Why.Curious)))
        assertEquals(PresetId.Blind, recommend(feel = NumbersFeel.NotSure, hard = true))
    }

    @Test
    fun theDesignsAnswers_recommendFuel_withRhythmNutrientAndBlind() {
        val design = OnboardingFixtures.DesignAnswers

        assertEquals(PresetId.Fuel, ModeRecommender.recommend(design))
        assertEquals(
            listOf(PresetId.Rhythm, PresetId.Nutrient, PresetId.Blind),
            ModeRecommender.alternatives(design, chosen = PresetId.Fuel),
        )
    }

    @Test
    fun alternatives_areThree_withoutTheChosenMode() {
        val none = answers()

        assertEquals(
            listOf(PresetId.Blind, PresetId.Delayed, PresetId.Nutrient),
            ModeRecommender.alternatives(none, chosen = PresetId.Full),
        )
        Presets.all.forEach { preset ->
            val alternatives = ModeRecommender.alternatives(none, chosen = preset.id)
            assertEquals(3, alternatives.size)
            assertFalse(preset.id in alternatives)
        }
    }

    @Test
    fun aPickedAlternative_putsTheRecommendationFirst() {
        val design = OnboardingFixtures.DesignAnswers

        assertEquals(
            listOf(PresetId.Fuel, PresetId.Nutrient, PresetId.Blind),
            ModeRecommender.alternatives(design, chosen = PresetId.Rhythm),
        )
    }

    @Test
    fun hard_neverOffersFullOrSprint() {
        listOf(answers(hard = true), answers(feel = NumbersFeel.LoveThem, hard = true)).forEach { hard ->
            Presets.all.forEach { preset ->
                listOf(false, true).forEach { all ->
                    val alternatives = ModeRecommender.alternatives(hard, chosen = preset.id, all = all)
                    assertFalse(PresetId.Full in alternatives, "Full offered to $hard")
                    assertFalse(PresetId.Sprint in alternatives, "Sprint offered to $hard")
                }
            }
        }
        assertFalse(ModeRecommender.isOffered(PresetId.Full, hard = true))
        assertTrue(ModeRecommender.isOffered(PresetId.Full, hard = false))
    }

    @Test
    fun allModes_listsEveryModeOnOffer_inTheModesOrder() {
        val all = ModeRecommender.alternatives(answers(), chosen = PresetId.Full, all = true)
        assertEquals(Presets.all.map { it.id } - PresetId.Full, all)

        val hard = ModeRecommender.alternatives(answers(hard = true), chosen = PresetId.Blind, all = true)
        assertEquals(Presets.all.map { it.id } - setOf(PresetId.Blind, PresetId.Full, PresetId.Sprint), hard)
    }
}
