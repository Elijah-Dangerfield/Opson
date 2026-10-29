package com.opson.features.settings.impl.profile

import androidx.lifecycle.viewModelScope
import com.opson.features.settings.impl.Toast
import com.opson.libraries.core.DeviceRegion
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.health.ConnectOutcome
import com.opson.libraries.health.HealthRecords
import com.opson.libraries.health.HealthReleaseFlag
import com.opson.libraries.health.HealthRepository
import com.opson.libraries.health.HealthSource
import com.opson.libraries.health.HealthStatus
import com.opson.libraries.nutrition.BodyFat
import com.opson.libraries.nutrition.BodySex
import com.opson.libraries.nutrition.HeightInput
import com.opson.libraries.nutrition.HeightUnit
import com.opson.libraries.nutrition.RegionUnits
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.ui.components.text.NumberInput
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.integrations_profile_empty
import opson.libraries.resources.generated.resources.integrations_profile_failed
import opson.libraries.resources.generated.resources.integrations_profile_filled
import opson.libraries.resources.generated.resources.integrations_profile_not_connected

/**
 * More › Profile. A typed field writes its setting only once it holds a sensible value (a year a
 * person could be born in, a height the equation takes, a body fat share), so a half-typed number
 * never reaches Burned or sync. What is typed stays on screen until then. Weight is never written
 * here: the row opens Weight, where weigh-ins are logged.
 */
