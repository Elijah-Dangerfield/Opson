package com.opson.features.settings.impl.micros

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.opson.features.settings.impl.SectionLabel
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsDefaults
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.SettingsRow
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.MicroUnit
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.ReferenceBasis
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.button.ButtonSecondary
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.EdgeCardDefaults
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.Dimension
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_back_targets
import opson.libraries.resources.generated.resources.settings_back_today_screen
import opson.libraries.resources.generated.resources.settings_loading_cd
import opson.libraries.resources.generated.resources.settings_micros_add_details
import opson.libraries.resources.generated.resources.settings_micros_add_details_line
import opson.libraries.resources.generated.resources.settings_micros_basis_age
import opson.libraries.resources.generated.resources.settings_micros_basis_age_sex
import opson.libraries.resources.generated.resources.settings_micros_basis_dv
import opson.libraries.resources.generated.resources.settings_micros_basis_sex
import opson.libraries.resources.generated.resources.settings_micros_caption
import opson.libraries.resources.generated.resources.settings_micros_invalid
import opson.libraries.resources.generated.resources.settings_micros_off_body
import opson.libraries.resources.generated.resources.settings_micros_off_link
import opson.libraries.resources.generated.resources.settings_micros_off_title
import opson.libraries.resources.generated.resources.settings_micros_on_today
import opson.libraries.resources.generated.resources.settings_micros_own
import opson.libraries.resources.generated.resources.settings_micros_own_field
import opson.libraries.resources.generated.resources.settings_micros_show_today
import opson.libraries.resources.generated.resources.settings_micros_suggested
import opson.libraries.resources.generated.resources.settings_micros_title
import opson.libraries.resources.generated.resources.settings_micros_today_full
import opson.libraries.resources.generated.resources.settings_micros_today_last
import opson.libraries.resources.generated.resources.settings_micros_unit_mg
import opson.libraries.resources.generated.resources.settings_micros_unit_ug
import opson.libraries.resources.generated.resources.settings_micros_use_suggested
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun MicroTargetsScreen(
    state: MicroTargetsState,
    onAction: (MicroTargetsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.settings_micros_title),
            backLabel = stringResource(if (state.backToTodayScreen) Res.string.settings_back_today_screen else Res.string.settings_back_targets),
            onBack = { onAction(MicroTargetsAction.BackTapped) },
        )
        when {
            !state.loaded -> DelayedLoadingIndicator(contentDescription = stringResource(Res.string.settings_loading_cd))
            state.off -> OffCard(onModes = { onAction(MicroTargetsAction.ModesTapped) })
            else -> {
                SettingsCard {
                    Text(
                        text = stringResource(state.basis.line),
                        typography = AppTheme.type.body,
                        color = AppTheme.colors.muted,
                        modifier = Modifier.padding(vertical = SettingsDefaults.CardPadding),
                    )
                    if (state.canAddDetails) {
                        SettingsRow(
                            title = stringResource(Res.string.settings_micros_add_details),
                            subtitle = stringResource(Res.string.settings_micros_add_details_line),
                            icon = null,
                            chevron = true,
                            onClick = { onAction(MicroTargetsAction.AddDetailsTapped) },
                        )
                    }
                }
                state.groups.forEach { group ->
                    SectionLabel(stringResource(group.group.label))
                    SettingsCard {
                        group.rows.forEachIndexed { index, row ->
                            SettingsRow(
                                title = stringResource(row.micro.label),
                                subtitle = reference(row),
                                icon = null,
                                trailingText = if (row.onToday) stringResource(Res.string.settings_micros_on_today) else null,
                                chevron = true,
                                showDivider = index < group.rows.lastIndex,
                                onClick = { onAction(MicroTargetsAction.NutrientTapped(row.micro)) },
                            )
                        }
                    }
                }
                Text(text = stringResource(Res.string.settings_micros_caption), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
            }
        }
    }
}

@Composable
private fun reference(row: MicroTargetRow): String =
    if (row.own) stringResource(Res.string.settings_micros_own, row.reference) else row.reference

private val ReferenceBasis.line: StringResource
    get() = when (this) {
        ReferenceBasis.AgeAndSex -> Res.string.settings_micros_basis_age_sex
        ReferenceBasis.Age -> Res.string.settings_micros_basis_age
        ReferenceBasis.Sex -> Res.string.settings_micros_basis_sex
        ReferenceBasis.DailyValue -> Res.string.settings_micros_basis_dv
    }

/** Vitamins and minerals are off: they turn on in Modes and numbers, where the pause applies. */
@Composable
private fun OffCard(onModes: () -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = EdgeCardDefaults.InsetPadding, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Text(text = stringResource(Res.string.settings_micros_off_title), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
        Text(text = stringResource(Res.string.settings_micros_off_body), typography = AppTheme.type.body, color = AppTheme.colors.muted)
        SettingsRow(title = stringResource(Res.string.settings_micros_off_link), icon = null, chevron = true, onClick = onModes)
    }
}

