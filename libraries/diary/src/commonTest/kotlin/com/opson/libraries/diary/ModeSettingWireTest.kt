package com.opson.libraries.diary

import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ModeTiming
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RevealOn
import com.opson.libraries.nutrition.Visibility
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * The mode on the wire (design sync 4). A 0.2 phone writes the new preset in a top-level `mode`
 * and, in `numbers`, an old preset with overrides that show a 0.1 phone the same levels. It
 * reads `mode` unless a 0.1 phone changed `numbers` after it, and migrates old presets.
 */
class ModeSettingWireTest {

    private val at = Instant.parse("2026-10-25T20:14:00Z")
    private val later = Instant.parse("2026-10-26T08:00:00Z")

    private fun encode(settings: Settings): JsonObject = SettingsJson.encodeToJsonElement(Settings.serializer(), settings).jsonObject

    private fun decode(json: JsonObject): Settings = SettingsJson.decodeFromJsonElement(Settings.serializer(), json)

    private fun decode(json: String): Settings = SettingsJson.decodeFromString(Settings.serializer(), json)

    @Test
    fun theModeIsATopLevelKey_andNumbersKeepsAPresetA01PhoneKnows() {
        val json = encode(Settings(numbers = NumbersSettings(preset = PresetId.MacrosOnly, changedAt = at)))
        val mode = json.getValue("mode").jsonObject
        assertEquals("macros_only", mode.getValue("preset").jsonPrimitive.content)
        val numbers = json.getValue("numbers").jsonObject
        assertEquals("nutrient", numbers.getValue("preset").jsonPrimitive.content)
        assertFalse("mode" in numbers, "nothing new inside numbers: a 0.1 save would drop it")
    }

    @Test
    fun everyModeRoundTrips() {
        val timing = ModeTiming(startedOn = LocalDate(2026, 10, 20), weeks = 2, then = PresetId.NoNumbers)
        listOf(
            NumbersSettings(),
            NumbersSettings(preset = PresetId.MacrosOnly, overrides = mapOf(Metric.Carbs to Visibility.Show), changedAt = at),
            NumbersSettings(preset = PresetId.MacrosWithGrams, changedAt = at),
            NumbersSettings.later().copy(revealOn = RevealOn.Week, changedAt = at),
            NumbersSettings(preset = PresetId.NoNumbers, progressPhotos = true, pauseBeforeMore = true, changedAt = at),
            NumbersSettings(preset = PresetId.Full, endAfter = timing, changedAt = at),
        ).forEach { numbers ->
            val settings = Settings(numbers = numbers)
            assertEquals(settings, decode(encode(settings)), numbers.toString())
        }
    }

    @Test
    fun a01Phone_readingWhatA02PhoneWrote_seesTheSameLevels() {
        val weekly = NumbersSettings.later(RevealOn.Week)
        val numbers = weekly.copy(overrides = weekly.overrides + (Metric.Protein to Visibility.Hide))
        val json = encode(Settings(numbers = numbers))
        // What a 0.1 phone reads: numbers alone, with the 0.1 presets.
        val oldNumbers = json.getValue("numbers").jsonObject
        assertEquals("coast", oldNumbers.getValue("preset").jsonPrimitive.content)
        val onOldPhone = decode(JsonObject(mapOf("numbers" to oldNumbers))).numbers
        Metric.entries.forEach { assertEquals(numbers.level(it), onOldPhone.level(it), it.name) }
        assertTrue(onOldPhone.revealsWeekly)
    }

    @Test
    fun a01Save_thatLeftTheModeAlone_keepsTheModeA02PhoneSet() {
        val mine = Settings(numbers = NumbersSettings(preset = PresetId.MacrosOnly, changedAt = at))
        val json = encode(mine)
        // A 0.1 phone rewrites numbers whole, with what it read, and changes the water goal.
        val afterOldSave = JsonObject(json + ("numbers" to json.getValue("numbers")) + ("waterGlasses" to kotlinx.serialization.json.JsonPrimitive(10)))
        val read = decode(afterOldSave)
        assertEquals(PresetId.MacrosOnly, read.numbers.preset)
        assertEquals(10, read.waterGlasses)
    }

    @Test
    fun a01Phone_pickingAMode_afterA02Phone_wins_migrated() {
        val json = encode(Settings(numbers = NumbersSettings(preset = PresetId.MacrosOnly, changedAt = at)))
        val picked = JsonObject(
            json.getValue("numbers").jsonObject +
                ("preset" to kotlinx.serialization.json.JsonPrimitive("blind")) +
                ("changedAtEpochMs" to kotlinx.serialization.json.JsonPrimitive(later.toEpochMilliseconds())),
        )
        val read = decode(JsonObject(json + ("numbers" to picked))).numbers
        assertEquals(PresetId.NoNumbers, read.preset)
        assertEquals(later, read.changedAt)
    }

    @Test
    fun a01Phone_pickingSprint_becomesFullNumbersWithATimer() {
        val read = decode("""{"numbers":{"preset":"sprint","changedAtEpochMs":1},"sprintStartedOn":"2026-10-20"}""").numbers
        assertEquals(PresetId.Full, read.preset)
        assertEquals(ModeTiming(LocalDate(2026, 10, 20), 2, PresetId.NoNumbers), read.endAfter)
    }

