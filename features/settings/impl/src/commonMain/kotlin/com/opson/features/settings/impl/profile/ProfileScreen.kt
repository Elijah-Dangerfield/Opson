package com.opson.features.settings.impl.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.semantics.Role
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
import com.opson.libraries.diary.ui.BirthdayField
import com.opson.libraries.diary.ui.Birthdays
import com.opson.libraries.diary.ui.HeightField
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.note
import com.opson.libraries.nutrition.SexChoice
import com.opson.libraries.ui.components.ChoiceRow
import kotlinx.datetime.LocalDate
import com.opson.libraries.health.HealthSource
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.BodyStage
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.button.ButtonGhost
import com.opson.libraries.ui.components.button.ButtonSize
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atTime
import kotlinx.datetime.toInstant
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.integrations_apple_health
import opson.libraries.resources.generated.resources.integrations_health_connect
import opson.libraries.resources.generated.resources.integrations_profile_fill
import opson.libraries.resources.generated.resources.integrations_profile_fill_note_apple_health
import opson.libraries.resources.generated.resources.integrations_profile_fill_note_health_connect
import opson.libraries.resources.generated.resources.plan_sex_inclusive
import opson.libraries.resources.generated.resources.profile_birthday
import opson.libraries.resources.generated.resources.settings_back_micros
import opson.libraries.resources.generated.resources.settings_back_plain
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
        state.fillFrom?.let { FillFromHealth(it, state.filling, onAction) }
        SectionLabel(stringResource(Res.string.settings_profile_body))
        Body(state, onAction)
    }
}

@Composable
private fun AboutYou(state: ProfileState, onAction: (ProfileAction) -> Unit) {
    EdgeCard(edgeToEdge = false, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        BirthdayField(
            parts = state.birthday,
            onChange = { onAction(ProfileAction.BirthdayTyped(it)) },
            today = state.today,
            ages = Birthdays.EQUATION_AGES,
            label = stringResource(Res.string.profile_birthday),
            imeAction = ImeAction.Next,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FieldLabel(stringResource(Res.string.settings_profile_sex))
            SexChoice.entries.forEach { choice ->
                ChoiceRow(
                    label = stringResource(choice.label),
                    selected = state.sex == choice,
                    onClick = { onAction(ProfileAction.SexPicked(choice)) },
                    role = Role.RadioButton,
                )
            }
            Text(
                text = stringResource(state.sex?.note ?: Res.string.settings_profile_sex_body),
                typography = AppTheme.type.secondarySmall,
                color = AppTheme.colors.muted,
            )
            Text(
                text = stringResource(Res.string.plan_sex_inclusive),
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
private fun FieldLabel(text: String) {
    Text(text = text, typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.muted)
}

/** "Fill in from Apple Health", under the details it fills, and what it uses. */
@Composable
private fun FillFromHealth(source: HealthSource, filling: Boolean, onAction: (ProfileAction) -> Unit) {
    val apple = source == HealthSource.AppleHealth
    val name = stringResource(if (apple) Res.string.integrations_apple_health else Res.string.integrations_health_connect)
    Column(Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ButtonGhost(onClick = { onAction(ProfileAction.FillFromHealthTapped) }, size = ButtonSize.Small, enabled = !filling) {
            Text(stringResource(Res.string.integrations_profile_fill, name))
        }
        Text(
            text = stringResource(if (apple) Res.string.integrations_profile_fill_note_apple_health else Res.string.integrations_profile_fill_note_health_connect),
            typography = AppTheme.type.secondarySmall,
            color = AppTheme.colors.muted,
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
        ProfileBack.Elsewhere -> Res.string.settings_back_plain
    }

/** Previews and the leak test: everything filled in, and a weigh-in on the fixtures' today. */
internal object ProfileFixtures {
    val filled = Settings(birthYear = 1990, heightCm = 168.0, heightUnit = HeightUnit.Cm, bodySex = BodySex.Female, bodyFatPercent = 24.0)
    val weighIn = WeighIn(
        id = "w1",
        measuredAt = SettingsFixtures.today.atTime(LocalTime(7, 30)).toInstant(TimeZone.UTC),
        kg = 64.2,
    )

    fun state(settings: Settings = filled, latest: WeighIn? = weighIn, preset: PresetId = PresetId.Full, fillFrom: HealthSource? = null): ProfileState =
        ProfileStateBuilder.build(settings, latest, SettingsFixtures.policy(NumbersSettings(preset = preset)), SettingsFixtures.timeZone, fillFrom = fillFrom)
}

/** Never filled in: no weigh-in, no BMI, empty fields. */
@Preview
@Composable
private fun ProfileEmptyPreview() {
    SettingsPreview { ProfileScreen(ProfileFixtures.state(Settings(), latest = null), onAction = {}) }
}

/** Never filled in, with Apple Health on the phone: "Fill in from Apple Health" under About you. */
@Preview
@Composable
private fun ProfileFillFromHealthPreview() {
    SettingsPreview { ProfileScreen(ProfileFixtures.state(Settings(), latest = null, fillFrom = HealthSource.AppleHealth), onAction = {}) }
}

@Preview
@Composable
private fun ProfileFillFromHealthConnectDarkPreview() {
    SettingsPreview(dark = true) { ProfileScreen(ProfileFixtures.state(fillFrom = HealthSource.HealthConnect), onAction = {}) }
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

/** A whole birthday, and pregnant: the note says it only adds that energy and keeps weight change out of the plan. */
@Preview
@Composable
private fun ProfileBirthdayPregnantPreview() {
    val settings = ProfileFixtures.filled.withBirthDate(LocalDate(1992, 4, 18)).copy(bodyStage = BodyStage.Pregnant)
    SettingsPreview { ProfileScreen(ProfileFixtures.state(settings), onAction = {}) }
}

@Preview
@Composable
private fun ProfileLargeTextPreview() {
    SettingsPreview(largeText = true) { ProfileScreen(ProfileFixtures.state(), onAction = {}) }
}
