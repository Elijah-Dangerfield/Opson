package com.opson.features.settings.impl.todayscreen

import com.opson.libraries.diary.MainNumberChoice
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.diary.TodayCardRules
import com.opson.libraries.diary.fastingTimerOn
import com.opson.libraries.diary.ui.SummaryStat
import com.opson.libraries.diary.ui.SummaryStats
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.Visibility
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_today_card_from_mode
import opson.libraries.resources.generated.resources.settings_today_card_later
import opson.libraries.resources.generated.resources.settings_today_equation_hidden
import opson.libraries.resources.generated.resources.settings_today_equation_unknown
import opson.libraries.resources.generated.resources.settings_today_balance_hidden
import opson.libraries.resources.generated.resources.settings_today_balance_unknown
import opson.libraries.resources.generated.resources.settings_today_nutrients_hidden
import opson.libraries.resources.generated.resources.settings_today_water_hidden
import opson.libraries.resources.generated.resources.settings_today_weight_hidden
import org.jetbrains.compose.resources.StringResource

/**
 * More › Today screen (design sync 4, 13.6a), the one place Today is arranged: the ⋮ menu's "Edit
 * Today screen" and a long press on the summary card open it too. It arranges, never shows more:
 * no amount appears here, and a row the levels or the mode override says so where it is, with the
 * way to the setting that decides ([OverrideNote]).
 *
 * The summary card has three shapes here, as Today has: design sync 4's presets and main number
 * ([presetsOn], `release.goalsAndEnergy` in All numbers), the older card's stats ([stats],
 * `release.todaySummary` otherwise), or a line saying the mode decides ([cardNote]).
 */
data class TodayScreenSettingsState(
    val loaded: Boolean = false,
    /** The card is All numbers' card, and design sync 4 arranges it: presets, main number, On the card. */
    val presetsOn: Boolean = false,
    /** Why the card can't be arranged here, with the way to what decides it; null when it can. */
    val cardNote: OverrideNote? = null,
    val presets: List<PresetOption> = emptyList(),
    /** The preset picked that Today can't draw yet, and why. */
    val presetNote: OverrideNote? = null,
    val mainNumber: MainNumberChoice = MainNumberChoice.Follow,
    /** The goal style "Follow my goal" follows: "Range shows To your range". */
    val follows: FollowStyle = FollowStyle.Range,
    val mainNumberNote: OverrideNote? = null,
    /** Card pages, Energy alone or Energy and Macros; null where there is no energy page or no macros page to add. */
    val macrosPage: Boolean? = true,
    /** The older card's stats under the bar; null where they don't draw. */
    val stats: StatsSection? = null,
    val burnedBreakdown: Boolean = true,
    /** The breakdown only draws on In and out; another preset picked leaves it with nothing to change. */
    val breakdownNeedsInOut: Boolean = false,
    val mealsCount: Boolean = false,
    /** The nutrients page switch; null where the card has no energy page to add it to. */
    val nutrientsLink: Boolean? = true,
    /** What the nutrients page lists, while it is on and vitamins and minerals show; empty hides the row. */
    val nutrientsOnPage: List<Micronutrient> = emptyList(),
    val nutrientsNote: OverrideNote? = null,
    val mealGroups: Boolean = true,
    val water: Boolean = true,
    val waterNote: OverrideNote? = null,
    /** The fasting card, only while fasting is offered and its timer is on; null when the row is not there. */
    val fasting: Boolean? = null,
    /** The Medication card (`release.medication`), only while medication is tracked; null when the row is not there. */
    val medication: Boolean? = null,
    /** The Weight row, with `release.todaySummary`, which draws it; null without. */
    val weight: Boolean? = true,
    val weightNote: OverrideNote? = null,
    /** Close the day's button in Today's header, not a row in the list. */
    val closeDay: Boolean = true,
)

enum class FollowStyle { Range, Exact, Deficit, Surplus, ByDay }

/** One card preset. Rings is [enabled] only with a single target ("Needs a single target"). */
data class PresetOption(val preset: SummaryCardPreset, val selected: Boolean, val enabled: Boolean)

/** A choice here another setting overrides: what to say, and the setting that decides. */
data class OverrideNote(val text: StringResource, val link: SettingLink)

/** Where an [OverrideNote] goes: Display, where the levels and modes are, or the details Burned is worked out from. */
enum class SettingLink { Display, RestingEnergy }

/** The older card's stats (design sync 2, 2.1e): [goal] is the onboarding goal the suggestion came from, null for the default. */
data class StatsSection(val goal: StringResource?, val rows: List<StatRow>, val ticked: Int)

/** One stat: ticked or not, or [locked] by the levels. */
data class StatRow(val stat: SummaryStat, val ticked: Boolean, val locked: Boolean)

/** The release flags that decide which parts of Today there are to arrange. */
data class TodayScreenFlags(
    val goalsAndEnergy: Boolean = true,
    val todaySummary: Boolean = true,
    val fasting: Boolean = false,
)

