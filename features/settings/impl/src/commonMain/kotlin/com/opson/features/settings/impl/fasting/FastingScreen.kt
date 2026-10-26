package com.opson.features.settings.impl.fasting

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SettingsCard
import com.opson.features.settings.impl.SettingsDefaults
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.libraries.diary.Fast
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.ui.FastingStatusCard
import com.opson.libraries.nutrition.FastingPlan
import com.opson.libraries.nutrition.NumbersSettings
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.EdgeCard
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.icon.IconTile
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.ui.time.DateCopy
import com.opson.system.AppTheme
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.fasting_not_in_mode
import opson.libraries.resources.generated.resources.fasting_not_in_mode_body
import opson.libraries.resources.generated.resources.fasting_show_on_today
import opson.libraries.resources.generated.resources.fasting_timer
import opson.libraries.resources.generated.resources.fasting_timer_body
import opson.libraries.resources.generated.resources.fasting_title
import opson.libraries.resources.generated.resources.fasting_window
import opson.libraries.resources.generated.resources.fasting_window_range
import opson.libraries.resources.generated.resources.settings_back_more
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview
import kotlin.time.Instant

/**
 * Fasting (design v3, fasting.png): the status card first, then the timer switch, the Today card
 * switch and the eating window. The Today switch and the plans are dimmed while the timer is
 * off, as the prototype has them.
 */
@Composable
fun FastingScreen(
    state: FastingState,
    onAction: (FastingAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.fasting_title),
            backLabel = stringResource(Res.string.settings_back_more),
            onBack = { onAction(FastingAction.BackTapped) },
        )
        when (val page = state.page) {
            is FastingPage.Offered -> Offered(page, onAction)
            FastingPage.NotInThisMode -> NotInThisMode()
            FastingPage.Hidden, null -> Unit
        }
    }
}

@Composable
private fun Offered(page: FastingPage.Offered, onAction: (FastingAction) -> Unit) {
    page.status?.let { FastingStatusCard(status = it, onEndFast = { onAction(FastingAction.EndFastTapped) }) }
    val dim = Modifier.alpha(if (page.timerOn) 1f else DIMMED)
    SettingsCard {
        SwitchRow(
            title = stringResource(Res.string.fasting_timer),
            body = stringResource(Res.string.fasting_timer_body),
            checked = page.timerOn,
            onToggle = { onAction(FastingAction.TimerToggled) },
        )
        HorizontalDivider(color = AppTheme.colors.line)
        SwitchRow(
            title = stringResource(Res.string.fasting_show_on_today),
            body = null,
            checked = page.showOnToday,
            enabled = page.timerOn,
            onToggle = { onAction(FastingAction.ShowOnTodayToggled) },
            modifier = dim,
        )
        HorizontalDivider(color = AppTheme.colors.line)
        Column(
            modifier = dim.fillMaxWidth().padding(vertical = AppTheme.spacing.section),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
        ) {
            Text(text = stringResource(Res.string.fasting_window), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            Text(
                text = stringResource(Res.string.fasting_window_range, page.opens.clock(), page.closes.clock()),
                typography = AppTheme.type.secondary.Tabular,
                color = AppTheme.colors.muted,
            )
            SegmentedControl(
                options = page.plans,
                selected = page.plan,
                onSelect = { onAction(FastingAction.PlanPicked(it)) },
                label = { it.key },
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    body: String?,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(vertical = AppTheme.spacing.section),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(text = title, typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
            if (body != null) Text(text = body, typography = AppTheme.type.secondary, color = AppTheme.colors.muted)
        }
        AppSwitch(checked = checked, onCheckedChange = { onToggle() }, enabled = enabled)
    }
}

@Composable
private fun NotInThisMode() {
    EdgeCard(edgeToEdge = false, contentPadding = SettingsDefaults.CardInset, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(icon = AppIcons.Schedule, tone = AppTheme.colors.nutrients.fiber)
            Text(text = stringResource(Res.string.fasting_not_in_mode), typography = AppTheme.type.cardTitle, color = AppTheme.colors.ink)
        }
        Text(text = stringResource(Res.string.fasting_not_in_mode_body), typography = AppTheme.type.body, color = AppTheme.colors.muted)
    }
}

private fun LocalTime.clock(): String = DateCopy.time(hour, minute)

/** How far the prototype fades the Today switch and the plans while the timer is off. */
private const val DIMMED = 0.45f

/** Fixed inputs for previews and the leak test, built through the real [FastingStateBuilder]. */
object FastingFixtures {
    private val zone = TimeZone.UTC
    private val evening = Instant.parse("2026-10-25T19:00:00Z")

    /** Last night's 14:10 fast, from the 7:00 PM close. */
    val lastNight = Fast(day = LocalDate(2026, 10, 25), startedAt = evening, goalHours = 14)

    /** Noon: the window is open (v3 fasting.png). */
    val noon: Instant = Instant.parse("2026-10-25T12:00:00Z")

    /** 8:40 the next morning: 13h 40m into last night's fast. */
    val morning: Instant = Instant.parse("2026-10-26T08:40:00Z")

    /** 9:00 PM, after a snack at 8:10 ended the evening's fast. */
    val night: Instant = Instant.parse("2026-10-25T21:00:00Z")

    fun settings(preset: PresetId = PresetId.Full, timerOn: Boolean = true, plan: FastingPlan = FastingPlan.FourteenTen) =
        Settings(numbers = NumbersSettings(preset = preset), fasting = timerOn, fastPlan = plan)

    fun state(settings: Settings = settings(), latest: Fast? = null, now: Instant = noon): FastingState =
        FastingStateBuilder.build(FastingInputs(settings, released = true, latest = latest, now = now, zone = zone))
}

@Preview
@Composable
private fun FastingPreview() {
    SettingsPreview { FastingScreen(FastingFixtures.state(), onAction = {}) }
}

@Preview
@Composable
private fun FastingRunningPreview() {
    SettingsPreview {
        FastingScreen(FastingFixtures.state(latest = FastingFixtures.lastNight, now = FastingFixtures.morning), onAction = {})
    }
}

@Preview
@Composable
private fun FastingFinishedPreview() {
    val ended = FastingFixtures.lastNight.copy(endedAt = Instant.parse("2026-10-25T20:10:00Z"))
    SettingsPreview { FastingScreen(FastingFixtures.state(latest = ended, now = FastingFixtures.night), onAction = {}) }
}

@Preview
@Composable
private fun FastingOffPreview() {
    SettingsPreview { FastingScreen(FastingFixtures.state(FastingFixtures.settings(timerOn = false)), onAction = {}) }
}

@Preview
@Composable
private fun FastingNotInThisModePreview() {
    SettingsPreview { FastingScreen(FastingFixtures.state(FastingFixtures.settings(preset = PresetId.Fuel)), onAction = {}) }
}

@Preview
@Composable
private fun FastingRunningDarkPreview() {
    SettingsPreview(dark = true) {
        FastingScreen(
            FastingFixtures.state(FastingFixtures.settings(preset = PresetId.Sprint), FastingFixtures.lastNight, FastingFixtures.morning),
            onAction = {},
        )
    }
}
