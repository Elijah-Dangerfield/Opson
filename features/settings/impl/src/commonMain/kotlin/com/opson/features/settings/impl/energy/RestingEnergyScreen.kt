package com.opson.features.settings.impl.energy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import com.opson.libraries.diary.ui.detail
import com.opson.libraries.diary.ui.label
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.ui.components.ChoiceRow
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SectionLabel
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsDefaults
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.SettingsRow
import com.opson.features.settings.impl.integrations.title
import com.opson.libraries.diary.Settings
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.nutrition.BodyDetail
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.Heights
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.DelayedLoadingIndicator
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.radio.RadioDot
import com.opson.libraries.ui.components.text.NumberField
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_back_energy
import opson.libraries.resources.generated.resources.settings_back_targets
import opson.libraries.resources.generated.resources.settings_resting_activity
import opson.libraries.resources.generated.resources.settings_resting_activity_none
import opson.libraries.resources.generated.resources.settings_resting_add_activity
import opson.libraries.resources.generated.resources.settings_resting_add_activity_line
import opson.libraries.resources.generated.resources.settings_resting_calculate
import opson.libraries.resources.generated.resources.settings_resting_calculate_line
import opson.libraries.resources.generated.resources.settings_resting_health
import opson.libraries.resources.generated.resources.settings_resting_health_line
import opson.libraries.resources.generated.resources.settings_resting_intro
import opson.libraries.resources.generated.resources.settings_resting_kcal_a_day
import opson.libraries.resources.generated.resources.settings_resting_own
import opson.libraries.resources.generated.resources.settings_resting_own_line
import opson.libraries.resources.generated.resources.settings_resting_everyday_measured
import opson.libraries.resources.generated.resources.settings_resting_needs_age
import opson.libraries.resources.generated.resources.settings_resting_needs_height
import opson.libraries.resources.generated.resources.settings_resting_needs_sex
import opson.libraries.resources.generated.resources.settings_resting_needs_weight
import opson.libraries.resources.generated.resources.settings_resting_profile
import opson.libraries.resources.generated.resources.settings_resting_profile_line
import opson.libraries.resources.generated.resources.settings_resting_profile_inputs
import opson.libraries.resources.generated.resources.settings_resting_profile_inputs_no_weight
import opson.libraries.resources.generated.resources.settings_resting_profile_needs
import opson.libraries.resources.generated.resources.settings_resting_profile_unsaid
import opson.libraries.resources.generated.resources.settings_resting_sex_female
import opson.libraries.resources.generated.resources.settings_resting_sex_male
import opson.libraries.resources.generated.resources.settings_resting_steps_workouts
import opson.libraries.resources.generated.resources.energy_tef_switch
import opson.libraries.resources.generated.resources.settings_resting_thermic_line
import opson.libraries.resources.generated.resources.settings_resting_title
import opson.libraries.resources.generated.resources.settings_loading_cd
import opson.libraries.resources.generated.resources.everyday_activity_body
import opson.libraries.resources.generated.resources.everyday_activity_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
fun RestingEnergyScreen(
    state: RestingEnergyState,
    onAction: (RestingEnergyAction) -> Unit,
    modifier: Modifier = Modifier,
    fromEnergyToday: Boolean = false,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.settings_resting_title),
            backLabel = stringResource(if (fromEnergyToday) Res.string.settings_back_energy else Res.string.settings_back_targets),
            onBack = { onAction(RestingEnergyAction.BackTapped) },
        )
        Row(verticalAlignment = Alignment.Top, modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = stringResource(Res.string.settings_resting_intro),
                typography = AppTheme.type.body,
                color = AppTheme.colors.ink,
                modifier = Modifier.weight(1f).padding(top = 8.dp),
            )
            EnergyInfoButton(onClick = { onAction(RestingEnergyAction.InfoTapped) })
        }
        if (!state.loaded) {
            // The health app's resting energy is read before the choices can draw.
            DelayedLoadingIndicator(contentDescription = stringResource(Res.string.settings_loading_cd))
            return@SettingsPage
        }
        Sources(state, onAction)
        SectionLabel(stringResource(Res.string.settings_resting_activity))
        SettingsCard {
            SettingsRow(
                title = stringResource(Res.string.settings_resting_steps_workouts),
                icon = null,
                subtitle = state.activitySource?.let { stringResource(it.title) } ?: stringResource(Res.string.settings_resting_activity_none),
                chevron = true,
                showDivider = true,
                onClick = { onAction(RestingEnergyAction.ActivityTapped) },
            )
            SettingsRow(
                title = stringResource(Res.string.settings_resting_add_activity),
                icon = null,
                subtitle = stringResource(Res.string.settings_resting_add_activity_line),
                showDivider = true,
                trailing = {
                    AppSwitch(
                        checked = state.activityInBurned,
                        onCheckedChange = { onAction(RestingEnergyAction.ActivityInBurnedToggled(it)) },
                    )
                },
            )
            // Not activity, so it sits beside the switch rather than under it: turning activity off leaves it on.
            // Named for what it is (owner feedback, 2026-10-30), with an (i) that explains it.
            SettingsRow(
                title = stringResource(Res.string.energy_tef_switch),
                icon = null,
                subtitle = stringResource(Res.string.settings_resting_thermic_line),
                trailing = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        EnergyInfoButton(onClick = { onAction(RestingEnergyAction.DigestingInfoTapped) })
                        AppSwitch(
                            checked = state.thermicEffectInBurned,
                            onCheckedChange = { onAction(RestingEnergyAction.ThermicEffectToggled(it)) },
                        )
                    }
                },
            )
        }
        Everyday(state, onAction)
    }
}

