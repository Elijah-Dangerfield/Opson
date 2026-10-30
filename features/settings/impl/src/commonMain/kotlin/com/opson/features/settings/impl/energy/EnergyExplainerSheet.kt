package com.opson.features.settings.impl.energy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.integrations.title
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.Burned
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.Heights
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingEnergy
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.Icon
import com.opson.libraries.ui.components.icon.IconButton
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.minTouchTarget
import com.opson.system.AppTheme
import com.opson.system.background
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.energy_digesting
import opson.libraries.resources.generated.resources.energy_info_balance_body
import opson.libraries.resources.generated.resources.energy_info_balance_burned
import opson.libraries.resources.generated.resources.energy_info_balance_deficit
import opson.libraries.resources.generated.resources.energy_info_balance_goal
import opson.libraries.resources.generated.resources.energy_info_balance_held
import opson.libraries.resources.generated.resources.energy_info_balance_moves
import opson.libraries.resources.generated.resources.energy_info_balance_rough
import opson.libraries.resources.generated.resources.energy_info_balance_rule_kg
import opson.libraries.resources.generated.resources.energy_info_balance_rule_lb
import opson.libraries.resources.generated.resources.energy_info_balance_surplus
import opson.libraries.resources.generated.resources.energy_info_balance_title
import opson.libraries.resources.generated.resources.energy_info_balance_working
import opson.libraries.resources.generated.resources.energy_info_burned_body
import opson.libraries.resources.generated.resources.energy_info_burned_estimate
import opson.libraries.resources.generated.resources.energy_info_burned_logged
import opson.libraries.resources.generated.resources.energy_info_burned_title
import opson.libraries.resources.generated.resources.energy_info_burned_working
import opson.libraries.resources.generated.resources.energy_info_cd
import opson.libraries.resources.generated.resources.energy_info_done
import opson.libraries.resources.generated.resources.energy_info_minus
import opson.libraries.resources.generated.resources.energy_info_part_digesting_line
import opson.libraries.resources.generated.resources.energy_info_part_everyday_line
import opson.libraries.resources.generated.resources.energy_info_part_exercise_line
import opson.libraries.resources.generated.resources.energy_info_part_resting_line
import opson.libraries.resources.generated.resources.energy_info_plus
import opson.libraries.resources.generated.resources.energy_info_profile
import opson.libraries.resources.generated.resources.energy_info_resting_age
import opson.libraries.resources.generated.resources.energy_info_resting_body
import opson.libraries.resources.generated.resources.energy_info_resting_female
import opson.libraries.resources.generated.resources.energy_info_resting_height
import opson.libraries.resources.generated.resources.energy_info_resting_how
import opson.libraries.resources.generated.resources.energy_info_resting_in_use_health
import opson.libraries.resources.generated.resources.energy_info_resting_in_use_own
import opson.libraries.resources.generated.resources.energy_info_resting_male
import opson.libraries.resources.generated.resources.energy_info_resting_rounded
import opson.libraries.resources.generated.resources.energy_info_resting_source
import opson.libraries.resources.generated.resources.energy_info_resting_unsaid
import opson.libraries.resources.generated.resources.energy_info_resting_weight
import opson.libraries.resources.generated.resources.energy_info_resting_working
import opson.libraries.resources.generated.resources.energy_info_watch_body
import opson.libraries.resources.generated.resources.energy_info_watch_title
import opson.libraries.resources.generated.resources.settings_energy_resting
import opson.libraries.resources.generated.resources.settings_height_cm
import opson.libraries.resources.generated.resources.settings_height_ft_in
import opson.libraries.resources.generated.resources.today_burned_everyday
import opson.libraries.resources.generated.resources.today_burned_exercise
import opson.libraries.resources.generated.resources.energy_info_burned_yours
import opson.libraries.resources.generated.resources.energy_info_part_health_line
import opson.libraries.resources.generated.resources.energy_info_part_exercise_health
import opson.libraries.resources.generated.resources.energy_info_part_estimate_line
import opson.libraries.resources.generated.resources.energy_info_part_exercise_logged
import opson.libraries.resources.generated.resources.energy_info_activity_off
import opson.libraries.resources.generated.resources.energy_tef_short
import opson.libraries.resources.generated.resources.energy_info_tef_off
import opson.libraries.resources.generated.resources.energy_info_tef_link
import opson.libraries.resources.generated.resources.energy_tef_title
import opson.libraries.resources.generated.resources.energy_info_tef_body
import opson.libraries.resources.generated.resources.energy_info_tef_share
import opson.libraries.resources.generated.resources.energy_info_tef_optional
import opson.libraries.resources.generated.resources.energy_info_tef_burned
import opson.libraries.resources.generated.resources.energy_info_tef_burned_plain
import opson.libraries.resources.generated.resources.energy_info_tef_today
import opson.libraries.resources.generated.resources.energy_tef_switch
import opson.libraries.resources.generated.resources.energy_tef_switch_line
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * One (i) sheet: the word, what it means, how Opson works it out, and the person's own working
 * where [state] carries it. Skippable by design: plain paragraphs, the numbers last, and Done.
 */
