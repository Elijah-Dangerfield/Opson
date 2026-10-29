package com.opson.features.today.impl.checkin

import androidx.lifecycle.viewModelScope
import com.opson.features.today.impl.Toast
import com.opson.libraries.diary.CheckIn
import com.opson.libraries.diary.CheckInMood
import com.opson.libraries.diary.DayCheckIns
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.Energy
import com.opson.libraries.diary.HungerLevel
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MoodLevel
import com.opson.libraries.diary.Sleep
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.medication.Symptom
import com.opson.libraries.medication.SymptomLog
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.today_checkin_saved
import kotlin.time.Clock

/**
 * The check-in sheet. [checkIn] is what is picked so far, the day's stored check-in to start
 * with. Sleep is about last night, so it is asked first, and up front, only in the morning
 * ([sleepFirst]); later in the day it waits behind "Add last night's sleep" unless it was
 * answered already ([sleepShown]). Another day's check-in shows every row.
 */
data class CheckInState(
    val loaded: Boolean = false,
    val isToday: Boolean = true,
    val sleepFirst: Boolean = false,
    val sleepShown: Boolean = false,
    val checkIn: CheckIn = CheckIn(),
    /**
     * Today's side effects, for the "Side effects" row (`release.medication`, while medication is
     * tracked). Null leaves the row out; they are logged on the Medication page it opens.
     */
    val sideEffects: List<Symptom>? = null,
)

sealed interface CheckInAction {
    data class Loaded(val checkIn: CheckIn?, val today: LocalDate, val now: LocalTime) : CheckInAction

    /** A step on the hunger slider, 1 to 10. */
    data class HungerPicked(val level: Int) : CheckInAction
    data object HungerCleared : CheckInAction

    // The 5-point rows: the picked one again clears it.
    data class EnergyTapped(val energy: Energy) : CheckInAction
    data class SleepTapped(val sleep: Sleep) : CheckInAction
    data class MoodTapped(val mood: MoodLevel) : CheckInAction

    /** A mood tag, pick any. */
    data class MoodTagTapped(val mood: CheckInMood) : CheckInAction

    /** "Add last night's sleep", after the morning. */
    data object AddSleepTapped : CheckInAction

    /** Today's side effects from [SymptomLog]; null while medication isn't tracked. */
    data class SideEffectsArrived(val symptoms: List<Symptom>?) : CheckInAction

    /** The "Side effects" row: what is picked is saved, then the Medication page opens. */
    data object SideEffectsTapped : CheckInAction
    data object SaveTapped : CheckInAction
    data object CloseTapped : CheckInAction
}

sealed interface CheckInEvent {
    data class ShowToast(val toast: Toast) : CheckInEvent
    data object Dismiss : CheckInEvent

    /** The check-in gives way to the Medication page, where side effects are logged. */
    data object OpenSideEffects : CheckInEvent
}

/** The sheet as a pure function of the day, the time and what is picked, so previews run the same rules. */
object CheckInStateBuilder {

    /** Until noon a check-in is the morning's, and asks how last night went. */
    val MORNING_ENDS: LocalTime = LocalTime(12, 0)

    fun build(day: LocalDate, today: LocalDate, now: LocalTime, checkIn: CheckIn, sleepAsked: Boolean, sideEffects: List<Symptom>? = null): CheckInState {
        val isToday = day == today
        val morning = isToday && now < MORNING_ENDS
        return CheckInState(
            loaded = true,
            isToday = isToday,
            sleepFirst = morning,
            sleepShown = !isToday || morning || sleepAsked || checkIn.sleep != null,
            checkIn = checkIn,
            sideEffects = sideEffects.takeIf { isToday },
        )
    }
}

/**
 * Opens on the day's stored check-in, read once, and saves the whole of it with Save, on its own
 * write ([DayCheckIns]): the check-in is never part of closing the day, and a closed day still
 * takes one. Close leaves the stored one as it was.
 */