/**
 * Everyday activity: one of four, each with what it looks like. Targets and onboarding pick the
 * same setting. While a health app counts the day's activity the pick changes nothing, so the
 * page says why it doesn't ask instead.
 */
@Composable
private fun Everyday(state: RestingEnergyState, onAction: (RestingEnergyAction) -> Unit) {
    SectionLabel(stringResource(Res.string.everyday_activity_title))
    val measuring = state.activitySource
    if (!state.asksEveryday && measuring != null) {
        Text(
            text = stringResource(Res.string.settings_resting_everyday_measured, stringResource(measuring.title)),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        return
    }
    val picked = state.everyday
    Text(
        text = stringResource(Res.string.everyday_activity_body),
        typography = AppTheme.type.secondary,
        color = AppTheme.colors.muted,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
    Column(modifier = Modifier.selectableGroup(), verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        EverydayActivity.entries.forEach { activity ->
            ChoiceRow(
                label = stringResource(activity.label),
                detail = stringResource(activity.detail),
                selected = activity == picked,
                onClick = { onAction(RestingEnergyAction.EverydayPicked(activity)) },
                role = Role.RadioButton,
            )
        }
    }
}

@Composable
private fun Sources(state: RestingEnergyState, onAction: (RestingEnergyAction) -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = PaddingValues(horizontal = SettingsDefaults.CardPadding), verticalArrangement = Arrangement.Top) {
        Column(modifier = Modifier.selectableGroup()) {
            Source(
                title = stringResource(Res.string.settings_resting_calculate),
                line = stringResource(Res.string.settings_resting_calculate_line),
                amount = state.calculated,
                selected = state.source == RestingSource.Calculate,
                onClick = { onAction(RestingEnergyAction.SourcePicked(RestingSource.Calculate)) },
            )
            state.profileMissing?.let { missing -> ProfileLink(missing, state.profileInputs, onAction) }
            state.health?.let { health ->
                HorizontalDivider(color = AppTheme.colors.line)
                Source(
                    title = stringResource(Res.string.settings_resting_health, stringResource(health.title)),
                    line = stringResource(Res.string.settings_resting_health_line),
                    amount = state.healthKcal,
                    selected = state.source == RestingSource.Health,
                    onClick = { onAction(RestingEnergyAction.SourcePicked(RestingSource.Health)) },
                )
            }
            HorizontalDivider(color = AppTheme.colors.line)
            Source(
                title = stringResource(Res.string.settings_resting_own),
                line = stringResource(Res.string.settings_resting_own_line),
                amount = Display.Hidden,
                selected = state.source == RestingSource.Own,
                onClick = { onAction(RestingEnergyAction.SourcePicked(RestingSource.Own)) },
            )
            state.ownText?.let { text ->
                NumberField(
                    value = text,
                    onValueChange = { onAction(RestingEnergyAction.OwnTyped(it)) },
                    unit = stringResource(Res.string.settings_resting_kcal_a_day),
                    allowDecimal = false,
                    modifier = Modifier.fillMaxWidth().padding(bottom = SettingsDefaults.CardPadding),
                )
            }
        }
    }
}

@Composable
private fun Source(title: String, line: String, amount: Display, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = SettingsDefaults.RowMinHeight)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            Text(text = line, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        NutrientText(metric = Metric.EnergyOut, display = amount, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink)
        RadioDot(selected = selected)
    }
}

/**
 * Under "Calculate it for me": the Profile it reads, with what it still needs from there or, once
 * nothing is missing, exactly what it was given ("Male, 28, 5 ft 11 in, 170 lb"), so a weight
 * logged in the wrong unit shows up here rather than only as a strange number.
 */
