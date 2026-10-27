package com.opson.libraries.diary

import com.opson.libraries.nutrition.DayGroup
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.MAX_DEFICIT_KCAL
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.FastingPlan
import com.opson.libraries.nutrition.LegacyPreset
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ModeTiming
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RevealOn
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.WaterUnit
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlin.time.Instant

/**
 * The one [Json] that reads and writes [Settings], on disk and on the wire.
 *
 * `coerceInputValues` turns an enum value this build does not know (written by
 * a newer build on another device) into the field's default instead of failing
 * the whole object, and `ignoreUnknownKeys` drops fields it does not know.
 */
val SettingsJson: Json = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    encodeDefaults = true
    explicitNulls = false
}

/**
 * Serializers for the `:libraries:nutrition` types [Settings] holds. That
 * module stays free of serialization, so the wire spelling lives here, and it
 * is the prototype's: `full`, `soften`, `some`, `ml`, `14:10`.
 *
 * Unknown values fail closed. A preset this build does not know reads as
 * No numbers and an unknown level as Hide, because showing a number the user
 * chose not to see is the one mistake this app cannot make; showing too few is
 * a tap to fix.
 */
internal open class KeyedEnumSerializer<E : Enum<E>>(
    serialName: String,
    private val values: List<E>,
    val key: (E) -> String,
    private val unknown: E,
) : KSerializer<E> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor(serialName, PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: E) = encoder.encodeString(key(value))

    override fun deserialize(decoder: Decoder): E = fromKey(decoder.decodeString()) ?: unknown

    fun fromKey(value: String): E? = values.firstOrNull { key(it) == value }
}

/** A preset by its key. Only names one in copy ("Your time on Full numbers is up"), never decides a level. */
internal object PresetKeySerializer :
    KeyedEnumSerializer<PresetId>("opson.PresetId", PresetId.entries, PresetId::key, PresetId.NoNumbers)

internal val Visibility.key: String get() = name.lowercase()

internal object VisibilitySerializer :
    KeyedEnumSerializer<Visibility>("opson.Visibility", Visibility.entries, { it.key }, Visibility.Hide)

internal object MetricKeys :
    KeyedEnumSerializer<Metric>("opson.Metric", Metric.entries, Metric::key, Metric.Calories)

internal object ExerciseAdjustmentSerializer : KeyedEnumSerializer<ExerciseAdjustment>(
    "opson.ExerciseAdjustment",
    ExerciseAdjustment.entries,
    { it.name.lowercase() },
    ExerciseAdjustment.Default,
)

/** [Settings.exerciseRange]: the same keys, but anything unreadable is None, never a range that moves. */
internal object ExerciseRangeSerializer : KeyedEnumSerializer<ExerciseAdjustment>(
    "opson.ExerciseRange",
    ExerciseAdjustment.entries,
    { it.name.lowercase() },
    ExerciseAdjustment.None,
)

internal object WaterUnitSerializer : KeyedEnumSerializer<WaterUnit>(
    "opson.WaterUnit",
    WaterUnit.entries,
    { if (it == WaterUnit.Ml) "ml" else "oz" },
    WaterUnit.Ml,
)

internal object WeightUnitSerializer :
    KeyedEnumSerializer<WeightUnit>("opson.WeightUnit", WeightUnit.entries, { it.name.lowercase() }, WeightUnit.Kg)

internal object FastingPlanSerializer :
    KeyedEnumSerializer<FastingPlan>("opson.FastingPlan", FastingPlan.entries, FastingPlan::key, FastingPlan.Default)

internal object DayOfWeekSerializer : KeyedEnumSerializer<DayOfWeek>(
    "opson.DayOfWeek",
    DayOfWeek.entries,
    { it.name.take(3).lowercase() },
    DayOfWeek.MONDAY,
)

/** Overrides keyed by metric. An entry for a metric this build does not know is dropped. */
internal object OverridesSerializer : KSerializer<Map<Metric, Visibility>> {
    private val delegate = MapSerializer(String.serializer(), String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: Map<Metric, Visibility>) =
        encoder.encodeSerializableValue(delegate, value.entries.associate { (m, v) -> m.key to v.key })

