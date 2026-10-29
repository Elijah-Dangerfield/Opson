package com.opson.libraries.diary.ui

import com.opson.libraries.diary.Settings
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Visibility
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_goal_changing_weight
import opson.libraries.resources.generated.resources.today_goal_eating_enough
import opson.libraries.resources.generated.resources.today_goal_eating_regularly
import opson.libraries.resources.generated.resources.today_goal_performance
import opson.libraries.resources.generated.resources.today_macro_carbs
import opson.libraries.resources.generated.resources.today_macro_fat
import opson.libraries.resources.generated.resources.today_macro_fiber
import opson.libraries.resources.generated.resources.today_macro_protein
import opson.libraries.resources.generated.resources.today_stat_deficit
import opson.libraries.resources.generated.resources.today_stat_expended
import opson.libraries.resources.generated.resources.today_stat_meals
import opson.libraries.resources.generated.resources.today_stat_to_range
import opson.libraries.resources.generated.resources.today_stat_water
import org.jetbrains.compose.resources.StringResource

/**
 * One stat the summary card's first page can carry under the eaten bar (design sync 2, 2.1 and
 * 2.1e). [wire] is what `Settings.todaySummaryStats` stores, so a renamed entry never loses a
 * person's choice; [metric] is the level that has to allow it.
 */
enum class SummaryStat(val wire: String, val metric: Metric?) {
    Expended("expended", Metric.EnergyOut),
    DeficitSoFar("deficit_so_far", Metric.EnergyOut),
    ToRange("to_range", Metric.Remaining),
    Meals("meals", null),
    Protein("protein", Metric.Protein),
    Carbs("carbs", Metric.Carbs),
    Fat("fat", Metric.Fat),
    Fiber("fiber", Metric.Fiber),
    Water("water", Metric.Water),
    ;

    val label: StringResource
        get() = when (this) {
            Expended -> Res.string.today_stat_expended
            DeficitSoFar -> Res.string.today_stat_deficit
            ToRange -> Res.string.today_stat_to_range
            Meals -> Res.string.today_stat_meals
            Protein -> Res.string.today_macro_protein
            Carbs -> Res.string.today_macro_carbs
            Fat -> Res.string.today_macro_fat
            Fiber -> Res.string.today_macro_fiber
            Water -> Res.string.today_stat_water
        }

    companion object {
        fun fromWire(wire: String): SummaryStat? = entries.firstOrNull { it.wire == wire }
    }
}

/**
 * Which stats show, in what order, and which may be picked. Today builds the stats row from it and
 * More › Today screen lists them for picking, so the two never disagree. The suggestion comes from the first
 * onboarding goal that has one; the person's own order wins once they choose. Nothing here
 * decides how a stat is drawn: a stat whose number the levels hide drops out when it is built.
 */
object SummaryStats {
    /** Page 1 has room for three. */
    const val MAX = 3

    private val default = listOf(SummaryStat.Expended, SummaryStat.ToRange, SummaryStat.Meals)

    /** The onboarding goal the suggestion came from, as stored ("changing_weight"), or null for the default. */
    fun suggestionGoal(settings: Settings): String? = settings.onboarding.why.firstOrNull { it in byGoal }

    /** Copy for "Suggested from your goals: {goal}". Null when no goal picked the suggestion. */
    fun goalLabel(settings: Settings): StringResource? = when (suggestionGoal(settings)) {
        GOAL_CHANGING_WEIGHT -> Res.string.today_goal_changing_weight
        GOAL_EATING_REGULARLY -> Res.string.today_goal_eating_regularly
        GOAL_EATING_ENOUGH -> Res.string.today_goal_eating_enough
        GOAL_PERFORMANCE -> Res.string.today_goal_performance
        else -> null
    }

    fun suggested(settings: Settings): List<SummaryStat> = suggestionGoal(settings)?.let(byGoal::get) ?: default

    /** The person's stats, or the suggestion when they never chose. Unknown keys are skipped. */
    fun chosen(settings: Settings): List<SummaryStat> =
        settings.todaySummaryStats?.mapNotNull(SummaryStat::fromWire)?.distinct()?.take(MAX) ?: suggested(settings)

    /** Every stat this mode can offer at all. Deficit so far only where deficits are offered (Full, Sprint). */
    fun offered(numbers: NumbersSettings): List<SummaryStat> =
        SummaryStat.entries.filter { it != SummaryStat.DeficitSoFar || TargetStyle.deficitAllowed(numbers.preset) }

    /**
     * Whether the levels let [stat] show anything: amounts at Show for the energies and the
     * remainder, anything but Hide for the macros and water. Meals is a count and always may.
     */
    fun allowed(stat: SummaryStat, numbers: NumbersSettings): Boolean = when (stat) {
        SummaryStat.Meals -> true
        SummaryStat.Expended -> numbers.level(Metric.EnergyOut) == Visibility.Show
        SummaryStat.DeficitSoFar ->
            numbers.level(Metric.EnergyOut) == Visibility.Show && numbers.level(Metric.Calories) == Visibility.Show
        SummaryStat.ToRange -> numbers.level(Metric.Remaining) == Visibility.Show && numbers.level(Metric.Calories) == Visibility.Show
        else -> numbers.level(requireNotNull(stat.metric)) != Visibility.Hide
    }

    /** The sheet's list: the chosen ones first in their order, then the rest as the enum orders them. */
    fun listed(settings: Settings, numbers: NumbersSettings = settings.numbers): List<SummaryStat> {
        val offered = offered(numbers)
        val chosen = chosen(settings).filter { it in offered }
        return chosen + (offered - chosen.toSet())
    }

    private const val GOAL_CHANGING_WEIGHT = "changing_weight"
    private const val GOAL_EATING_REGULARLY = "eating_regularly"
    private const val GOAL_EATING_ENOUGH = "eating_enough"
    private const val GOAL_PERFORMANCE = "performance"

    private val byGoal: Map<String, List<SummaryStat>> = mapOf(
        GOAL_CHANGING_WEIGHT to listOf(SummaryStat.Expended, SummaryStat.DeficitSoFar, SummaryStat.Meals),
        GOAL_EATING_REGULARLY to listOf(SummaryStat.Meals, SummaryStat.ToRange, SummaryStat.Water),
        GOAL_EATING_ENOUGH to listOf(SummaryStat.Protein, SummaryStat.Meals, SummaryStat.ToRange),
        GOAL_PERFORMANCE to listOf(SummaryStat.Protein, SummaryStat.Meals, SummaryStat.ToRange),
    )
}
