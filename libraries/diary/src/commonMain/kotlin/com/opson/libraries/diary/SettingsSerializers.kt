package com.opson.libraries.diary

import com.opson.libraries.nutrition.DayGroup
import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.MAX_DEFICIT_KCAL
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.FastingPlan
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.WaterUnit
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.datetime.DayOfWeek
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
 * Blind and an unknown level as Hide, because showing a number the user chose
 * not to see is the one mistake this app cannot make; showing too few is a
 * tap to fix.
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

internal object PresetIdSerializer :
    KeyedEnumSerializer<PresetId>("opson.PresetId", PresetId.entries, PresetId::key, PresetId.Blind)

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

@Serializable
private class NumbersWire(
    @Serializable(with = PresetIdSerializer::class)
    val preset: PresetId = PresetId.Full,
    @Serializable(with = OverridesSerializer::class)
    val overrides: Map<Metric, Visibility> = emptyMap(),
    val pauseBeforeMore: Boolean = false,
    val changedAtEpochMs: Long? = null,
    /** `"21:00"`. Missing (settings from before v3) or unreadable is the default, 9:00 pm. */
    val revealAt: String? = null,
)

internal object NumbersSettingsSerializer : KSerializer<NumbersSettings> {
    override val descriptor: SerialDescriptor = NumbersWire.serializer().descriptor

    override fun serialize(encoder: Encoder, value: NumbersSettings) = encoder.encodeSerializableValue(
        NumbersWire.serializer(),
        NumbersWire(
            preset = value.preset,
            overrides = value.overrides,
            pauseBeforeMore = value.pauseBeforeMore,
            changedAtEpochMs = value.changedAt?.toEpochMilliseconds(),
            revealAt = value.revealAt.toWire(),
        ),
    )

    override fun deserialize(decoder: Decoder): NumbersSettings {
        val wire = decoder.decodeSerializableValue(NumbersWire.serializer())
        return NumbersSettings(
            preset = wire.preset,
            overrides = wire.overrides,
            pauseBeforeMore = wire.pauseBeforeMore,
            changedAt = wire.changedAtEpochMs?.let(Instant::fromEpochMilliseconds),
            revealAt = wire.revealAt?.let(::revealAtFromWire) ?: NumbersSettings.DEFAULT_REVEAL_AT,
        )
    }

    /** Hours and minutes only: a reveal time has no seconds, and `"21:00"` reads the same everywhere. */
    private fun LocalTime.toWire(): String =
        hour.toString().padStart(2, '0') + ":" + minute.toString().padStart(2, '0')

    private fun revealAtFromWire(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()
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