@Inject
@Suppress("LongParameterList")
class ProfileViewModel(
    @Assisted private val back: ProfileBack,
    private val settingsRepository: SettingsRepository,
    weights: WeightRepository,
    policySource: VisibilityPolicySource,
    private val calendar: LocalCalendar,
    private val region: DeviceRegion,
    private val health: HealthRepository,
    private val records: HealthRecords,
    healthFlag: HealthReleaseFlag,
) : SEAViewModel<ProfileState, ProfileEvent, ProfileAction>(initialStateArg = ProfileState(back = back)) {

    private var inputs: ProfileAction.InputsArrived? = null
    private var drafts = ProfileDrafts()
    private var filling = false

    /** Offered with `release.health` on; tapping it connects first if need be. */
    private val fillFrom: HealthSource? = health.source.takeIf { healthFlag() }

    init {
        val latest = weights.observeWeighIns().map { list -> list.maxByOrNull { it.measuredAt } }.distinctUntilChanged()
        viewModelScope.launch {
            combine(settingsRepository.observe(), latest, policySource.policy, ProfileAction::InputsArrived).collect(::takeAction)
        }
    }

    override suspend fun handleAction(action: ProfileAction) {
        when (action) {
            is ProfileAction.InputsArrived -> {
                inputs = action
                rebuild(action)
            }
            is ProfileAction.BirthYearTyped -> {
                val text = NumberInput.sanitize(action.text, allowDecimal = false).take(YEAR_DIGITS)
                drafts = drafts.copy(birthYear = text)
                rebuild(action)
                val year = text.toIntOrNull()?.takeIf { it in birthYears() }
                if (year != null) settingsRepository.update { it.copy(birthYear = year) }
            }
            is ProfileAction.SexPicked -> settingsRepository.update { it.copy(bodySex = action.sex) }
            is ProfileAction.HeightUnitPicked -> {
                drafts = drafts.copy(height = height().inUnit(action.unit))
                settingsRepository.update { it.copy(heightUnit = action.unit) }
                rebuild(action)
            }
            is ProfileAction.HeightCmTyped -> heightTyped(action) { it.copy(cm = NumberInput.sanitize(action.text, allowDecimal = true)) }
            is ProfileAction.HeightFeetTyped -> heightTyped(action) { it.copy(feet = NumberInput.sanitize(action.text, allowDecimal = false)) }
            is ProfileAction.HeightInchesTyped -> heightTyped(action) { it.copy(inches = NumberInput.sanitize(action.text, allowDecimal = true)) }
            is ProfileAction.BodyFatTyped -> {
                val text = NumberInput.sanitize(action.text, allowDecimal = true)
                drafts = drafts.copy(bodyFat = text)
                rebuild(action)
                // Emptied is a choice too: it clears the stored value (zero, see Settings.bodyFatPercent).
                val percent = if (text.isBlank()) 0.0 else text.toDoubleOrNull()?.let(BodyFat::inRange)
                if (percent != null) settingsRepository.update { it.copy(bodyFatPercent = percent) }
            }
            ProfileAction.FillFromHealthTapped -> fillFromHealth(action)
            ProfileAction.WeightTapped -> sendEvent(ProfileEvent.OpenWeight)
            ProfileAction.BackTapped -> sendEvent(ProfileEvent.Close)
        }
    }

    /** The height fields as they stand: a draft, or else the stored height. */
    private fun height(): HeightInput = ProfileStateBuilder.height(inputs?.settings ?: Settings(), drafts, region.height())

    /** A height field typed: kept as typed, and saved in centimetres once it is a height in range. */
    private suspend fun heightTyped(action: ProfileAction, edit: (HeightInput) -> HeightInput) {
        val typed = edit(height())
        drafts = drafts.copy(height = typed)
        rebuild(action)
        val cm = typed.cmOrNull()
        if (cm != null) settingsRepository.update { it.copy(heightCm = cm) }
    }

    /**
     * Connects first when it isn't (the prompt comes from this tap), then writes what the store
     * has through [ProfileFill]. Drafts of the fields it fills go, so the new values show.
     */
    private suspend fun fillFromHealth(action: ProfileAction) {
        if (filling || fillFrom == null) return
        filling = true
        rebuild(action)
        val connected = health.status.value == HealthStatus.Connected || health.connect() == ConnectOutcome.Connected
        val profile = if (connected) records.profile() else null
        var filled = false
        if (profile != null) {
            settingsRepository.update { current -> ProfileFill.apply(current, profile, birthYears())?.also { filled = true } ?: current }
            if (filled) drafts = drafts.copy(birthYear = null, height = null)
        }
        filling = false
        rebuild(action)
        val toast = when {
            !connected -> Res.string.integrations_profile_not_connected
            profile == null -> Res.string.integrations_profile_failed
            !filled -> Res.string.integrations_profile_empty
            else -> Res.string.integrations_profile_filled
        }
        sendEvent(ProfileEvent.ShowToast(Toast(toast)))
    }

    /** Someone at least 13, since the equation is for adults and teenagers, and no older than 120. */
    private fun birthYears(): IntRange = calendar.today().year.let { (it - MAX_AGE)..(it - MIN_AGE) }

    private suspend fun rebuild(action: ProfileAction) {
        val (settings, latest, policy) = inputs ?: return
        action.updateState { ProfileStateBuilder.build(settings, latest, policy, calendar.timeZone(), drafts, region.height(), back, fillFrom, filling) }
    }

    private companion object {
        const val YEAR_DIGITS = 4
        const val MIN_AGE = 13
        const val MAX_AGE = 120
    }
}

/** The region's height unit, shown until the person picks one. */
private fun DeviceRegion.height(): HeightUnit = RegionUnits.height(countryCode())

sealed interface ProfileEvent {
    /** Weight, in Trends, where a weigh-in is logged. */
    data object OpenWeight : ProfileEvent
    data class ShowToast(val toast: Toast) : ProfileEvent
    data object Close : ProfileEvent
}

sealed interface ProfileAction {
    data class InputsArrived(val settings: Settings, val latest: WeighIn?, val policy: VisibilityPolicy) : ProfileAction
    data class BirthYearTyped(val text: String) : ProfileAction
    data class SexPicked(val sex: BodySex) : ProfileAction

    /** Centimetres, or feet and inches. Saved, so the page opens in it next time. */
    data class HeightUnitPicked(val unit: HeightUnit) : ProfileAction
    data class HeightCmTyped(val text: String) : ProfileAction
    data class HeightFeetTyped(val text: String) : ProfileAction
    data class HeightInchesTyped(val text: String) : ProfileAction
    data class BodyFatTyped(val text: String) : ProfileAction

    /** "Fill in from Apple Health" (or Health Connect). */
    data object FillFromHealthTapped : ProfileAction
    data object WeightTapped : ProfileAction
    data object BackTapped : ProfileAction
}
