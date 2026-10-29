package com.opson.leaktest

import com.opson.libraries.nutrition.PresetId
import org.junit.Test
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Guards what [CaloriesHiddenLeakTest] covers, so coverage can't shrink without a failing build:
 * deleting a surface, renaming its id, dropping a feature's list, a preset that quietly starts
 * showing calories, or unmarking a surface's `showsCaloriesInFull` to dodge the control.
 *
 * A new surface is covered the moment its feature lists it, and this fails until its id is in
 * [REQUIRED_SURFACES] (and, if it shows calories in Full, [SHOWS_CALORIES_IN_FULL]), so it stays
 * covered. Add it to the table in docs/plans/screens/blind-leak-test.md too.
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

    /** The pin is only a pin if a new surface has to join it: two barcode surfaces once did not. */
    @Test
    fun everyListedSurfaceIsPinned() {
        val unpinned = SURFACES.map { it.id }.toSet() - REQUIRED_SURFACES.values.flatten().toSet()
        assertTrue(unpinned.isEmpty(), "$unpinned are not in REQUIRED_SURFACES, so deleting them would not fail the build")
        val uncontrolled = SURFACES.filter { it.showsCaloriesInFull }.map { it.id }.toSet() - SHOWS_CALORIES_IN_FULL.toSet()
        assertTrue(
            uncontrolled.isEmpty(),
            "$uncontrolled are not in SHOWS_CALORIES_IN_FULL, so unmarking them would drop them from the control unnoticed",
        )
    }

    @Test
    fun everyEnergyOutSurfaceIsListed() {
        val missing = EnergyOutHiddenLeakTest.ENERGY_OUT_SURFACES - SURFACES.map { it.id }.toSet()
        assertTrue(missing.isEmpty(), "EnergyOutHiddenLeakTest names $missing, which no feature lists any more")
    }

    /** A renamed micros surface would otherwise drop out of MicrosHiddenLeakTest without a word. */
    @Test
    fun everyMicrosSurfaceIsListed() {
        val missing = MicrosHiddenLeakTest.MICROS_SURFACES - SURFACES.map { it.id }.toSet()
        assertTrue(missing.isEmpty(), "MicrosHiddenLeakTest names $missing, which no feature lists any more")
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
    fun everyModeThatDoesNotShowCaloriesIsTested_noNumbersFirst() {
        assertEquals(listOf("no_numbers", "macros_only", "macros_grams", "later", "later-weekly"), MODES_UNDER_TEST.map { it.name })
        assertEquals(PresetId.entries.toSet(), MODES_UNDER_TEST.map { it.settings.preset }.toSet(), "every preset but All numbers, and Later on it")
    }

    @Test
    fun everyDesignedRevealNamesARealSurfaceInATestedMode() {
        val ids = SURFACES.map { it.id }.toSet()
        val modes = MODES_UNDER_TEST.map { it.name }.toSet()
        REVEALED_BY_DESIGN.keys.forEach { (mode, id) ->
            assertTrue(id in ids, "REVEALED_BY_DESIGN names $id, which no feature lists any more")
            assertTrue(mode in modes, "REVEALED_BY_DESIGN names $mode, which the leak test does not run")
        }
        assertTrue(REVEALED_BY_DESIGN.keys.none { (mode, _) -> mode == "no_numbers" }, "No numbers reveals nothing, anywhere")
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
                "onboarding-goal", "onboarding-goal-from-burn", "onboarding-resting-energy", "onboarding-meal-times",
            ),
            "TodayLeakSurfaces" to listOf(
                "today", "today-big-day", "today-added", "today-untracked", "today-groups-off", "today-past", "today-future",
                "today-day-menu", "today-day-menu-armed", "today-cards", "calendar", "meal-sheet", "meal-note",
                "water-sheet", "item-sheet", "day-note", "day-close", "day-close-big-day",
                "today-timer-ended", "today-timer-waiting",
                "today-expended", "today-exercise", "today-burned", "today-burned-health",
                "today-summary", "today-macros", "today-sticky", "today-collapsed", "today-weight", "today-select",
                "today-select-remove", "today-swipe-delete", "today-delete-confirm", "day-close-check-in",
                "today-cards-summary",
                "today-summary-in-out", "today-summary-range-bar", "today-summary-rings", "today-summary-equation",
                "today-calendar", "today-select-v4", "today-drag-move", "today-moved", "food-actions", "food-actions-amount",
                "today-notes",
                "today-closed", "day-closed-sheet", "day-close-closed", "food-actions-closed",
            ),
            "LoggerLeakSurfaces" to listOf(
                "add-sheet", "add-sheet-expanded", "add-sheet-uncategorized",
                "search-full", "search-browse", "search-saved-meals", "search-pick-for-meal", "search-tabs", "search-options",
                "search-empty",
                "logger-describe", "logger-describe-voice", "logger-describe-edited", "logger-describe-quick-add", "logger-describe-find",
                "logger-saved",
                "food-detail", "food-detail-nutrients", "food-detail-units", "food-detail-scan", "food-detail-no-numbers",
                "food-detail-more", "food-detail-liquid", "food-detail-creamer", "food-detail-pounds",
                "logger-photo", "logger-photo-swap", "logger-photo-start", "logger-scan-miss",
                "submit-food-photos", "submit-food-review", "submit-food-by-hand",
                "submit-food-save-photos", "submit-food-save-review", "submit-food-photo-failed",
                "search-unverified", "food-detail-in-review",
                "exercise-sheet", "exercise-sheet-other", "exercise-sheet-pick",
                "add-sheet-closed", "search-closed",
            ),
            "FoodsLeakSurfaces" to listOf(
                "foods", "foods-create-meal", "foods-add-food", "foods-add-food-blind", "edit-a-copy", "edit-a-copy-label", "edit-a-copy-sizes",
                "foods-add-food-pack", "foods-unverified",
            ),
            "TrendsLeakSurfaces" to listOf(
                "trends", "trends-blind", "trends-coast", "trends-settings", "nutrients", "nutrients-week",
                "nutrient-foods", "nutrient-foods-week", "nutrient-foods-protein",
                "weight", "patterns", "patterns-blind", "progress-photos", "progress-photos-compare",
            ),
            "SettingsLeakSurfaces" to listOf(
                "more", "modes", "modes-end-after", "modes-each-number", "pause-sheet", "targets", "targets-goal-style", "targets-deficit",
                "targets-by-day", "targets-exact", "targets-surplus", "targets-by-day-shapes",
                "targets-deficit-estimated", "targets-macros", "targets-safety-notes",
                "resting-energy", "energy-today", "today-screen-settings", "micro-targets", "micro-target-sheet",
                "water-settings", "support", "support-delete", "support-export-numbers", "support-provider-first", "support-find", "look-and-feel",
                "provider-summary", "provider-summary-warning", "provider-summary-custom",
                "reminders", "reminder-notification",
                "integrations", "integrations-leave-out", "fasting",
                "widget", "widgets",
            ),
        )

        val SHOWS_CALORIES_IN_FULL: List<String> = listOf(
            "onboarding-goal", "onboarding-goal-from-burn", "onboarding-resting-energy",
            "today", "today-big-day", "today-added", "today-untracked", "today-groups-off", "today-past", "today-future",
            "today-day-menu", "today-day-menu-armed", "item-sheet", "day-close", "day-close-big-day",
            "add-sheet-expanded", "search-full", "search-browse", "search-saved-meals", "search-pick-for-meal",
            "logger-describe", "logger-saved", "food-detail", "logger-photo", "logger-photo-swap", "submit-food-review",
            "foods", "foods-create-meal", "foods-add-food",
            "trends", "trends-blind", "trends-coast", "nutrients", "nutrients-week", "patterns", "patterns-blind",
            "targets", "targets-deficit", "targets-by-day", "targets-exact", "targets-surplus", "targets-by-day-shapes",
            "targets-goal-style", "energy-today", "targets-deficit-estimated", "targets-macros", "targets-safety-notes",
            "today-summary-in-out", "today-summary-range-bar", "today-summary-rings", "today-summary-equation",
            "today-timer-ended", "today-timer-waiting", "today-expended", "today-exercise", "integrations",
            "today-summary", "today-macros", "today-sticky", "today-collapsed", "today-weight", "today-select",
            "today-select-remove", "today-swipe-delete", "today-delete-confirm", "day-close-check-in",
            "today-calendar", "today-select-v4", "today-drag-move", "today-moved", "food-actions", "edit-a-copy",
            "logger-describe-voice", "search-tabs", "food-detail-units",
            "food-detail-scan", "food-detail-nutrients", "logger-describe-edited", "logger-describe-find", "logger-describe-quick-add",
            "widget", "widgets", "food-detail-more", "food-detail-liquid", "food-detail-creamer", "food-detail-pounds",
            "edit-a-copy-label", "edit-a-copy-sizes",
            "today-burned", "today-burned-health", "exercise-sheet", "exercise-sheet-other",
            "provider-summary", "provider-summary-warning", "provider-summary-custom",
            "today-closed", "day-close-closed", "food-actions-closed",
            "search-unverified", "food-detail-in-review", "submit-food-save-review", "foods-add-food-pack", "foods-unverified",
        )
    }
}
