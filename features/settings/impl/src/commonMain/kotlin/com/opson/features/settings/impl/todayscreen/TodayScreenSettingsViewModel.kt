package com.opson.features.settings.impl.todayscreen

import androidx.lifecycle.viewModelScope
import com.opson.libraries.diary.CelebrationsRelease
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.diary.RecapsRelease
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.MainNumberChoice
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.SummaryCardPreset
import com.opson.libraries.diary.TodaySummaryEnabled
import com.opson.libraries.diary.fastingTimerOn
import com.opson.libraries.diary.ui.SummaryStat
import com.opson.libraries.diary.ui.SummaryStats
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.medication.MedicationTodaySwitch
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.ExerciseRelease
import com.opson.libraries.health.GoalsAndEnergyEnabled
import com.opson.libraries.health.HealthReleaseFlag
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject

/**
 * More › Today screen: every change is one settings write, so Today redraws as it lands. Burned is
 * read only to say when a pick needs it (Equation, Deficit or surplus); nothing here shows it.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Inject
class TodayScreenSettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val burnedRepository: BurnedRepository,
    private val calendar: LocalCalendar,
    private val goalsAndEnergy: GoalsAndEnergyEnabled,
    private val todaySummary: TodaySummaryEnabled,
    private val fastingRelease: FastingRelease,
    healthFlag: HealthReleaseFlag,
    exerciseRelease: ExerciseRelease,
    private val medicationCard: MedicationTodaySwitch = MedicationTodaySwitch.None,
    celebrations: CelebrationsRelease = CelebrationsRelease.Off,
    recaps: RecapsRelease = RecapsRelease.Off,
) : SEAViewModel<TodayScreenSettingsState, TodayScreenSettingsEvent, TodayScreenSettingsAction>(
    initialStateArg = TodayScreenSettingsState(),
) {
    private val flags = TodayScreenFlags(
        goalsAndEnergy = goalsAndEnergy(),
        todaySummary = todaySummary(),
        fasting = fastingRelease.isOn(),
        celebrations = celebrations.isOn(),
        recaps = recaps.isOn(),
        steps = healthFlag() && exerciseRelease.isOn(),
    )

    init {
        viewModelScope.launch {
            combine(settingsRepository.observe(), burnedKnown(), medicationCard.shown) { settings, known, medication ->
                TodayScreenSettingsAction.InputsArrived(settings, known, medication)
            }.collect(::takeAction)
        }
    }

    /** With `release.goalsAndEnergy` off there are no presets to need it, so Burned is never read. */
    private fun burnedKnown(): Flow<Boolean> {
        if (!flags.goalsAndEnergy) return flowOf(true)
        return burnedRepository.burned(calendar.today()).map { it != null }.distinctUntilChanged()
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun handleAction(action: TodayScreenSettingsAction) {
        when (action) {
            is TodayScreenSettingsAction.InputsArrived -> action.updateState {
                TodayScreenSettingsStateBuilder.build(action.settings, flags, action.burnedKnown).copy(medication = action.medication)
            }
            is TodayScreenSettingsAction.MedicationToggled -> if (state.medication != null) medicationCard.set(action.on)
            is TodayScreenSettingsAction.PresetPicked -> {
                // Rings with a range is not offered; the card would only fall back.
                val offered = state.presets.firstOrNull { it.preset == action.preset }?.enabled == true
                if (offered && state.presetsOn) settingsRepository.update { it.copy(todayCard = action.preset) }
            }
            TodayScreenSettingsAction.MainNumberTapped -> sendEvent(TodayScreenSettingsEvent.OpenChoice(TodayScreenChoice.MainNumber))
            TodayScreenSettingsAction.CardPagesTapped -> sendEvent(TodayScreenSettingsEvent.OpenChoice(TodayScreenChoice.CardPages))
            is TodayScreenSettingsAction.Toggled -> toggled(action.row, action.on)
            is TodayScreenSettingsAction.StatToggled -> statToggled(action.stat)
            is TodayScreenSettingsAction.StatMoved -> statMoved(action.from, action.to)
            TodayScreenSettingsAction.StatsReset -> settingsRepository.update { it.copy(todaySummaryStats = null) }
            TodayScreenSettingsAction.NutrientsPickTapped -> sendEvent(TodayScreenSettingsEvent.OpenMicroTargets)
            is TodayScreenSettingsAction.LinkTapped -> sendEvent(TodayScreenSettingsEvent.Open(action.link))
            TodayScreenSettingsAction.BackTapped -> sendEvent(TodayScreenSettingsEvent.Close)
        }
    }

    private suspend fun toggled(row: TodaySwitch, on: Boolean) {
        // The fasting card's switch is only there while fasting is offered and its timer is on.
        if (row == TodaySwitch.Fasting && !settingsRepository.settings.value.fastingTimerOn(flags.fasting)) return
        settingsRepository.update { row.set(it, on) }
    }

    /** Ticks or unticks [stat]; a fourth tick does nothing, page 1 has room for three. A locked stat never ticks. */
    private suspend fun statToggled(stat: SummaryStat) {
        settingsRepository.update { settings ->
            val numbers = settings.numbers
            if (!SummaryStats.allowed(stat, numbers)) return@update settings
            val chosen = SummaryStats.chosen(settings).filter { SummaryStats.allowed(it, numbers) }
            val next = when {
                stat in chosen -> chosen - stat
                chosen.size < SummaryStats.MAX -> chosen + stat
                else -> return@update settings
            }
            settings.copy(todaySummaryStats = next.map { it.wire })
        }
    }

    /** Moves the row at [from] to [to]; what is ticked keeps its new order. */
    private suspend fun statMoved(from: Int, to: Int) {
        settingsRepository.update { settings ->
            val numbers = settings.numbers
            val listed = SummaryStats.listed(settings, numbers).toMutableList()
            if (from !in listed.indices || to !in listed.indices || from == to) return@update settings
            listed.add(to, listed.removeAt(from))
            val chosen = SummaryStats.chosen(settings).filter { SummaryStats.allowed(it, numbers) }.toSet()
            settings.copy(todaySummaryStats = listed.filter { it in chosen }.map { it.wire })
        }
    }
}

