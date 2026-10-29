package com.opson.libraries.diary

import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.DayGroup
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.FastingPlan
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GroupShape
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.LegacyPreset
import com.opson.libraries.nutrition.MAX_DEFICIT_KCAL
import com.opson.libraries.nutrition.MAX_GOAL_KCAL
import com.opson.libraries.nutrition.MAX_SURPLUS_KCAL
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.ModeTiming
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.RetiredPreset
import com.opson.libraries.nutrition.RevealOn
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.WaterUnit
import com.opson.libraries.nutrition.WeightGoal
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

/**
 * A preset by its key. Only names one in copy ("Your time on All numbers is up"), never decides a
 * level. A retired key names what replaced it.
 */
internal object PresetKeySerializer :
    KeyedEnumSerializer<PresetId>("opson.PresetId", PresetId.entries, PresetId::key, PresetId.NoNumbers) {
    override fun deserialize(decoder: Decoder): PresetId {
        val key = decoder.decodeString()
        return fromKey(key) ?: RetiredPreset.read(key, emptyMap())?.first ?: PresetId.NoNumbers
    }
}

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

/** [Settings.restingSource]: anything unreadable is the calculation, the default. */
internal object RestingSourceSerializer : KeyedEnumSerializer<RestingSource>(
    "opson.RestingSource",
    RestingSource.entries,
    { it.name.lowercase() },
    RestingSource.Calculate,
)

/** [Settings.everydayActivity]: anything unreadable is the default, mostly sitting. */
internal object EverydayActivitySerializer : KeyedEnumSerializer<EverydayActivity>(
    "opson.EverydayActivity",
    EverydayActivity.entries,
    EverydayActivity::key,
    EverydayActivity.Default,
)

/** [Settings.bodySex]: anything unreadable is unsaid, the midpoint of the equation's two constants. */
internal object BodySexSerializer :
    KeyedEnumSerializer<BodySex>("opson.BodySex", BodySex.entries, { it.name.lowercase() }, BodySex.Unsaid)

/** [Settings.bodyStage]: anything unreadable is none, so no stage's extra energy is added on a guess. */
internal object BodyStageSerializer :
    KeyedEnumSerializer<BodyStage>("opson.BodyStage", BodyStage.entries, BodyStage::key, BodyStage.None)

@Serializable
private class WeightGoalWire(
    val direction: String = "none",
    val targetKg: Double? = null,
    val kgPerWeek: Double = 0.0,
    val startKg: Double? = null,
    val startDate: LocalDate? = null,
    val targetsKcal: Int? = null,
)

/**
 * [Settings.weightGoal] as one object. A direction this build doesn't know reads as no goal, and
 * a pace that is not a positive number as none, so nothing is planned from a value it can't read.
 */
internal object WeightGoalSerializer : KSerializer<WeightGoal> {
    override val descriptor: SerialDescriptor = WeightGoalWire.serializer().descriptor

    override fun serialize(encoder: Encoder, value: WeightGoal) = encoder.encodeSerializableValue(
        WeightGoalWire.serializer(),
        WeightGoalWire(value.direction.key, value.targetKg, value.kgPerWeek, value.startKg, value.startDate, value.targetsKcal),
    )

    override fun deserialize(decoder: Decoder): WeightGoal {
        val wire = decoder.decodeSerializableValue(WeightGoalWire.serializer())
        val direction = GoalDirection.fromKey(wire.direction) ?: return WeightGoal.None
        return WeightGoal(
            direction = direction,
            targetKg = wire.targetKg,
            kgPerWeek = wire.kgPerWeek.takeIf { it.isFinite() && it > 0 } ?: 0.0,
            startKg = wire.startKg,
            startDate = wire.startDate,
            targetsKcal = wire.targetsKcal,
        )
    }
}

internal object WaterUnitSerializer : KeyedEnumSerializer<WaterUnit>(
    "opson.WaterUnit",
    WaterUnit.entries,
    { if (it == WaterUnit.Ml) "ml" else "oz" },
    WaterUnit.Ml,
)