@Inject
class CheckInViewModel(
    @Assisted requestedDay: LocalDate?,
    private val diary: DiaryRepository,
    private val checkIns: DayCheckIns,
    private val calendar: LocalCalendar,
    private val clock: Clock,
    private val symptoms: SymptomLog = SymptomLog.None,
) : SEAViewModel<CheckInState, CheckInEvent, CheckInAction>(initialStateArg = CheckInState()) {

    private val day: LocalDate = requestedDay ?: calendar.today()
    private var model = Model()
    private var saving = false

    private data class Model(
        val today: LocalDate? = null,
        val now: LocalTime = LocalTime(0, 0),
        val checkIn: CheckIn = CheckIn(),
        val sleepAsked: Boolean = false,
        val sideEffects: List<Symptom>? = null,
    )

    init {
        viewModelScope.launch {
            val stored = diary.observeDay(day).first().checkIn
            val now = clock.now().toLocalDateTime(calendar.timeZone())
            takeAction(CheckInAction.Loaded(stored, now.date, now.time))
        }
        // Side effects are today's: the Medication page logs today's.
        if (day == calendar.today()) {
            viewModelScope.launch {
                combine(symptoms.asking, symptoms.observeDay(day)) { asking, felt -> felt.keys.sortedBy { it.ordinal }.takeIf { asking } }
                    .distinctUntilChanged()
                    .collect { takeAction(CheckInAction.SideEffectsArrived(it)) }
            }
        }
    }

    override suspend fun handleAction(action: CheckInAction) {
        when (action) {
            is CheckInAction.Loaded -> action.rebuild { copy(today = action.today, now = action.now, checkIn = action.checkIn ?: CheckIn()) }
            is CheckInAction.HungerPicked -> action.answer { it.copy(hunger = HungerLevel.of(action.level) ?: it.hunger) }
            CheckInAction.HungerCleared -> action.answer { it.copy(hunger = null) }
            is CheckInAction.EnergyTapped -> action.answer { it.copy(energy = action.energy.takeIf { e -> e != it.energy }) }
            is CheckInAction.SleepTapped -> action.answer { it.copy(sleep = action.sleep.takeIf { s -> s != it.sleep }) }
            is CheckInAction.MoodTapped -> action.answer { it.copy(mood = action.mood.takeIf { m -> m != it.mood }) }
            is CheckInAction.MoodTagTapped -> action.answer {
                it.copy(moods = if (action.mood in it.moods) it.moods - action.mood else it.moods + action.mood)
            }
            CheckInAction.AddSleepTapped -> action.rebuild { copy(sleepAsked = true) }
            is CheckInAction.SideEffectsArrived -> action.rebuild { copy(sideEffects = action.symptoms) }
            CheckInAction.SaveTapped -> if (save()) sendEvent(CheckInEvent.Dismiss)
            CheckInAction.SideEffectsTapped -> if (state.sideEffects != null && save()) sendEvent(CheckInEvent.OpenSideEffects)
            CheckInAction.CloseTapped -> sendEvent(CheckInEvent.Dismiss)
        }
    }

    /** Writes the picks, once. False before the day is read: the picks are empty then, and saving them would clear what is stored. */
    private suspend fun save(): Boolean {
        if (!state.loaded || saving) return false
        saving = true
        val checkIn = model.checkIn.takeUnless { it.isEmpty }
        checkIns.setCheckIn(day, checkIn)
        if (checkIn != null) sendEvent(CheckInEvent.ShowToast(Toast(Res.string.today_checkin_saved)))
        return true
    }

    private suspend fun CheckInAction.answer(change: (CheckIn) -> CheckIn) = rebuild { copy(checkIn = change(checkIn)) }

    private suspend fun CheckInAction.rebuild(change: Model.() -> Model) {
        model = model.change()
        val current = model
        val today = current.today ?: return
        updateState { CheckInStateBuilder.build(day, today, current.now, current.checkIn, current.sleepAsked, current.sideEffects) }
    }
}