    override fun deserialize(decoder: Decoder): Map<Metric, Visibility> =
        decoder.decodeSerializableValue(delegate).mapNotNull { (metric, level) ->
            val m = MetricKeys.fromKey(metric) ?: return@mapNotNull null
            m to (VisibilitySerializer.fromKey(level) ?: Visibility.Hide)
        }.toMap()
}

/**
 * `numbers` as [NumbersSettingsSerializer] reads and writes it. On the wire [mode],
 * [progressPhotos] and [sprintStartedOn] are top-level keys ([SettingsSerializer] moves them),
 * and the rest is exactly 0.1's `numbers`, which 0.1 phones still read and rewrite whole.
 */
@Serializable
private class NumbersWire(
    /** A [LegacyPreset] key, so a 0.1 phone shows the same levels. [mode] holds the real preset. */
    val preset: String = LegacyPreset.Full.key,
    @Serializable(with = OverridesSerializer::class)
    val overrides: Map<Metric, Visibility> = emptyMap(),
    val pauseBeforeMore: Boolean = false,
    val changedAtEpochMs: Long? = null,
    /** `"21:00"`. Missing (settings from before v3) or unreadable is the default, 9:00 pm. */
    val revealAt: String? = null,
    val progressPhotos: Boolean? = null,
    val mode: ModeWire? = null,
    /** 0.1's Sprint start, read only to migrate a Sprint into End after a set time. Never written. */
    val sprintStartedOn: LocalDate? = null,
)

/**
 * The top-level `mode` (design sync 4): the preset as one of the four, its overrides, when Later's
 * totals open, and End after a set time. Written whole with the 0.1 `numbers` beside it, both
 * stamped [changedAtEpochMs].
 */
@Serializable
private class ModeWire(
    val preset: String,
    @Serializable(with = OverridesSerializer::class)
    val overrides: Map<Metric, Visibility> = emptyMap(),
    val changedAtEpochMs: Long? = null,
    /** `"day"` or `"week"`. */
    val revealOn: String? = null,
    val endAfter: EndAfterWire? = null,
)

@Serializable
private class EndAfterWire(val startedOn: LocalDate, val weeks: Int, val then: String)

/**
 * [NumbersSettings] on the wire, written for two app versions at once.
 *
 * Writing: the top-level `mode` carries the preset and its overrides, and `numbers` carries the
 * [LegacyPreset] and overrides that show a 0.1 phone the same levels ([LegacyPreset.closestTo]).
 *
 * Reading: `mode` wins unless `numbers` changed after it. A 0.1 phone never sees `mode` and
 * rewrites `numbers` whole on every save, but only stamps `numbers.changedAtEpochMs` when its
 * person changes the mode, so a later stamp in `numbers` is a newer choice, made on an old phone,
 * and it is migrated ([LegacyPreset.migrate]). A save that only changed water or a meal time
 * leaves the stamps equal and `mode` stands.
 */
internal object NumbersSettingsSerializer : KSerializer<NumbersSettings> {
    private const val REVEAL_DAY = "day"
    private const val REVEAL_WEEK = "week"

    override val descriptor: SerialDescriptor = NumbersWire.serializer().descriptor

    override fun serialize(encoder: Encoder, value: NumbersSettings) {
        val (legacy, legacyOverrides) = LegacyPreset.closestTo(value)
        val changedAt = value.changedAt?.toEpochMilliseconds()
        encoder.encodeSerializableValue(
            NumbersWire.serializer(),
            NumbersWire(
                preset = legacy.key,
                overrides = legacyOverrides,
                pauseBeforeMore = value.pauseBeforeMore,
                changedAtEpochMs = changedAt,
                revealAt = value.revealAt.toWire(),
                progressPhotos = value.progressPhotos,
                mode = ModeWire(
                    preset = value.preset.key,
                    overrides = value.overrides,
                    changedAtEpochMs = changedAt,
                    revealOn = if (value.revealsWeekly) REVEAL_WEEK else REVEAL_DAY,
                    endAfter = value.endAfter?.let { EndAfterWire(it.startedOn, it.weeks, it.then.key) },
                ),
            ),
        )
    }

