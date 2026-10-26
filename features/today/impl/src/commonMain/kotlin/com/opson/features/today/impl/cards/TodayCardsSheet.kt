package com.opson.features.today.impl.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewModelScope
import com.opson.features.today.impl.Toast
import com.opson.libraries.diary.NumbersChanger
import com.opson.libraries.diary.NumbersOutcome
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.ui.TodayCard
import com.opson.libraries.diary.ui.TodayCardPadding
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.NumbersChange
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.ui.components.AppSwitch
import com.opson.libraries.ui.components.HorizontalDivider
import com.opson.libraries.ui.components.ListRow
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.icon.AppIcons
import com.opson.system.AppTheme
import com.opson.system.color.ToneColors
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_cards_groups
import opson.libraries.resources.generated.resources.today_cards_groups_body
import opson.libraries.resources.generated.resources.today_cards_title
import opson.libraries.resources.generated.resources.today_cards_water
import opson.libraries.resources.generated.resources.today_cards_water_body
import opson.libraries.resources.generated.resources.today_toast_saved
import opson.libraries.resources.generated.resources.today_toast_saved_fewer
import org.jetbrains.compose.resources.stringResource

/**
 * "Edit Today's cards" from the ⋮ menu (design v3, 2.11): meal groups, and the water card,
 * which moved here from the Day menu's "Hide water". Fasting is 0.2. Hiding water applies at
 * once through [NumbersChanger]. Showing it again is a change that shows more, and v3 keeps
 * those in Modes and numbers behind the pause, so turning the switch on opens Modes.
 */
data class TodayCardsState(val mealGroups: Boolean = true, val water: Boolean = true)

sealed interface TodayCardsAction {
    data class InputsArrived(val settings: Settings, val policy: VisibilityPolicy) : TodayCardsAction
    data object MealGroupsToggled : TodayCardsAction
    data object WaterToggled : TodayCardsAction
    data object CloseTapped : TodayCardsAction
}

sealed interface TodayCardsEvent {
    data class OpenPause(val change: NumbersChange) : TodayCardsEvent
    data object OpenModes : TodayCardsEvent
    data class ShowToast(val toast: Toast) : TodayCardsEvent
    data object Dismiss : TodayCardsEvent
}

object TodayCardsStateBuilder {
    fun build(settings: Settings, policy: VisibilityPolicy): TodayCardsState =
        TodayCardsState(mealGroups = settings.mealGroups, water = policy.level(Metric.Water) != Visibility.Hide)
}

@Inject
class TodayCardsViewModel(
    private val settingsRepository: SettingsRepository,
    private val policySource: VisibilityPolicySource,
    private val numbersChanger: NumbersChanger,
) : SEAViewModel<TodayCardsState, TodayCardsEvent, TodayCardsAction>(initialStateArg = TodayCardsState()) {

    init {
        viewModelScope.launch {
            combine(settingsRepository.settings, policySource.policy) { s, p -> TodayCardsAction.InputsArrived(s, p) }.collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: TodayCardsAction) {
        when (action) {
            is TodayCardsAction.InputsArrived -> action.updateState { TodayCardsStateBuilder.build(action.settings, action.policy) }
            TodayCardsAction.MealGroupsToggled -> settingsRepository.update { it.copy(mealGroups = !it.mealGroups) }
            TodayCardsAction.WaterToggled -> {
                if (policySource.policy.value.level(Metric.Water) == Visibility.Hide) {
                    sendEvent(TodayCardsEvent.OpenModes)
                    return
                }
                val change = NumbersChange.Override(Metric.Water, Visibility.Hide)
                when (val outcome = numbersChanger.request(change)) {
                    NumbersOutcome.NeedsPause -> sendEvent(TodayCardsEvent.OpenPause(change))
                    is NumbersOutcome.Applied -> sendEvent(
                        TodayCardsEvent.ShowToast(Toast(if (outcome.fewer) Res.string.today_toast_saved_fewer else Res.string.today_toast_saved)),
                    )
                }
            }
            TodayCardsAction.CloseTapped -> sendEvent(TodayCardsEvent.Dismiss)
        }
    }
}

@Composable
fun TodayCardsSheetContent(
    state: TodayCardsState,
    onAction: (TodayCardsAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(title = stringResource(Res.string.today_cards_title), onClose = { onAction(TodayCardsAction.CloseTapped) })
        TodayCard(contentPadding = TodayCardPadding.List, verticalArrangement = Arrangement.Top) {
            ListRow(
                title = stringResource(Res.string.today_cards_groups),
                subtitle = stringResource(Res.string.today_cards_groups_body),
                icon = AppIcons.ViewAgenda,
                iconTone = ToneColors(AppTheme.colors.accent, AppTheme.colors.accentTint),
            ) {
                AppSwitch(checked = state.mealGroups, onCheckedChange = { onAction(TodayCardsAction.MealGroupsToggled) })
            }
            HorizontalDivider(color = AppTheme.colors.line)
            ListRow(
                title = stringResource(Res.string.today_cards_water),
                subtitle = stringResource(Res.string.today_cards_water_body),
                icon = AppIcons.WaterDrop,
                iconTone = AppTheme.colors.nutrients.water,
            ) {
                AppSwitch(checked = state.water, onCheckedChange = { onAction(TodayCardsAction.WaterToggled) })
            }
        }
    }
}
