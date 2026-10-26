package com.opson.leaktest

import com.opson.libraries.nutrition.PresetId
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards what [BlindModeLeakTest] covers, so coverage can't shrink without a failing build:
 * deleting a surface, renaming its id, dropping a feature's list, a preset that quietly starts
 * showing calories, or unmarking a surface's `showsCaloriesInFull` to dodge the control.
 *
 * A new surface is covered the moment its feature lists it; add its id to [REQUIRED_SURFACES] (and
 * the table in docs/plans/screens/blind-leak-test.md) so it stays covered.
 */
class SurfacesCoverageTest {

    @Test
    fun everyRequiredSurfaceIsListed_byItsFeature() {
        REQUIRED_SURFACES.forEach { (list, required) ->
            val ids = SURFACES_BY_LIST[list]?.map { it.id }.orEmpty()
            val missing = required - ids.toSet()
            assertTrue(missing.isEmpty(), "$list no longer lists $missing, so the leak test stopped covering them")
        }
    }

    @Test
    fun everyEnergyOutSurfaceIsListed() {
        val missing = EnergyOutHiddenLeakTest.ENERGY_OUT_SURFACES - SURFACES.map { it.id }.toSet()
        assertTrue(missing.isEmpty(), "EnergyOutHiddenLeakTest names $missing, which no feature lists any more")
    }

    @Test
    fun idsAreUnique() {
        val duplicates = SURFACES.groupBy { it.id }.filterValues { it.size > 1 }.keys
        assertTrue(duplicates.isEmpty(), "Surface ids listed twice: $duplicates")
    }

    @Test
    fun theControlCoversEverySurfaceThatShowsCalories() {
        val controlled = SURFACES.filter { it.showsCaloriesInFull }.map { it.id }.toSet()
        val dropped = SHOWS_CALORIES_IN_FULL - controlled
        assertTrue(dropped.isEmpty(), "$dropped no longer marked showsCaloriesInFull, so the control stopped checking them")
    }

    @Test
    fun everyPresetThatDoesNotShowCaloriesIsTested() {
        assertEquals(
            setOf(
                PresetId.Delayed, PresetId.Blind, PresetId.Nutrient, PresetId.Rhythm,
                PresetId.Explorer, PresetId.Mindful, PresetId.Fuel, PresetId.Coast,
            ),
            PRESETS_UNDER_TEST.toSet(),
        )
    }

    @Test
    fun everyDesignedRevealNamesARealSurfaceInATestedPreset() {
        val ids = SURFACES.map { it.id }.toSet()
        REVEALED_BY_DESIGN.keys.forEach { (preset, id) ->
            assertTrue(id in ids, "REVEALED_BY_DESIGN names $id, which no feature lists any more")
            assertTrue(preset in PRESETS_UNDER_TEST, "REVEALED_BY_DESIGN names $preset, which the leak test does not run")
        }
    }

    /**
     * Every `XLeakSurfaces` object in a feature's source is in [SURFACES_BY_LIST]. Read off the
     * source tree because a feature this module does not depend on is not on its classpath either.
     */
    @Test
    fun everyFeaturesSurfaceListIsJoined() {
        val repoRoot = File(requireNotNull(System.getProperty("leaktest.repoRoot")) { "Run through Gradle" })
        val declared = File(repoRoot, "features").walkTopDown()
            .filter { it.isFile && it.name.endsWith("LeakSurfaces.kt") && "/build/" !in it.path }
            .flatMap { file -> Regex("""object (\w+LeakSurfaces)\b""").findAll(file.readText()).map { it.groupValues[1] } }
            .toSet()
        assertTrue(declared.isNotEmpty(), "Found no LeakSurfaces objects under ${repoRoot.absolutePath}/features")
        assertEquals(declared, SURFACES_BY_LIST.keys, "A feature's LeakSurfaces object is not joined into SURFACES_BY_LIST")
    }

    private companion object {
        val REQUIRED_SURFACES: Map<String, List<String>> = mapOf(
            "OnboardingLeakSurfaces" to listOf(
                "onboarding-welcome", "onboarding-statement", "onboarding-recommendation", "onboarding-account",
            ),
            "TodayLeakSurfaces" to listOf(
                "today", "today-big-day", "today-added", "today-untracked", "today-groups-off", "today-past", "today-future",
                "today-day-menu", "today-day-menu-armed", "today-cards", "calendar", "meal-sheet", "meal-note",
                "meal-feel", "water-sheet", "item-sheet", "day-note", "day-close", "day-close-big-day",
                "today-reminders",
                "today-expended", "today-exercise",
                "today-summary", "today-macros", "today-sticky", "today-collapsed", "today-weight", "today-select",
                "today-select-remove", "today-swipe-delete", "today-delete-confirm", "day-close-check-in",
                "today-cards-summary",
            ),
            "LoggerLeakSurfaces" to listOf(
                "logger", "logger-picked", "logger-search", "logger-search-empty", "logger-describe",
                "logger-saved", "logger-amount", "logger-how-it-felt",
                "logger-photo", "logger-photo-swap", "logger-photo-start",
                "logger-describe-voice",
                "logger-search-tabs", "logger-search-options",
                "logger-uncategorized", "logger-amount-units",
            ),
            "FoodsLeakSurfaces" to listOf("foods", "foods-create-meal", "foods-add-food", "foods-add-food-blind"),
            "TrendsLeakSurfaces" to listOf(
                "trends", "trends-blind", "trends-coast", "trends-settings", "nutrients", "nutrients-week",
                "weight", "patterns", "patterns-blind", "progress-photos", "progress-photos-compare",
            ),
            "SettingsLeakSurfaces" to listOf(
                "more", "modes", "pause-sheet", "targets", "targets-goal-style", "targets-deficit",
                "targets-by-day", "water-settings", "support", "support-delete", "support-find", "look-and-feel",
                "reminders", "reminder-notification",
                "integrations", "integrations-leave-out", "fasting",
                "widget", "widgets",
            ),
        )

        val SHOWS_CALORIES_IN_FULL: List<String> = listOf(
            "today", "today-big-day", "today-added", "today-untracked", "today-groups-off", "today-past", "today-future",
            "today-day-menu", "today-day-menu-armed", "item-sheet", "day-close", "day-close-big-day",
            "logger", "logger-picked", "logger-search", "logger-describe", "logger-saved", "logger-amount",
            "logger-photo", "logger-photo-swap",
            "foods", "foods-create-meal", "foods-add-food",
            "trends", "trends-blind", "trends-coast", "nutrients", "nutrients-week", "patterns", "patterns-blind",
            "targets", "targets-deficit", "targets-by-day",
            "today-reminders", "today-expended", "today-exercise", "integrations",
            "today-summary", "today-macros", "today-sticky", "today-collapsed", "today-weight", "today-select",
            "today-select-remove", "today-swipe-delete", "today-delete-confirm", "day-close-check-in",
            "logger-describe-voice", "logger-search-tabs", "logger-uncategorized", "logger-amount-units",
            "widget", "widgets",
        )
    }
}