/** One nutrient's sheet: the suggestion and why, a field for their own, and whether Today shows it. */
@Composable
fun MicroTargetSheetContent(
    state: MicroTargetSheetState,
    onAction: (MicroTargetAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = Dimension.D400, bottom = Dimension.D900),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHandle(Modifier.align(Alignment.CenterHorizontally))
        SheetHeader(title = stringResource(state.micro.label), onClose = { onAction(MicroTargetAction.CloseTapped) })
        if (!state.loaded || state.off) return@Column
        Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            Text(text = stringResource(Res.string.settings_micros_suggested, state.suggested), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
            Text(text = stringResource(state.basis.line), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        NumberField(
            value = state.ownText,
            onValueChange = { onAction(MicroTargetAction.OwnTyped(it)) },
            label = stringResource(Res.string.settings_micros_own_field),
            unit = stringResource(if (state.micro.unit == MicroUnit.Mg) Res.string.settings_micros_unit_mg else Res.string.settings_micros_unit_ug),
            placeholder = state.suggestedNumber,
            modifier = Modifier.fillMaxWidth(),
        )
        if (state.invalid) {
            Text(text = stringResource(Res.string.settings_micros_invalid), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        if (state.own) {
            ButtonSecondary(onClick = { onAction(MicroTargetAction.UseSuggestedTapped) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.settings_micros_use_suggested))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
        ) {
            Column(Modifier.weight(1f)) {
                Text(text = stringResource(Res.string.settings_micros_show_today), typography = AppTheme.type.bodyStrong, color = AppTheme.colors.ink)
                val note = when {
                    state.todayFull -> Res.string.settings_micros_today_full
                    state.todayLast -> Res.string.settings_micros_today_last
                    else -> null
                }
                note?.let { Text(text = stringResource(it), typography = AppTheme.type.secondarySmall, color = AppTheme.colors.muted) }
            }
            AppSwitch(
                checked = state.onToday,
                onCheckedChange = { onAction(MicroTargetAction.TodayToggled(it)) },
                enabled = !state.todayFull && !state.todayLast,
            )
        }
    }
}

private val previewToday = SettingsFixtures.today

@Composable
private fun Page(settings: Settings, goalsAndEnergy: Boolean = true) {
    val policy = SettingsFixtures.policy(settings.numbers)
    MicroTargetsScreen(MicroTargetsStateBuilder.build(settings, policy, previewToday, goalsAndEnergy = goalsAndEnergy), onAction = {})
}

/** Nothing known about them: the label's Daily Values, and the link to add age and sex. */
@Preview
@Composable
private fun MicroTargetsPreview() = SettingsPreview { Page(SettingsFixtures.settings()) }

/** A woman of 36 with her own vitamin D, and vitamin D on Today in place of sodium. */
@Preview
@Composable
private fun MicroTargetsPersonalDarkPreview() = SettingsPreview(dark = true) {
    Page(
        SettingsFixtures.settings().copy(birthYear = 1990, bodySex = BodySex.Female)
            .withMicroTarget(Micronutrient.VitaminD, 25.0)
            .withTodayNutrient(Micronutrient.Sodium, false)
            .withTodayNutrient(Micronutrient.VitaminD, true),
    )
}

@Preview
@Composable
private fun MicroTargetsOffPreview() = SettingsPreview { Page(SettingsFixtures.settings(NumbersSettings(preset = PresetId.MacrosOnly))) }

@Preview
@Composable
private fun MicroTargetSheetPreview() = SettingsPreview {
    val settings = SettingsFixtures.settings().copy(birthYear = 1990, bodySex = BodySex.Female)
    MicroTargetSheetContent(
        MicroTargetsStateBuilder.sheet(settings, SettingsFixtures.policy(settings.numbers), previewToday, Micronutrient.Iron),
        onAction = {},
    )
}

/** Their own vitamin D, and Today already full. */
@Preview
@Composable
private fun MicroTargetSheetOwnPreview() = SettingsPreview {
    val settings = SettingsFixtures.settings().withMicroTarget(Micronutrient.VitaminD, 25.0)
    MicroTargetSheetContent(
        MicroTargetsStateBuilder.sheet(settings, SettingsFixtures.policy(settings.numbers), previewToday, Micronutrient.VitaminD),
        onAction = {},
    )
}

@Preview
@Composable
private fun MicroTargetSheetInvalidPreview() = SettingsPreview {
    val settings = SettingsFixtures.settings()
    MicroTargetSheetContent(
        MicroTargetsStateBuilder.sheet(settings, SettingsFixtures.policy(settings.numbers), previewToday, Micronutrient.Sodium, typed = "999999"),
        onAction = {},
    )
}