    override fun deserialize(decoder: Decoder): NumbersSettings {
        val wire = decoder.decodeSerializableValue(NumbersWire.serializer())
        val mode = wire.mode?.takeIf { it.isCurrent(wire) }
        val base = NumbersSettings(
            pauseBeforeMore = wire.pauseBeforeMore,
            changedAt = (mode?.changedAtEpochMs ?: wire.changedAtEpochMs)?.let(Instant::fromEpochMilliseconds),
            revealAt = wire.revealAt?.let(::revealAtFromWire) ?: NumbersSettings.DEFAULT_REVEAL_AT,
            progressPhotos = wire.progressPhotos,
        )
        val preset = mode?.let { PresetId.fromKey(it.preset) }
        if (mode != null && preset != null) {
            return base.copy(
                preset = preset,
                overrides = mode.overrides,
                revealOn = revealOnFromWire(mode.revealOn),
                endAfter = mode.endAfter?.toTiming(),
            )
        }
        val migrated = LegacyPreset.migrate(wire.preset, wire.overrides, wire.progressPhotos, wire.sprintStartedOn)
            ?: LegacyPreset.Migrated(PresetId.NoNumbers, wire.overrides, RevealOn.DayClose, wire.progressPhotos, null)
        return base.copy(
            preset = migrated.preset,
            overrides = migrated.overrides,
            progressPhotos = migrated.progressPhotos,
            revealOn = migrated.revealOn,
            // A timer set on a new phone survives a mode picked on an old one.
            endAfter = migrated.endAfter ?: wire.mode?.endAfter?.toTiming(),
        )
    }

    /** Readable by this build, and not overtaken by a later change an old phone made to `numbers`. */
    private fun ModeWire.isCurrent(wire: NumbersWire): Boolean {
        if (PresetId.fromKey(preset) == null) return false
        val numbersChanged = wire.changedAtEpochMs ?: return true
        val modeChanged = changedAtEpochMs ?: return false
        return modeChanged >= numbersChanged
    }

    /** Missing is each day, the default. A value this build doesn't know is weekly: the later opening. */
    private fun revealOnFromWire(value: String?): RevealOn = when (value) {
        null, REVEAL_DAY -> RevealOn.DayClose
        else -> RevealOn.Week
    }

    /** A timer whose next preset this build can't read is dropped rather than guessed. */
    private fun EndAfterWire.toTiming(): ModeTiming? =
        PresetId.fromKey(then)?.let { ModeTiming(startedOn = startedOn, weeks = weeks.coerceAtLeast(1), then = it) }

    /** Hours and minutes only: a reveal time has no seconds, and `"21:00"` reads the same everywhere. */
    private fun LocalTime.toWire(): String =
        hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')

    private fun revealAtFromWire(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()
}

/**
 * [Settings] as the plugin writes it, with three keys of `numbers` moved to the top level and
 * back: `mode`, `progressPhotos`, and 0.1's `sprintStartedOn` (read, never written). The server
 * merges settings one level deep (`stored || incoming`) and a 0.1 build rewrites `numbers` whole
 * without them, so inside `numbers` they would be lost on that build's next save.
 * `progressPhotos` is written even when null, so picking a preset, which clears the choice,
 * clears it on the server too instead of the merge keeping the old one.
 */
internal object SettingsSerializer : JsonTransformingSerializer<Settings>(Settings.generatedSerializer()) {
    private const val NUMBERS = "numbers"
    private const val MODE = "mode"
    private const val PROGRESS_PHOTOS = "progressPhotos"
    private const val SPRINT_STARTED_ON = "sprintStartedOn"
    private val lifted = setOf(MODE, PROGRESS_PHOTOS, SPRINT_STARTED_ON)

