package com.opson.libraries.widgets

import com.opson.libraries.diary.Fast
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.nutrition.FastingPlan
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.minus
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.hours

/**
 * The snapshot JSON the app writes, kept as files the Swift extension's decoder is checked
 * against (`scripts/check_widget_snapshot.sh`), so a field renamed on one side fails a build
 * instead of drawing "Open Opson" or a 0 on someone's home screen.
 *
 * When the shape changes on purpose, record the files again and run the Swift check:
 * `RECORD_WIDGET_FIXTURES=1 ./gradlew :libraries:widgets:testDebugUnitTest`.
 *
 * JVM only, in `androidUnitTest`: it reads and writes files in the repo.
 */
class WidgetSnapshotFixtureTest {

    private val pretty = Json {
        encodeDefaults = true
        prettyPrint = true
    }

    @Test
    fun allNumbers_matchesTheFixture() = check("snapshot-full.json", full())

    @Test
    fun noNumbers_matchesTheFixture() = check("snapshot-no-numbers.json", build(PresetId.NoNumbers, fasting = false))

    @Test
    fun theFixtures_decodeAsTheAppReadsThem() {
        listOf("snapshot-full.json", "snapshot-no-numbers.json").forEach { name ->
            assertTrue(WidgetJson.decode(file(name).readText()) != null, "$name no longer decodes")
        }
    }

    private fun full(): WidgetSnapshot = build(PresetId.Full, fasting = true)

    private fun build(preset: PresetId, fasting: Boolean): WidgetSnapshot {
        // The design's day with some water and macros, so every widget has something to draw.
        val day = WidgetFixtures.day.copy(
            waterMl = 1250,
            itemsBySlot = WidgetFixtures.day.itemsBySlot.mapValues { (_, items) ->
                items.map { it.copy(protein = 12.0, carbs = 30.0, fat = 8.0, fiber = 3.0) }
            },
        )
        val inputs = WidgetFixtures.inputs(NumbersSettings(preset = preset), day).let { inputs ->
            if (!fasting) {
                inputs
            } else {
                // The design's noon, in the eating window, with last night's fast done.
                val lastNight = Fast(
                    day = WidgetFixtures.today.minus(DatePeriod(days = 1)),
                    startedAt = WidgetFixtures.noon - 17.hours,
                    goalHours = 14,
                )
                inputs.copy(
                    fasting = FastingInputs(FastingPlan.FourteenTen, lastNight, lastLoggedAt = null, now = WidgetFixtures.noon, zone = WidgetFixtures.timeZone),
                )
            }
        }
        return WidgetSnapshotBuilder.build(inputs, WidgetFixtures.policy(preset), ENGLISH)
    }

    private fun check(name: String, snapshot: WidgetSnapshot) {
        val json = pretty.encodeToString(WidgetSnapshot.serializer(), snapshot) + "\n"
        val file = file(name)
        if (System.getenv("RECORD_WIDGET_FIXTURES") == "1") {
            file.parentFile.mkdirs()
            file.writeText(json)
            return
        }
        assertTrue(file.exists(), "${file.path} is missing. Record it: RECORD_WIDGET_FIXTURES=1 ./gradlew :libraries:widgets:testDebugUnitTest")
        assertEquals(
            file.readText(),
            json,
            "The widget snapshot's JSON changed. If that was meant, record the fixtures again " +
                "(RECORD_WIDGET_FIXTURES=1 ./gradlew :libraries:widgets:testDebugUnitTest) and run scripts/check_widget_snapshot.sh",
        )
    }

    /** `apps/ios/TestFixtures/widgets`, found from wherever Gradle runs the test. */
    private fun file(name: String): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null && !File(dir, "settings.gradle.kts").exists()) dir = dir.parentFile
        return File(requireNotNull(dir) { "No repo root above ${System.getProperty("user.dir")}" }, "apps/ios/TestFixtures/widgets/$name")
    }

    private companion object {
        /** strings_widgets.xml's English, as the app resolves it. */
        val ENGLISH = WidgetCopy(
            today = "Today",
            log = "Log food",
            noteLog = "Tap + to log",
            noteUntracked = "Not tracking today",
            meals = mapOf(
                MealSlot.Breakfast to WidgetCopy.MealCopy("Breakfast", "Breakfast, done", "Breakfast, not yet"),
                MealSlot.Lunch to WidgetCopy.MealCopy("Lunch", "Lunch, done", "Lunch, not yet"),
                MealSlot.Snack to WidgetCopy.MealCopy("Snack", "Snack, done", "Snack, not yet"),
                MealSlot.Dinner to WidgetCopy.MealCopy("Dinner", "Dinner, done", "Dinner, not yet"),
            ),
        )
    }
}
