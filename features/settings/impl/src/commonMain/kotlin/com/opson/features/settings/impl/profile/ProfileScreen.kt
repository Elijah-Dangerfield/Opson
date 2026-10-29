package com.opson.features.settings.impl.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SectionLabel
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.SettingsRow
import com.opson.libraries.diary.OnboardingAnswers
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.ui.HeightField
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_back_micros
import opson.libraries.resources.generated.resources.settings_back_more
import opson.libraries.resources.generated.resources.settings_back_resting
import opson.libraries.resources.generated.resources.settings_loading_cd
import opson.libraries.resources.generated.resources.settings_profile_about
import opson.libraries.resources.generated.resources.settings_profile_bmi
import opson.libraries.resources.generated.resources.settings_profile_bmi_note
import opson.libraries.resources.generated.resources.settings_profile_body
import opson.libraries.resources.generated.resources.settings_profile_body_fat
import opson.libraries.resources.generated.resources.settings_profile_intro
import opson.libraries.resources.generated.resources.settings_profile_percent
import opson.libraries.resources.generated.resources.settings_profile_sex
import opson.libraries.resources.generated.resources.settings_profile_sex_body
import opson.libraries.resources.generated.resources.settings_profile_title
import opson.libraries.resources.generated.resources.settings_profile_weight
import opson.libraries.resources.generated.resources.settings_profile_weight_none
import opson.libraries.resources.generated.resources.settings_profile_weight_on
import opson.libraries.resources.generated.resources.settings_resting_birth_year
import opson.libraries.resources.generated.resources.settings_resting_sex_female
import opson.libraries.resources.generated.resources.settings_resting_sex_male
import opson.libraries.resources.generated.resources.settings_resting_sex_unsaid
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * More › Profile (Cronometer's profile, in Opson's voice): the details every estimate reads, in one
 * place. About you is always there; Weight and body shows the latest weigh-in and, where the mode
 * shows weight, BMI with what it is and an optional body fat.
 */
@Composable
fun ProfileScreen(
    state: ProfileState,
    onAction: (ProfileAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.settings_profile_title),
            backLabel = stringResource(state.back.label),
            onBack = { onAction(ProfileAction.BackTapped) },
        )
        Text(
            text = stringResource(Res.string.settings_profile_intro),
            typography = AppTheme.type.body,
            color = AppTheme.colors.ink,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (!state.loaded) {
            DelayedLoadingIndicator(contentDescription = stringResource(Res.string.settings_loading_cd))
            return@SettingsPage
        }
        SectionLabel(stringResource(Res.string.settings_profile_about))
        AboutYou(state, onAction)
        SectionLabel(stringResource(Res.string.settings_profile_body))
        Body(state, onAction)
    }
}

@Composable
private fun AboutYou(state: ProfileState, onAction: (ProfileAction) -> Unit) {
    EdgeCard(edgeToEdge = false, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        NumberField(
            value = state.birthYearText,
            onValueChange = { onAction(ProfileAction.BirthYearTyped(it)) },
            label = stringResource(Res.string.settings_resting_birth_year),
            allowDecimal = false,
            imeAction = ImeAction.Next,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = stringResource(Res.string.settings_profile_sex),
                typography = AppTheme.type.secondarySmallStrong,
                color = AppTheme.colors.muted,
            )
            SegmentedControl(
                options = BodySex.entries,
                selected = state.sex,
                onSelect = { sex -> sex?.let { onAction(ProfileAction.SexPicked(it)) } },
                label = { sex ->
                    stringResource(
                        when (sex) {
                            BodySex.Female -> Res.string.settings_resting_sex_female
                            BodySex.Male -> Res.string.settings_resting_sex_male
                            BodySex.Unsaid, null -> Res.string.settings_resting_sex_unsaid
                        },
                    )
                },
            )
            Text(
                text = stringResource(Res.string.settings_profile_sex_body),
                typography = AppTheme.type.secondarySmall,
                color = AppTheme.colors.muted,
            )
        }
        HeightField(
            input = state.height,
            onUnitPicked = { onAction(ProfileAction.HeightUnitPicked(it)) },
            onCmTyped = { onAction(ProfileAction.HeightCmTyped(it)) },
            onFeetTyped = { onAction(ProfileAction.HeightFeetTyped(it)) },
            onInchesTyped = { onAction(ProfileAction.HeightInchesTyped(it)) },
        )
    }
}

