package com.opson.features.onboarding.impl

import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets

/**
 * Picks a starting mode from the setup answers. A port of `pickMode` in
 * `docs/design/handoff/phone-logic.js`.
 *
 * No answers at all gives Delayed reveal, which is also where "Skip, start
 * logging" lands: someone who skipped setup should not see a calorie total on
 * day one without choosing to, and Full numbers is one tap away in Modes.
 */
object ModeRecommender {

    private const val MAX_ALTERNATIVES = 4

    /**
     * Presets that show every number. Hidden from someone who said numbers have
     * been hard: Sprint shows exactly what Full does, so hiding only Full would
     * leave the same exposure under another name.
     */
    private val allNumbers = setOf(PresetId.Full, PresetId.Sprint)

    fun recommend(answers: SetupAnswers): PresetId = when {
        answers.feel == NumbersFeel.LoveThem ->
            if (answers.hard) PresetId.Delayed else PresetId.Full
        answers.feel == NumbersFeel.RatherNot ->
            if (Why.EatingRegularly in answers.why) PresetId.Rhythm else PresetId.Blind
        answers.feel == NumbersFeel.SomeAreFine ->
            if (Why.EatingEnough in answers.why) PresetId.Fuel else PresetId.Nutrient
        Notice.HowEatingFeels in answers.notice -> PresetId.Mindful
        answers.hard -> PresetId.Blind
        else -> PresetId.Delayed
    }

    /** Up to four other starting points, in the Modes screen's order. */
    fun alternatives(chosen: PresetId, hard: Boolean): List<PresetId> =
        Presets.all.asSequence()
            .map { it.id }
            .filter { it != chosen }
            .filter { isOffered(it, hard) }
            .take(MAX_ALTERNATIVES)
            .toList()

    /** Whether [preset] may be offered at all given the hard answer. */
    fun isOffered(preset: PresetId, hard: Boolean): Boolean = !hard || preset !in allNumbers
}