@Composable
private fun ProfileLink(missing: Set<BodyDetail>, inputs: ProfileInputs?, onAction: (RestingEnergyAction) -> Unit) {
    val needs = missing.sortedBy { it.ordinal }.map { stringResource(it.needs) }
    SettingsRow(
        title = stringResource(Res.string.settings_resting_profile),
        icon = null,
        subtitle = when {
            needs.isNotEmpty() -> stringResource(Res.string.settings_resting_profile_needs, needs.joinToString(", "))
            inputs != null -> inputs.text()
            else -> stringResource(Res.string.settings_resting_profile_line)
        },
        chevron = true,
        onClick = { onAction(RestingEnergyAction.ProfileTapped) },
    )
}

@Composable
private fun ProfileInputs.text(): String {
    val sex = stringResource(
        when (sex) {
            BodySex.Male -> Res.string.settings_resting_sex_male
            BodySex.Female -> Res.string.settings_resting_sex_female
            BodySex.Unsaid -> Res.string.settings_resting_profile_unsaid
        },
    )
    val height = heightText(heightCm, heightUnit)
    return when (val shown = weight) {
        is Display.Shown -> stringResource(Res.string.settings_resting_profile_inputs, sex, age, height, shown.text)
        else -> stringResource(Res.string.settings_resting_profile_inputs_no_weight, sex, age, height)
    }
}

private val BodyDetail.needs: StringResource
    get() = when (this) {
        BodyDetail.Weight -> Res.string.settings_resting_needs_weight
        BodyDetail.Height -> Res.string.settings_resting_needs_height
        BodyDetail.Age -> Res.string.settings_resting_needs_age
        BodyDetail.Sex -> Res.string.settings_resting_needs_sex
    }

/** 5 ft 11 in, born 1998, male, weighing in at 170 lb: what the inputs line reads back. */
private val previewProfile = Settings(
    heightCm = Heights.cm(5, 11.0),
    heightUnit = HeightUnit.FtIn,
    birthYear = 1998,
    bodySex = BodySex.Male,
    weightUnit = WeightUnit.Lb,
)

private fun previewState(
    settings: Settings = previewProfile,
    choices: RestingChoices = RestingChoices(1760, emptySet(), 1840, HealthSource.HealthConnect, 77.11),
    preset: PresetId = PresetId.Full,
) = RestingEnergyStateBuilder.build(
    settings,
    choices,
    SettingsFixtures.policy(NumbersSettings(preset = preset)),
    activitySource = HealthSource.HealthConnect,
    today = SettingsFixtures.today,
)

@Preview
@Composable
private fun RestingEnergyPreview() {
    SettingsPreview { RestingEnergyScreen(previewState(), onAction = {}) }
}

/** No health app, and the profile still short of three details: the row says which, and Everyday activity is asked. */
@Preview
@Composable
private fun RestingEnergyNeedsDetailsPreview() {
    val choices = RestingChoices(null, setOf(BodyDetail.Height, BodyDetail.Age, BodyDetail.Sex), null, null, 72.0)
    SettingsPreview { RestingEnergyScreen(previewState(choices = choices).copy(activitySource = null), onAction = {}) }
}

/** Apple Health counts the day's activity: no everyday activity question, and the line says why. */
@Preview
@Composable
private fun RestingEnergyHealthSourcePreview() {
    val choices = RestingChoices(1610, emptySet(), 1560, HealthSource.AppleHealth, 72.0)
    val state = RestingEnergyStateBuilder.build(
        Settings(restingSource = RestingSource.Health),
        choices,
        SettingsFixtures.policy(NumbersSettings(preset = PresetId.Full)),
        activitySource = HealthSource.AppleHealth,
        today = SettingsFixtures.today,
    )
    SettingsPreview { RestingEnergyScreen(state, onAction = {}) }
}

/** No numbers: the choices without amounts, and the inputs line naming the weigh-in rather than the weight. */
@Preview
@Composable
private fun RestingEnergyNoNumbersPreview() {
    SettingsPreview { RestingEnergyScreen(previewState(preset = PresetId.NoNumbers), onAction = {}) }
}

@Preview
@Composable
private fun RestingEnergyOwnDarkPreview() {
    SettingsPreview(dark = true) {
        RestingEnergyScreen(previewState(settings = Settings(restingSource = RestingSource.Own, restingOwnKcal = 1600)), onAction = {})
    }
}

/** Health Connect connected but "Add activity to Burned" off: the everyday baseline counts again, so it is asked. */
@Preview
@Composable
private fun RestingEnergyEverydayPreview() {
    SettingsPreview {
        RestingEnergyScreen(previewState().copy(activityInBurned = false, everyday = EverydayActivity.SomeWalking), onAction = {})
    }
}

@Preview
@Composable
private fun RestingEnergyLargeTextPreview() {
    SettingsPreview(largeText = true) { RestingEnergyScreen(previewState(), onAction = {}) }
}
