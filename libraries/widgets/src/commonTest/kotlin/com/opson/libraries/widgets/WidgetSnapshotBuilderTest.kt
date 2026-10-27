package com.opson.libraries.widgets

import com.opson.libraries.diary.Day
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.nutrition.DayGroup
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toInstant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class WidgetSnapshotBuilderTest {

    private fun build(
        preset: PresetId,
        day: Day = WidgetFixtures.day,
        mealGroups: Boolean = true,
        policy: VisibilityPolicy = WidgetFixtures.policy(preset),
    ): WidgetSnapshot = WidgetSnapshotBuilder.build(
        WidgetFixtures.inputs(NumbersSettings(preset = preset), day, mealGroups),
        policy,
        COPY,
    )

    private val WidgetSnapshot.today: WidgetDay get() = days.first()

    @Test
    fun full_showsTheNumbersCard_asTheDesign() {
        val energy = assertNotNull(build(PresetId.Full).today.energy)
        assertEquals("1,245", energy.amount)
        assertEquals("kcal", energy.unit)
        assertEquals("1,245 kcal", energy.label)
        // The bar runs to 2,760 (2,300 plus 20%), so the range box is 1,900 to 2,300 of that.
        assertEquals(1245 / 2760f, energy.fraction, 0.001f)
        assertEquals(1900 / 2760f, energy.rangeStart, 0.001f)
        assertEquals(2300 / 2760f, energy.rangeEnd, 0.001f)
    }

    @Test
    fun aRangeThatDiffersByDay_drawsThatDaysRange() {
        // 2026-10-25 is a Sunday: its group's range, not the base 1,900 to 2,300.
        val base = WidgetFixtures.inputs(NumbersSettings(preset = PresetId.Full), WidgetFixtures.day, mealGroups = true)
        val weekend = DayGroup(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), minKcal = 2300, maxKcal = 2700)
        val weekdays = DayGroup(DayOfWeek.entries.toSet() - weekend.days, minKcal = 1900, maxKcal = 2300)
        val byDay = base.settings.targets.copy(style = TargetStyle.ByDay(listOf(weekdays, weekend)))
        val inputs = base.copy(settings = base.settings.copy(targets = byDay))

        val energy = assertNotNull(WidgetSnapshotBuilder.build(inputs, WidgetFixtures.policy(PresetId.Full), COPY).today.energy)

        // The bar runs to 3,240 (2,700 plus 20%).
        assertEquals(2300 / 3240f, energy.rangeStart, 0.001f)
        assertEquals(2700 / 3240f, energy.rangeEnd, 0.001f)
    }

    @Test
    fun oneNumber_drawsAMarkAtIt_notABox() {
        val base = WidgetFixtures.inputs(NumbersSettings(preset = PresetId.Full), WidgetFixtures.day, mealGroups = true)
        val inputs = base.copy(settings = base.settings.copy(targets = base.settings.targets.useExact()))

        val energy = assertNotNull(WidgetSnapshotBuilder.build(inputs, WidgetFixtures.policy(PresetId.Full), COPY).today.energy)

        // The bar runs to 2,520 (2,100 plus 20%), and the goal's ends meet.
        assertEquals(2100 / 2520f, energy.rangeStart, 0.001f)
        assertEquals(energy.rangeStart, energy.rangeEnd)
    }

    @Test
    fun aSetDeficit_drawsItsTargetAgainstBurned() {
        val base = WidgetFixtures.inputs(NumbersSettings(preset = PresetId.Full), WidgetFixtures.day, mealGroups = true)
        val deficit = base.settings.targets.copy(startingKcal = 1500, style = TargetStyle.Deficit(500, 500))
        val inputs = base.copy(settings = base.settings.copy(targets = deficit), burnedKcal = 2180)

        val energy = assertNotNull(WidgetSnapshotBuilder.build(inputs, WidgetFixtures.policy(PresetId.Full), COPY).today.energy)

        // 2,180 less 500 is 1,680; the bar runs to 2,016.
        assertEquals(1680 / 2016f, energy.rangeStart, 0.001f)
        assertEquals(energy.rangeStart, energy.rangeEnd)
    }

    @Test
    fun full_drawsNoMealsAndNoNote() {
        val today = build(PresetId.Full).today
        assertTrue(today.meals.isEmpty())
        assertNull(today.note)
    }

    @Test
    fun aBigDay_fillsTheBarNoFurtherThanItsEnd() {
        val big = WidgetFixtures.day.copy(
            itemsBySlot = WidgetFixtures.day.itemsBySlot.mapValues { (_, items) -> items.map { it.copy(kcal = 2000.0) } },
        )
        assertEquals(1f, assertNotNull(build(PresetId.Full, big).today.energy).fraction)
    }

    @Test
    fun everyPresetThatDoesNotShowCalories_getsTheMealCircles_andNoAmount() {
        hidingPresets.forEach { preset ->
            val today = build(preset).today
            assertNull(today.energy, "$preset")
            assertEquals(MealSlot.meals.map { it.wire }, today.meals.map { it.slot }, "$preset")
        }
    }

    /** The snapshot itself is the leak surface: no platform can show what isn't in it. */
    @Test
    fun noSnapshotThatHidesCalories_carriesACalorieAmountAnywhere() {
        hidingPresets.forEach { preset ->
            listOf(true, false).forEach { groups ->
                val json = WidgetJson.encode(build(preset, mealGroups = groups))
                assertNull(LEAK.find(json), "$preset groups=$groups leaks: ${LEAK.find(json)?.value}")
                assertTrue("1,245" !in json && "1245" !in json, "$preset groups=$groups carries the amount")
            }
        }
    }

    @Test
    fun fullDoesCarryTheAmount_soTheLeakCheckAboveCanFail() {
        assertNotNull(LEAK.find(WidgetJson.encode(build(PresetId.Full))))
    }

    @Test
    fun delayed_staysOnTheCircles_afterTheRevealToo() {
        val evening = VisibilityPolicy(
            NumbersSettings.later(),
            clockAt(LocalDateTime(WidgetFixtures.today, LocalTime(22, 0))),
            WidgetFixtures.timeZone,
        )
        val closed = WidgetFixtures.day.copy(closedAt = WidgetFixtures.noon)
        val today = build(PresetId.Full, closed, policy = evening).today
        assertNull(today.energy)
        assertEquals(4, today.meals.size)
    }

    @Test
    fun theCircles_tickWhatIsDone() {
        val meals = build(PresetId.NoNumbers).today.meals
        assertEquals(listOf(true, true, true, false), meals.map { it.done })
        assertEquals("diary_slot_breakfast, done", meals.first().description)
        assertEquals("diary_slot_dinner, not yet", meals.last().description)
        assertEquals("diary_slot_lunch", meals[1].label)
    }

    @Test
    fun ateNoDetails_countsAsDone() {
        val day = WidgetFixtures.day.copy(ateNoDetails = setOf(MealSlot.Dinner))
        assertTrue(build(PresetId.NoNumbers, day).today.meals.all { it.done })
    }

    @Test
    fun mealGroupsOff_withCaloriesHidden_saysTapToLog() {
        val today = build(PresetId.NoNumbers, mealGroups = false).today
        assertTrue(today.meals.isEmpty())
        assertEquals(NOTE_LOG, today.note)
    }

    @Test
    fun mealGroupsOff_inFull_stillShowsTheNumbers() {
        assertNotNull(build(PresetId.Full, mealGroups = false).today.energy)
    }

    @Test
    fun anUntrackedDay_saysSo_inEveryMode() {
        val untracked = WidgetFixtures.day.copy(untracked = true)
        (hidingPresets + PresetId.Full).forEach { preset ->
            val today = build(preset, untracked).today
            assertNull(today.energy, "$preset")
            assertTrue(today.meals.isEmpty(), "$preset")
            assertEquals(NOTE_UNTRACKED, today.note, "$preset")
        }
    }

    @Test
    fun tomorrow_isAnEmptyDay_soTheWidgetTurnsOverAtMidnight() {
        val snapshot = build(PresetId.Full)
        assertEquals(listOf("2026-10-25", "2026-10-26"), snapshot.days.map { it.day })
        val tomorrow = snapshot.days.last()
        assertEquals("0", assertNotNull(tomorrow.energy).amount)
        assertEquals(0f, tomorrow.energy?.fraction)
        assertTrue(build(PresetId.NoNumbers).days.last().meals.none { it.done })
    }

    @Test
    fun dayFor_picksTheDate_thenTheLastForLater_thenTheFirst() {
        val snapshot = build(PresetId.Full)
        assertEquals("2026-10-25", snapshot.dayFor(LocalDate(2026, 10, 25))?.day)
        assertEquals("2026-10-26", snapshot.dayFor(LocalDate(2026, 10, 26))?.day)
        assertEquals("2026-10-26", snapshot.dayFor(LocalDate(2026, 11, 2))?.day)
        assertEquals("2026-10-25", snapshot.dayFor(LocalDate(2026, 10, 1))?.day)
    }

    @Test
    fun theSnapshotCarriesTheCopy_theLink_andTheVersion() {
        val snapshot = build(PresetId.Full)
        assertEquals(WidgetSnapshotBuilder.VERSION, snapshot.version)
        assertEquals("widgets_log", snapshot.logLabel)
        assertEquals(WidgetLinks.LOG, snapshot.logLink)
        assertEquals("widgets_today", snapshot.today.title)
    }

    @Test
    fun colours_areSlate() {
        val slate = build(PresetId.Full).colors
        assertEquals("#0F766F", slate.light.accent)
        assertEquals("#EEF1F6", slate.light.background)
        assertEquals("#18202E", slate.light.bar)
        assertEquals("#E0892B", slate.light.meals.getValue("breakfast").content)
        assertEquals("#6CCBBF", slate.dark.accent)
        assertEquals("#1B2130", slate.dark.background)
    }

    @Test
    fun json_roundTrips_andRefusesAnotherVersion() {
        val snapshot = build(PresetId.Full)
        val json = WidgetJson.encode(snapshot)
        assertEquals(snapshot, WidgetJson.decode(json))
        assertNull(WidgetJson.decode(json.replace("\"version\":1", "\"version\":99")))
        assertNull(WidgetJson.decode("not json"))
        assertNull(WidgetJson.decode(null))
    }

    private companion object {
        const val NOTE_LOG = "widgets_note_log"
        const val NOTE_UNTRACKED = "widgets_note_untracked"

        /** The leak test's regex, `blind-leak-test.md`. */
        val LEAK = Regex("""\d[\d,.]*\s*(kcal|cal\b|calories)|\d[\d,]*\s*left\b""", RegexOption.IGNORE_CASE)

        val hidingPresets: List<PresetId> = Presets.all.filter { it.level(Metric.Calories) != Visibility.Show }.map { it.id }

        /** Keys for values, as the reminders tests do, so a test never depends on English copy. */
        val COPY = WidgetCopy(
            today = "widgets_today",
            log = "widgets_log",
            noteLog = NOTE_LOG,
            noteUntracked = NOTE_UNTRACKED,
            meals = MealSlot.meals.associateWith { slot ->
                val label = "diary_slot_${slot.wire}"
                WidgetCopy.MealCopy(label, "$label, done", "$label, not yet")
            },
        )

        fun clockAt(at: LocalDateTime): Clock = object : Clock {
            override fun now(): Instant = at.toInstant(WidgetFixtures.timeZone)
        }
    }
}
