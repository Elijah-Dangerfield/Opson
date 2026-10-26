package com.opson.libraries.diary

import com.opson.libraries.nutrition.ExerciseAdjustment
import com.opson.libraries.nutrition.FastingPlan
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Targets
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.WaterUnit
import com.opson.libraries.nutrition.WeightUnit
import kotlinx.datetime.DayOfWeek
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
    private val key: (E) -> String,
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
        ),
    )

    override fun deserialize(decoder: Decoder): NumbersSettings {
        val wire = decoder.decodeSerializableValue(NumbersWire.serializer())
        return NumbersSettings(
            preset = wire.preset,
            overrides = wire.overrides,
            pauseBeforeMore = wire.pauseBeforeMore,
            changedAt = wire.changedAtEpochMs?.let(Instant::fromEpochMilliseconds),
        )
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
        )
    }
}