    override fun transformSerialize(element: JsonElement): JsonElement {
        val settings = element as? JsonObject ?: return element
        val numbers = settings[NUMBERS] as? JsonObject ?: return element
        val top = mapOf(PROGRESS_PHOTOS to (numbers[PROGRESS_PHOTOS] ?: JsonNull)) +
            listOfNotNull(numbers[MODE]?.let { MODE to it })
        return JsonObject(settings + (NUMBERS to JsonObject(numbers - lifted)) + top)
    }

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val settings = element as? JsonObject ?: return element
        val top = lifted.mapNotNull { key -> settings[key]?.takeUnless { it is JsonNull }?.let { key to it } }.toMap()
        val numbers = settings[NUMBERS] as? JsonObject
        if (top.isEmpty() && numbers?.keys?.any { it in lifted } != true) return element
        // Only the top-level keys count: one inside numbers came from nowhere this serializer wrote.
        val moved = (numbers ?: JsonObject(emptyMap())) - lifted + top
        return JsonObject(settings - lifted + (NUMBERS to JsonObject(moved)))
    }
}

@Serializable
private class TargetsWire(
    val energyKcal: Int,
    val minKcal: Int,
    val maxKcal: Int,
    val proteinG: Int,
    val carbsG: Int,
    val fatG: Int,
    val fiberG: Int,
    val startingKcal: Int? = null,
    val startingMinKcal: Int? = null,
    val style: TargetStyleWire? = null,
)

/**
 * The goal style. `kind` is `range`, `deficit` or `by_day`; anything else reads as a range, the
 * one style that needs nothing else to be safe.
 */
@Serializable
private class TargetStyleWire(
    val kind: String = KIND_RANGE,
    val deficitKcal: Int? = null,
    val startingDeficitKcal: Int? = null,
    val groups: List<DayGroupWire> = emptyList(),
) {
    companion object {
        const val KIND_RANGE = "range"
        const val KIND_DEFICIT = "deficit"
        const val KIND_BY_DAY = "by_day"
    }
}

/** One day group: days as `mon`..`sun`. */
@Serializable
private class DayGroupWire(
    val days: List<String> = emptyList(),
    val minKcal: Int,
    val maxKcal: Int,
)

