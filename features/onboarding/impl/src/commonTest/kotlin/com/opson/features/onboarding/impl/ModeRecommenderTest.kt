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
        hard: Boolean = false,
    ) = SetupAnswers(why, feel, if (hard) StatementAnswer.Agree else null)

    private fun recommend(
        why: Set<Why> = emptySet(),
        feel: NumbersFeel? = null,
        hard: Boolean = false,
    ) = ModeRecommender.recommend(answers(why, feel, hard))

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
    fun loveThem_isFull_orMacrosWithGramsWhenNumbersHaveBeenHard() {
        assertEquals(PresetId.Full, recommend(feel = NumbersFeel.LoveThem))
        assertEquals(PresetId.MacrosWithGrams, recommend(feel = NumbersFeel.LoveThem, hard = true))
    }

    @Test
    fun ratherNot_isNoNumbers_eatingRegularlyIncluded() {
        assertEquals(PresetId.NoNumbers, recommend(feel = NumbersFeel.RatherNot))
        assertEquals(PresetId.NoNumbers, recommend(why = setOf(Why.Curious, Why.EatingRegularly), feel = NumbersFeel.RatherNot))
    }

    @Test
    fun someAreFine_isMacrosOnly_eatingEnoughIncluded() {
        assertEquals(PresetId.MacrosOnly, recommend(feel = NumbersFeel.SomeAreFine))
        assertEquals(PresetId.MacrosOnly, recommend(why = setOf(Why.EatingEnough), feel = NumbersFeel.SomeAreFine))
    }

    @Test
    fun someAreFine_staysMacrosOnly_withTheStatement_asTheDesignDraws() {
        // Macros only hides calories and grams already, so agreeing does not move it.
        assertEquals(PresetId.MacrosOnly, recommend(feel = NumbersFeel.SomeAreFine, hard = true))
    }

    @Test
    fun reason_namesTheAnswerThatDecided() {
        assertEquals(RecommendationReason.NoAnswers, ModeRecommender.reason(answers()))
        assertEquals(RecommendationReason.LovesNumbers, ModeRecommender.reason(answers(feel = NumbersFeel.LoveThem)))
        assertEquals(RecommendationReason.Gentler, ModeRecommender.reason(answers(feel = NumbersFeel.LoveThem, hard = true)))
        assertEquals(RecommendationReason.Gentler, ModeRecommender.reason(answers(hard = true)))
        assertEquals(RecommendationReason.SomeAreFine, ModeRecommender.reason(answers(feel = NumbersFeel.SomeAreFine, hard = true)))
        assertEquals(RecommendationReason.RatherNot, ModeRecommender.reason(answers(feel = NumbersFeel.RatherNot, hard = true)))
        assertEquals(RecommendationReason.NotSure, ModeRecommender.reason(answers(feel = NumbersFeel.NotSure)))
        assertEquals(RecommendationReason.NotSure, ModeRecommender.reason(answers(why = setOf(Why.Curious))))
    }

    @Test
    fun reason_andRecommendation_agree_forEveryAnswer() {
        // The line under the mode must never explain a different mode than the one shown.
        val expected = mapOf(
            RecommendationReason.NoAnswers to setOf(PresetId.Full),
            RecommendationReason.LovesNumbers to setOf(PresetId.Full),
            RecommendationReason.SomeAreFine to setOf(PresetId.MacrosOnly),
            RecommendationReason.RatherNot to setOf(PresetId.NoNumbers),
            RecommendationReason.Gentler to setOf(PresetId.MacrosWithGrams, PresetId.NoNumbers),
            RecommendationReason.NotSure to setOf(PresetId.MacrosOnly),
        )
        (listOf(null) + NumbersFeel.entries).forEach { feel ->
            listOf(emptySet(), setOf(Why.EatingRegularly)).forEach { why ->
                listOf(false, true).forEach { hard ->
                    val answers = answers(why = why, feel = feel, hard = hard)
                    val reason = ModeRecommender.reason(answers)
                    assertTrue(ModeRecommender.recommend(answers) in expected.getValue(reason), "$answers gave $reason")
                }
            }
        }
    }

    @Test
    fun anAnswerThatDecidesNothing_isMacrosOnly_orNoNumbersWhenHard() {
        assertEquals(PresetId.MacrosOnly, recommend(feel = NumbersFeel.NotSure))
        assertEquals(PresetId.MacrosOnly, recommend(why = setOf(Why.Curious)))
        assertEquals(PresetId.NoNumbers, recommend(feel = NumbersFeel.NotSure, hard = true))
    }

    @Test
    fun everyRecommendation_isOneOfTheFour() {
        val feels = listOf(null) + NumbersFeel.entries
        feels.forEach { feel ->
            listOf(false, true).forEach { hard ->
                val picked = recommend(why = Why.entries.toSet(), feel = feel, hard = hard)
                assertTrue(picked in PresetId.entries)
                assertTrue(ModeRecommender.isOffered(picked, hard), "$picked offered to hard=$hard")
            }
        }
    }

    @Test
    fun theDesignsAnswers_recommendMacrosOnly_withTheOtherTwoOnOffer() {
        val design = OnboardingFixtures.DesignAnswers

        assertEquals(PresetId.MacrosOnly, ModeRecommender.recommend(design))
        assertEquals(
            listOf(PresetId.NoNumbers, PresetId.MacrosWithGrams),
            ModeRecommender.alternatives(design, chosen = PresetId.MacrosOnly),
        )
    }

    @Test
    fun alternatives_areTheOtherModes_withoutTheChosenOne() {
        val none = answers()

        assertEquals(
            listOf(PresetId.NoNumbers, PresetId.MacrosOnly, PresetId.MacrosWithGrams),
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
            listOf(PresetId.MacrosOnly, PresetId.MacrosWithGrams),
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
        assertEquals(listOf(PresetId.MacrosWithGrams, PresetId.MacrosOnly), hard)
    }
}