@Composable
fun EnergyExplainerSheetContent(state: EnergyExplainerState, onAction: (EnergyExplainerAction) -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = 10.dp, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        SheetHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
        when (state.topic) {
            EnergyTopic.Resting -> RestingTopic(state, onAction)
            EnergyTopic.Burned -> BurnedTopic(state, onAction)
            EnergyTopic.Balance -> BalanceTopic(state)
            EnergyTopic.Digesting -> DigestingTopic(state, onAction)
        }
        FlatButton(
            text = stringResource(Res.string.energy_info_done),
            onClick = { onAction(EnergyExplainerAction.DoneTapped) },
            centered = true,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        )
    }
}

@Composable
private fun RestingTopic(state: EnergyExplainerState, onAction: (EnergyExplainerAction) -> Unit) {
    Title(stringResource(Res.string.settings_energy_resting))
    Paragraph(stringResource(Res.string.energy_info_resting_body))
    Paragraph(stringResource(Res.string.energy_info_resting_how))
    state.working?.let { working ->
        Heading(stringResource(Res.string.energy_info_resting_working))
        Working {
            val weight = (working.weight as? Display.Shown)?.text.orEmpty()
            Term(stringResource(Res.string.energy_info_resting_weight, weight), working.weightTerm, adds = true)
            Term(stringResource(Res.string.energy_info_resting_height, working.heightCm), working.heightTerm, adds = true)
            Term(stringResource(Res.string.energy_info_resting_age, working.age), working.ageTerm, adds = false)
            Term(stringResource(working.sex.term), working.sexTerm, adds = working.sexAdds)
            HorizontalDivider(color = AppTheme.colors.line)
            Total(stringResource(Res.string.settings_energy_resting), working.total)
        }
        Note(stringResource(Res.string.energy_info_resting_rounded))
    }
    state.inUse?.let { inUse ->
        NutrientPhrase(
            metric = Metric.EnergyOut,
            display = inUse.amount,
            phrase = { amount ->
                val source = inUse.source
                if (inUse.from == RestingFrom.Health && source != null) {
                    stringResource(Res.string.energy_info_resting_in_use_health, stringResource(source.title), amount)
                } else {
                    stringResource(Res.string.energy_info_resting_in_use_own, amount)
                }
            },
            typography = AppTheme.type.body.Tabular,
            color = AppTheme.colors.ink,
        )
    }
    Heading(stringResource(Res.string.energy_info_watch_title))
    Paragraph(stringResource(Res.string.energy_info_watch_body))
    FlatButton(
        text = stringResource(Res.string.energy_info_profile),
        onClick = { onAction(EnergyExplainerAction.ProfileTapped) },
        tone = FlatButtonTone.Outlined,
        centered = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BurnedTopic(state: EnergyExplainerState, onAction: (EnergyExplainerAction) -> Unit) {
    Title(stringResource(Res.string.energy_info_burned_title))
    Paragraph(stringResource(Res.string.energy_info_burned_yours))
    // Only the parts this person's Burned has: the app, the estimate, or neither, and digesting food once it is on.
    val health = state.parts.health
    Part(stringResource(Res.string.settings_energy_resting), stringResource(Res.string.energy_info_part_resting_line))
    if (health != null && state.parts.activityCounted) {
        Part(stringResource(Res.string.today_burned_everyday), stringResource(Res.string.energy_info_part_health_line, stringResource(health.title)))
        Part(stringResource(Res.string.today_burned_exercise), stringResource(Res.string.energy_info_part_exercise_health, stringResource(health.title)))
    } else if (health == null) {
        Part(stringResource(Res.string.today_burned_everyday), stringResource(Res.string.energy_info_part_estimate_line))
        if (state.parts.activityCounted) Part(stringResource(Res.string.today_burned_exercise), stringResource(Res.string.energy_info_part_exercise_logged))
    }
    if (!state.parts.activityCounted) Note(stringResource(Res.string.energy_info_activity_off))
    // Digesting food is a tenth of what was eaten: it is not a part of Burned where calories hide.
    if (state.digesting) {
        if (state.digestingOn) {
            Part(stringResource(Res.string.energy_tef_short), stringResource(Res.string.energy_info_part_digesting_line))
        } else {
            Paragraph(stringResource(Res.string.energy_info_tef_off))
        }
        DigestingSwitch(state.digestingOn, onAction)
        EnergyInfoLink(stringResource(Res.string.energy_info_tef_link), onClick = { onAction(EnergyExplainerAction.DigestingInfoTapped) })
    }
    state.burned?.let { burned ->
        Heading(stringResource(Res.string.energy_info_burned_working))
        Working {
            Term(stringResource(Res.string.settings_energy_resting), burned.resting, adds = null)
            NutrientVisible(burned.everyday) { Term(stringResource(Res.string.today_burned_everyday), burned.everyday, adds = true) }
            NutrientVisible(burned.movement) { Term(stringResource(Res.string.today_burned_everyday), burned.movement, adds = true) }
            NutrientVisible(burned.workouts) { Term(stringResource(Res.string.today_burned_exercise), burned.workouts, adds = true) }
            NutrientVisible(burned.exercise) { Term(stringResource(Res.string.energy_info_burned_logged), burned.exercise, adds = true) }
            NutrientVisible(burned.food) { Term(stringResource(Res.string.energy_tef_short), burned.food, adds = true) }
            HorizontalDivider(color = AppTheme.colors.line)
            Total(stringResource(Res.string.energy_info_burned_title), burned.total)
        }
    }
    if (health == null) Note(stringResource(Res.string.energy_info_burned_estimate))
    Heading(stringResource(Res.string.energy_info_watch_title))
    Paragraph(stringResource(Res.string.energy_info_watch_body))
    FlatButton(
        text = stringResource(Res.string.energy_info_resting_source),
        onClick = { onAction(EnergyExplainerAction.RestingEnergyTapped) },
        tone = FlatButtonTone.Outlined,
        centered = true,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun BalanceTopic(state: EnergyExplainerState) {
    Title(stringResource(Res.string.energy_info_balance_title))
    Paragraph(stringResource(Res.string.energy_info_balance_body))
    // "About 3,500 calories" is a calorie amount, even a general one: only where calories show.
    if (state.ruleNumbers) {
        Paragraph(stringResource(if (state.weightUnit == WeightUnit.Lb) Res.string.energy_info_balance_rule_lb else Res.string.energy_info_balance_rule_kg))
    }
    Paragraph(stringResource(Res.string.energy_info_balance_rough))
    state.balance?.let { balance ->
        Heading(stringResource(Res.string.energy_info_balance_working))
        Working {
            Term(stringResource(Res.string.energy_info_balance_burned), balance.burned, adds = null)
            Term(
                stringResource(if (balance.surplus) Res.string.energy_info_balance_surplus else Res.string.energy_info_balance_deficit),
                balance.amount,
                adds = balance.surplus,
                metric = Metric.Calories,
            )
            HorizontalDivider(color = AppTheme.colors.line)
            Total(stringResource(Res.string.energy_info_balance_goal), balance.goal, metric = Metric.Calories)
        }
        Note(stringResource(if (balance.held) Res.string.energy_info_balance_held else Res.string.energy_info_balance_moves))
    }
}

/**
 * Digesting food, the thermic effect of food: what it is, about how much, why it is off unless
 * turned on, and what it does to Burned, with its switch right here (owner feedback, 2026-10-30:
 * "that's a nice point to educate"). The amounts only where calories show.
 */
@Composable
private fun DigestingTopic(state: EnergyExplainerState, onAction: (EnergyExplainerAction) -> Unit) {
    Title(stringResource(Res.string.energy_tef_title))
    Paragraph(stringResource(Res.string.energy_info_tef_body))
    Paragraph(stringResource(Res.string.energy_info_tef_share))
    Paragraph(stringResource(Res.string.energy_info_tef_optional))
    Paragraph(stringResource(if (state.ruleNumbers) Res.string.energy_info_tef_burned else Res.string.energy_info_tef_burned_plain))
    NutrientPhrase(
        metric = Metric.EnergyOut,
        display = state.digestingToday,
        phrase = { stringResource(Res.string.energy_info_tef_today, it) },
        typography = AppTheme.type.body.Tabular,
        color = AppTheme.colors.ink,
    )
    if (state.digesting) DigestingSwitch(state.digestingOn, onAction)
}

/** "Count digesting food (TEF)" and its switch, on the sheets that explain it. */
@Composable
private fun DigestingSwitch(on: Boolean, onAction: (EnergyExplainerAction) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(AppTheme.shapes.inputLarge)
            .background(AppTheme.colors.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = stringResource(Res.string.energy_tef_switch), typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
            Text(text = stringResource(Res.string.energy_tef_switch_line), typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        AppSwitch(checked = on, onCheckedChange = { onAction(EnergyExplainerAction.DigestingToggled(it)) })
    }
}

@Composable
private fun Title(text: String) {
    Text(text = text, typography = AppTheme.type.sheetTitle, color = AppTheme.colors.ink, modifier = Modifier.padding(top = 12.dp))
}

@Composable
private fun Heading(text: String) {
    Text(text = text, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun Paragraph(text: String) {
    Text(text = text, typography = AppTheme.type.body, color = AppTheme.colors.ink)
}

@Composable
private fun Note(text: String) {
    Text(text = text, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
}

/** One part of Burned: its name, and a line on where it comes from. */
@Composable
private fun Part(name: String, line: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = name, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink)
        Text(text = line, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
    }
}

@Composable
private fun Working(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

/** A line of the working: what it is, and its amount, signed by [adds] (null for the first line). */
@Composable
private fun Term(label: String, amount: Display, adds: Boolean?, metric: Metric = Metric.EnergyOut) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Text(text = label, typography = AppTheme.type.secondary, color = AppTheme.colors.inkSoft, modifier = Modifier.weight(1f))
        NutrientPhrase(
            metric = metric,
            display = amount,
            phrase = { text ->
                when (adds) {
                    null -> text
                    true -> stringResource(Res.string.energy_info_plus, text)
                    false -> stringResource(Res.string.energy_info_minus, text)
                }
            },
            typography = AppTheme.type.secondary.Tabular,
            color = AppTheme.colors.ink,
        )
    }
}

@Composable
private fun Total(label: String, amount: Display, metric: Metric = Metric.EnergyOut) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Text(text = label, typography = AppTheme.type.bodyBold, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        NutrientText(metric = metric, display = amount, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink)
    }
}

private val BodySex.term
    get() = when (this) {
        BodySex.Male -> Res.string.energy_info_resting_male
        BodySex.Female -> Res.string.energy_info_resting_female
        BodySex.Unsaid -> Res.string.energy_info_resting_unsaid
    }

/** The (i) beside an energy word. 44 dp to tap, the icon small and muted, so it doesn't compete with the word. */
@Composable
internal fun EnergyInfoButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val description = stringResource(Res.string.energy_info_cd)
    IconButton(
        icon = AppIcons.Info,
        onClick = onClick,
        iconColor = AppTheme.colors.muted,
        size = IconButton.Size.Small,
        modifier = modifier.semantics { contentDescription = description },
    )
}

/** "How a deficit works" with the (i) before it, where an icon on its own would float. */
@Composable
internal fun EnergyInfoLink(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .minTouchTarget()
            .bounceClick(scaleDown = 0.98f, onClick = onClick)
            .semantics(mergeDescendants = true) { role = Role.Button },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon = AppIcons.Info, size = 18.dp, color = AppTheme.colors.muted)
        Text(text = text, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.ink)
    }
}

/** A height as the person types it: "5 ft 11 in", or "180 cm". */
@Composable
internal fun heightText(cm: Double, unit: HeightUnit): String = when (unit) {
    HeightUnit.Cm -> stringResource(Res.string.settings_height_cm, cm.roundToInt())
    HeightUnit.FtIn -> Heights.feetInches(cm).let { ft ->
        val inches = if (ft.inches == floor(ft.inches)) ft.inches.toInt().toString() else ft.inches.toString()
        stringResource(Res.string.settings_height_ft_in, ft.feet, inches)
    }
}

/** The owner's profile: 5 ft 11 in, born 1998, male, 170 lb, on 2026-10-25. */
internal object EnergyExplainerFixtures {
    private val settings = SettingsFixtures.settings().copy(
        heightCm = Heights.cm(5, 11.0),
        heightUnit = HeightUnit.FtIn,
        birthYear = 1998,
        bodySex = BodySex.Male,
        weightUnit = WeightUnit.Lb,
        targets = SettingsFixtures.deficitTargets,
    )
    private val choices = RestingChoices(1760, emptySet(), 1840, HealthSource.AppleHealth, 77.11)
    private val burned = Burned(
        RestingEnergy(1760, RestingFrom.Calculated),
        everydayKcal = EverydayActivity.MostlySitting.kcalFor(1760),
        exerciseKcal = 210,
        foodKcal = 160,
    )

    /** [digesting] turns "Count digesting food" on; [health] reads activity from Apple Health. */
    fun inputs(numbers: NumbersSettings = NumbersSettings(preset = PresetId.Full), digesting: Boolean = false, health: Boolean = false) =
        EnergyExplainerInputs(
            settings.copy(numbers = numbers, thermicEffectInBurned = digesting),
            choices,
            when {
                health -> burned.copy(everydayKcal = null, movementKcal = 420, workoutKcal = 180, foodKcal = burned.foodKcal.takeIf { digesting })
                else -> burned.copy(foodKcal = burned.foodKcal.takeIf { digesting })
            },
            SettingsFixtures.today,
        )

    fun state(topic: EnergyTopic, numbers: NumbersSettings = NumbersSettings(preset = PresetId.Full), digesting: Boolean = false, health: Boolean = false) =
        EnergyExplainerStateBuilder.build(topic, inputs(numbers, digesting, health), SettingsFixtures.policy(numbers))
}

@Preview
@Composable
private fun EnergyExplainerRestingPreview() {
    SettingsPreview { EnergyExplainerSheetContent(EnergyExplainerFixtures.state(EnergyTopic.Resting), onAction = {}) }
}

/** No numbers: the same words, no working. */
@Preview
@Composable
private fun EnergyExplainerRestingNoNumbersPreview() {
    SettingsPreview { EnergyExplainerSheetContent(EnergyExplainerFixtures.state(EnergyTopic.Resting, NumbersSettings(preset = PresetId.NoNumbers)), onAction = {}) }
}

@Preview
@Composable
private fun EnergyExplainerBurnedPreview() {
    SettingsPreview { EnergyExplainerSheetContent(EnergyExplainerFixtures.state(EnergyTopic.Burned), onAction = {}) }
}

/** With digesting food on and a health app: only the parts this Burned has. */
@Preview
@Composable
private fun EnergyExplainerBurnedDigestingPreview() {
    SettingsPreview { EnergyExplainerSheetContent(EnergyExplainerFixtures.state(EnergyTopic.Burned, digesting = true, health = true), onAction = {}) }
}

@Preview
@Composable
private fun EnergyExplainerDigestingPreview() {
    SettingsPreview { EnergyExplainerSheetContent(EnergyExplainerFixtures.state(EnergyTopic.Digesting, digesting = true), onAction = {}) }
}

/** Off, and in a mode without calories: the words, no amounts, no switch. */
@Preview
@Composable
private fun EnergyExplainerDigestingNoNumbersPreview() {
    SettingsPreview {
        EnergyExplainerSheetContent(EnergyExplainerFixtures.state(EnergyTopic.Digesting, NumbersSettings(preset = PresetId.NoNumbers)), onAction = {})
    }
}

@Preview
@Composable
private fun EnergyExplainerBalanceDarkPreview() {
    SettingsPreview(dark = true) { EnergyExplainerSheetContent(EnergyExplainerFixtures.state(EnergyTopic.Balance), onAction = {}) }
}

@Preview
@Composable
private fun EnergyExplainerRestingLargeTextPreview() {
    SettingsPreview(largeText = true) { EnergyExplainerSheetContent(EnergyExplainerFixtures.state(EnergyTopic.Resting), onAction = {}) }
}
