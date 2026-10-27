package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets

/**
 * Picks a starting mode from the setup answers. Once a port of `pickMode` in
 * `docs/design/handoff/phone-logic.js`, with design v3's default: answers that
 * say nothing give All numbers, which is also where "Skip and start logging"
 * lands, so skipping every question and skipping setup agree. Someone who agreed
 * numbers have been hard is never offered All numbers ([isOffered]).
 */
object ModeRecommender {

    private const val SUGGESTED_ALTERNATIVES = 3

    /** The preset that shows every number. Hidden from someone who agreed numbers have been hard. */
    private val allNumbers = setOf(PresetId.Full)

    /** After the answers run out, the gentler modes first. */
    private val fallback = listOf(PresetId.NoNumbers, PresetId.MacrosOnly, PresetId.MacrosWithGrams, PresetId.Full)

    /**
     * The four starting points. How someone feels about numbers decides it; the statement only
     * ever takes it gentler, and never to All numbers. Answers that say nothing about numbers (only
     * a reason, or "Not sure yet") start in Macros only, which shows the macros without an amount.
     */
    fun recommend(answers: SetupAnswers): PresetId = when (reason(answers)) {
        RecommendationReason.NoAnswers, RecommendationReason.LovesNumbers -> PresetId.Full
        RecommendationReason.SomeAreFine -> PresetId.MacrosOnly
        RecommendationReason.RatherNot -> PresetId.NoNumbers
        RecommendationReason.Gentler -> if (answers.feel == NumbersFeel.LoveThem) PresetId.MacrosWithGrams else PresetId.NoNumbers
        RecommendationReason.NotSure -> PresetId.MacrosOnly
    }

    /**
     * Which answer decided [recommend], for the line under the starting point. It names the
     * numbers answer, never the statement itself: "You asked for a gentler start" says what the
     * person chose without repeating it back.
     */
    fun reason(answers: SetupAnswers): RecommendationReason = when {
        answers.isEmpty -> RecommendationReason.NoAnswers
        answers.feel == NumbersFeel.RatherNot -> RecommendationReason.RatherNot
        // Macros only already hides calories and grams, so it stands with the statement too (design 1.5).
        answers.feel == NumbersFeel.SomeAreFine -> RecommendationReason.SomeAreFine
        answers.hard -> RecommendationReason.Gentler
        answers.feel == NumbersFeel.LoveThem -> RecommendationReason.LovesNumbers
        else -> RecommendationReason.NotSure
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
        if (Why.EatingRegularly in answers.why) add(PresetId.NoNumbers)
        if (answers.feel == NumbersFeel.SomeAreFine || Why.EatingEnough in answers.why) add(PresetId.MacrosOnly)
        if (answers.feel == NumbersFeel.LoveThem) {
            add(PresetId.Full)
            add(PresetId.MacrosWithGrams)
        }
        if (answers.feel == NumbersFeel.RatherNot || answers.hard) add(PresetId.NoNumbers)
    }
}

/** Which answer decided the starting point. The recommendation step says it in one line. */
enum class RecommendationReason {
    /** Every question skipped: All numbers, as "Skip and start logging". */
    NoAnswers,
    LovesNumbers,
    SomeAreFine,
    RatherNot,

    /** Agreed numbers have been hard, and did not say they would rather see none. */
    Gentler,

    /** "Not sure yet", or only a reason: Macros only, the macros as bars with no amounts. */
    NotSure,
}
