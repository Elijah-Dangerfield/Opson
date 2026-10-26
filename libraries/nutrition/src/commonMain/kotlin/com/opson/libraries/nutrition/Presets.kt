package com.opson.libraries.nutrition

/** Which Today layout a preset uses. Screens pick a layout from this, never from the preset id. */
enum class TodayLayout { Full, Meals, Rhythm, Explorer }

/** When delayed numbers open. `null` on a preset behaves as [DayClose]. */
enum class RevealOn { DayClose, Week }

enum class PresetId(val key: String) {
    Full("full"),
    Delayed("delayed"),
    Blind("blind"),
    Nutrient("nutrient"),
    Rhythm("rhythm"),
    Explorer("explorer"),
    Mindful("mindful"),
    Fuel("fuel"),
    Sprint("sprint"),
    Coast("coast"),
    ;

    companion object {
        fun fromKey(key: String): PresetId? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A mode. Labels, descriptions and banner copy live in the UI as string
 * resources keyed by [id]; [banner] only says whether the mode has one.
 */
data class ModePreset(
    val id: PresetId,
    val today: TodayLayout,
    val vis: Map<Metric, Visibility>,
    val revealOn: RevealOn? = null,
    val mealCount: Boolean = false,
    val fastingAllowed: Boolean = false,
    val banner: Boolean = false,
) {
    fun level(metric: Metric): Visibility = vis.getValue(metric)
}

/** The ten modes, ported from `PRESETS` in `docs/design/handoff/phone-logic.js`. */
object Presets {

    private fun fill(level: Visibility): Map<Metric, Visibility> = Metric.entries.associateWith { level }

    private val fullVis = fill(Visibility.Show) + (Metric.Micros to Visibility.Hide)

    // The prototype softens water in every preset that does not share Full's
    // map, so Full and Sprint show water and every gentler mode keeps the bar.
    private fun gentle(vis: Map<Metric, Visibility>) = vis + (Metric.Water to Visibility.Soften)

    val Full = ModePreset(
        id = PresetId.Full,
        today = TodayLayout.Full,
        vis = fullVis,
        fastingAllowed = true,
    )

    val Delayed = ModePreset(
        id = PresetId.Delayed,
        today = TodayLayout.Full,
        revealOn = RevealOn.DayClose,
        vis = gentle(
            fill(Visibility.Delay) + mapOf(Metric.Micros to Visibility.Hide, Metric.Weight to Visibility.Soften),
        ),
    )

    val Blind = ModePreset(
        id = PresetId.Blind,
        today = TodayLayout.Meals,
        vis = gentle(fill(Visibility.Hide)),
    )

    val Nutrient = ModePreset(
        id = PresetId.Nutrient,
        today = TodayLayout.Full,
        vis = gentle(
            fill(Visibility.Hide) + mapOf(
                Metric.Protein to Visibility.Soften,
                Metric.Fiber to Visibility.Soften,
                Metric.Weight to Visibility.Soften,
            ),
        ),
    )

    val Rhythm = ModePreset(
        id = PresetId.Rhythm,
        today = TodayLayout.Rhythm,
        mealCount = true,
        vis = gentle(fill(Visibility.Hide)),
    )

    val Explorer = ModePreset(
        id = PresetId.Explorer,
        today = TodayLayout.Explorer,
        vis = gentle(fill(Visibility.Hide)),
    )

    val Mindful = ModePreset(
        id = PresetId.Mindful,
        today = TodayLayout.Meals,
        banner = true,
        vis = gentle(fill(Visibility.Hide)),
    )

    val Fuel = ModePreset(
        id = PresetId.Fuel,
        today = TodayLayout.Full,
        mealCount = true,
        vis = gentle(
            fill(Visibility.Hide) + mapOf(
                Metric.Calories to Visibility.Soften,
                Metric.Protein to Visibility.Show,
                Metric.Fiber to Visibility.Show,
                Metric.Weight to Visibility.Soften,
            ),
        ),
    )

    val Sprint = ModePreset(
        id = PresetId.Sprint,
        today = TodayLayout.Full,
        banner = true,
        fastingAllowed = true,
        vis = fullVis,
    )

    val Coast = ModePreset(
        id = PresetId.Coast,
        today = TodayLayout.Meals,
        revealOn = RevealOn.Week,
        banner = true,
        vis = gentle(
            fill(Visibility.Hide) + mapOf(Metric.Calories to Visibility.Delay, Metric.Weight to Visibility.Soften),
        ),
    )

    /** In the order the modes screen lists them. */
    val all: List<ModePreset> = listOf(Full, Delayed, Blind, Nutrient, Rhythm, Explorer, Mindful, Fuel, Sprint, Coast)

    private val byId = all.associateBy { it.id }

    operator fun get(id: PresetId): ModePreset = byId.getValue(id)
}
