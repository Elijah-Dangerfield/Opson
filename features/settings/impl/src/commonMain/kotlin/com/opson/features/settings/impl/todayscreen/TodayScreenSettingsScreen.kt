package com.opson.features.settings.impl.todayscreen

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SectionLabel
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.SettingsRow
import com.opson.libraries.diary.MainNumberChoice
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.ProgressRing
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.radio.RadioRow
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_back_more
import opson.libraries.resources.generated.resources.settings_today_burned_breakdown
import opson.libraries.resources.generated.resources.settings_today_burned_breakdown_line
import opson.libraries.resources.generated.resources.settings_today_card_from_mode
import opson.libraries.resources.generated.resources.settings_today_card_pages
import opson.libraries.resources.generated.resources.settings_today_close_day
import opson.libraries.resources.generated.resources.settings_today_follow_by_day
import opson.libraries.resources.generated.resources.settings_today_follow_deficit
import opson.libraries.resources.generated.resources.settings_today_follow_exact
import opson.libraries.resources.generated.resources.settings_today_follow_range
import opson.libraries.resources.generated.resources.settings_today_follow_surplus
import opson.libraries.resources.generated.resources.settings_today_in_list
import opson.libraries.resources.generated.resources.settings_today_main_balance
import opson.libraries.resources.generated.resources.settings_today_main_eaten
import opson.libraries.resources.generated.resources.settings_today_main_follow
import opson.libraries.resources.generated.resources.settings_today_main_goal
import opson.libraries.resources.generated.resources.settings_today_main_number
import opson.libraries.resources.generated.resources.settings_today_meals_count
import opson.libraries.resources.generated.resources.settings_today_needs_single
import opson.libraries.resources.generated.resources.settings_today_nutrients_link
import opson.libraries.resources.generated.resources.settings_today_on_card
import opson.libraries.resources.generated.resources.settings_today_pages_energy
import opson.libraries.resources.generated.resources.settings_today_pages_energy_macros
import opson.libraries.resources.generated.resources.settings_today_preset_equation
import opson.libraries.resources.generated.resources.settings_today_preset_in_out
import opson.libraries.resources.generated.resources.settings_today_preset_range_bar
import opson.libraries.resources.generated.resources.settings_today_preset_rings
import opson.libraries.resources.generated.resources.settings_today_summary_card
import opson.libraries.resources.generated.resources.settings_today_title
import opson.libraries.resources.generated.resources.settings_today_water
import opson.libraries.resources.generated.resources.settings_today_weight
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun TodayScreenSettingsScreen(
    state: TodayScreenSettingsState,
    onAction: (TodayScreenSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.settings_today_title),
            backLabel = stringResource(Res.string.settings_back_more),
            onBack = { onAction(TodayScreenSettingsAction.BackTapped) },
        )
        if (!state.loaded) return@SettingsPage
        SectionLabel(stringResource(Res.string.settings_today_summary_card))
        if (state.cardArranged) {
            Presets(state.presets, onAction)
            SettingsCard {
                SettingsRow(
                    title = stringResource(Res.string.settings_today_main_number),
                    icon = null,
                    trailing = { Value(stringResource(state.mainNumber.label)) },
                    chevron = true,
                    showDivider = true,
                    onClick = { onAction(TodayScreenSettingsAction.MainNumberTapped) },
                )
                SettingsRow(
                    title = stringResource(Res.string.settings_today_card_pages),
                    icon = null,
                    trailing = {
                        Value(stringResource(if (state.macrosPage) Res.string.settings_today_pages_energy_macros else Res.string.settings_today_pages_energy))
                    },
                    chevron = true,
                    onClick = { onAction(TodayScreenSettingsAction.CardPagesTapped) },
                )
            }
            SectionLabel(stringResource(Res.string.settings_today_on_card))
            SettingsCard {
                Switch(Res.string.settings_today_burned_breakdown, state.burnedBreakdown, TodaySwitch.BurnedBreakdown, onAction, Res.string.settings_today_burned_breakdown_line)
                Switch(Res.string.settings_today_meals_count, state.mealsCount, TodaySwitch.MealsCount, onAction)
                Switch(Res.string.settings_today_nutrients_link, state.nutrientsLink, TodaySwitch.NutrientsLink, onAction, last = true)
            }
        } else {
            Text(
                text = stringResource(Res.string.settings_today_card_from_mode),
                typography = AppTheme.type.secondary,
                color = AppTheme.colors.muted,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        SectionLabel(stringResource(Res.string.settings_today_in_list))
        SettingsCard {
            Switch(Res.string.settings_today_water, state.water, TodaySwitch.Water, onAction)
            Switch(Res.string.settings_today_weight, state.weight, TodaySwitch.Weight, onAction)
            Switch(Res.string.settings_today_close_day, state.closeDay, TodaySwitch.CloseDay, onAction, last = true)
        }
    }
}

