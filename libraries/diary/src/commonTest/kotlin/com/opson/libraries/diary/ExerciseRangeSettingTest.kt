package com.opson.libraries.diary

import com.opson.libraries.nutrition.ExerciseAdjustment
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * `exerciseRange`, the 0.2 answer to "Should exercise change your range?": its own top-level key
 * (settings sync merges shallowly), unanswered until picked, and never applied for someone who
 * said numbers have been hard.
 */
class ExerciseRangeSettingTest {

    @Test
    fun unanswered_isNull_andCountsAsNo() {
        val settings = Settings()
        assertNull(settings.exerciseRange)
        assertEquals(ExerciseAdjustment.None, settings.exerciseInRange)
    }

    @Test
    fun theOldKey_isNotTheAnswer() {
        // 0.1 synced "some" for everyone; that must not move anyone's range.
        assertEquals(ExerciseAdjustment.None, Settings(exercise = ExerciseAdjustment.All).exerciseInRange)
    }

    @Test
    fun aPick_isWhatApplies() {
        ExerciseAdjustment.entries.forEach { choice ->
            assertEquals(choice, Settings(exerciseRange = choice).exerciseInRange)
        }
    }

    @Test
    fun numbersHaveBeenHard_neverMovesTheRange_whateverIsStored() {
        ExerciseAdjustment.entries.forEach { choice ->
            val settings = Settings(exerciseRange = choice, onboarding = OnboardingAnswers(hard = true))
            assertEquals(ExerciseAdjustment.None, settings.exerciseInRange, choice.name)
        }
    }

    @Test
    fun roundTrips_asItsOwnTopLevelKey() {
        ExerciseAdjustment.entries.forEach { choice ->
            val json = SettingsJson.encodeToString(Settings.serializer(), Settings(exerciseRange = choice))
            assertTrue("\"exerciseRange\":\"${choice.name.lowercase()}\"" in json, json)
            assertEquals(choice, SettingsJson.decodeFromString(Settings.serializer(), json).exerciseRange)
        }
    }

    @Test
    fun unanswered_isLeftOffTheWire() {
        // Absent, so a shallow merge on the server never overwrites a choice made on another device.
        val json = SettingsJson.encodeToString(Settings.serializer(), Settings())
        assertFalse("exerciseRange" in json, json)
    }

    @Test
    fun missingKey_decodesUnanswered() {
        assertNull(SettingsJson.decodeFromString(Settings.serializer(), """{"exercise":"all"}""").exerciseRange)
    }

    @Test
    fun unknownValue_decodesToNo() {
        assertEquals(
            ExerciseAdjustment.None,
            SettingsJson.decodeFromString(Settings.serializer(), """{"exerciseRange":"lots"}""").exerciseRange,
        )
    }
}
