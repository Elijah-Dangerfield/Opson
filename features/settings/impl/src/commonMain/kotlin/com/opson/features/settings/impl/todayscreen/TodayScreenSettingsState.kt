package com.opson.features.settings.impl.todayscreen

import com.opson.libraries.diary.MainNumberChoice
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.Presets
import com.opson.libraries.nutrition.TargetStyle
import com.opson.libraries.nutrition.TodayLayout
import com.opson.libraries.nutrition.Visibility

/**
 * More › Today screen (design sync 4, 13.6a). Arranges Today, never shows more: no amount
 * appears here, and outside All numbers ([cardArranged] false) the card's layout comes from the
 * mode, so only the list's rows can be changed.
 */
data class TodayScreenSettingsState(
    val loaded: Boolean = false,
    val cardArranged: Boolean = true,
    val presets: List<PresetOption> = emptyList(),
    val mainNumber: MainNumberChoice = MainNumberChoice.Follow,
    /** The goal style "Follow my goal" follows: "Range shows To your range". */
    val follows: FollowStyle = FollowStyle.Range,
    /** Card pages: Energy alone, or Energy and Macros. */
    val macrosPage: Boolean = true,
    val burnedBreakdown: Boolean = true,
    val mealsCount: Boolean = false,
    val nutrientsLink: Boolean = true,
    /** What the nutrients page lists, while it is on and vitamins and minerals show; empty hides the row. */
    val nutrientsOnPage: List<Micronutrient> = emptyList(),
    val water: Boolean = true,
    val weight: Boolean = true,
    val closeDay: Boolean = true,
)

enum class FollowStyle { Range, Exact, Deficit, Surplus, ByDay }

/** One card preset. Rings is [enabled] only with a single target ("Needs a single target"). */
data class PresetOption(val preset: SummaryCardPreset, val selected: Boolean, val enabled: Boolean)

object TodayScreenSettingsStateBuilder {
    fun build(settings: Settings): TodayScreenSettingsState {
        val style = settings.targets.style
        val single = singleTarget(style)
        // Rings with a range falls back to In and out, so that is what the card draws and what shows picked.
        val drawn = if (settings.todayCard == SummaryCardPreset.Rings && !single) SummaryCardPreset.InOut else settings.todayCard
        return TodayScreenSettingsState(
            loaded = true,
            cardArranged = arranged(settings.numbers.preset),
            presets = SummaryCardPreset.entries.map { PresetOption(it, it == drawn, it != SummaryCardPreset.Rings || single) },
            mainNumber = settings.todayMainNumber,
            follows = when (style) {
                TargetStyle.Range -> FollowStyle.Range
                TargetStyle.Exact -> FollowStyle.Exact
                is TargetStyle.Deficit -> FollowStyle.Deficit
                is TargetStyle.Surplus -> FollowStyle.Surplus
                is TargetStyle.ByDay -> FollowStyle.ByDay
            },
            macrosPage = settings.todayMacrosPage,
            burnedBreakdown = settings.todayBurnedBreakdown,
            mealsCount = settings.todayMealsCount,
            nutrientsLink = settings.todayNutrientsLink,
            nutrientsOnPage = settings.todayMicronutrients.takeIf {
                settings.todayNutrientsLink && settings.numbers.level(Metric.Micros) != Visibility.Hide
            }.orEmpty(),
            water = settings.todayWater,
            weight = settings.todayWeight,
            closeDay = settings.todayCloseDay,
        )
    }

    /** One number, a set deficit or a set surplus: a single target every day. Different by day counts as a range. */
    fun singleTarget(style: TargetStyle): Boolean =
        style is TargetStyle.Exact || style is TargetStyle.Deficit || style is TargetStyle.Surplus

    /** The card is arranged here only in a mode whose Today is All numbers' card: that layout, calories on show. */
    fun arranged(preset: PresetId): Boolean = Presets[preset].let { it.today == TodayLayout.Full && it.level(Metric.Calories) == Visibility.Show }
}