@Composable
private fun Value(text: String) {
    Text(text = text, typography = AppTheme.type.bodyBold, color = AppTheme.colors.accent, maxLines = 1)
}

@Composable
private fun Switch(
    title: StringResource,
    on: Boolean,
    row: TodaySwitch,
    onAction: (TodayScreenSettingsAction) -> Unit,
    subtitle: StringResource? = null,
    last: Boolean = false,
) {
    SettingsRow(
        title = stringResource(title),
        icon = null,
        subtitle = subtitle?.let { stringResource(it) },
        showDivider = !last,
        trailing = { AppSwitch(checked = on, onCheckedChange = { onAction(TodayScreenSettingsAction.Toggled(row, it)) }) },
    )
}

/** The four presets two by two, each a sketch of its card with no numbers in it. */
@Composable
private fun Presets(presets: List<PresetOption>, onAction: (TodayScreenSettingsAction) -> Unit) {
    Column(Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        presets.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
                pair.forEach { option -> PresetTile(option, onAction, Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun PresetTile(option: PresetOption, onAction: (TodayScreenSettingsAction) -> Unit, modifier: Modifier) {
    val colors = AppTheme.colors
    val shape = AppTheme.shapes.card
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (option.selected) colors.accentTint else colors.surface)
            .border(if (option.selected) 2.dp else 1.dp, (if (option.selected) colors.accent else colors.line).color, shape)
            .selectable(
                selected = option.selected,
                enabled = option.enabled,
                role = Role.RadioButton,
                onClick = { onAction(TodayScreenSettingsAction.PresetPicked(option.preset)) },
            )
            .alpha(if (option.enabled) 1f else DISABLED)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.height(SKETCH), contentAlignment = Alignment.CenterStart) { Sketch(option.preset) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(option.preset.label),
                typography = AppTheme.type.cardTitle,
                color = colors.ink,
                modifier = Modifier.weight(1f),
            )
            if (option.selected) Icon(icon = AppIcons.CheckCircle, size = 26.dp, color = colors.accent)
        }
        if (!option.enabled) {
            Text(text = stringResource(Res.string.settings_today_needs_single), typography = AppTheme.type.secondarySmall, color = colors.muted)
        }
    }
}

@Composable
private fun Sketch(preset: SummaryCardPreset) {
    val n = AppTheme.colors.nutrients
    val eaten = listOf(0.08f to n.protein.content, 0.26f to n.carbs.content, 0.15f to n.fat.content)
    when (preset) {
        SummaryCardPreset.InOut -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SketchBar(eaten)
            SketchBar(listOf(0.62f to AppTheme.colors.restingEnergy, 0.22f to AppTheme.colors.activeEnergy))
        }
        SummaryCardPreset.RangeBar -> SketchBar(eaten)
        SummaryCardPreset.Rings -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { ProgressRing(fraction = 0.3f, color = n.carbs.content, size = 36.dp, trackColor = AppTheme.colors.track) }
        }
        SummaryCardPreset.Equation -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SketchBlock()
            Text("−", typography = AppTheme.type.cardTitle, color = AppTheme.colors.muted)
            SketchBlock()
            Text("=", typography = AppTheme.type.cardTitle, color = AppTheme.colors.muted)
            SketchBlock()
        }
    }
}

@Composable
private fun SketchBar(parts: List<Pair<Float, ColorResource>>) {
    Row(Modifier.fillMaxWidth().height(8.dp).clip(AppTheme.shapes.pill).background(AppTheme.colors.track)) {
        parts.forEach { (weight, color) -> Box(Modifier.weight(weight).height(8.dp).background(color)) }
        val rest = 1f - parts.sumOf { it.first.toDouble() }.toFloat()
        if (rest > 0f) Box(Modifier.weight(rest).height(8.dp))
    }
}

@Composable
private fun SketchBlock() {
    Box(Modifier.size(width = 28.dp, height = 12.dp).clip(AppTheme.shapes.pill).background(AppTheme.colors.inkSoft))
}

