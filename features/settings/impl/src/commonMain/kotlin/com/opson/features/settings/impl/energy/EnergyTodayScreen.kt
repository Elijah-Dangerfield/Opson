package com.opson.features.settings.impl.energy

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SettingsDefaults
import com.opson.features.settings.impl.SettingsFixtures
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.features.settings.impl.integrations.title
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.health.HealthSource
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.ui.NutrientVisible
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.RestingFrom
import com.opson.libraries.nutrition.ui.NutrientPhrase
import com.opson.libraries.nutrition.ui.NutrientText
import com.opson.libraries.ui.bounceClick
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.ListRowChevron
import com.opson.libraries.ui.components.RangeBar
import com.opson.libraries.ui.components.RangeBarDefaults
import com.opson.libraries.ui.components.RangeBarSegment
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.system.color.ColorResource
import com.opson.system.AppTheme
import com.opson.system.background
import com.opson.system.thenIf
import kotlinx.datetime.LocalDate
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_back_today
import opson.libraries.resources.generated.resources.settings_energy_burned
import opson.libraries.resources.generated.resources.settings_energy_burned_unknown
import opson.libraries.resources.generated.resources.settings_energy_deficit_so_far
import opson.libraries.resources.generated.resources.settings_energy_eaten
import opson.libraries.resources.generated.resources.settings_energy_estimates
import opson.libraries.resources.generated.resources.settings_energy_kcal
import opson.libraries.resources.generated.resources.settings_energy_likely
import opson.libraries.resources.generated.resources.settings_energy_logged
import opson.libraries.resources.generated.resources.settings_energy_movement
import opson.libraries.resources.generated.resources.today_burned_everyday
import opson.libraries.resources.generated.resources.today_burned_exercise
import opson.libraries.resources.generated.resources.settings_energy_no_activity
import opson.libraries.resources.generated.resources.settings_energy_not_counted
import opson.libraries.resources.generated.resources.settings_energy_nutrients
import opson.libraries.resources.generated.resources.settings_energy_plus_minus
import opson.libraries.resources.generated.resources.settings_energy_plus_minus_cd
import opson.libraries.resources.generated.resources.settings_energy_resting
import opson.libraries.resources.generated.resources.settings_energy_resting_calculated
import opson.libraries.resources.generated.resources.settings_energy_resting_health
import opson.libraries.resources.generated.resources.settings_energy_resting_own
import opson.libraries.resources.generated.resources.settings_energy_surplus_so_far
import opson.libraries.resources.generated.resources.settings_energy_title
import opson.libraries.resources.generated.resources.settings_energy_to_goal
import opson.libraries.resources.generated.resources.settings_energy_to_range
import opson.libraries.resources.generated.resources.settings_energy_workouts
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Instant

@Composable
fun EnergyTodayScreen(
    state: EnergyTodayState,
    onAction: (EnergyTodayAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.settings_energy_title),
            backLabel = stringResource(Res.string.settings_back_today),
            onBack = { onAction(EnergyTodayAction.BackTapped) },
        )
        if (!state.loaded) return@SettingsPage
        state.eaten?.let { EatenCard(it, onAction) }
        state.burned?.let { BurnedCardView(it, onAction) }
        if (state.balance != null || state.toGoal != null) BalanceCard(state.balance, state.toGoal)
    }
}

@Composable
private fun EatenCard(eaten: EatenSection, onAction: (EnergyTodayAction) -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = SettingsDefaults.CardInset, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Label(stringResource(Res.string.settings_energy_eaten))
        Row(verticalAlignment = Alignment.CenterVertically) {
            BigAmount(Metric.Calories, eaten.amount, modifier = Modifier.weight(1f))
            if (eaten.plusMinus is Display.Shown) {
                val description = stringResource(Res.string.settings_energy_plus_minus_cd, eaten.plusMinus.text)
                Box(
                    modifier = Modifier
                        .clip(AppTheme.shapes.input)
                        .background(AppTheme.colors.soft)
                        .semantics { contentDescription = description }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    NutrientPhrase(
                        metric = Metric.Calories,
                        display = eaten.plusMinus,
                        phrase = { stringResource(Res.string.settings_energy_plus_minus, it) },
                        typography = AppTheme.type.bodyBold.Tabular,
                        color = AppTheme.colors.ink,
                    )
                }
            }
        }
        RangeBar(
            fill = (eaten.amount as? Display.Shown)?.fraction ?: 0f,
            rangeStart = eaten.rangeStart,
            rangeEnd = eaten.rangeEnd,
            colors = RangeBarDefaults.summary(),
            segments = eaten.parts.map { RangeBarSegment(it.weight, macroColor(it.metric)) },
        )
        if (eaten.likely is Display.Shown) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                NutrientPhrase(
                    metric = Metric.Calories,
                    display = eaten.likely,
                    phrase = { stringResource(Res.string.settings_energy_likely, it) },
                    typography = AppTheme.type.secondary.Tabular,
                    color = AppTheme.colors.muted,
                )
                if (eaten.estimates > 0) {
                    Text(
                        text = stringResource(Res.string.settings_energy_estimates, eaten.estimates, eaten.foods),
                        typography = AppTheme.type.secondary,
                        color = AppTheme.colors.muted,
                    )
                }
            }
        }
        LinkRow(stringResource(Res.string.settings_energy_nutrients)) { onAction(EnergyTodayAction.NutrientsTapped) }
    }
}

