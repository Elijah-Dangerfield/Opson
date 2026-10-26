package com.opson.features.settings.impl.widgets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.opson.features.settings.impl.SectionLabel
import com.opson.features.settings.impl.SettingsHeader
import com.opson.features.settings.impl.SettingsPage
import com.opson.features.settings.impl.SettingsPreview
import com.opson.libraries.nutrition.PresetId
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.components.text.Text
import com.opson.libraries.widgets.WidgetCard
import com.opson.libraries.widgets.WidgetFixtures
import com.opson.libraries.widgets.WidgetSize
import com.opson.libraries.widgets.WidgetSnapshotBuilder
import com.opson.libraries.widgets.rememberWidgetCopy
import com.opson.system.AppTheme
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.settings_back_more
import opson.libraries.resources.generated.resources.widgets_home_screen
import opson.libraries.resources.generated.resources.widgets_how_to_add
import opson.libraries.resources.generated.resources.widgets_intro
import opson.libraries.resources.generated.resources.widgets_title
import org.jetbrains.compose.resources.stringResource
import org.jetbrains.compose.ui.tooling.preview.Preview

/**
 * Widgets (0.2): the handoff's page under More. The person's own widget in both sizes, drawn
 * from the snapshot the real one reads, so what they see here is what their home screen shows.
 */
@Composable
fun WidgetsScreen(
    state: WidgetsState,
    onAction: (WidgetsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier = modifier) {
        SettingsHeader(
            title = stringResource(Res.string.widgets_title),
            backLabel = stringResource(Res.string.settings_back_more),
            onBack = { onAction(WidgetsAction.BackTapped) },
        )
        Text(
            text = stringResource(Res.string.widgets_intro),
            typography = AppTheme.type.body,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        SectionLabel(stringResource(Res.string.widgets_home_screen))
        val snapshot = state.snapshot
        val today = state.today
        if (snapshot != null && today != null) {
            // On the hero colour, as v3 draws them: a widget sits on a wallpaper, not on the page.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(AppTheme.shapes.card)
                    .background(AppTheme.colors.hero.color)
                    .padding(AppTheme.spacing.cardHorizontal),
                verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
            ) {
                WidgetCard(snapshot, today, WidgetSize.Medium)
                WidgetCard(snapshot, today, WidgetSize.Small)
            }
        }
        Text(
            text = stringResource(Res.string.widgets_how_to_add),
            typography = AppTheme.type.secondary,
            color = AppTheme.colors.muted,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

/** The page under [policy], from the real builder and the design's day. For previews and the leak test. */
@Composable
internal fun WidgetsFixtureScreen(policy: VisibilityPolicy) {
    val snapshot = WidgetSnapshotBuilder.build(WidgetFixtures.inputs(policy.settings), policy, rememberWidgetCopy())
    WidgetsScreen(WidgetsStateBuilder.build(snapshot), onAction = {})
}

@Preview
@Composable
private fun WidgetsPreview() {
    SettingsPreview { WidgetsFixtureScreen(WidgetFixtures.policy(PresetId.Full)) }
}

@Preview
@Composable
private fun WidgetsDarkPreview() {
    SettingsPreview(dark = true) { WidgetsFixtureScreen(WidgetFixtures.policy(PresetId.Full)) }
}

@Preview
@Composable
private fun WidgetsBlindPreview() {
    SettingsPreview { WidgetsFixtureScreen(WidgetFixtures.policy(PresetId.Blind)) }
}

/** Before the first snapshot arrives: the words, no widget. */
@Preview
@Composable
private fun WidgetsLoadingPreview() {
    SettingsPreview { WidgetsScreen(WidgetsState(), onAction = {}) }
}
