package com.opson.features.today.impl.meal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.viewModelScope
import com.opson.features.today.impl.Toast
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Mood
import com.opson.libraries.diary.ui.SectionLabel
import com.opson.libraries.diary.ui.label
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.ui.components.SheetHeader
import com.opson.libraries.ui.components.button.FlatButton
import com.opson.libraries.ui.components.button.FlatButtonDefaults
import com.opson.libraries.ui.components.button.FlatButtonTone
import com.opson.libraries.ui.components.chip.PillChip
import com.opson.libraries.ui.components.chip.PillChipSize
import com.opson.system.AppTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_feel_fullness
import opson.libraries.resources.generated.resources.today_feel_hunger
import opson.libraries.resources.generated.resources.today_feel_mood
import opson.libraries.resources.generated.resources.today_feel_save
import opson.libraries.resources.generated.resources.today_feel_subtitle
import opson.libraries.resources.generated.resources.today_feel_title
import opson.libraries.resources.generated.resources.today_toast_feel_saved
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

/**
 * How a meal felt: hunger before, fullness after, any moods. All optional and
 * only for the user. Nothing from this sheet goes to telemetry: it is health
 * data.
 */
data class MealFeelState(
    val slot: MealSlot,
    val hunger: Hunger? = null,
    val fullness: Fullness? = null,
    val moods: Set<Mood> = emptySet(),
    val loaded: Boolean = false,
)

sealed interface MealFeelAction {
    data class FeelingLoaded(val feeling: MealFeeling?) : MealFeelAction
    data class HungerTapped(val hunger: Hunger) : MealFeelAction
    data class FullnessTapped(val fullness: Fullness) : MealFeelAction
    data class MoodTapped(val mood: Mood) : MealFeelAction
    data object SaveTapped : MealFeelAction
    data object CloseTapped : MealFeelAction
}

sealed interface MealFeelEvent {
    data class ShowToast(val toast: Toast) : MealFeelEvent
    data object Dismiss : MealFeelEvent
}

@Inject
class MealFeelViewModel(
    @Assisted private val day: LocalDate,
    @Assisted private val slot: MealSlot,
    private val diary: DiaryRepository,
) : SEAViewModel<MealFeelState, MealFeelEvent, MealFeelAction>(initialStateArg = MealFeelState(slot)) {

    /** The stored note rides along unchanged; this sheet does not edit it. */
    private var note: String? = null

    private var prefilled = false

    init {
        viewModelScope.launch {
            diary.observeDay(day).collect { takeAction(MealFeelAction.FeelingLoaded(it.feelings[slot])) }
        }
    }

    override suspend fun handleAction(action: MealFeelAction) {
        when (action) {
            is MealFeelAction.FeelingLoaded -> {
                note = action.feeling?.note
                val feeling = action.feeling
                if (!prefilled) {
                    action.updateState {
                        it.copy(hunger = feeling?.hunger, fullness = feeling?.fullness, moods = feeling?.moods.orEmpty(), loaded = true)
                    }
                    prefilled = true
                }
            }
            is MealFeelAction.HungerTapped -> action.updateState { it.copy(hunger = action.hunger.takeIf { h -> h != it.hunger }) }
            is MealFeelAction.FullnessTapped -> action.updateState { it.copy(fullness = action.fullness.takeIf { f -> f != it.fullness }) }
            is MealFeelAction.MoodTapped -> action.updateState {
                it.copy(moods = if (action.mood in it.moods) it.moods - action.mood else it.moods + action.mood)
            }
            MealFeelAction.SaveTapped -> {
                val current = state
                diary.setFeeling(day, slot, MealFeeling(current.hunger, current.fullness, current.moods, note))
                sendEvent(MealFeelEvent.ShowToast(Toast(Res.string.today_toast_feel_saved)))
                sendEvent(MealFeelEvent.Dismiss)
            }
            MealFeelAction.CloseTapped -> sendEvent(MealFeelEvent.Dismiss)
        }
    }
}

@Composable
fun MealFeelSheetContent(
    state: MealFeelState,
    onAction: (MealFeelAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = AppTheme.spacing.page, end = AppTheme.spacing.page, bottom = AppTheme.spacing.page),
        verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.section),
    ) {
        SheetHeader(
            title = stringResource(Res.string.today_feel_title, stringResource(state.slot.sentenceName)),
            subtitle = stringResource(Res.string.today_feel_subtitle),
            onClose = { onAction(MealFeelAction.CloseTapped) },
        )
        ChipGroup(Res.string.today_feel_hunger, Hunger.entries, { it == state.hunger }, { it.label }) { onAction(MealFeelAction.HungerTapped(it)) }
        ChipGroup(Res.string.today_feel_fullness, Fullness.entries, { it == state.fullness }, { it.label }) {
            onAction(MealFeelAction.FullnessTapped(it))
        }
        ChipGroup(Res.string.today_feel_mood, Mood.entries, { it in state.moods }, { it.label }, role = Role.Checkbox) {
            onAction(MealFeelAction.MoodTapped(it))
        }
        FlatButton(
            text = stringResource(Res.string.today_feel_save),
            onClick = { onAction(MealFeelAction.SaveTapped) },
            tone = FlatButtonTone.Accent,
            centered = true,
            minHeight = FlatButtonDefaults.TallMinHeight,
            typography = AppTheme.type.button,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChipGroup(
    title: StringResource,
    options: List<T>,
    selected: (T) -> Boolean,
    label: (T) -> StringResource,
    role: Role = Role.RadioButton,
    onTap: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
        SectionLabel(stringResource(title))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
            verticalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip),
        ) {
            options.forEach { option ->
                PillChip(
                    label = stringResource(label(option)),
                    selected = selected(option),
                    onClick = { onTap(option) },
                    size = PillChipSize.Compact,
                    role = role,
                )
            }
        }
    }
}
