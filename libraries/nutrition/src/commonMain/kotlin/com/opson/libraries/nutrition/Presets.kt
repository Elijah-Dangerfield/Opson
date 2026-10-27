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
 * The four starting points, by how many numbers they show: all of them, macros with their grams,
 * macros as bars alone, none. [key] is the wire spelling. Design sync 4's Nutrients first and
 * Later were folded into these on 2026-10-27 ([RetiredPreset]), and the ten presets before them
 * are [LegacyPreset]s, which old settings still carry and this build migrates.
 */
enum class PresetId(val key: String) {
    Full("full"),
    MacrosWithGrams("macros_grams"),
    MacrosOnly("macros_only"),
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

/** The four modes, in the order Modes and numbers lists them. */
object Presets {

    private fun fill(level: Visibility): Map<Metric, Visibility> = Metric.entries.associateWith { level }

    // Water stays a bar of glasses with no amount in every mode that hides amounts.
    private fun gentle(vis: Map<Metric, Visibility>) = vis + (Metric.Water to Visibility.Soften)

    private val energy = listOf(Metric.Calories, Metric.Remaining, Metric.EnergyOut)

    /**
     * All numbers: calories, macros with their grams, energy, vitamins and minerals. The
     * prototype's `FULL_VIS` hid micros as an opt-in; this mode promises every number, and that
     * opt-in left a Full user with vitamin and mineral data on nearly every food and no sign of
     * it anywhere (2026-10-27, `docs/decisions.md`).
     */
    val Full = ModePreset(
        id = PresetId.Full,
        today = TodayLayout.Full,
        vis = fill(Visibility.Show),
        fastingAllowed = true,
    )

    /**
     * Macros with grams: every number All numbers shows except energy. No calories, nothing
     * remaining and no energy out, anywhere; protein, carbs, fat and fiber in grams.
     */
    val MacrosWithGrams = ModePreset(
        id = PresetId.MacrosWithGrams,
        today = TodayLayout.Full,
        vis = fill(Visibility.Show) + energy.associateWith { Visibility.Hide },
    )

    /**
     * Macros only: protein, carbs, fat and fiber as fill bars and words, never a gram, and no
     * calories. Weight is its trend and water its glasses. What Nutrients first became, less its
     * calorie bar.
     */
    val MacrosOnly = ModePreset(
        id = PresetId.MacrosOnly,
        today = TodayLayout.Full,
        mealCount = true,
        vis = gentle(
            fill(Visibility.Hide) +
                Metric.entries.filter { it.isMacro }.associateWith { Visibility.Soften } +
                (Metric.Weight to Visibility.Soften),
        ),
        progressPhotos = false,
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
    val all: List<ModePreset> = listOf(Full, MacrosWithGrams, MacrosOnly, NoNumbers)

    private val byId = all.associateBy { it.id }

    operator fun get(id: PresetId): ModePreset = byId.getValue(id)

    /**
     * Every number on Later: the levels the Later mode set before it folded into All numbers.
     * Each number can still be set to Later on its own; these are what a Later user keeps.
     */
    val laterLevels: Map<Metric, Visibility> =
        gentle(fill(Visibility.Delay) + mapOf(Metric.Micros to Visibility.Hide, Metric.Weight to Visibility.Soften))
}

/**
 * Design sync 4's modes this build no longer offers, kept to read settings that still name one.
 * Nutrients first was the mode without calories; it becomes Macros only, the person's own changes
 * kept. Later becomes All numbers with every number still on Later ([Presets.laterLevels]), so a
 * Later user sees what they saw, their own changes on top.
 */
enum class RetiredPreset(val key: String) {
    NutrientsFirst("nutrients_first"),
    Later("later"),
    ;

    /** The preset and overrides that replace this one, with its [overrides]. */
    fun migrate(overrides: Map<Metric, Visibility>): Pair<PresetId, Map<Metric, Visibility>> = when (this) {
        NutrientsFirst -> PresetId.MacrosOnly to overrides
        Later -> PresetId.Full to (Presets.laterLevels + overrides).filter { (metric, level) -> Presets.Full.level(metric) != level }
    }

    companion object {
        fun fromKey(key: String): RetiredPreset? = entries.firstOrNull { it.key == key }

        /**
         * A mode [key] with its [overrides]: a current preset as it is, a retired one as what
         * replaced it. `null` for a key that is neither.
         */
        fun read(key: String, overrides: Map<Metric, Visibility>): Pair<PresetId, Map<Metric, Visibility>>? =
            PresetId.fromKey(key)?.let { it to overrides } ?: fromKey(key)?.migrate(overrides)

        /**
         * The preset an End after a set time names by [key]. Nutrients first is Macros only. A
         * timer ending in Later has no one preset to switch to without showing more at once, so
         * it reads as `null`, and the timer is dropped rather than guessed.
         */
        fun timerTarget(key: String): PresetId? =
            PresetId.fromKey(key) ?: PresetId.MacrosOnly.takeIf { fromKey(key) == NutrientsFirst }
    }
}
