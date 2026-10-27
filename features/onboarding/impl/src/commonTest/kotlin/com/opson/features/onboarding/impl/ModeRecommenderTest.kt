package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The recommendation over design sync 4's four modes: every row of the old `pickMode` lands on
 * the mode its old preset became, and the rules for what else is offered.
 */
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
    fun agreeingWithTheStatementAlone_isNoNumbers() {
        assertEquals(PresetId.NoNumbers, recommend(hard = true))
    }

    @Test
    fun loveThem_isFull_orLaterWhenNumbersHaveBeenHard() {
        assertEquals(PresetId.Full, recommend(feel = NumbersFeel.LoveThem))
        assertEquals(PresetId.Later, recommend(feel = NumbersFeel.LoveThem, hard = true))
    }

    @Test
    fun ratherNot_isNoNumbers_eatingRegularlyIncluded() {
        assertEquals(PresetId.NoNumbers, recommend(feel = NumbersFeel.RatherNot))
        assertEquals(PresetId.NoNumbers, recommend(why = setOf(Why.Curious, Why.EatingRegularly), feel = NumbersFeel.RatherNot))
    }

    @Test
    fun someAreFine_isNutrientsFirst_eatingEnoughIncluded() {
        assertEquals(PresetId.NutrientsFirst, recommend(feel = NumbersFeel.SomeAreFine))
        assertEquals(PresetId.NutrientsFirst, recommend(why = setOf(Why.EatingEnough), feel = NumbersFeel.SomeAreFine))
    }

    @Test
    fun howEatingFeels_isNoNumbers_whenTheFeelAnswerDoesNotDecide() {
        assertEquals(PresetId.NoNumbers, recommend(notice = Notice.HowEatingFeels))
        assertEquals(PresetId.NoNumbers, recommend(feel = NumbersFeel.NotSure, notice = Notice.HowEatingFeels))
        assertEquals(PresetId.Full, recommend(feel = NumbersFeel.LoveThem, notice = Notice.HowEatingFeels))
    }

    @Test
    fun anAnswerThatDecidesNothing_isLater_orNoNumbersWhenHard() {
        assertEquals(PresetId.Later, recommend(feel = NumbersFeel.NotSure))
        assertEquals(PresetId.Later, recommend(why = setOf(Why.Curious)))
        assertEquals(PresetId.NoNumbers, recommend(feel = NumbersFeel.NotSure, hard = true))
    }

    @Test
    fun everyRecommendation_isOneOfTheFour() {
        val feels = listOf(null) + NumbersFeel.entries
        val notices = listOf(null) + Notice.entries
        feels.forEach { feel ->
            notices.forEach { notice ->
                listOf(false, true).forEach { hard ->
                    val picked = recommend(why = Why.entries.toSet(), feel = feel, notice = notice, hard = hard)
                    assertTrue(picked in PresetId.entries)
                    assertTrue(ModeRecommender.isOffered(picked, hard), "$picked offered to hard=$hard")
                }
            }
        }
    }

    @Test
    fun theDesignsAnswers_recommendNutrientsFirst_withTheGentlerTwo() {
        val design = OnboardingFixtures.DesignAnswers

        assertEquals(PresetId.NutrientsFirst, ModeRecommender.recommend(design))
        assertEquals(
            listOf(PresetId.NoNumbers, PresetId.Later),
            ModeRecommender.alternatives(design, chosen = PresetId.NutrientsFirst),
        )
    }

    @Test
    fun alternatives_areTheOtherModes_withoutTheChosenOne() {
        val none = answers()

        assertEquals(
            listOf(PresetId.NoNumbers, PresetId.Later, PresetId.NutrientsFirst),
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
            listOf(PresetId.NutrientsFirst, PresetId.Later),
            ModeRecommender.alternatives(design, chosen = PresetId.NoNumbers),
        )
    }

    @Test
    fun hard_neverOffersFullNumbers() {
        listOf(answers(hard = true), answers(feel = NumbersFeel.LoveThem, hard = true)).forEach { hard ->
            Presets.all.forEach { preset ->
                listOf(false, true).forEach { all ->
                    val alternatives = ModeRecommender.alternatives(hard, chosen = preset.id, all = all)
                    assertFalse(PresetId.Full in alternatives, "Full offered to $hard")
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

        val hard = ModeRecommender.alternatives(answers(hard = true), chosen = PresetId.NoNumbers, all = true)
        assertEquals(listOf(PresetId.NutrientsFirst, PresetId.Later), hard)
    }
}
