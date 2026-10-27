package com.opson.features.onboarding.impl

import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

class SetupAnswersTest {

    @Test
    fun eachQuestion_offersItsOwnOptions_inOrder() {
        assertEquals(Why.entries, SetupQuestion.Reason.options)
        assertEquals(NumbersFeel.entries, SetupQuestion.Feeling.options)
        assertEquals(Notice.entries, SetupQuestion.Focus.options)
        assertEquals(StatementAnswer.entries, SetupQuestion.Statement.options)
        assertEquals((1..4).toList(), SetupQuestion.entries.map { it.number })
        assertEquals(
            listOf("question_1", "question_2", "question_3", "statement"),
            SetupQuestion.entries.map { it.eventName },
        )
        assertEquals(listOf(true, false, false, false), SetupQuestion.entries.map { it.multiSelect })
    }

    @Test
    fun tap_togglesTheWhyAnswers_andReplacesEveryOther() {
        val answers = SetupAnswers()
            .tap(Why.Curious)
            .tap(NumbersFeel.LoveThem)
            .tap(NumbersFeel.NotSure)
            .tap(Notice.Patterns)
            .tap(Notice.Amounts)
            .tap(StatementAnswer.Agree)
            .tap(StatementAnswer.NotReally)

        assertEquals(
            SetupAnswers(setOf(Why.Curious), NumbersFeel.NotSure, Notice.Amounts, StatementAnswer.NotReally),
            answers,
        )
        assertEquals(emptySet(), answers.tap(Why.Curious).why)
    }

    @Test
    fun hard_isOnlyAgreeingWithTheStatement() {
        assertTrue(SetupAnswers(statement = StatementAnswer.Agree).hard)
        assertFalse(SetupAnswers(statement = StatementAnswer.NotReally).hard)
        assertFalse(SetupAnswers().hard)
    }

    @Test
    fun isEmpty_meansNothingThatCouldShapeAStartingPoint() {
        assertTrue(SetupAnswers().isEmpty)
        assertTrue(SetupAnswers(statement = StatementAnswer.NotReally).isEmpty, "Not really says nothing on its own")
        assertFalse(SetupAnswers(statement = StatementAnswer.Agree).isEmpty)
        assertFalse(SetupAnswers(why = setOf(Why.Curious)).isEmpty)
        assertFalse(SetupAnswers(feel = NumbersFeel.NotSure).isEmpty)
        assertFalse(SetupAnswers(notice = Notice.Patterns).isEmpty)
    }

    @Test
    fun choices_markWhatIsPicked() {
        val choices = SetupAnswers(why = setOf(Why.Performance)).choices(SetupQuestion.Reason)

        assertEquals(listOf(Why.Performance), choices.filter { it.selected }.map { it.option })
        assertEquals(Why.entries.size, choices.size)
    }

    @Test
    fun toSettings_storesTheOptionKeys_inQuestionOrder() {
        val answers = SetupAnswers(
            why = setOf(Why.WorkingWithProvider, Why.Curious),
            feel = NumbersFeel.RatherNot,
            notice = Notice.HowEatingFeels,
            statement = StatementAnswer.Agree,
        )

        assertEquals(
            OnboardingAnswers(
                why = listOf("curious", "working_with_provider"),
                feel = "rather_not",
                notice = listOf("how_eating_feels"),
                hard = true,
            ),
            answers.toSettings(),
        )
        assertEquals(OnboardingAnswers(), SetupAnswers().toSettings())
        assertEquals(OnboardingAnswers(), SetupAnswers(statement = StatementAnswer.NotReally).toSettings())
    }

    @Test
    fun startingWith_setsThePreset_clearsOverridesAndThePause_andKeepsChangedAtNull() {
        val before = Settings(
            numbers = NumbersSettings(
                preset = PresetId.Full,
                overrides = mapOf(Metric.Water to Visibility.Hide),
                pauseBeforeMore = true,
                changedAt = null,
            ),
        )

        val after = before.startingWith(PresetId.NoNumbers, SetupAnswers(statement = StatementAnswer.Agree))

        assertEquals(NumbersSettings(preset = PresetId.NoNumbers), after.numbers)
        assertNull(after.numbers.changedAt)
        assertEquals(OnboardingAnswers(hard = true), after.onboarding)
        assertEquals(before.copy(numbers = after.numbers, onboarding = after.onboarding), after)
    }

    @Test
    fun startingWith_ignoresAStampFromBefore() {
        // A changedAt already on the device belongs to someone else's choice;
        // onboarding's own pick is "set during setup".
        val stamped = Settings(numbers = NumbersSettings(changedAt = Instant.parse("2026-01-01T00:00:00Z")))

        assertNull(stamped.startingWith(PresetId.Later, SetupAnswers()).numbers.changedAt)
    }
}
