package com.opson.features.today.impl.water

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.opson.features.today.impl.Toast
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.Units
import com.opson.libraries.nutrition.WaterProgress
import com.opson.libraries.nutrition.WaterUnit
import com.opson.libraries.ui.components.ListRowChevron
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.SheetMenuGroup
import com.opson.libraries.ui.components.SheetMenuNumberRow
import com.opson.libraries.ui.components.SheetMenuRow
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.libraries.ui.components.text.NumberInput
import com.opson.system.AppTheme
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_toast_water
import opson.libraries.resources.generated.resources.today_toast_water_added
import opson.libraries.resources.generated.resources.today_toast_water_removed
import opson.libraries.resources.generated.resources.today_water_add
import opson.libraries.resources.generated.resources.today_water_add_glass
import opson.libraries.resources.generated.resources.today_water_custom
import opson.libraries.resources.generated.resources.today_water_remove
import opson.libraries.resources.generated.resources.today_water_settings
import opson.libraries.resources.generated.resources.today_water_title
import opson.libraries.resources.generated.resources.today_water_unit_floz
import opson.libraries.resources.generated.resources.today_water_unit_ml
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

/**
 * The water sheet. It shows the glass size, a setting, and never how much has
 * been drunk: that is the water row's job, through the policy.
 */
data class WaterSheetState(
    val glassLabel: String = "",
    val unit: WaterUnit = WaterUnit.Ml,
    /** Digits only. */
    val custom: String = "",
    /** Only when the day has some water to take off. */
    val canRemove: Boolean = false,
)

sealed interface WaterSheetAction {
    data class InputsArrived(val day: Day, val settings: Settings) : WaterSheetAction
    data object AddGlassTapped : WaterSheetAction
    data class CustomAmountTyped(val text: String) : WaterSheetAction
    data object AddCustomTapped : WaterSheetAction
    data object RemoveGlassTapped : WaterSheetAction
    data object WaterSettingsTapped : WaterSheetAction
    data object CloseTapped : WaterSheetAction
}

sealed interface WaterSheetEvent {
    data object OpenWaterSettings : WaterSheetEvent
    data class ShowToast(val toast: Toast) : WaterSheetEvent

    /** The day is closed (Close the day, maybe on another phone): no water was added or taken off. */
    data class DayClosed(val day: LocalDate) : WaterSheetEvent
    data object Dismiss : WaterSheetEvent
}

object WaterSheetStateBuilder {
    fun build(day: Day, settings: Settings, custom: String): WaterSheetState = WaterSheetState(
        glassLabel = WaterProgress.glassSize(settings.glassMl, settings.waterUnit),
        unit = settings.waterUnit,
        custom = custom,
        canRemove = day.waterMl > 0,
    )

    /** What a typed custom amount adds, in ml; null for nothing or zero. */
    fun customMl(custom: String, unit: WaterUnit): Int? {
        val amount = custom.toIntOrNull()?.takeIf { it > 0 } ?: return null
        return when (unit) {
            WaterUnit.Ml -> amount
            WaterUnit.FlOz -> Units.flOzToMl(amount.toDouble()).roundToInt()
        }
    }
}

@Inject
class WaterSheetViewModel(
    @Assisted private val day: LocalDate,
    private val diary: DiaryRepository,
    private val settingsRepository: SettingsRepository,
) : SEAViewModel<WaterSheetState, WaterSheetEvent, WaterSheetAction>(initialStateArg = WaterSheetState()) {

    private var inputs: Pair<Day, Settings>? = null
    private var custom = ""

    /** Added or removed, and the sheet is closing: a second tap queued behind the first would count a second glass. */
    private var written = false

    init {
        viewModelScope.launch {
            combine(diary.observeDay(day), settingsRepository.settings) { d, s -> WaterSheetAction.InputsArrived(d, s) }
                .collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: WaterSheetAction) {
        when (action) {
            is WaterSheetAction.InputsArrived -> {
                inputs = action.day to action.settings
                action.rebuild()
            }
            WaterSheetAction.AddGlassTapped -> if (!written) {
                written = true
                finish(diary.addWater(day, settings().glassMl), Toast(Res.string.today_toast_water))
            }
            is WaterSheetAction.CustomAmountTyped -> {
                custom = NumberInput.sanitize(action.text, allowDecimal = false)
                action.rebuild()
            }
            WaterSheetAction.AddCustomTapped -> {
                if (written) return
                val ml = WaterSheetStateBuilder.customMl(custom, settings().waterUnit) ?: return
                written = true
                finish(diary.addWater(day, ml), Toast(Res.string.today_toast_water_added))
            }
            WaterSheetAction.RemoveGlassTapped -> if (!written) {
                written = true
                finish(diary.removeWater(day, settings().glassMl), Toast(Res.string.today_toast_water_removed))
            }
            WaterSheetAction.WaterSettingsTapped -> sendEvent(WaterSheetEvent.OpenWaterSettings)
            WaterSheetAction.CloseTapped -> sendEvent(WaterSheetEvent.Dismiss)
        }
    }

    private fun settings(): Settings = inputs?.second ?: settingsRepository.settings.value

    private suspend fun WaterSheetAction.rebuild() {
        val (d, s) = inputs ?: return
        updateState { WaterSheetStateBuilder.build(d, s, custom) }
    }

    private fun finish(write: DayWrite<Unit>, toast: Toast) {
        when (write) {
            is DayWrite.Written -> {
                sendEvent(WaterSheetEvent.ShowToast(toast))
                sendEvent(WaterSheetEvent.Dismiss)
            }
            is DayWrite.DayClosed -> sendEvent(WaterSheetEvent.DayClosed(write.day))
        }
    }
}

@Composable
fun WaterSheetContent(
    state: WaterSheetState,
    onAction: (WaterSheetAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.row),
    ) {
        SheetHeader(title = stringResource(Res.string.today_water_title), onClose = { onAction(WaterSheetAction.CloseTapped) })
        SheetMenuGroup {
            SheetMenuRow(
                label = stringResource(Res.string.today_water_add_glass, state.glassLabel),
                icon = AppIcons.LocalDrink,
                onClick = { onAction(WaterSheetAction.AddGlassTapped) },
            )
            SheetMenuNumberRow(
                value = state.custom,
                onValueChange = { onAction(WaterSheetAction.CustomAmountTyped(it)) },
                icon = AppIcons.Edit,
                placeholder = stringResource(Res.string.today_water_custom),
                unit = stringResource(if (state.unit == WaterUnit.FlOz) Res.string.today_water_unit_floz else Res.string.today_water_unit_ml),
                actionLabel = stringResource(Res.string.today_water_add),
                onAction = { onAction(WaterSheetAction.AddCustomTapped) },
            )
            if (state.canRemove) {
                SheetMenuRow(
                    label = stringResource(Res.string.today_water_remove),
                    icon = AppIcons.Remove,
                    onClick = { onAction(WaterSheetAction.RemoveGlassTapped) },
                )
            }
            SheetMenuRow(
                label = stringResource(Res.string.today_water_settings),
                icon = AppIcons.Settings,
                onClick = { onAction(WaterSheetAction.WaterSettingsTapped) },
                showDivider = false,
            ) {
                ListRowChevron()
            }
        }
    }
}