/** A switch on the page, and the setting it writes. */
enum class TodaySwitch(val set: (Settings, Boolean) -> Settings) {
    BurnedBreakdown({ s, on -> s.copy(todayBurnedBreakdown = on) }),
    MealsCount({ s, on -> s.copy(todayMealsCount = on) }),
    NutrientsLink({ s, on -> s.copy(todayNutrientsLink = on) }),
    MealGroups({ s, on -> s.copy(mealGroups = on) }),
    Water({ s, on -> s.copy(todayWater = on) }),
    Fasting({ s, on -> s.copy(fastToday = on) }),
    Steps({ s, on -> s.copy(todaySteps = on) }),
    Weight({ s, on -> s.copy(todayWeight = on) }),
    CloseDay({ s, on -> s.copy(todayCloseDay = on) }),
    Streak({ s, on -> s.copy(todayStreak = on) }),
    RecapBanner({ s, on -> s.copy(recapBanner = on) }),
    Tips({ s, on -> s.copy(tips = on) }),
    Suggestions({ s, on -> s.copy(suggestions = on) }),
}

/** The two choices that open a sheet: the main number (13.6b) and the card's pages. */
enum class TodayScreenChoice(val wire: String) {
    MainNumber("main_number"),
    CardPages("card_pages"),
    ;

    companion object {
        fun fromWire(value: String): TodayScreenChoice = entries.firstOrNull { it.wire == value } ?: MainNumber
    }
}

sealed interface TodayScreenSettingsEvent {
    data class OpenChoice(val choice: TodayScreenChoice) : TodayScreenSettingsEvent