@Composable
private fun Body(state: ProfileState, onAction: (ProfileAction) -> Unit) {
    val weight = state.weight
    SettingsCard {
        SettingsRow(
            title = stringResource(Res.string.settings_profile_weight),
            icon = null,
            subtitle = when (weight) {
                ProfileWeight.None -> stringResource(Res.string.settings_profile_weight_none)
                is ProfileWeight.Logged -> stringResource(Res.string.settings_profile_weight_on, weight.onText)
            },
            chevron = true,
            showDivider = state.bmi !is Display.Hidden,
            onClick = { onAction(ProfileAction.WeightTapped) },
            trailing = {
                if (weight is ProfileWeight.Logged) {
                    NutrientText(metric = Metric.Weight, display = weight.display, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink)
                }
            },
        )
        if (state.bmi !is Display.Hidden) {
            SettingsRow(
                title = stringResource(Res.string.settings_profile_bmi),
                icon = null,
                subtitle = stringResource(Res.string.settings_profile_bmi_note),
                trailing = {
                    // BMI is weight over height: tagged as weight, so a mode that hides weight is held to it.
                    NutrientText(metric = Metric.Weight, display = state.bmi, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink)
                },
            )
        }
    }
    state.bodyFatText?.let { text ->
        EdgeCard(edgeToEdge = false) {
            NumberField(
                value = text,
                onValueChange = { onAction(ProfileAction.BodyFatTyped(it)) },
                label = stringResource(Res.string.settings_profile_body_fat),
                unit = stringResource(Res.string.settings_profile_percent),
                allowDecimal = true,
            )
        }
    }
}

private val ProfileBack.label: StringResource
    get() = when (this) {
        ProfileBack.More -> Res.string.settings_back_more
        ProfileBack.RestingEnergy -> Res.string.settings_back_resting
        ProfileBack.Micros -> Res.string.settings_back_micros
    }

/** Previews and the leak test: everything filled in, and a weigh-in on the fixtures' today. */
internal object ProfileFixtures {
    val filled = Settings(birthYear = 1990, heightCm = 168.0, heightUnit = HeightUnit.Cm, bodySex = BodySex.Female, bodyFatPercent = 24.0)
    val weighIn = WeighIn(
        id = "w1",
        measuredAt = SettingsFixtures.today.atTime(LocalTime(7, 30)).toInstant(TimeZone.UTC),
        kg = 64.2,
    )

    fun state(settings: Settings = filled, latest: WeighIn? = weighIn, preset: PresetId = PresetId.Full): ProfileState =
        ProfileStateBuilder.build(settings, latest, SettingsFixtures.policy(NumbersSettings(preset = preset)), SettingsFixtures.timeZone)
}

/** Never filled in: no weigh-in, no BMI, empty fields. */
@Preview
@Composable
private fun ProfileEmptyPreview() {
    SettingsPreview { ProfileScreen(ProfileFixtures.state(Settings(), latest = null), onAction = {}) }
}

/** Filled in, All numbers: the weigh-in, BMI with its note, and body fat. */
@Preview
@Composable
private fun ProfileFilledPreview() {
    SettingsPreview { ProfileScreen(ProfileFixtures.state(), onAction = {}) }
}

/** Pounds and feet and inches, opened from Resting energy. */
@Preview
@Composable
private fun ProfileImperialDarkPreview() {
    val settings = ProfileFixtures.filled.copy(weightUnit = WeightUnit.Lb, heightUnit = HeightUnit.FtIn)
    SettingsPreview(dark = true) { ProfileScreen(ProfileFixtures.state(settings).copy(back = ProfileBack.RestingEnergy), onAction = {}) }
}

/** No numbers: the details stay, the weigh-in says when but not what, and BMI and body fat are gone. */
@Preview
@Composable
private fun ProfileBlindPreview() {
    SettingsPreview { ProfileScreen(ProfileFixtures.state(preset = PresetId.NoNumbers), onAction = {}) }
}

/** After "numbers have been hard": weight as the mode shows it, never BMI or body fat. */
@Preview
@Composable
private fun ProfileHardPreview() {
    val settings = ProfileFixtures.filled.copy(onboarding = OnboardingAnswers(hard = true))
    SettingsPreview { ProfileScreen(ProfileFixtures.state(settings, preset = PresetId.MacrosOnly), onAction = {}) }
}

@Preview
@Composable
private fun ProfileLargeTextPreview() {
    SettingsPreview(largeText = true) { ProfileScreen(ProfileFixtures.state(), onAction = {}) }
}