    @Test
    fun aTimerSetOnA02Phone_survivesAModePickedOnA01Phone() {
        val timing = ModeTiming(startedOn = LocalDate(2026, 10, 20), weeks = 4, then = PresetId.NoNumbers)
        val json = encode(Settings(numbers = NumbersSettings(preset = PresetId.Full, endAfter = timing, changedAt = at)))
        val picked = JsonObject(
            json.getValue("numbers").jsonObject +
                ("preset" to kotlinx.serialization.json.JsonPrimitive("delayed")) +
                ("changedAtEpochMs" to kotlinx.serialization.json.JsonPrimitive(later.toEpochMilliseconds())),
        )
        val read = decode(JsonObject(json + ("numbers" to picked))).numbers
        assertEquals(PresetId.Full, read.preset)
        assertEquals(Visibility.Delay, read.level(Metric.Calories), "Delayed is every number on Later")
        assertEquals(timing, read.endAfter)
    }

    @Test
    fun oldSettings_withNoModeKey_migrate() {
        assertEquals(PresetId.NoNumbers, decode("""{"numbers":{"preset":"mindful"}}""").numbers.preset)
        val coast = decode("""{"numbers":{"preset":"coast"}}""").numbers
        assertEquals(PresetId.Full, coast.preset)
        assertEquals(Visibility.Delay, coast.level(Metric.Calories))
        assertEquals(RevealOn.Week, coast.revealOn)
        assertEquals(Visibility.Hide, coast.level(Metric.Protein))
        val nutrient = decode("""{"numbers":{"preset":"nutrient"}}""").numbers
        assertEquals(Visibility.Hide, nutrient.level(Metric.Calories), "never more than Nutrient focus showed")
    }

    @Test
    fun aModeFromANewerBuild_fallsBackToTheNumbersItWroteForOldPhones() {
        val read = decode("""{"numbers":{"preset":"fuel"},"mode":{"preset":"sparkle"}}""").numbers
        assertEquals(PresetId.MacrosOnly, read.preset)
        assertEquals(Visibility.Show, read.level(Metric.Protein))
    }

    @Test
    fun anUnreadableTimer_isDropped_andAnUnknownRevealIsWeekly() {
        val read = decode(
            """{"numbers":{"preset":"delayed"},"mode":{"preset":"later","revealOn":"monthly","endAfter":{"startedOn":"2026-10-20","weeks":2,"then":"sparkle"}}}""",
        ).numbers
        assertNull(read.endAfter)
        assertEquals(RevealOn.Week, read.revealOn)
        assertEquals(PresetId.Full, read.preset, "Later is retired: All numbers with every number on Later")
        assertEquals(Visibility.Delay, read.level(Metric.Calories))
    }

    @Test
    fun aRetiredNutrientsFirst_readsAsMacrosOnly_keepingThePersonsChanges() {
        val ms = at.toEpochMilliseconds()
        val read = decode(
            """{"numbers":{"preset":"fuel","changedAtEpochMs":$ms},"mode":{"preset":"nutrients_first","overrides":{"carbs":"show"},"changedAtEpochMs":$ms}}""",
        ).numbers
        assertEquals(PresetId.MacrosOnly, read.preset)
        assertEquals(mapOf(Metric.Carbs to Visibility.Show), read.overrides)
        assertEquals(Visibility.Hide, read.level(Metric.Calories), "Macros only has no calories")
        assertEquals(at, read.changedAt)
    }

    @Test
    fun aRetiredLater_readsAsAllNumbers_withEveryNumberStillOnLater() {
        val ms = at.toEpochMilliseconds()
        val read = decode(
            """{"numbers":{"preset":"coast","changedAtEpochMs":$ms},"mode":{"preset":"later","overrides":{"water":"hide"},"revealOn":"week","changedAtEpochMs":$ms}}""",
        ).numbers
        assertEquals(PresetId.Full, read.preset)
        assertEquals(RevealOn.Week, read.revealOn)
        assertEquals(Visibility.Hide, read.level(Metric.Water), "their own change stays")
        Metric.entries.filter { it != Metric.Water }.forEach { metric ->
            assertEquals(NumbersSettings.later().level(metric), read.level(metric), metric.name)
        }
    }

    @Test
    fun aTimerEndingInARetiredMode_switchesToWhatReplacedIt_orIsDroppedForLater() {
        val toNutrientsFirst = decode(
            """{"mode":{"preset":"full","endAfter":{"startedOn":"2026-10-20","weeks":2,"then":"nutrients_first"}}}""",
        ).numbers
        assertEquals(PresetId.MacrosOnly, toNutrientsFirst.endAfter?.then)
        val toLater = decode("""{"mode":{"preset":"no_numbers","endAfter":{"startedOn":"2026-10-20","weeks":2,"then":"later"}}}""").numbers
        assertNull(toLater.endAfter)
    }

    @Test
    fun aTimerNotice_fromARetiredMode_namesWhatReplacedIt() {
        assertEquals(PresetId.MacrosOnly, decode("""{"modeTimerEnded":{"on":"2026-11-03","from":"nutrients_first"}}""").modeTimerEnded?.from)
        assertEquals(PresetId.Full, decode("""{"modeTimerEnded":{"on":"2026-11-03","from":"later"}}""").modeTimerEnded?.from)
    }

    @Test
    fun theTimerNotice_isATopLevelKey() {
        val ended = ModeTimerEnded(on = LocalDate(2026, 11, 3), from = PresetId.Full)
        val json = encode(Settings(modeTimerEnded = ended))
        val wire = json.getValue("modeTimerEnded").jsonObject
        assertEquals("2026-11-03", wire.getValue("on").jsonPrimitive.content)
        assertEquals("full", wire.getValue("from").jsonPrimitive.content)
        assertEquals(ended, decode(json).modeTimerEnded)
    }
}
