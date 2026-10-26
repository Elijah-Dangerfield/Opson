package com.opson.features.onboarding.impl

import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import kotlinx.datetime.LocalDate

class SetupAnswersTest {

    @Test
    fun eachQuestion_offersItsOwnOptions_inOrder() {
        assertEquals(Why.entries, SetupQuestion.Reason.options)
        assertEquals(NumbersFeel.entries, SetupQuestion.Feeling.options)
        assertEquals(Notice.entries, SetupQuestion.Focus.options)
        assertEquals(listOf<SetupOption>(HardBefore), SetupQuestion.Hard.options)
        assertEquals((1..4).toList(), SetupQuestion.entries.map { it.number })
    }

    @Test
    fun tap_replacesTheFeelAnswer_andTogglesTheRest() {
        val answers = SetupAnswers()
            .tap(Why.Curious)
            .tap(NumbersFeel.LoveThem)
            .tap(NumbersFeel.NotSure)
            .tap(Notice.Patterns)
            .tap(HardBefore)

        assertEquals(SetupAnswers(setOf(Why.Curious), NumbersFeel.NotSure, setOf(Notice.Patterns), hard = true), answers)
        assertEquals(SetupAnswers(feel = NumbersFeel.NotSure), answers.tap(Why.Curious).tap(Notice.Patterns).tap(HardBefore))
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
            notice = setOf(Notice.HowEatingFeels, Notice.Amounts),
            hard = true,
        )

        assertEquals(
            OnboardingAnswers(
                why = listOf("curious", "working_with_provider"),
                feel = "rather_not",
                notice = listOf("amounts", "how_eating_feels"),
                hard = true,
            ),
            answers.toSettings(),
        )
        assertEquals(OnboardingAnswers(), SetupAnswers().toSettings())
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

        val after = before.startingWith(PresetId.Blind, SetupAnswers(hard = true), today = LocalDate(2026, 10, 25))

        assertEquals(NumbersSettings(preset = PresetId.Blind), after.numbers)
        assertNull(after.numbers.changedAt)
        assertEquals(OnboardingAnswers(hard = true), after.onboarding)
        assertEquals(before.copy(numbers = after.numbers, onboarding = after.onboarding), after)
    }

    @Test
    fun startingWith_sprint_recordsTheStartDay_andOtherModesLeaveIt() {
        val today = LocalDate(2026, 10, 25)
        val earlier = LocalDate(2026, 10, 1)

        assertEquals(today, Settings().startingWith(PresetId.Sprint, SetupAnswers(), today).sprintStartedOn)
        assertEquals(
            earlier,
            Settings(sprintStartedOn = earlier).startingWith(PresetId.Delayed, SetupAnswers(), today).sprintStartedOn,
        )
        assertNull(Settings().startingWith(PresetId.Fuel, SetupAnswers(), today).sprintStartedOn)
    }

    @Test
    fun startingWith_ignoresAStampFromBefore() {
        // A changedAt already on the device belongs to someone else's choice;
        // onboarding's own pick is "set during setup".
        val stamped = Settings(numbers = NumbersSettings(changedAt = Instant.parse("2026-01-01T00:00:00Z")))

        assertNull(stamped.startingWith(PresetId.Delayed, SetupAnswers(), LocalDate(2026, 10, 25)).numbers.changedAt)
    }
}
