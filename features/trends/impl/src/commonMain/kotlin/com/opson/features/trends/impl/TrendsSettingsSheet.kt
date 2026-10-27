package com.opson.features.trends.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.SegmentedControl
import com.opson.libraries.ui.components.SheetHandle
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.text.Text
import com.opson.system.AppTheme
import com.opson.system.Dimension
import com.opson.system.background
import kotlinx.datetime.DayOfWeek
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.trends_settings_card_expended
import opson.libraries.resources.generated.resources.trends_settings_card_intake
import opson.libraries.resources.generated.resources.trends_settings_card_journal
import opson.libraries.resources.generated.resources.trends_settings_card_nutrients
import opson.libraries.resources.generated.resources.trends_settings_card_recap
import opson.libraries.resources.generated.resources.trends_settings_card_weight
import opson.libraries.resources.generated.resources.trends_settings_monday
import opson.libraries.resources.generated.resources.trends_settings_saturday
import opson.libraries.resources.generated.resources.trends_settings_show
import opson.libraries.resources.generated.resources.trends_settings_sunday
import opson.libraries.resources.generated.resources.trends_settings_title
import opson.libraries.resources.generated.resources.trends_settings_week_start
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * The tune button's sheet: week start and which cards show. The range moved onto the page
 * (design v3). The body, apart from its `BottomSheet` so previews and the leak test can render it.
 */
@Composable
fun TrendsSettingsSheetContent(
    state: TrendsSettingsState,
    onAction: (TrendsSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, top = Dimension.D400, bottom = Dimension.D900),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHandle(Modifier.align(Alignment.CenterHorizontally))
        SheetHeader(
            title = stringResource(Res.string.trends_settings_title),
            onClose = { onAction(TrendsSettingsAction.CloseTapped) },
        )
        Section(Res.string.trends_settings_week_start) {
            SegmentedControl(
                options = TrendsSettingsStateBuilder.WEEK_STARTS,
                selected = state.weekStart,
                onSelect = { onAction(TrendsSettingsAction.WeekStartPicked(it)) },
                label = { stringResource(it.label) },
            )
        }
        Section(Res.string.trends_settings_show) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(AppTheme.shapes.groupedList)
                    .background(AppTheme.colors.surface)
                    .padding(horizontal = Dimension.D700),
            ) {
                state.toggles.forEachIndexed { index, toggle ->
                    ListRow(
                        title = stringResource(toggle.card.label),
                        minHeight = AppTheme.spacing.touchTarget,
                        showDivider = index < state.toggles.lastIndex,
                    ) {
                        AppSwitch(
                            checked = toggle.on,
                            onCheckedChange = { onAction(TrendsSettingsAction.CardToggled(toggle.card)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Section(title: StringResource, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Dimension.D200)) {
        Text(text = stringResource(title), typography = AppTheme.type.secondarySmallStrong, color = AppTheme.colors.muted)
        content()
    }
}

private val DayOfWeek.label: StringResource
    get() = when (this) {
        DayOfWeek.SATURDAY -> Res.string.trends_settings_saturday
        DayOfWeek.SUNDAY -> Res.string.trends_settings_sunday
        else -> Res.string.trends_settings_monday
    }

private val TrendsCard.label: StringResource
    get() = when (this) {
        TrendsCard.Intake -> Res.string.trends_settings_card_intake
        TrendsCard.Recap -> Res.string.trends_settings_card_recap
        TrendsCard.Nutrients -> Res.string.trends_settings_card_nutrients
        TrendsCard.Expended -> Res.string.trends_settings_card_expended
        TrendsCard.Weight -> Res.string.trends_settings_card_weight
        TrendsCard.Journal -> Res.string.trends_settings_card_journal
    }
