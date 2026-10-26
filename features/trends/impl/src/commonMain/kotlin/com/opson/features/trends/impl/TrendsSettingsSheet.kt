package com.opson.features.trends.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.opson.libraries.diary.TrendsRange
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
import opson.libraries.resources.generated.resources.trends_settings_card_intake
import opson.libraries.resources.generated.resources.trends_settings_card_nutrients
import opson.libraries.resources.generated.resources.trends_settings_card_recap
import opson.libraries.resources.generated.resources.trends_settings_card_weight
import opson.libraries.resources.generated.resources.trends_settings_monday
import opson.libraries.resources.generated.resources.trends_settings_range
import opson.libraries.resources.generated.resources.trends_settings_range_3m
import opson.libraries.resources.generated.resources.trends_settings_range_4w
import opson.libraries.resources.generated.resources.trends_settings_range_week
import opson.libraries.resources.generated.resources.trends_settings_saturday
import opson.libraries.resources.generated.resources.trends_settings_show
import opson.libraries.resources.generated.resources.trends_settings_sunday
import opson.libraries.resources.generated.resources.trends_settings_title
import opson.libraries.resources.generated.resources.trends_settings_week_start
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/** The Trends settings sheet body, apart from its `BottomSheet` so previews and the leak test can render it. */
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
        Section(Res.string.trends_settings_range) {
            SegmentedControl(
                options = TrendsRange.entries,
                selected = state.range,
                onSelect = { onAction(TrendsSettingsAction.RangePicked(it)) },
                label = { stringResource(it.label) },
            )
        }
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

private val TrendsRange.label: StringResource
    get() = when (this) {
        TrendsRange.Week -> Res.string.trends_settings_range_week
        TrendsRange.FourWeeks -> Res.string.trends_settings_range_4w
        TrendsRange.ThreeMonths -> Res.string.trends_settings_range_3m
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
        TrendsCard.Weight -> Res.string.trends_settings_card_weight
    }
