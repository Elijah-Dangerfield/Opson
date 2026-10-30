package com.opson.libraries.diary

import com.opson.libraries.nutrition.ActivityFrom
import com.opson.libraries.nutrition.BaselineChange
import com.opson.libraries.nutrition.BaselineInputs
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.BurnMeasure
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.Goal
import com.opson.libraries.nutrition.GoalAveraging
import com.opson.libraries.nutrition.GoalBaseline
import com.opson.libraries.nutrition.GoalDirection
import com.opson.libraries.nutrition.GoalKind
import com.opson.libraries.nutrition.GoalRecalculated
import com.opson.libraries.nutrition.GoalStatus
import com.opson.libraries.nutrition.GoalType
import com.opson.libraries.nutrition.RestingFrom
import kotlinx.datetime.LocalDate
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * [Settings.goals] on the wire: a list of flat objects, one a goal, each with `id`, `kind` and the
 * fields its kind uses (docs/plans/goals-v2.md, "Storage and migration"). A kind this build
 * doesn't know is kept as it came ([GoalType.Unknown]) and written back unchanged, so an older
 * build never deletes a newer build's goal. A goal it knows but can't read sensibly (no id, no
 * start date) is dropped rather than guessed at.
 */
internal object GoalsSerializer : KSerializer<List<Goal>> {
    private val elements = ListSerializer(JsonElement.serializer())

    override val descriptor: SerialDescriptor = elements.descriptor

    override fun serialize(encoder: Encoder, value: List<Goal>) {
        val json = value.map { goal ->
            val unknown = goal.type as? GoalType.Unknown
            if (unknown != null) SettingsJson.parseToJsonElement(unknown.raw) else SettingsJson.encodeToJsonElement(GoalWire.serializer(), GoalWire.of(goal))
        }
        encoder.encodeSerializableValue(elements, json)
    }