internal val SummaryCardPreset.label: StringResource
    get() = when (this) {
        SummaryCardPreset.InOut -> Res.string.settings_today_preset_in_out
        SummaryCardPreset.RangeBar -> Res.string.settings_today_preset_range_bar
        SummaryCardPreset.Rings -> Res.string.settings_today_preset_rings
        SummaryCardPreset.Equation -> Res.string.settings_today_preset_equation
    }

internal val MainNumberChoice.label: StringResource
    get() = when (this) {
        MainNumberChoice.Follow -> Res.string.settings_today_main_follow
        MainNumberChoice.Goal -> Res.string.settings_today_main_goal
        MainNumberChoice.Balance -> Res.string.settings_today_main_balance
        MainNumberChoice.Eaten -> Res.string.settings_today_main_eaten
    }

/** The main number (13.6b) or card pages sheet's body, apart from the `BottomSheet` so previews can draw it. */
@Composable
fun TodayScreenChoiceSheetContent(state: TodayScreenChoiceState, onAction: (TodayScreenChoiceAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = 10.dp, bottom = AppTheme.spacing.page),
    ) {
        SheetHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
        Text(
            text = stringResource(if (state.choice == TodayScreenChoice.MainNumber) Res.string.settings_today_main_number else Res.string.settings_today_card_pages),
            typography = AppTheme.type.sheetTitle,
            color = AppTheme.colors.ink,
            modifier = Modifier.padding(top = 12.dp, bottom = 8.dp),
        )
        Column(Modifier.selectableGroup()) {
            when (state.choice) {
                TodayScreenChoice.MainNumber -> MainNumberChoice.entries.forEachIndexed { index, choice ->
                    RadioRow(
                        title = stringResource(choice.label),
                        subtitle = if (choice == MainNumberChoice.Follow) {
                            stringResource(
                                when (state.follows) {
                                    FollowStyle.Range -> Res.string.settings_today_follow_range
                                    FollowStyle.Exact -> Res.string.settings_today_follow_exact
                                    FollowStyle.Deficit -> Res.string.settings_today_follow_deficit
                                    FollowStyle.Surplus -> Res.string.settings_today_follow_surplus
                                    FollowStyle.ByDay -> Res.string.settings_today_follow_by_day
                                },
                            )
                        } else {
                            null
                        },
                        selected = choice == state.mainNumber,
                        onClick = { onAction(TodayScreenChoiceAction.MainNumberPicked(choice)) },
                        showDivider = index < MainNumberChoice.entries.lastIndex,
                    )
                }
                TodayScreenChoice.CardPages -> {
                    RadioRow(
                        title = stringResource(Res.string.settings_today_pages_energy),
                        selected = !state.macrosPage,
                        onClick = { onAction(TodayScreenChoiceAction.PagesPicked(macros = false)) },
                    )
                    RadioRow(
                        title = stringResource(Res.string.settings_today_pages_energy_macros),
                        selected = state.macrosPage,
                        onClick = { onAction(TodayScreenChoiceAction.PagesPicked(macros = true)) },
                        showDivider = false,
                    )
                }
            }
        }
    }
}

private const val DISABLED = 0.5f
private val SKETCH = 44.dp

@Preview
@Composable
private fun TodayScreenSettingsPreview() {
    SettingsPreview(dark = true) {
        TodayScreenSettingsScreen(TodayScreenSettingsStateBuilder.build(SettingsFixtures.settings()), onAction = {})
    }
}

@Preview
@Composable
private fun TodayScreenSettingsOneNumberPreview() {
    SettingsPreview {
        val settings = SettingsFixtures.settings(targets = SettingsFixtures.exactTargets).copy(todayCard = SummaryCardPreset.Rings)
        TodayScreenSettingsScreen(TodayScreenSettingsStateBuilder.build(settings), onAction = {})
    }
}

@Preview
@Composable
private fun TodayScreenSettingsFromModePreview() {
    SettingsPreview {
        TodayScreenSettingsScreen(TodayScreenSettingsStateBuilder.build(SettingsFixtures.settings(NumbersSettings(preset = PresetId.Blind))), onAction = {})
    }
}

@Preview
@Composable
private fun TodayScreenMainNumberPreview() {
    SettingsPreview(dark = true) {
        TodayScreenChoiceSheetContent(TodayScreenChoiceState.of(TodayScreenChoice.MainNumber, SettingsFixtures.settings()), onAction = {})
    }
}

@Preview
@Composable
private fun TodayScreenCardPagesPreview() {
    SettingsPreview {
        TodayScreenChoiceSheetContent(TodayScreenChoiceState.of(TodayScreenChoice.CardPages, Settings()), onAction = {})
    }
}