@Composable
private fun BurnedCardView(burned: BurnedSection, onAction: (EnergyTodayAction) -> Unit) {
    EdgeCard(edgeToEdge = false, contentPadding = SettingsDefaults.CardInset, verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
        Label(stringResource(Res.string.settings_energy_burned))
        if (!burned.known) {
            LinkRow(stringResource(Res.string.settings_energy_burned_unknown)) { onAction(EnergyTodayAction.RestingEnergyTapped) }
            return@EdgeCard
        }
        BigAmount(Metric.EnergyOut, burned.total)
        BurnedBar(burned.restingShare, burned.activeShare)
        val source = burned.activitySource?.let { stringResource(it.title) }
        BurnedRow(
            dot = AppTheme.colors.restingEnergy,
            title = stringResource(Res.string.settings_energy_resting),
            line = when (burned.restingFrom) {
                RestingFrom.Health -> stringResource(Res.string.settings_energy_resting_health, source.orEmpty())
                RestingFrom.Own -> stringResource(Res.string.settings_energy_resting_own)
                RestingFrom.Calculated, null -> stringResource(Res.string.settings_energy_resting_calculated)
            },
            amount = burned.resting,
            onClick = { onAction(EnergyTodayAction.RestingEnergyTapped) },
        )
        val activityLine = when {
            !burned.activityCounted -> stringResource(Res.string.settings_energy_not_counted)
            source == null -> stringResource(Res.string.settings_energy_no_activity)
            else -> source
        }
        // Everyday activity stands in for the health app's movement; a day never has both.
        if (burned.everyday is Display.Hidden) {
            HorizontalDivider(color = AppTheme.colors.line)
            BurnedRow(AppTheme.colors.activeEnergy, stringResource(Res.string.settings_energy_movement), activityLine, burned.movement)
            HorizontalDivider(color = AppTheme.colors.line)
            BurnedRow(AppTheme.colors.activeEnergy, stringResource(Res.string.settings_energy_workouts), activityLine, burned.workouts)
        } else {
            HorizontalDivider(color = AppTheme.colors.line)
            BurnedRow(AppTheme.colors.activeEnergy, stringResource(Res.string.today_burned_everyday), activityLine, burned.everyday)
        }
        NutrientVisible(burned.exercise) {
            HorizontalDivider(color = AppTheme.colors.line)
            BurnedRow(AppTheme.colors.activeEnergy, stringResource(Res.string.today_burned_exercise), stringResource(Res.string.settings_energy_logged), burned.exercise)
        }
    }
}

/** Resting and active energy end to end, each in its own colour, on the eaten bar's scale. */
@Composable
private fun BurnedBar(resting: Float, active: Float) {
    val track = AppTheme.colors.track.color
    val restingColor = AppTheme.colors.restingEnergy.color
    val activeColor = AppTheme.colors.activeEnergy.color
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(BAR_HEIGHT)
            .drawBehind {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, size = size, cornerRadius = radius)
                val restingWidth = size.width * resting
                if (restingWidth > 0f) drawRoundRect(restingColor, size = Size(restingWidth, size.height), cornerRadius = radius)
                val activeWidth = size.width * active
                if (activeWidth > 0f) {
                    drawRoundRect(
                        activeColor,
                        topLeft = Offset(restingWidth + GAP_PX, 0f),
                        size = Size((activeWidth - GAP_PX).coerceAtLeast(0f), size.height),
                        cornerRadius = radius,
                    )
                }
            },
    )
}

