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

    /**
     * Presets that show every number. Hidden from someone who agreed numbers have
     * been hard: Sprint shows exactly what Full does, so hiding only Full would
     * leave the same exposure under another name.
     */
    private val allNumbers = setOf(PresetId.Full, PresetId.Sprint)

    /** After the answers run out, the gentler modes first. */
    private val fallback = listOf(PresetId.Blind, PresetId.Delayed, PresetId.Nutrient, PresetId.Full)

    fun recommend(answers: SetupAnswers): PresetId = when {
        answers.isEmpty -> PresetId.Full
        answers.feel == NumbersFeel.LoveThem ->
            if (answers.hard) PresetId.Delayed else PresetId.Full
        answers.feel == NumbersFeel.RatherNot ->
            if (Why.EatingRegularly in answers.why) PresetId.Rhythm else PresetId.Blind
        answers.feel == NumbersFeel.SomeAreFine ->
            if (Why.EatingEnough in answers.why) PresetId.Fuel else PresetId.Nutrient
        answers.notice == Notice.HowEatingFeels -> PresetId.Mindful
        answers.hard -> PresetId.Blind
        else -> PresetId.Delayed
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
        if (Why.EatingRegularly in answers.why) add(PresetId.Rhythm)
        if (answers.feel == NumbersFeel.SomeAreFine || answers.notice == Notice.Nutrients) add(PresetId.Nutrient)
        if (Why.EatingEnough in answers.why) add(PresetId.Fuel)
        if (answers.notice == Notice.HowEatingFeels) add(PresetId.Mindful)
        if (answers.feel == NumbersFeel.LoveThem) {
            add(PresetId.Full)
            add(PresetId.Delayed)
        }
        if (answers.feel == NumbersFeel.RatherNot || answers.hard) add(PresetId.Blind)
    }
}
