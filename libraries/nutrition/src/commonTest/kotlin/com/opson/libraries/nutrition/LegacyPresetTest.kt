package com.opson.libraries.nutrition

import com.opson.libraries.nutrition.Visibility.Delay
import com.opson.libraries.nutrition.Visibility.Hide
import com.opson.libraries.nutrition.Visibility.Show
import com.opson.libraries.nutrition.Visibility.Soften
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Settings that still name one of the ten old presets. The rule: a migrated person sees exactly
 * what they saw, every metric, so no mapping ever raises (or quietly lowers) anything.
 */
class LegacyPresetTest {

    private val today = LocalDate(2026, 10, 27)

    private fun migrate(
        key: String,
        overrides: Map<Metric, Visibility> = emptyMap(),
        photos: Boolean? = null,
        sprintStartedOn: LocalDate? = null,
    ) = assertNotNull(LegacyPreset.migrate(key, overrides, photos, sprintStartedOn))

    private fun LegacyPreset.Migrated.settings() =
        NumbersSettings(preset = preset, overrides = overrides, progressPhotos = progressPhotos, revealOn = revealOn, endAfter = endAfter)

    @Test
    fun eachOldPreset_becomesTheModeTheDesignNames() {
        assertEquals(
            mapOf(
                "full" to PresetId.Full,
                "delayed" to PresetId.Later,
                "blind" to PresetId.NoNumbers,
                "nutrient" to PresetId.NutrientsFirst,
                "rhythm" to PresetId.NoNumbers,
                "explorer" to PresetId.NoNumbers,
                "mindful" to PresetId.NoNumbers,
                "fuel" to PresetId.NutrientsFirst,
                "sprint" to PresetId.Full,
                "coast" to PresetId.Later,
            ),
            LegacyPreset.entries.associate { it.key to migrate(it.key).preset },
        )
    }

    @Test
    fun everyOldPreset_withAnyOverride_landsOnExactlyTheLevelsItHad() {
        val overrideSets = listOf(emptyMap<Metric, Visibility>()) + Metric.entries.flatMap { metric -> Visibility.entries.map { mapOf(metric to it) } }
        LegacyPreset.entries.forEach { legacy ->
            overrideSets.forEach { overrides ->
                val migrated = migrate(legacy.key, overrides).settings()
                Metric.entries.forEach { metric ->
                    val before = overrides[metric] ?: legacy.vis.getValue(metric)
                    assertEquals(before, migrated.level(metric), "${legacy.key} $overrides, $metric")
                }
            }
        }
    }

    @Test
    fun noMigration_isARaise() {
        LegacyPreset.entries.forEach { legacy ->
            val before = NumbersSettings(overrides = legacy.vis)
            val after = migrate(legacy.key).settings()
            assertEquals(false, before.isRaise(after), legacy.key)
        }
    }

    @Test
    fun aPresetThatMatches_carriesNoOverrides() {
        listOf("full", "delayed", "blind", "rhythm", "mindful").forEach { assertEquals(emptyMap(), migrate(it).overrides, it) }
    }

    @Test
    fun nutrientFocus_keepsCaloriesHidden() {
        // Nutrients first softens calories; Nutrient focus hid them. Showing a word would be a raise.
        assertEquals(mapOf(Metric.Calories to Hide), migrate("nutrient").overrides)
    }

    @Test
    fun fuel_keepsProteinAndFiberAsAmounts() {
        assertEquals(mapOf(Metric.Protein to Show, Metric.Fiber to Show), migrate("fuel").overrides)
    }

    @Test
    fun coast_becomesLaterWeekly_withEverythingButCaloriesStillHidden() {
        val coast = migrate("coast")
        assertEquals(RevealOn.Week, coast.revealOn)
        assertEquals(Delay, coast.settings().level(Metric.Calories))
        listOf(Metric.Remaining, Metric.EnergyOut, Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber).forEach {
            assertEquals(Hide, coast.overrides[it], it.name)
        }
        assertEquals(RevealOn.DayClose, migrate("delayed").revealOn)
    }

    @Test
    fun photos_stayWhereThePresetHadThem() {
        assertEquals(true, migrate("explorer").progressPhotos, "Explorer showed photos; No numbers hides them")
        assertEquals(true, migrate("nutrient").progressPhotos, "Nutrient focus showed photos; Nutrients first hides them")
        assertNull(migrate("blind").progressPhotos)
        assertNull(migrate("fuel").progressPhotos)
        assertEquals(false, migrate("full", photos = false).progressPhotos, "a choice the person made is kept")
    }

    @Test
    fun aSprintUnderway_endsWhenItsFourteenDaysDo_thenNoNumbers() {
        val start = LocalDate(2026, 10, 20)
        val sprint = migrate("sprint", sprintStartedOn = start)
        assertEquals(PresetId.Full, sprint.preset)
        assertEquals(ModeTiming(startedOn = start, weeks = 2, then = PresetId.NoNumbers), sprint.endAfter)
        assertEquals(LocalDate(2026, 11, 3), sprint.endAfter?.endsOn)
    }

    @Test
    fun aSprintAlreadyOver_keepsItsTimer_soItEndsAsSprintPromised() {
        val over = migrate("sprint", sprintStartedOn = LocalDate(2026, 9, 1)).endAfter
        assertEquals(true, over?.isDue(today))
    }

    @Test
    fun aSprintWithNoStart_hasNoTimer() {
        assertNull(migrate("sprint").endAfter)
    }

    @Test
    fun aCurrentKey_readsAsItself_andAnUnknownOneAsNull() {
        val later = migrate("later", mapOf(Metric.Water to Hide))
        assertEquals(PresetId.Later, later.preset)
        assertEquals(mapOf(Metric.Water to Hide), later.overrides)
        assertNull(LegacyPreset.migrate("strict", emptyMap(), null, null))
    }

    @Test
    fun closestTo_givesAnOldPhoneTheSameLevels() {
        val settings = listOf(
            NumbersSettings(),
            NumbersSettings(preset = PresetId.NutrientsFirst),
            NumbersSettings(preset = PresetId.NutrientsFirst, overrides = mapOf(Metric.Calories to Hide)),
            NumbersSettings(preset = PresetId.Later),
            NumbersSettings(preset = PresetId.Later, revealOn = RevealOn.Week),
            NumbersSettings(preset = PresetId.NoNumbers),
            NumbersSettings(preset = PresetId.NoNumbers, overrides = mapOf(Metric.Weight to Soften)),
        )
        settings.forEach { numbers ->
            val (legacy, overrides) = LegacyPreset.closestTo(numbers)
            Metric.entries.forEach { metric ->
                assertEquals(numbers.level(metric), overrides[metric] ?: legacy.vis.getValue(metric), "$numbers, $metric")
            }
        }
        assertEquals(LegacyPreset.Coast, LegacyPreset.closestTo(NumbersSettings(preset = PresetId.Later, revealOn = RevealOn.Week)).first)
        assertEquals(LegacyPreset.Blind to emptyMap<Metric, Visibility>(), LegacyPreset.closestTo(NumbersSettings(preset = PresetId.NoNumbers)))
    }

    @Test
    fun aRoundTripThroughAnOldPhone_landsWhereItStarted() {
        val start = NumbersSettings(preset = PresetId.NutrientsFirst, overrides = mapOf(Metric.Carbs to Soften))
        val (legacy, overrides) = LegacyPreset.closestTo(start)
        val back = migrate(legacy.key, overrides).settings()
        assertEquals(start.preset, back.preset)
        Metric.entries.forEach { assertEquals(start.level(it), back.level(it), it.name) }
    }
}
