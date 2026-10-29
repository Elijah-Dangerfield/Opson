package com.opson.libraries.widgets

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.Visibility
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The calories, macros, water and meals widgets' part of the snapshot, mode by mode. */
class WidgetSnapshotWidgetsTest {

    private fun today(preset: PresetId, day: Day = WidgetFixtures.day, mealGroups: Boolean = true): WidgetDay =
        WidgetSnapshotBuilder.build(
            WidgetFixtures.inputs(NumbersSettings(preset = preset), day, mealGroups),
            WidgetFixtures.policy(preset),
            WidgetCopy(today = "today", log = "log", noteLog = "noteLog", noteUntracked = "noteUntracked", meals = emptyMap()),
        ).days.first()

    @Test
    fun full_hasWhatIsLeftToTheRange_asTodaysToRangeStat() {
        // 1,245 eaten, the range starts at 1,900.
        val left = assertNotNull(today(PresetId.Full).left)
        assertEquals("655", left.amount)
        assertEquals("to range", left.unit)
        assertEquals("655 left", left.label)
    }

    @Test
    fun insideTheRange_whatIsLeftSaysOnTarget_neverZero() {
        val big = WidgetFixtures.day.copy(
            itemsBySlot = WidgetFixtures.day.itemsBySlot + (MealSlot.Dinner to WidgetFixtures.day.items(MealSlot.Lunch).map { it.copy(kcal = 400.0) }),
        )
        val left = assertNotNull(today(PresetId.Full, big).left)
        assertEquals("On", left.amount)
        assertEquals("target", left.unit)
    }

    @Test
    fun theRing_fillsToTheTopOfTheRange() {
        val energy = assertNotNull(today(PresetId.Full).energy)
        assertEquals(1245 / 2300f, energy.ring, 0.001f)
        assertEquals(1900 / 2300f, energy.ringRangeStart, 0.001f)
    }

    @Test
    fun everyModeThatHidesWhatIsLeft_hasNoLeft() {
        Presets.all.filter { it.level(Metric.Remaining) != Visibility.Show }.forEach { preset ->
            assertNull(today(preset.id).left, "${preset.id}")
        }
    }

    @Test
    fun mealsDone_isInEveryMode_withMealGroupsOn() {
        Presets.all.forEach { preset ->
            val progress = assertNotNull(today(preset.id).progress, "${preset.id}")
            assertEquals(3, progress.done)
            assertEquals(4, progress.total)
            assertEquals("3 of 4 meals", progress.label)
        }
        assertNull(today(PresetId.Full, mealGroups = false).progress)
    }

    @Test
    fun macros_areOnlyTheOnesShownInFull() {
        assertEquals(listOf("protein", "carbs", "fat", "fiber"), today(PresetId.Full).macros.map { it.key })
        Presets.all.forEach { preset ->
            val shown = Metric.entries.filter { it.isMacro && preset.level(it) == Visibility.Show }.map { it.key }
            assertEquals(shown, today(preset.id).macros.map { it.key }, "${preset.id}")
        }
    }

    @Test
    fun aMacro_readsAgainstItsTarget() {
        val protein = today(PresetId.Full).macros.first()
        assertEquals("Protein", protein.label)
        assertEquals("0", protein.amount)
        assertEquals("g", protein.unit)
        assertEquals("0 / 120 g", protein.text)
    }

    @Test
    fun water_isTheAmountInFull_theGlassesWhenSoftened_andNothingWhenHidden() {
        val day = WidgetFixtures.day.copy(waterMl = 1250)
        val full = assertNotNull(today(PresetId.Full, day).water)
        assertEquals("1,250 of 2,000 ml", full.text)
        assertEquals(5, full.filled)
        assertEquals(8, full.total)
        assertEquals("250 ml", full.glass)

        Presets.all.forEach { preset ->
            val water = today(preset.id, day).water
            when (preset.level(Metric.Water)) {
                Visibility.Show -> assertNotNull(water?.text, "${preset.id}")
                Visibility.Soften -> {
                    assertNotNull(water, "${preset.id}")
                    assertNull(water.text, "${preset.id} softened water has no amount")
                    assertEquals("5 of 8 glasses", water.label)
                }
                else -> assertNull(water, "${preset.id}")
            }
        }
    }

    @Test
    fun mealRows_nameWhatIsLogged_andItsEnergyOnlyInAModeThatShowsIt() {
        val rows = today(PresetId.Full).mealRows
        assertEquals(listOf("breakfast", "lunch", "snack", "dinner"), rows.map { it.slot })
        assertEquals("Oat porridge with milk, banana, coffee with milk", rows[0].foods)
        assertEquals("445 kcal", rows[0].calories)
        assertEquals("Nothing yet", rows[3].foods)
        assertEquals(WidgetLinks.log(MealSlot.Dinner), rows[3].link)

        Presets.all.filter { it.level(Metric.Calories) != Visibility.Show }.forEach { preset ->
            assertTrue(today(preset.id).mealRows.all { it.calories == null }, "${preset.id}")
        }
    }

    @Test
    fun mealGroupsOff_isOneRowOfAllFood() {
        val rows = today(PresetId.Full, mealGroups = false).mealRows
        assertEquals(1, rows.size)
        assertEquals("All food", rows.single().label)
        assertEquals(WidgetLinks.LOG, rows.single().link)
    }

    @Test
    fun anUntrackedDay_hasNoneOfIt() {
        val day = today(PresetId.Full, WidgetFixtures.day.copy(untracked = true, waterMl = 500))
        assertNull(day.left)
        assertNull(day.progress)
        assertNull(day.water)
        assertTrue(day.macros.isEmpty())
        assertTrue(day.mealRows.isEmpty())
    }

    @Test
    fun theSnapshotCarriesItsLinks_labels_andNutrientColours() {
        val snapshot = WidgetSnapshotBuilder.build(
            WidgetFixtures.inputs(),
            WidgetFixtures.policy(PresetId.Full),
            WidgetCopy(today = "today", log = "log", noteLog = "noteLog", noteUntracked = "noteUntracked", meals = emptyMap()),
        )
        assertEquals(WidgetLinks.TODAY, snapshot.todayLink)
        assertEquals(WidgetLinks.WATER_ADD, snapshot.waterLink)
        assertEquals("Add a glass", snapshot.labels.addGlass)
        assertEquals(setOf("protein", "carbs", "fat", "fiber", "water"), snapshot.colors.light.nutrients.keys)
        assertNull(snapshot.fasting, "no fasting inputs, no timer")
    }

    /** JSON the previous app wrote has none of the new fields; it still reads, with their defaults. */
    @Test
    fun jsonFromBeforeTheNewWidgets_stillDecodes() {
        val old = """{"version":1,"days":[{"day":"2026-10-25","title":"Today","energy":null,"meals":[],"note":"Tap + to log"}],""" +
            """"logLabel":"Log food","logLink":"opson://widget/log","colors":{"light":$OLD_SWATCH,"dark":$OLD_SWATCH}}"""
        val snapshot = assertNotNull(WidgetJson.decode(old))
        assertEquals(WidgetLinks.TODAY, snapshot.todayLink)
        assertTrue(snapshot.days.single().mealRows.isEmpty())
    }

    private companion object {
        const val OLD_SWATCH = """{"background":"#000000","ink":"#000000","muted":"#000000","track":"#000000",""" +
            """"bar":"#000000","accent":"#000000","onAccent":"#000000","meals":{}}"""
    }
}