@Composable
private fun BurnedRow(dot: ColorResource, title: String, line: String, amount: Display, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .thenIf(onClick != null) { bounceClick(scaleDown = 0.98f, onClick = onClick!!) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        Box(Modifier.size(12.dp).clip(AppTheme.shapes.pill).background(dot))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            Text(text = line, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        NutrientText(metric = Metric.EnergyOut, display = amount, typography = AppTheme.type.bodyBold.Tabular, color = AppTheme.colors.ink)
        if (onClick != null) ListRowChevron()
    }
}

@Composable
private fun BalanceCard(balance: Balance?, toGoal: ToGoal?) {
    EdgeCard(edgeToEdge = false, contentPadding = SettingsDefaults.CardInset) {
        Row(verticalAlignment = Alignment.Bottom) {
            if (balance != null) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label(stringResource(if (balance.surplus) Res.string.settings_energy_surplus_so_far else Res.string.settings_energy_deficit_so_far))
                    BigAmount(Metric.EnergyOut, balance.amount)
                }
            } else {
                Box(Modifier.weight(1f))
            }
            if (toGoal != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label(stringResource(if (toGoal.single) Res.string.settings_energy_to_goal else Res.string.settings_energy_to_range))
                    NutrientText(metric = toGoal.metric, display = toGoal.amount, typography = AppTheme.type.figureLarge, color = AppTheme.colors.ink)
                }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text = text, typography = AppTheme.type.secondaryStrong, color = AppTheme.colors.muted)
}

@Composable
private fun BigAmount(metric: Metric, amount: Display, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        NutrientText(metric = metric, display = amount, typography = AppTheme.type.h1.Tabular, color = AppTheme.colors.ink)
        if (amount is Display.Shown) {
            Text(
                text = stringResource(Res.string.settings_energy_kcal),
                typography = AppTheme.type.bodyBold,
                color = AppTheme.colors.muted,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
    }
}

@Composable
private fun LinkRow(text: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().bounceClick(scaleDown = 0.98f, onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink, modifier = Modifier.weight(1f))
        ListRowChevron()
    }
}

@Composable
private fun macroColor(metric: Metric): ColorResource {
    val nutrients = AppTheme.colors.nutrients
    return when (metric) {
        Metric.Protein -> nutrients.protein
        Metric.Carbs -> nutrients.carbs
        else -> nutrients.fat
    }.content
}

private val BAR_HEIGHT = 12.dp
private const val GAP_PX = 3f

/** Design sync 4's day: 1,245 eaten across seven foods, five of them estimates. */
internal object EnergyTodayFixtures {
    val today = LocalDate(2026, 10, 27)

    private fun item(id: String, slot: MealSlot, kcal: Double, precision: Precision = Precision.Estimate, method: LogMethod = LogMethod.Search) = LogItem(
        id = id,
        day = today,
        slot = slot,
        loggedAt = Instant.parse("2026-10-27T12:00:00Z"),
        name = id,
        precision = precision,
        method = method,
        kcal = kcal,
        protein = kcal * 0.05,
        carbs = kcal * 0.13,
        fat = kcal * 0.03,
    )

    val day = Day(
        date = today,
        itemsBySlot = MealSlot.entries.associateWith { emptyList<LogItem>() } + mapOf(
            MealSlot.Breakfast to listOf(item("oats", MealSlot.Breakfast, 310.0, Precision.Weighed), item("coffee", MealSlot.Breakfast, 35.0)),
            MealSlot.Lunch to listOf(item("bar", MealSlot.Lunch, 230.0, method = LogMethod.Barcode), item("soup", MealSlot.Lunch, 280.0)),
            MealSlot.Snack to listOf(item("apple", MealSlot.Snack, 95.0)),
            MealSlot.Dinner to listOf(item("pasta", MealSlot.Dinner, 220.0), item("salad", MealSlot.Dinner, 75.0)),
        ),
    )

    fun inputs(settings: Settings = SettingsFixtures.settings(), burned: Boolean = true) = EnergyTodayInputs(
        today = today,
        day = day,
        settings = settings,
        burned = SettingsFixtures.burned.takeIf { burned },
        activitySource = HealthSource.HealthConnect,
    )

    fun state(preset: PresetId = PresetId.Full, settings: Settings = SettingsFixtures.settings(), burned: Boolean = true) =
        EnergyTodayStateBuilder.build(inputs(settings, burned), SettingsFixtures.policy(NumbersSettings(preset = preset)))
}

@Preview
@Composable
private fun EnergyTodayPreview() {
    SettingsPreview { EnergyTodayScreen(EnergyTodayFixtures.state(), onAction = {}) }
}

@Preview
@Composable
private fun EnergyTodayNoBurnedDarkPreview() {
    SettingsPreview(dark = true) { EnergyTodayScreen(EnergyTodayFixtures.state(burned = false), onAction = {}) }
}

@Preview
@Composable
private fun EnergyTodayLargeTextPreview() {
    SettingsPreview(largeText = true) { EnergyTodayScreen(EnergyTodayFixtures.state(), onAction = {}) }
}
