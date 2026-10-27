package com.opson.features.today.impl.meal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import com.opson.libraries.ui.components.chip.ChoiceChip
import com.opson.libraries.ui.components.text.TextArea
import com.opson.system.AppTheme
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_feel_fullness
import opson.libraries.resources.generated.resources.today_feel_hunger
import opson.libraries.resources.generated.resources.today_feel_mood_any
import opson.libraries.resources.generated.resources.today_feel_note
import opson.libraries.resources.generated.resources.today_feel_save
import opson.libraries.resources.generated.resources.today_feel_skip
import opson.libraries.resources.generated.resources.today_feel_subtitle_optional
import opson.libraries.resources.generated.resources.today_feel_title_question
import opson.libraries.resources.generated.resources.today_feel_too_full
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
    /** The meal's note, edited here too (design sync 2, 3.10). */
    val note: String = "",
    val loaded: Boolean = false,
)

sealed interface MealFeelAction {
    data class FeelingLoaded(val feeling: MealFeeling?) : MealFeelAction
    data class HungerTapped(val hunger: Hunger) : MealFeelAction
    data class FullnessTapped(val fullness: Fullness) : MealFeelAction
    data class MoodTapped(val mood: Mood) : MealFeelAction
    data class NoteTyped(val text: String) : MealFeelAction
    data object SaveTapped : MealFeelAction

    /** Skip: as big as Save, and saves nothing. */
    data object SkipTapped : MealFeelAction
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

    private var prefilled = false

    init {
        viewModelScope.launch {
            diary.observeDay(day).collect { takeAction(MealFeelAction.FeelingLoaded(it.feelings[slot])) }
        }
    }

    override suspend fun handleAction(action: MealFeelAction) {
        when (action) {
            is MealFeelAction.FeelingLoaded -> {
                val feeling = action.feeling
                if (!prefilled) {
                    action.updateState {
                        it.copy(
                            hunger = feeling?.hunger,
                            fullness = feeling?.fullness,
                            moods = feeling?.moods.orEmpty(),
                            note = feeling?.note.orEmpty(),
                            loaded = true,
                        )
                    }
                    prefilled = true
                }
            }
            is MealFeelAction.HungerTapped -> action.updateState { it.copy(hunger = action.hunger.takeIf { h -> h != it.hunger }) }
            is MealFeelAction.FullnessTapped -> action.updateState { it.copy(fullness = action.fullness.takeIf { f -> f != it.fullness }) }
            is MealFeelAction.MoodTapped -> action.updateState {
                it.copy(moods = if (action.mood in it.moods) it.moods - action.mood else it.moods + action.mood)
            }
            is MealFeelAction.NoteTyped -> action.updateState { it.copy(note = action.text) }
            MealFeelAction.SkipTapped -> sendEvent(MealFeelEvent.Dismiss)
            MealFeelAction.SaveTapped -> {
                val current = state
                diary.setFeeling(day, slot, MealFeeling(current.hunger, current.fullness, current.moods, current.note.trim().ifEmpty { null }))
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
            title = stringResource(Res.string.today_feel_title_question, stringResource(state.slot.sentenceName)),
            subtitle = stringResource(Res.string.today_feel_subtitle_optional),
            onClose = { onAction(MealFeelAction.CloseTapped) },
        )
        ChipGroup(Res.string.today_feel_hunger, HungerOrder, { it == state.hunger }, { it.feelLabel }) { onAction(MealFeelAction.HungerTapped(it)) }
        ChipGroup(Res.string.today_feel_fullness, Fullness.entries, { it == state.fullness }, { it.feelLabel }) {
            onAction(MealFeelAction.FullnessTapped(it))
        }
        ChipGroup(Res.string.today_feel_mood_any, MoodOrder, { it in state.moods }, { it.label }, role = Role.Checkbox) {
            onAction(MealFeelAction.MoodTapped(it))
        }
        TextArea(
            value = state.note,
            onValueChange = { onAction(MealFeelAction.NoteTyped(it)) },
            placeholder = stringResource(Res.string.today_feel_note),
            minLines = 1,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.row)) {
            FlatButton(
                text = stringResource(Res.string.today_feel_skip),
                onClick = { onAction(MealFeelAction.SkipTapped) },
                tone = FlatButtonTone.AccentText,
                centered = true,
                minHeight = FlatButtonDefaults.TallMinHeight,
                typography = AppTheme.type.button,
                modifier = Modifier.weight(1f),
            )
            FlatButton(
                text = stringResource(Res.string.today_feel_save),
                onClick = { onAction(MealFeelAction.SaveTapped) },
                tone = FlatButtonTone.Accent,
                centered = true,
                minHeight = FlatButtonDefaults.TallMinHeight,
                typography = AppTheme.type.button,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/** Least to most, as the design lists them. */
private val HungerOrder = Hunger.entries

/** The design's order; Anxious and Low follow, since they are stored moods too. */
private val MoodOrder = listOf(Mood.Calm, Mood.Rushed, Mood.Social, Mood.Stressed, Mood.Happy, Mood.Anxious, Mood.Low)

/** "A bit" and "Too full" here; the stored values and every other screen's words are unchanged. */
private val Hunger.feelLabel: StringResource
    get() = label

private val Fullness.feelLabel: StringResource
    get() = if (this == Fullness.VeryFull) Res.string.today_feel_too_full else label

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
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AppTheme.spacing.chip)) {
            options.forEach { option ->
                ChoiceChip(
                    label = stringResource(label(option)),
                    selected = selected(option),
                    onClick = { onTap(option) },
                    role = role,
                    unselectedColor = AppTheme.colors.surface,
                )
            }
        }
    }
}
