package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets

/**
 * Picks a starting mode from the setup answers. A port of `pickMode` in
 * `docs/design/handoff/phone-logic.js`, with design v3's default: answers that
 * say nothing give Full numbers, which is also where "Skip and start logging"
 * lands, so skipping every question and skipping setup agree.
 */
object ModeRecommender {

    private const val SUGGESTED_ALTERNATIVES = 3

    /** The preset that shows every number. Hidden from someone who agreed numbers have been hard. */
    private val allNumbers = setOf(PresetId.Full)

    /** After the answers run out, the gentler modes first. */
    private val fallback = listOf(PresetId.NoNumbers, PresetId.Later, PresetId.NutrientsFirst, PresetId.Full)

    /**
     * The four starting points of design sync 4. Where the old answers pointed at Rhythm, Mindful
     * or Explorer, they point at No numbers now, which keeps meal timing and how meals felt; Fuel
     * and Nutrient focus answers point at Nutrients first.
     */
    fun recommend(answers: SetupAnswers): PresetId = when {
        answers.isEmpty -> PresetId.Full
        answers.feel == NumbersFeel.LoveThem ->
            if (answers.hard) PresetId.Later else PresetId.Full
        answers.feel == NumbersFeel.RatherNot -> PresetId.NoNumbers
        answers.feel == NumbersFeel.SomeAreFine -> PresetId.NutrientsFirst
        answers.notice == Notice.HowEatingFeels -> PresetId.NoNumbers
        answers.hard -> PresetId.NoNumbers
        else -> PresetId.Later
    }

    /**
     * Other starting points beside [chosen]. [recommended] comes first, so a picked
     * alternative and the recommendation swap places; then the modes the answers
     * point at; then the gentler fallbacks. Three of them, or with [all] every mode
     * on offer in the Modes screen's order.
     */
    fun alternatives(
        answers: SetupAnswers,
        chosen: PresetId,
        recommended: PresetId = recommend(answers),
        all: Boolean = false,
    ): List<PresetId> {
        val candidates = if (all) {
            Presets.all.map { it.id }
        } else {
            listOf(recommended) + pointedAt(answers) + fallback
        }
        val offered = candidates
            .distinct()
            .filter { it != chosen && isOffered(it, answers.hard) }
        return if (all) offered else offered.take(SUGGESTED_ALTERNATIVES)
    }

    /** Whether [preset] may be offered at all given the statement. */
    fun isOffered(preset: PresetId, hard: Boolean): Boolean = !hard || preset !in allNumbers

    private fun pointedAt(answers: SetupAnswers): List<PresetId> = buildList {
        if (Why.EatingRegularly in answers.why || answers.notice == Notice.HowEatingFeels) add(PresetId.NoNumbers)
        if (answers.feel == NumbersFeel.SomeAreFine || answers.notice == Notice.Nutrients || Why.EatingEnough in answers.why) {
            add(PresetId.NutrientsFirst)
        }
        if (answers.feel == NumbersFeel.LoveThem) {
            add(PresetId.Full)
            add(PresetId.Later)
        }
        if (answers.feel == NumbersFeel.RatherNot || answers.hard) add(PresetId.NoNumbers)
    }
}