    /** Vitamins and minerals, where the page's four are picked. */
    data object OpenMicroTargets : TodayScreenSettingsEvent

    /** The setting an [OverrideNote] names, or a locked stat's levels. */
    data class Open(val link: SettingLink) : TodayScreenSettingsEvent
    data object Close : TodayScreenSettingsEvent
}

sealed interface TodayScreenSettingsAction {
    data class InputsArrived(val settings: Settings, val burnedKnown: Boolean = true, val medication: Boolean? = null) : TodayScreenSettingsAction

    /** The Medication card's switch, which lives on the medication plan rather than in Settings. */
    data class MedicationToggled(val on: Boolean) : TodayScreenSettingsAction
    data class PresetPicked(val preset: SummaryCardPreset) : TodayScreenSettingsAction
    data object MainNumberTapped : TodayScreenSettingsAction
    data object CardPagesTapped : TodayScreenSettingsAction
    data object NutrientsPickTapped : TodayScreenSettingsAction
    data class Toggled(val row: TodaySwitch, val on: Boolean) : TodayScreenSettingsAction
    data class StatToggled(val stat: SummaryStat) : TodayScreenSettingsAction

    /** A drag (or Move up and Move down) took the row at [from] to [to] in the list of stats. */
    data class StatMoved(val from: Int, val to: Int) : TodayScreenSettingsAction
    data object StatsReset : TodayScreenSettingsAction
    data class LinkTapped(val link: SettingLink) : TodayScreenSettingsAction
    data object BackTapped : TodayScreenSettingsAction
}

/**
 * The main number (13.6b) or card pages sheet. Picking writes at once and closes. A new main
 * number also forgets what the card's toggle remembered for every goal style: this sets what the
 * toggle resets to.
 */
@Inject
class TodayScreenChoiceViewModel(
    @Assisted private val choice: TodayScreenChoice,
    private val settingsRepository: SettingsRepository,
) : SEAViewModel<TodayScreenChoiceState, TodayScreenChoiceEvent, TodayScreenChoiceAction>(
    initialStateArg = TodayScreenChoiceState(choice),
) {
    init {
        viewModelScope.launch {
            settingsRepository.observe().collect { takeAction(TodayScreenChoiceAction.InputsArrived(it)) }
        }
    }

    override suspend fun handleAction(action: TodayScreenChoiceAction) {
        when (action) {
            is TodayScreenChoiceAction.InputsArrived -> action.updateState { TodayScreenChoiceState.of(choice, action.settings) }
            is TodayScreenChoiceAction.MainNumberPicked -> {
                settingsRepository.update { it.copy(todayMainNumber = action.choice, todayMainNumberByStyle = emptyMap()) }
                sendEvent(TodayScreenChoiceEvent.Dismiss)
            }
            is TodayScreenChoiceAction.PagesPicked -> {
                settingsRepository.update { it.copy(todayMacrosPage = action.macros) }
                sendEvent(TodayScreenChoiceEvent.Dismiss)
            }
        }
    }
}

data class TodayScreenChoiceState(
    val choice: TodayScreenChoice,
    val mainNumber: MainNumberChoice = MainNumberChoice.Follow,
    val follows: FollowStyle = FollowStyle.Range,
    val macrosPage: Boolean = true,
) {
    companion object {
        fun of(choice: TodayScreenChoice, settings: Settings): TodayScreenChoiceState {
            val page = TodayScreenSettingsStateBuilder.build(settings)
            return TodayScreenChoiceState(choice, page.mainNumber, page.follows, settings.todayMacrosPage)
        }
    }
}

sealed interface TodayScreenChoiceEvent {
    data object Dismiss : TodayScreenChoiceEvent
}

sealed interface TodayScreenChoiceAction {
    data class InputsArrived(val settings: Settings) : TodayScreenChoiceAction
    data class MainNumberPicked(val choice: MainNumberChoice) : TodayScreenChoiceAction
    data class PagesPicked(val macros: Boolean) : TodayScreenChoiceAction
}