    override fun deserialize(decoder: Decoder): List<Goal> =
        decoder.decodeSerializableValue(elements).mapNotNull { element ->
            val kind = ((element as? JsonObject)?.get("kind") as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
            if (GoalKind.fromKey(kind) == null) return@mapNotNull unknown(kind, element)
            readOrNull { SettingsJson.decodeFromJsonElement(GoalWire.serializer(), element).toGoal() }
        }

    private fun unknown(kind: String, element: JsonElement): Goal? {
        val obj = element as? JsonObject ?: return null
        val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return null
        val start = obj["startDate"]?.jsonPrimitive?.contentOrNull?.let { readOrNull { LocalDate.parse(it) } } ?: return null
        return Goal(id = id, type = GoalType.Unknown(kind, element.toString()), startDate = start, setsTarget = false)
    }
}

/** Null for a value that doesn't parse (a SerializationException or a bad date are both IllegalArgumentExceptions). */
private inline fun <T> readOrNull(block: () -> T): T? = try {
    block()
} catch (_: IllegalArgumentException) {
    null
}

@Serializable
private class GoalWire(
    val id: String,
    val kind: String,
    val startDate: LocalDate,
    val status: String = GoalStatus.Active.key,
    val averaging: String = GoalAveraging.Daily.key,
    val toleranceKcal: Int = Goal.DEFAULT_TOLERANCE_KCAL,
    val setsTarget: Boolean? = null,
    val targetsKcal: Int? = null,
    // Weight
    val direction: String? = null,
    val targetKg: Double? = null,
    val kgPerWeek: Double? = null,
    val startKg: Double? = null,
    // Energy balance
    val kcalPerDay: Int? = null,
    // Burn
    val kcal: Int? = null,
    val measure: String? = null,
    // Steps and meals
    val count: Int? = null,
    val baseline: BaselineWire? = null,
    val declined: InputsWire? = null,
    // Answered recalculations, for the goal screen. An older build drops them when it rewrites the
    // goal, which costs only the record.
    val history: List<HistoryWire>? = null,
) {
    fun toGoal(): Goal? {
        val type = when (GoalKind.fromKey(kind)) {
            GoalKind.Weight -> {
                val direction = direction?.let(GoalDirection::fromKey)?.takeIf { it == GoalDirection.Lose || it == GoalDirection.Gain || it == GoalDirection.Maintain }
                    ?: return null
                GoalType.Weight(direction, targetKg, kgPerWeek?.takeIf { it.isFinite() && it > 0 } ?: 0.0, startKg)
            }
            GoalKind.EnergyBalance -> GoalType.EnergyBalance(kcalPerDay ?: return null)
            GoalKind.Burn -> GoalType.Burn(kcal?.takeIf { it > 0 } ?: return null, measure?.let(BurnMeasure::fromKey) ?: BurnMeasure.Active)
            GoalKind.Steps -> GoalType.Steps(count?.takeIf { it > 0 } ?: return null)
            GoalKind.TrackEveryDay -> GoalType.TrackEveryDay
            GoalKind.Meals -> GoalType.Meals(count?.takeIf { it in 1..MAX_MEALS } ?: GoalType.DEFAULT_MEALS)
            GoalKind.Unknown, null -> return null
        }
        return Goal(
            id = id,
            type = type,
            startDate = startDate,
            status = GoalStatus.fromKey(status) ?: GoalStatus.Paused,
            averaging = GoalAveraging.fromKey(averaging) ?: GoalAveraging.Daily,
            toleranceKcal = toleranceKcal.coerceIn(0, MAX_TOLERANCE_KCAL),
            setsTarget = (setsTarget ?: true) && type.setsCalories,
            baseline = baseline?.toBaseline(),
            declined = declined?.toInputs(),
            targetsKcal = targetsKcal,
            history = history.orEmpty().mapNotNull { it.toEntry() }.takeLast(Goal.MAX_HISTORY),
        )
    }

    companion object {
        private const val MAX_MEALS = 6
        private const val MAX_TOLERANCE_KCAL = 500

        fun of(goal: Goal): GoalWire {
            val type = goal.type
            val weight = type as? GoalType.Weight
            return GoalWire(
                id = goal.id,
                kind = type.kind.key,
                startDate = goal.startDate,
                status = goal.status.key,
                averaging = goal.averaging.key,
                toleranceKcal = goal.toleranceKcal,
                setsTarget = goal.setsTarget,
                targetsKcal = goal.targetsKcal,
                direction = weight?.direction?.key,
                targetKg = weight?.targetKg,
                kgPerWeek = weight?.kgPerWeek,
                startKg = weight?.startKg,
                kcalPerDay = (type as? GoalType.EnergyBalance)?.kcalPerDay,
                kcal = (type as? GoalType.Burn)?.kcal,
                measure = (type as? GoalType.Burn)?.measure?.key,
                count = (type as? GoalType.Steps)?.count ?: (type as? GoalType.Meals)?.count,
                baseline = goal.baseline?.let(BaselineWire::of),
                declined = goal.declined?.let(InputsWire::of),
                history = goal.history.takeIf { it.isNotEmpty() }?.map(HistoryWire::of),
            )
        }
    }
}

@Serializable
private class BaselineWire(
    val burnKcal: Int,
    val extraKcal: Int = 0,
    val digesting: Boolean = false,
    val sex: String? = null,
    val inputs: InputsWire? = null,
) {
    fun toBaseline() = GoalBaseline(burnKcal, extraKcal, digesting, sex?.let(::sexOf), inputs?.toInputs())

    companion object {
        fun of(baseline: GoalBaseline) = BaselineWire(
            burnKcal = baseline.burnKcal,
            extraKcal = baseline.extraKcal,
            digesting = baseline.digesting,
            sex = baseline.sex?.name?.lowercase(),
            inputs = baseline.inputs?.let(InputsWire::of),
        )
    }
}

@Serializable
private class InputsWire(
    val restingFrom: String,
    val restingKcal: Int,
    val activityFrom: String,
    val everyday: String,
    val activityKcal: Int,
    val digesting: Boolean,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val ageYears: Int? = null,
    val sex: String? = null,
    val stage: String = BodyStage.None.key,
    val bodyFatPercent: Double? = null,
) {
    fun toInputs() = BaselineInputs(
        restingFrom = RestingFrom.entries.firstOrNull { it.name.lowercase() == restingFrom } ?: RestingFrom.Calculated,
        restingKcal = restingKcal,
        activityFrom = ActivityFrom.fromKey(activityFrom) ?: ActivityFrom.Everyday,
        everyday = EverydayActivity.fromKey(everyday) ?: EverydayActivity.Default,
        activityKcal = activityKcal,
        digesting = digesting,
        weightKg = weightKg,
        heightCm = heightCm,
        ageYears = ageYears,
        sex = sex?.let(::sexOf),
        stage = BodyStage.fromKey(stage) ?: BodyStage.None,
        bodyFatPercent = bodyFatPercent,
    )

    companion object {
        fun of(inputs: BaselineInputs) = InputsWire(
            restingFrom = inputs.restingFrom.name.lowercase(),
            restingKcal = inputs.restingKcal,
            activityFrom = inputs.activityFrom.key,
            everyday = inputs.everyday.key,
            activityKcal = inputs.activityKcal,
            digesting = inputs.digesting,
            weightKg = inputs.weightKg,
            heightCm = inputs.heightCm,
            ageYears = inputs.ageYears,
            sex = inputs.sex?.name?.lowercase(),
            stage = inputs.stage.key,
            bodyFatPercent = inputs.bodyFatPercent,
        )
    }
}

@Serializable
private class HistoryWire(
    val date: LocalDate,
    val changes: List<String> = emptyList(),
    val oldAimKcal: Int? = null,
    val newAimKcal: Int,
    val accepted: Boolean,
) {
    /** A change this build doesn't know is left out of the entry, not the entry out of the list. */
    fun toEntry() = GoalRecalculated(
        date = date,
        changes = changes.mapNotNull { key -> BaselineChange.entries.firstOrNull { it.name == key } }.toSet(),
        oldAimKcal = oldAimKcal,
        newAimKcal = newAimKcal,
        accepted = accepted,
    )

    companion object {
        fun of(entry: GoalRecalculated) = HistoryWire(
            date = entry.date,
            changes = entry.changes.map { it.name },
            oldAimKcal = entry.oldAimKcal,
            newAimKcal = entry.newAimKcal,
            accepted = entry.accepted,
        )
    }
}

private fun sexOf(key: String): BodySex? = BodySex.entries.firstOrNull { it.name.lowercase() == key }