internal object TargetsSerializer : KSerializer<Targets> {
    override val descriptor: SerialDescriptor = TargetsWire.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Targets) = encoder.encodeSerializableValue(
        TargetsWire.serializer(),
        TargetsWire(
            energyKcal = value.energyKcal,
            minKcal = value.minKcal,
            maxKcal = value.maxKcal,
            proteinG = value.proteinG,
            carbsG = value.carbsG,
            fatG = value.fatG,
            fiberG = value.fiberG,
            startingKcal = value.startingKcal,
            startingMinKcal = value.startingMinKcal,
            style = value.style.toWire(),
        ),
    )

    override fun deserialize(decoder: Decoder): Targets {
        val wire = decoder.decodeSerializableValue(TargetsWire.serializer())
        return Targets(
            energyKcal = wire.energyKcal,
            minKcal = wire.minKcal,
            maxKcal = wire.maxKcal,
            proteinG = wire.proteinG,
            carbsG = wire.carbsG,
            fatG = wire.fatG,
            fiberG = wire.fiberG,
            // The floor is where the user started. Losing it would let the
            // target drift below it, so a missing value pins it to today's target.
            startingKcal = wire.startingKcal ?: wire.energyKcal,
            startingMinKcal = wire.startingMinKcal ?: wire.minKcal,
        ).let { targets -> targets.copy(style = wire.style?.toStyle(targets) ?: TargetStyle.Range) }
    }

    private fun TargetStyle.toWire(): TargetStyleWire = when (this) {
        TargetStyle.Range -> TargetStyleWire(TargetStyleWire.KIND_RANGE)
        is TargetStyle.Deficit -> TargetStyleWire(
            TargetStyleWire.KIND_DEFICIT,
            deficitKcal = kcal,
            startingDeficitKcal = startingKcal,
        )
        is TargetStyle.ByDay -> TargetStyleWire(
            TargetStyleWire.KIND_BY_DAY,
            groups = groups.map { group ->
                DayGroupWire(
                    days = DayOfWeek.entries.filter { it in group.days }.map(DayOfWeekSerializer.key),
                    minKcal = group.minKcal,
                    maxKcal = group.maxKcal,
                )
            },
        )
    }

    /**
     * Reads a style back, repairing rather than trusting it: another build or a hand-edited row
     * could break the rules the write path keeps. A deficit is held to 0..its start (or the
     * dietitian's maximum), groups to four, each weekday to exactly one group (first wins,
     * missing days join the first), and a group whose range breaks the floor takes the base
     * range. Anything unreadable is a plain range.
     */
    private fun TargetStyleWire.toStyle(targets: Targets): TargetStyle = when (kind) {
        TargetStyleWire.KIND_DEFICIT -> {
            val start = (startingDeficitKcal ?: 0).coerceAtLeast(0)
            val cap = MAX_DEFICIT_KCAL ?: start
            TargetStyle.Deficit(kcal = (deficitKcal ?: 0).coerceIn(0, cap), startingKcal = start)
        }
        TargetStyleWire.KIND_BY_DAY -> byDay(targets) ?: TargetStyle.Range
        else -> TargetStyle.Range
    }

    private fun TargetStyleWire.byDay(targets: Targets): TargetStyle.ByDay? {
        val wires = groups.take(TargetStyle.MAX_GROUPS)
        if (wires.isEmpty()) return null
        val seen = mutableSetOf<DayOfWeek>()
        val decoded = wires.map { wire ->
            val days = wire.days.mapNotNull(DayOfWeekSerializer::fromKey).filter(seen::add).toSet()
            val safe = wire.minKcal >= targets.lowFloorKcal &&
                (wire.minKcal + wire.maxKcal) / 2 >= targets.floorKcal &&
                wire.maxKcal - wire.minKcal >= Targets.STEP_KCAL
            if (safe) {
                DayGroup(days, wire.minKcal, wire.maxKcal)
            } else {
                DayGroup(days, targets.minKcal, targets.maxKcal)
            }
        }.toMutableList()
        val missing = DayOfWeek.entries.toSet() - seen
        decoded[0] = decoded[0].copy(days = decoded[0].days + missing)
        return TargetStyle.ByDay(decoded)
    }
}

@Serializable
private class MealTimesWire(
    val breakfast: String? = null,
    val lunch: String? = null,
    val snack: String? = null,
    val dinner: String? = null,
)

/** `{"breakfast":"08:00",...}`. A missing or unreadable meal reads as its default, never failing the object. */
internal object MealTimesSerializer : KSerializer<MealTimes> {
    override val descriptor: SerialDescriptor = MealTimesWire.serializer().descriptor

    override fun serialize(encoder: Encoder, value: MealTimes) = encoder.encodeSerializableValue(
        MealTimesWire.serializer(),
        MealTimesWire(
            breakfast = value.breakfast.toWire(),
            lunch = value.lunch.toWire(),
            snack = value.snack.toWire(),
            dinner = value.dinner.toWire(),
        ),
    )

    override fun deserialize(decoder: Decoder): MealTimes {
        val wire = decoder.decodeSerializableValue(MealTimesWire.serializer())
        return MealTimes(
            breakfast = wire.breakfast.toTime() ?: MealTimes.DEFAULT_BREAKFAST,
            lunch = wire.lunch.toTime() ?: MealTimes.DEFAULT_LUNCH,
            snack = wire.snack.toTime() ?: MealTimes.DEFAULT_SNACK,
            dinner = wire.dinner.toTime() ?: MealTimes.DEFAULT_DINNER,
        )
    }

    private fun LocalTime.toWire(): String =
        hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')

    private fun String?.toTime(): LocalTime? = this?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
}
