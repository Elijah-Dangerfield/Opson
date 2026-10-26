package com.opson.features.settings

import com.opson.libraries.navigation.Route
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Visibility
import kotlinx.serialization.Serializable

/**
 * The "Pause before showing more" sheet, holding the change it will apply after the wait.
 *
 * The change travels as plain strings rather than a serialized [NumbersChange]: an enum or
 * object argument needs a registered nav type in every graph that can open the sheet, and a
 * missing one fails only at graph build on iOS. Build it with [NumbersChange.toRoute] and read
 * it back with [change]; nothing else should spell these keys.
 */
@Serializable
class PauseBeforeMoreRoute(
    val kind: String,
    val presetKey: String? = null,
    val metricKey: String? = null,
    val levelKey: String? = null,
) : Route() {

    /** The change this sheet holds, or null for a route no [toRoute] could have built. */
    fun change(): NumbersChange? = when (kind) {
        KIND_PRESET -> presetKey?.let(PresetId::fromKey)?.let(NumbersChange::PickPreset)
        KIND_OVERRIDE -> {
            val metric = metricKey?.let(::metricFor)
            val level = levelKey?.let(::levelFor)
            if (metric != null && level != null) NumbersChange.Override(metric, level) else null
        }
        KIND_CLEAR -> metricKey?.let(::metricFor)?.let(NumbersChange::ClearOverride)
        KIND_PAUSE -> NumbersChange.TurnOffPause
        else -> null
    }

    internal companion object {
        const val KIND_PRESET = "preset"
        const val KIND_OVERRIDE = "override"
        const val KIND_CLEAR = "clear"
        const val KIND_PAUSE = "pause"

        fun metricFor(key: String): Metric? = Metric.entries.firstOrNull { it.key == key }

        fun levelFor(key: String): Visibility? = Visibility.entries.firstOrNull { it.routeKey == key }
    }
}

/** The pause sheet route for this change. Today, Trends and Settings all open the sheet this way. */
fun NumbersChange.toRoute(): PauseBeforeMoreRoute = when (this) {
    is NumbersChange.PickPreset -> PauseBeforeMoreRoute(PauseBeforeMoreRoute.KIND_PRESET, presetKey = id.key)
    is NumbersChange.Override -> PauseBeforeMoreRoute(
        PauseBeforeMoreRoute.KIND_OVERRIDE,
        metricKey = metric.key,
        levelKey = level.routeKey,
    )
    is NumbersChange.ClearOverride -> PauseBeforeMoreRoute(PauseBeforeMoreRoute.KIND_CLEAR, metricKey = metric.key)
    NumbersChange.TurnOffPause -> PauseBeforeMoreRoute(PauseBeforeMoreRoute.KIND_PAUSE)
}

/** Stable on purpose: a route saved in navigation state must still read back after an update. */
private val Visibility.routeKey: String
    get() = when (this) {
        Visibility.Show -> "show"
        Visibility.Soften -> "soften"
        Visibility.Delay -> "delay"
        Visibility.Hide -> "hide"
    }
