package com.opson.libraries.nutrition

/**
 * Which Today layout a preset uses. Screens pick a layout from this, never from the preset id.
 * [Full] is the meals with their foods under a summary card the levels choose. [MealTimeline] is
 * No numbers (design sync 4, 13.5d): the day's meals as a timeline, then the meals by name.
 */
enum class TodayLayout { Full, MealTimeline }

/** When numbers set to Later open: at the reveal time each day, or once a week on the week view. */
enum class RevealOn { DayClose, Week }

/**
 * The four starting points (design sync 4, 13.5). [key] is the wire spelling. The ten presets
 * before them are [LegacyPreset]s, which old settings still carry and this build migrates.
 */
enum class PresetId(val key: String) {
    Full("full"),
    NutrientsFirst("nutrients_first"),
    Later("later"),
    NoNumbers("no_numbers"),
    ;

    companion object {
        fun fromKey(key: String): PresetId? = entries.firstOrNull { it.key == key }
    }
}

/**
 * A mode. Labels and descriptions live in the UI as string resources keyed by [id].
 */
data class ModePreset(
    val id: PresetId,
    val today: TodayLayout,
    val vis: Map<Metric, Visibility>,
    /** "3 of 4 meals" on the softened card. */
    val mealCount: Boolean = false,
    val fastingAllowed: Boolean = false,
    /**
     * Whether Patterns spells out gaps and shifts between meals ("Longest gap", "Dinner on Fri
     * and Sat, about 45 min later"). Off in No numbers (design v3, 5.7): a gap between meals can
     * feed restriction for exactly the people who pick it.
     */
    val mealGaps: Boolean = true,
    /**
     * Whether progress photos show unless the person picks otherwise. Off in the two modes made
     * from Blind, Mindful, Rhythm and Fuel (design sync 2, 8.x: they "default to Hide"). See
     * [NumbersSettings.showsProgressPhotos].
     */
    val progressPhotos: Boolean = true,
) {
    fun level(metric: Metric): Visibility = vis.getValue(metric)
}

/** The four modes (design sync 4, 13.5a). */
object Presets {

    private fun fill(level: Visibility): Map<Metric, Visibility> = Metric.entries.associateWith { level }

    // Water stays a bar of glasses with no amount in every mode but Full numbers.
    private fun gentle(vis: Map<Metric, Visibility>) = vis + (Metric.Water to Visibility.Soften)

    /**
     * Every number, vitamins and minerals included. The prototype's `FULL_VIS` hid micros as an
     * opt-in; Full promises every number, and that opt-in left a Full user with vitamin and
     * mineral data on nearly every food and no sign of it anywhere (2026-10-27, `docs/decisions.md`).
     */
    val Full = ModePreset(
        id = PresetId.Full,
        today = TodayLayout.Full,
        vis = fill(Visibility.Show),
        fastingAllowed = true,
    )

    /**
     * Protein, fiber and water up front, calories as a fill bar (13.5b): words and bars, never an
     * amount. Made from Nutrient focus and Fuel.
     */
    val NutrientsFirst = ModePreset(
        id = PresetId.NutrientsFirst,
        today = TodayLayout.Full,
        mealCount = true,
        vis = gentle(
            fill(Visibility.Hide) + mapOf(
                Metric.Calories to Visibility.Soften,
                Metric.Protein to Visibility.Soften,
                Metric.Fiber to Visibility.Soften,
                Metric.Weight to Visibility.Soften,
            ),
        ),
        progressPhotos = false,
    )

    /** Log all day; totals open at the reveal time (13.5c). Made from Delayed reveal and Coast. */
    val Later = ModePreset(
        id = PresetId.Later,
        today = TodayLayout.Full,
        vis = gentle(fill(Visibility.Delay) + mapOf(Metric.Micros to Visibility.Hide, Metric.Weight to Visibility.Soften)),
    )

    /**
     * Meals, timing and how they felt, no amounts anywhere (13.5d). Made from Blind, Rhythm,
     * Mindful and Explorer.
     */
    val NoNumbers = ModePreset(
        id = PresetId.NoNumbers,
        today = TodayLayout.MealTimeline,
        vis = gentle(fill(Visibility.Hide)),
        mealGaps = false,
        progressPhotos = false,
    )

    /** In the order Modes and numbers lists them. */
    val all: List<ModePreset> = listOf(Full, NutrientsFirst, Later, NoNumbers)

    private val byId = all.associateBy { it.id }

    operator fun get(id: PresetId): ModePreset = byId.getValue(id)
}
