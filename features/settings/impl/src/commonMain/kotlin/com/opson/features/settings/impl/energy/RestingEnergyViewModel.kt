package com.opson.features.settings.impl.energy

import androidx.lifecycle.viewModelScope
import com.opson.libraries.core.DeviceRegion
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.BurnedRepository
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.health.RestingChoices
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.EverydayActivity
import com.opson.libraries.nutrition.HeightInput
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.RegionUnits
import com.opson.libraries.nutrition.RestingEnergies
import com.opson.libraries.nutrition.RestingSource
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.ui.components.text.NumberInput
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Inject

/**
 * Resting energy. A typed field writes its setting only once it holds a sensible value (a year
 * a person could be born in, a height, a resting energy from 500 to 5,000), so typing "1600"
 * writes once and a half-typed number never reaches Burned or sync. What is typed stays on screen
 * until then.
 */
@Inject
class RestingEnergyViewModel(
    private val settingsRepository: SettingsRepository,
    burnedRepository: BurnedRepository,
    policySource: VisibilityPolicySource,
    health: HealthRepository,
    healthFlag: HealthReleaseFlag,
    private val calendar: LocalCalendar,
    private val region: DeviceRegion,
) : SEAViewModel<RestingEnergyState, RestingEnergyEvent, RestingEnergyAction>(initialStateArg = RestingEnergyState()) {

    private var inputs: RestingEnergyAction.InputsArrived? = null
    private var drafts = RestingDrafts()

    init {
        val activity = if (healthFlag()) {
            health.status.map { status -> health.source.takeIf { status == HealthStatus.Connected } }
        } else {
            flowOf(null)
        }
        viewModelScope.launch {
            combine(settingsRepository.observe(), burnedRepository.restingChoices(), policySource.policy, activity, RestingEnergyAction::InputsArrived)
                .collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: RestingEnergyAction) {
        when (action) {
            is RestingEnergyAction.InputsArrived -> {
                inputs = action
                rebuild(action)
            }
            is RestingEnergyAction.SourcePicked -> settingsRepository.update { it.copy(restingSource = action.source) }
            is RestingEnergyAction.OwnTyped -> {
                val text = NumberInput.sanitize(action.text, allowDecimal = false)
                drafts = drafts.copy(own = text)
                rebuild(action)
                val kcal = text.toIntOrNull()?.takeIf { it in RestingEnergies.OWN_MIN_KCAL..RestingEnergies.OWN_MAX_KCAL }
                if (kcal != null) settingsRepository.update { it.copy(restingOwnKcal = kcal, restingSource = RestingSource.Own) }
            }
            is RestingEnergyAction.BirthYearTyped -> {
                val text = NumberInput.sanitize(action.text, allowDecimal = false).take(YEAR_DIGITS)
                drafts = drafts.copy(birthYear = text)
                rebuild(action)
                val year = text.toIntOrNull()?.takeIf { it in birthYears() }
                if (year != null) settingsRepository.update { it.copy(birthYear = year) }
            }
            is RestingEnergyAction.HeightUnitPicked -> {
                drafts = drafts.copy(height = height().inUnit(action.unit))
                settingsRepository.update { it.copy(heightUnit = action.unit) }
                rebuild(action)
            }
            is RestingEnergyAction.HeightCmTyped -> heightTyped(action) { it.copy(cm = NumberInput.sanitize(action.text, allowDecimal = true)) }
            is RestingEnergyAction.HeightFeetTyped -> heightTyped(action) { it.copy(feet = NumberInput.sanitize(action.text, allowDecimal = false)) }
            is RestingEnergyAction.HeightInchesTyped -> heightTyped(action) { it.copy(inches = NumberInput.sanitize(action.text, allowDecimal = true)) }
            is RestingEnergyAction.SexPicked -> settingsRepository.update { it.copy(bodySex = action.sex) }
            is RestingEnergyAction.ActivityInBurnedToggled -> settingsRepository.update { it.copy(activityInBurned = action.on) }
            is RestingEnergyAction.EverydayPicked -> settingsRepository.update { it.copy(everydayActivity = action.activity) }
            RestingEnergyAction.ActivityTapped -> sendEvent(RestingEnergyEvent.OpenIntegrations)
            RestingEnergyAction.BackTapped -> sendEvent(RestingEnergyEvent.Close)
        }
    }

    /** The height fields as they stand: a draft, or else the stored height. */
    private fun height(): HeightInput =
        RestingEnergyStateBuilder.height(inputs?.settings ?: Settings(), drafts, region.height())

    /** A height field typed: kept as typed, and saved in centimetres once it is a height in range. */
    private suspend fun heightTyped(action: RestingEnergyAction, edit: (HeightInput) -> HeightInput) {
        val typed = edit(height())
        drafts = drafts.copy(height = typed)
        rebuild(action)
        val cm = typed.cmOrNull()
        if (cm != null) settingsRepository.update { it.copy(heightCm = cm) }
    }

    /** Someone at least 13, since the equation is for adults and teenagers, and no older than 120. */
    private fun birthYears(): IntRange = calendar.today().year.let { (it - MAX_AGE)..(it - MIN_AGE) }

    private suspend fun rebuild(action: RestingEnergyAction) {
        val (settings, choices, policy, activity) = inputs ?: return
        action.updateState { RestingEnergyStateBuilder.build(settings, choices, policy, drafts, activity, region.height()) }
    }

    private companion object {
        const val YEAR_DIGITS = 4
        const val MIN_AGE = 13
        const val MAX_AGE = 120
    }
}

/** The region's height unit, shown until the person picks one. */
private fun DeviceRegion.height(): HeightUnit = RegionUnits.height(countryCode())

sealed interface RestingEnergyEvent {
    data object OpenIntegrations : RestingEnergyEvent
    data object Close : RestingEnergyEvent
}

sealed interface RestingEnergyAction {
    data class InputsArrived(
        val settings: Settings,
        val choices: RestingChoices,
        val policy: VisibilityPolicy,
        val activitySource: HealthSource?,
    ) : RestingEnergyAction

    data class SourcePicked(val source: RestingSource) : RestingEnergyAction

    /** Typing in "Enter my own" picks it, once the number is sensible. */
    data class OwnTyped(val text: String) : RestingEnergyAction
    data class BirthYearTyped(val text: String) : RestingEnergyAction
    /** Centimetres, or feet and inches. Saved, so the screen opens in it next time. */
    data class HeightUnitPicked(val unit: HeightUnit) : RestingEnergyAction
    data class HeightCmTyped(val text: String) : RestingEnergyAction
    data class HeightFeetTyped(val text: String) : RestingEnergyAction
    data class HeightInchesTyped(val text: String) : RestingEnergyAction
    data class SexPicked(val sex: BodySex) : RestingEnergyAction
    data class ActivityInBurnedToggled(val on: Boolean) : RestingEnergyAction
    data class EverydayPicked(val activity: EverydayActivity) : RestingEnergyAction
    data object ActivityTapped : RestingEnergyAction
    data object BackTapped : RestingEnergyAction
}
