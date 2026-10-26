package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.logger.HowItFeltRoute
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.MealFeeling
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Mood
import com.opson.libraries.flowroutines.SEAViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/** How it felt for one meal. No numbers, so nothing here goes through the policy. */
data class HowItFeltState(
    val loading: Boolean = true,
    val slot: MealSlot = MealSlot.Dinner,
    val hunger: Hunger? = null,
    val fullness: Fullness? = null,
    val moods: Set<Mood> = emptySet(),
    val note: String = "",
)

sealed interface HowItFeltAction {
    data class FeelingLoaded(val feeling: MealFeeling?) : HowItFeltAction
    data class HungerTapped(val hunger: Hunger) : HowItFeltAction
    data class FullnessTapped(val fullness: Fullness) : HowItFeltAction
    data class MoodTapped(val mood: Mood) : HowItFeltAction
    data class NoteTyped(val text: String) : HowItFeltAction
    data object SaveTapped : HowItFeltAction
    data object SkipTapped : HowItFeltAction
    data object CloseTapped : HowItFeltAction
}

sealed interface HowItFeltEvent {
    data object Dismiss : HowItFeltEvent
}

/**
 * How it felt for one meal, prefilled from what the meal already has. Skip writes nothing. No
 * telemetry: feelings are health data. Nothing opens it since design sync 2 (see
 * [HowItFeltRoute]); Today's meal card has its own sheet.
 */
@Inject
class HowItFeltViewModel(
    @Assisted route: HowItFeltRoute,
    private val diary: DiaryRepository,
) : SEAViewModel<HowItFeltState, HowItFeltEvent, HowItFeltAction>(
    initialStateArg = HowItFeltState(slot = route.slot),
) {
    private val day: LocalDate = LocalDate.parse(route.dayIso)
    private val slot: MealSlot = route.slot

    init {
        viewModelScope.launch {
            takeAction(HowItFeltAction.FeelingLoaded(diary.observeDay(day).first().feelings[slot]))
        }
    }

    override suspend fun handleAction(action: HowItFeltAction) {
        when (action) {
            is HowItFeltAction.FeelingLoaded -> action.updateState {
                val feeling = action.feeling ?: MealFeeling()
                it.copy(
                    loading = false,
                    hunger = feeling.hunger,
                    fullness = feeling.fullness,
                    moods = feeling.moods,
                    note = feeling.note.orEmpty(),
                )
            }
            is HowItFeltAction.HungerTapped -> action.updateState { it.copy(hunger = action.hunger.takeIf { h -> h != it.hunger }) }
            is HowItFeltAction.FullnessTapped -> action.updateState {
                it.copy(fullness = action.fullness.takeIf { f -> f != it.fullness })
            }
            is HowItFeltAction.MoodTapped -> action.updateState {
                it.copy(moods = if (action.mood in it.moods) it.moods - action.mood else it.moods + action.mood)
            }
            is HowItFeltAction.NoteTyped -> action.updateState { it.copy(note = action.text) }
            HowItFeltAction.SaveTapped -> {
                val current = state
                val feeling = MealFeeling(
                    hunger = current.hunger,
                    fullness = current.fullness,
                    moods = current.moods,
                    note = current.note.trim().ifEmpty { null },
                )
                diary.setFeeling(day, slot, feeling.takeUnless { it.isEmpty })
                sendEvent(HowItFeltEvent.Dismiss)
            }
            HowItFeltAction.SkipTapped, HowItFeltAction.CloseTapped -> sendEvent(HowItFeltEvent.Dismiss)
        }
    }
}
