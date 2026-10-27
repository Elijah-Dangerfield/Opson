package com.opson.libraries.nutrition

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * The ten presets before design sync 4, kept for two jobs: reading settings that still name one
 * (0.1 phones keep writing them), and writing a `numbers.preset` those phones understand.
 *
 * The levels are 0.1's, frozen, with one deliberate exception: Full and Sprint show vitamins and
 * minerals, as Full numbers does since 2026-10-27 (`docs/decisions.md`), so a Full user is not
 * migrated into a micros override. A 0.1 build reading "full" hides them, which is fewer.
 */
enum class LegacyPreset(
    val key: String,
    /** The mode it became (design sync 4, 13.5: "What happened to the others"). */
    val becomes: PresetId,
    internal val vis: Map<Metric, Visibility>,
    internal val revealOn: RevealOn = RevealOn.DayClose,
    internal val progressPhotos: Boolean = true,
) {
    Full("full", PresetId.Full, fullVis),
    Delayed("delayed", PresetId.Later, gentle(fill(Visibility.Delay) + mapOf(Metric.Micros to Visibility.Hide, Metric.Weight to Visibility.Soften))),
    Blind("blind", PresetId.NoNumbers, gentle(fill(Visibility.Hide)), progressPhotos = false),
    Nutrient(
        "nutrient",
        PresetId.NutrientsFirst,
        gentle(fill(Visibility.Hide) + mapOf(Metric.Protein to Visibility.Soften, Metric.Fiber to Visibility.Soften, Metric.Weight to Visibility.Soften)),
    ),
    Rhythm("rhythm", PresetId.NoNumbers, gentle(fill(Visibility.Hide)), progressPhotos = false),
    Explorer("explorer", PresetId.NoNumbers, gentle(fill(Visibility.Hide))),
    Mindful("mindful", PresetId.NoNumbers, gentle(fill(Visibility.Hide)), progressPhotos = false),
    Fuel(
        "fuel",
        PresetId.NutrientsFirst,
        gentle(
            fill(Visibility.Hide) + mapOf(
                Metric.Calories to Visibility.Soften,
                Metric.Protein to Visibility.Show,
                Metric.Fiber to Visibility.Show,
                Metric.Weight to Visibility.Soften,
            ),
        ),
        progressPhotos = false,
    ),
    Sprint("sprint", PresetId.Full, fullVis),
    Coast(
        "coast",
        PresetId.Later,
        gentle(fill(Visibility.Hide) + mapOf(Metric.Calories to Visibility.Delay, Metric.Weight to Visibility.Soften)),
        revealOn = RevealOn.Week,
    ),
    ;

    companion object {
        /** Sprint's length: "Sprint, day 9 of 14". */
        const val SPRINT_DAYS = 14

        fun fromKey(key: String): LegacyPreset? = entries.firstOrNull { it.key == key }

        /**
         * The settings a legacy [key] with its [overrides] becomes, showing exactly what it showed:
         * every level the new preset would set differently is kept as an override, so nobody lands
         * on more numbers (or fewer) than they had. Photos the same.
         *
         * Sprint becomes Full numbers with End after a set time: [SPRINT_DAYS] from
         * [sprintStartedOn], then No numbers, which is what Sprint promised ("Track for two weeks
         * to learn, then take a break"). A sprint whose days are already over switches the first
         * time the timer is checked, with the notice on Today; that is fewer numbers, never more.
         * No start date, no timer.
         *
         * `null` for a key that is neither legacy nor current, which callers read fail-closed.
         */
        fun migrate(
            key: String,
            overrides: Map<Metric, Visibility>,
            progressPhotos: Boolean?,
            sprintStartedOn: LocalDate?,
        ): Migrated? {
            val legacy = fromKey(key) ?: return PresetId.fromKey(key)?.let { Migrated(it, overrides, RevealOn.DayClose, progressPhotos, null) }
            val target = Presets[legacy.becomes]
            val kept = Metric.entries.mapNotNull { metric ->
                val level = overrides[metric] ?: legacy.vis.getValue(metric)
                (metric to level).takeIf { level != target.level(metric) }
            }.toMap()
            val photos = progressPhotos ?: legacy.progressPhotos.takeIf { it != target.progressPhotos }
            val timing = sprintStartedOn?.takeIf { legacy == Sprint }?.let {
                ModeTiming(startedOn = it, weeks = SPRINT_DAYS / ModeTiming.DAYS_PER_WEEK, then = PresetId.NoNumbers)
            }
            return Migrated(legacy.becomes, kept, legacy.revealOn, photos, timing)
        }

        /**
         * The legacy preset and overrides that show what [settings] shows, for a 0.1 phone. Picked
         * so its levels match as closely as an old preset can, with the rest as overrides.
         */
        fun closestTo(settings: NumbersSettings): Pair<LegacyPreset, Map<Metric, Visibility>> {
            val legacy = when (settings.preset) {
                PresetId.Full -> Full
                PresetId.NutrientsFirst -> Fuel
                PresetId.Later -> if (settings.revealOn == RevealOn.Week) Coast else Delayed
                PresetId.NoNumbers -> Blind
            }
            val overrides = Metric.entries.mapNotNull { metric ->
                val level = settings.level(metric)
                (metric to level).takeIf { level != legacy.vis.getValue(metric) }
            }.toMap()
            return legacy to overrides
        }
    }

    /** What [migrate] returns: the pieces of [NumbersSettings] a legacy preset decides. */
    data class Migrated(
        val preset: PresetId,
        val overrides: Map<Metric, Visibility>,
        val revealOn: RevealOn,
        val progressPhotos: Boolean?,
        val endAfter: ModeTiming?,
    )
}

private fun fill(level: Visibility): Map<Metric, Visibility> = Metric.entries.associateWith { level }

private fun gentle(vis: Map<Metric, Visibility>) = vis + (Metric.Water to Visibility.Soften)

private val fullVis = fill(Visibility.Show)

/** [days] after [this]. */
internal fun LocalDate.plusDays(days: Int): LocalDate = plus(DatePeriod(days = days))