object TodayScreenSettingsStateBuilder {
    /**
     * [burnedKnown] is whether there is a Burned for today at all (resting energy needs body
     * details, a health app or a number entered); Equation and Deficit or surplus need it.
     */
    fun build(settings: Settings, flags: TodayScreenFlags = TodayScreenFlags(), burnedKnown: Boolean = true): TodayScreenSettingsState {
        val numbers = settings.numbers
        val style = settings.targets.style
        val single = singleTarget(style)
        val energyPage = TodayCardRules.energyPage(numbers)
        val presetsOn = flags.goalsAndEnergy && TodayCardRules.presetsApply(numbers)
        val burnedShown = numbers.level(Metric.EnergyOut) == Visibility.Show
        val burnedOnShow = burnedShown && burnedKnown
        // What Today draws for the pick: Rings with a range and Equation without Burned fall back to In and out.
        val drawn = TodayCardRules.drawn(settings.todayCard, single, burnedOnShow)
        val burnedNote = { hidden: StringResource, unknown: StringResource ->
            if (!burnedShown) OverrideNote(hidden, SettingLink.Display) else OverrideNote(unknown, SettingLink.RestingEnergy)
        }
        return TodayScreenSettingsState(
            loaded = true,
            presetsOn = presetsOn,
            cardNote = when {
                !energyPage -> OverrideNote(Res.string.settings_today_card_from_mode, SettingLink.Display)
                flags.goalsAndEnergy && !presetsOn -> OverrideNote(Res.string.settings_today_card_later, SettingLink.Display)
                else -> null
            },
            // Rings with a range shows In and out picked, since that is what the card draws; Equation stays picked with its note.
            presets = SummaryCardPreset.entries.map { option ->
                val shown = if (settings.todayCard == SummaryCardPreset.Rings && !single) SummaryCardPreset.InOut else settings.todayCard
                PresetOption(option, option == shown, option != SummaryCardPreset.Rings || single)
            },
            presetNote = if (presetsOn && settings.todayCard == SummaryCardPreset.Equation && drawn != SummaryCardPreset.Equation) {
                burnedNote(Res.string.settings_today_equation_hidden, Res.string.settings_today_equation_unknown)
            } else {
                null
            },
            mainNumber = settings.todayMainNumber,
            follows = followStyle(style),
            mainNumberNote = if (presetsOn && settings.todayMainNumber == MainNumberChoice.Balance && !burnedOnShow) {
                burnedNote(Res.string.settings_today_balance_hidden, Res.string.settings_today_balance_unknown)
            } else {
                null
            },
            macrosPage = settings.todayMacrosPage.takeIf { flags.todaySummary && energyPage },
            stats = if (flags.todaySummary && energyPage && !presetsOn) stats(settings, numbers) else null,
            burnedBreakdown = settings.todayBurnedBreakdown,
            breakdownNeedsInOut = drawn != SummaryCardPreset.InOut,
            mealsCount = settings.todayMealsCount,
            nutrientsLink = settings.todayNutrientsLink.takeIf { energyPage },
            nutrientsOnPage = settings.todayMicronutrients.takeIf {
                settings.todayNutrientsLink && numbers.level(Metric.Micros) != Visibility.Hide
            }.orEmpty(),
            nutrientsNote = hiddenNote(numbers, Metric.Micros, Res.string.settings_today_nutrients_hidden).takeIf { energyPage && settings.todayNutrientsLink },
            mealGroups = settings.mealGroups,
            water = settings.todayWater,
            waterNote = hiddenNote(numbers, Metric.Water, Res.string.settings_today_water_hidden).takeIf { settings.todayWater },
            fasting = settings.fastToday.takeIf { settings.fastingTimerOn(flags.fasting) },
            weight = settings.todayWeight.takeIf { flags.todaySummary },
            weightNote = hiddenNote(numbers, Metric.Weight, Res.string.settings_today_weight_hidden).takeIf { flags.todaySummary && settings.todayWeight },
            closeDay = settings.todayCloseDay,
        )
    }

    /** One number, a set deficit or a set surplus: a single target every day. Different by day counts as a range. */
    fun singleTarget(style: TargetStyle): Boolean =
        style is TargetStyle.Exact || style is TargetStyle.Deficit || style is TargetStyle.Surplus

    private fun followStyle(style: TargetStyle): FollowStyle = when (style) {
        TargetStyle.Range -> FollowStyle.Range
        TargetStyle.Exact -> FollowStyle.Exact
        is TargetStyle.Deficit -> FollowStyle.Deficit
        is TargetStyle.Surplus -> FollowStyle.Surplus
        is TargetStyle.ByDay -> FollowStyle.ByDay
    }

    /** A row the levels hide entirely: switching it on here would show nothing. */
    private fun hiddenNote(numbers: NumbersSettings, metric: Metric, text: StringResource): OverrideNote? =
        OverrideNote(text, SettingLink.Display).takeIf { numbers.level(metric) == Visibility.Hide }

    /** Page 1's stats: the chosen ones first, in order, then the rest; one the levels hide is locked, never ticked. */
    private fun stats(settings: Settings, numbers: NumbersSettings): StatsSection {
        val chosen = SummaryStats.chosen(settings).filter { SummaryStats.allowed(it, numbers) }.toSet()
        val rows = SummaryStats.listed(settings, numbers).map { stat ->
            val locked = !SummaryStats.allowed(stat, numbers)
            StatRow(stat = stat, ticked = !locked && stat in chosen, locked = locked)
        }
        return StatsSection(goal = SummaryStats.goalLabel(settings), rows = rows, ticked = rows.count { it.ticked })
    }
}