internal object HeightUnitSerializer :
    KeyedEnumSerializer<HeightUnit>("opson.HeightUnit", HeightUnit.entries, { if (it == HeightUnit.Cm) "cm" else "ft_in" }, HeightUnit.Cm)

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
 * The top-level `mode` (design sync 4): the preset as one of the four (a retired one reads as what
 * replaced it), its overrides, when Later's totals open, and End after a set time. Written whole with the 0.1 `numbers` beside it, both
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
        // A mode this build retired reads as what replaced it (RetiredPreset): Nutrients first is
        // Macros only, Later is All numbers with each number still on Later.
        val read = mode?.let { RetiredPreset.read(it.preset, it.overrides) }
        if (mode != null && read != null) {
            val (preset, overrides) = read
            return base.copy(
                preset = preset,
                overrides = overrides,
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
        if (RetiredPreset.read(preset, overrides) == null) return false
        val numbersChanged = wire.changedAtEpochMs ?: return true
        val modeChanged = changedAtEpochMs ?: return false
        return modeChanged >= numbersChanged
    }

    /** Missing is each day, the default. A value this build doesn't know is weekly: the later opening. */
    private fun revealOnFromWire(value: String?): RevealOn = when (value) {
        null, REVEAL_DAY -> RevealOn.DayClose
        else -> RevealOn.Week
    }

    /** A timer whose next preset this build can't read, or that ended in Later, is dropped rather than guessed. */
    private fun EndAfterWire.toTiming(): ModeTiming? =
        RetiredPreset.timerTarget(then)?.let { ModeTiming(startedOn = startedOn, weeks = weeks.coerceAtLeast(1), then = it) }

    /** Hours and minutes only: a reveal time has no seconds, and `"21:00"` reads the same everywhere. */
    private fun LocalTime.toWire(): String =
        hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')

    private fun revealAtFromWire(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()
}

/**
 * [Settings] as the plugin writes it, with keys lifted out of their objects to the top level and
 * put back on the way in: from `numbers`, `mode`, `progressPhotos` and 0.1's `sprintStartedOn`
 * (read, never written); from `targets`, `style` as `targetStyle`. The server merges settings one
 * level deep (`stored || incoming`) and a 0.1 build rewrites `numbers` and `targets` whole without
 * them, so inside those objects they would be lost on that build's next save. `progressPhotos`
 * and `targetStyle` are written even when null or a plain range, so a change back to the default
 * reaches the server instead of the merge keeping the old choice.
 */
internal object SettingsSerializer : JsonTransformingSerializer<Settings>(Settings.generatedSerializer()) {
    private const val NUMBERS = "numbers"
    private const val MODE = "mode"
    private const val PROGRESS_PHOTOS = "progressPhotos"
    private const val SPRINT_STARTED_ON = "sprintStartedOn"
    private val fromNumbers = setOf(MODE, PROGRESS_PHOTOS, SPRINT_STARTED_ON)
    private const val TARGETS = "targets"
    private const val STYLE = "style"
    private const val TARGET_STYLE = "targetStyle"

    override fun transformSerialize(element: JsonElement): JsonElement {
        val settings = element as? JsonObject ?: return element
        val numbers = settings[NUMBERS] as? JsonObject
        val targets = settings[TARGETS] as? JsonObject
        val lifted = buildMap {
            if (numbers != null) {
                put(NUMBERS, JsonObject(numbers - fromNumbers))
                put(PROGRESS_PHOTOS, numbers[PROGRESS_PHOTOS] ?: JsonNull)
                numbers[MODE]?.let { put(MODE, it) }
            }
            if (targets != null) {
                put(TARGETS, JsonObject(targets - STYLE))
                put(TARGET_STYLE, targets[STYLE] ?: JsonNull)
            }
        }
        return JsonObject(settings + lifted)
    }

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val settings = element as? JsonObject ?: return element
        return settings.withNumbersKeysInNumbers().withStyleInTargets()
    }

    private fun JsonObject.withNumbersKeysInNumbers(): JsonObject {
        val top = fromNumbers.mapNotNull { key -> this[key]?.takeUnless { it is JsonNull }?.let { key to it } }.toMap()
        val numbers = this[NUMBERS] as? JsonObject
        if (top.isEmpty() && numbers?.keys?.any { it in fromNumbers } != true) return JsonObject(this - fromNumbers)
        // Only the top-level keys count: one inside numbers came from nowhere this serializer wrote.
        val moved = (numbers ?: JsonObject(emptyMap())) - fromNumbers + top
        return JsonObject(this - fromNumbers + (NUMBERS to JsonObject(moved)))
    }

    /**
     * The top-level style wins. Without one, a style inside targets still counts: settings saved
     * before it moved up kept it there. With no targets there is nothing to put it in.
     */
    private fun JsonObject.withStyleInTargets(): JsonObject {
        val style = this[TARGET_STYLE]?.takeUnless { it is JsonNull }
        val targets = this[TARGETS] as? JsonObject
        if (style == null || targets == null) return JsonObject(this - TARGET_STYLE)
        return JsonObject(this - TARGET_STYLE + (TARGETS to JsonObject(targets + (STYLE to style))))
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
 * The goal style, which travels as the top-level `targetStyle` ([SettingsSerializer]). `kind` is
 * `range`, `exact`, `deficit`, `surplus` or `by_day`; anything else reads as a range, the one
 * style that needs nothing else to be safe. One number needs no field of its own: it is the aim.
 */
@Serializable
private class TargetStyleWire(
    val kind: String = KIND_RANGE,
    val deficitKcal: Int? = null,
    val startingDeficitKcal: Int? = null,
    val surplusKcal: Int? = null,
    val startingSurplusKcal: Int? = null,
    val groups: List<DayGroupWire> = emptyList(),
) {
    companion object {
        const val KIND_RANGE = "range"
        const val KIND_EXACT = "exact"
        const val KIND_DEFICIT = "deficit"
        const val KIND_SURPLUS = "surplus"
        const val KIND_BY_DAY = "by_day"
    }
}

/**
 * One day group: days as `mon`..`sun`. `shape` is `range`, `exact`, `deficit` (with `deficitKcal`
 * and `startingDeficitKcal`) or `surplus` (with `surplusKcal` and `startingSurplusKcal`); missing
 * or unknown, the group is a range. Every shape keeps `minKcal` and `maxKcal`, so a build that
 * predates a shape reads that day as the group's own range, never as nothing.
 */
@Serializable
private class DayGroupWire(
    val days: List<String> = emptyList(),
    val minKcal: Int,
    val maxKcal: Int,
    val shape: String? = null,
    val surplusKcal: Int? = null,
    val startingSurplusKcal: Int? = null,
    val deficitKcal: Int? = null,
    val startingDeficitKcal: Int? = null,
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
        TargetStyle.Exact -> TargetStyleWire(TargetStyleWire.KIND_EXACT)
        is TargetStyle.Surplus -> TargetStyleWire(TargetStyleWire.KIND_SURPLUS, surplusKcal = kcal, startingSurplusKcal = startingKcal)
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
                    shape = when (group.shape) {
                        GroupShape.Range -> TargetStyleWire.KIND_RANGE
                        GroupShape.Exact -> TargetStyleWire.KIND_EXACT
                        is GroupShape.Surplus -> TargetStyleWire.KIND_SURPLUS
                        is GroupShape.Deficit -> TargetStyleWire.KIND_DEFICIT
                    },
                    surplusKcal = (group.shape as? GroupShape.Surplus)?.kcal,
                    startingSurplusKcal = (group.shape as? GroupShape.Surplus)?.startingKcal,
                    deficitKcal = (group.shape as? GroupShape.Deficit)?.kcal,
                    startingDeficitKcal = (group.shape as? GroupShape.Deficit)?.startingKcal,
                )
            },
        )
    }

    /**
     * Reads a style back, repairing rather than trusting it: another build or a hand-edited row
     * could break the rules the write path keeps. A deficit is held to 0..its start (or the
     * dietitian's maximum) and a surplus the same way, the style's or a group's alike,
     * groups to four, each weekday to exactly one group (first wins, missing days join the
     * first), and a group whose range breaks the floor or the ceiling takes the base range. Anything unreadable
     * is a plain range.
     */
    private fun TargetStyleWire.toStyle(targets: Targets): TargetStyle = when (kind) {
        TargetStyleWire.KIND_EXACT -> TargetStyle.Exact
        TargetStyleWire.KIND_SURPLUS -> surplus(surplusKcal, startingSurplusKcal).let { (kcal, start) -> TargetStyle.Surplus(kcal, start) }
        TargetStyleWire.KIND_DEFICIT -> deficit(deficitKcal, startingDeficitKcal).let { (kcal, start) -> TargetStyle.Deficit(kcal, start) }
        TargetStyleWire.KIND_BY_DAY -> byDay(targets) ?: TargetStyle.Range
        else -> TargetStyle.Range
    }

    private fun TargetStyleWire.byDay(targets: Targets): TargetStyle.ByDay? {
        val wires = groups.take(TargetStyle.MAX_GROUPS)
        if (wires.isEmpty()) return null
        val seen = mutableSetOf<DayOfWeek>()
        val decoded = wires.map { wire ->
            val days = wire.days.mapNotNull(DayOfWeekSerializer::fromKey).filter(seen::add).toSet()
            val safe = wire.minKcal >= targets.floorKcal &&
                wire.maxKcal <= MAX_GOAL_KCAL &&
                wire.maxKcal - wire.minKcal >= Targets.STEP_KCAL
            val shape = when (wire.shape) {
                TargetStyleWire.KIND_EXACT -> GroupShape.Exact
                TargetStyleWire.KIND_SURPLUS -> surplus(wire.surplusKcal, wire.startingSurplusKcal).let { (kcal, start) -> GroupShape.Surplus(kcal, start) }
                TargetStyleWire.KIND_DEFICIT -> deficit(wire.deficitKcal, wire.startingDeficitKcal).let { (kcal, start) -> GroupShape.Deficit(kcal, start) }
                else -> GroupShape.Range
            }
            if (safe) {
                DayGroup(days, wire.minKcal, wire.maxKcal, shape)
            } else {
                DayGroup(days, targets.minKcal, targets.maxKcal, shape)
            }
        }.toMutableList()
        val missing = DayOfWeek.entries.toSet() - seen
        decoded[0] = decoded[0].copy(days = decoded[0].days + missing)
        return TargetStyle.ByDay(decoded)
    }

    /** A deficit and where it started, held to 0..its start (or the dietitian's maximum). */
    private fun deficit(kcal: Int?, startingKcal: Int?): Pair<Int, Int> {
        val start = (startingKcal ?: 0).coerceAtLeast(0)
        val cap = MAX_DEFICIT_KCAL ?: start
        return (kcal ?: 0).coerceIn(0, cap) to start
    }

    /** A surplus and where it started, held to 0..its start (or the clinician's maximum), as a deficit is. */
    private fun surplus(kcal: Int?, startingKcal: Int?): Pair<Int, Int> {
        val start = (startingKcal ?: 0).coerceAtLeast(0)
        val cap = MAX_SURPLUS_KCAL ?: start
        return (kcal ?: 0).coerceIn(0, cap) to start
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
