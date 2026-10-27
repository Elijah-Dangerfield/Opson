package com.opson.features.logger.impl

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.PresetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [VoiceStateBuilder] and how [LoggerStateBuilder] carries it: the flag, each
 * capture's phase, notice and announcement, and that lines under a spoken
 * description go through the policy like typed ones. Also [WordBucket]. What
 * moves the capture between states is [DescribeVoiceViewModelTest]'s.
 */
class VoiceStateBuilderTest {
    private val full = policy(PresetId.Full)
    private val blind = policy(PresetId.NoNumbers)

    @Test
    fun theFlagOffMeansNoMic() {
        assertNull(VoiceStateBuilder.build(enabled = false, capture = VoiceCapture.Listening("", "2 eggs")))
        assertNull(VoiceFixtures.describeState(full, enabled = false).describe.voice)
        assertNull(LoggerFixtures.describeState(full).describe.voice, "the 0.1 Describe pane is unchanged")
    }

    @Test
    fun idleShowsTheMicAndTheDesignsPrompt() {
        assertEquals(VoiceUi(), VoiceStateBuilder.build(true, VoiceCapture.Idle()))
        assertEquals(VoiceUi(), VoiceStateBuilder.build(true, VoiceCapture.Asking))
    }

    @Test
    fun listeningSaysSoAndShowsTheWordsSoFar() {
        val state = VoiceFixtures.listening(full)

        assertEquals(VoiceUi(phase = VoicePhase.Listening, announcement = VoiceAnnouncement.Listening), state.describe.voice)
        assertEquals("2 eggs, toast with butter and", state.describe.text)
    }

    @Test
    fun stoppedIsAnnouncedWhileSettlingAndAfter() {
        assertEquals(
            VoiceUi(phase = VoicePhase.Transcribing, announcement = VoiceAnnouncement.Stopped),
            VoiceStateBuilder.build(true, VoiceCapture.Transcribing("", "2 eggs")),
        )
        assertEquals(
            VoiceUi(announcement = VoiceAnnouncement.Stopped),
            VoiceStateBuilder.build(true, VoiceCapture.Idle(afterSession = true)),
        )
    }

    @Test
    fun aNoticeCarriesItsReasonAndFocusFollowsTypeInstead() {
        val denied = VoiceNotice.Denied(canAskAgain = false)
        assertEquals(VoiceUi(notice = denied), VoiceFixtures.notice(full, denied).describe.voice)
        assertTrue(VoiceStateBuilder.build(true, VoiceCapture.Idle(focusText = true))?.focusText == true)
    }

    @Test
    fun pickingForAMealHasNoMic() {
        val state = LoggerStateBuilder.build(
            inputs = LoggerFixtures.inputs(),
            policy = full,
            choices = LoggerChoices(slot = MealSlot.Dinner, pickingForMeal = true, voiceEnabled = true),
        )
        assertNull(state.describe.voice)
    }

    /** The voice path's lines are the Describe lines: Full shows their energy, Blind hides it. */
    @Test
    fun linesUnderASpokenDescriptionGoThroughThePolicy() {
        val shown = VoiceFixtures.listeningOverLines(full).describe.lines
        val hidden = VoiceFixtures.listeningOverLines(blind).describe.lines

        assertEquals(listOf("Egg, large", "Toast, white"), shown.map { it.name })
        shown.forEach { assertIs<Display.Shown>(it.calories) }
        hidden.forEach { assertEquals(Display.Hidden, it.calories) }
        assertEquals(LoggerFixtures.describeState(full).describe.lines, shown)
    }

    @Test
    fun wordsAreBucketedNeverCounted() {
        assertEquals(WordBucket.UpTo3, WordBucket.of("coffee"))
        assertEquals(WordBucket.UpTo3, WordBucket.of("  two   eggs  "))
        assertEquals(WordBucket.UpTo7, WordBucket.of("two eggs and toast"))
        assertEquals(WordBucket.UpTo15, WordBucket.of(VoiceFixtures.SPOKEN))
        assertEquals(WordBucket.More, WordBucket.of(List(16) { "egg" }.joinToString(" ")))
    }
}
