package com.opson.features.settings.impl.profile

import androidx.lifecycle.viewModelScope
import com.opson.libraries.core.DeviceRegion
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeighIn
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.flowroutines.SEAViewModel
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

/**
 * More › Profile. A typed field writes its setting only once it holds a sensible value (a year a
 * person could be born in, a height the equation takes, a body fat share), so a half-typed number
 * never reaches Burned or sync. What is typed stays on screen until then. Weight is never written
 * here: the row opens Weight, where weigh-ins are logged.
 */
@Inject
class ProfileViewModel(
    @Assisted private val back: ProfileBack,
    private val settingsRepository: SettingsRepository,
    weights: WeightRepository,
    policySource: VisibilityPolicySource,
    private val calendar: LocalCalendar,
    private val region: DeviceRegion,
) : SEAViewModel<ProfileState, ProfileEvent, ProfileAction>(initialStateArg = ProfileState(back = back)) {

    private var inputs: ProfileAction.InputsArrived? = null
    private var drafts = ProfileDrafts()

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

    /** Someone at least 13, since the equation is for adults and teenagers, and no older than 120. */
    private fun birthYears(): IntRange = calendar.today().year.let { (it - MAX_AGE)..(it - MIN_AGE) }

    private suspend fun rebuild(action: ProfileAction) {
        val (settings, latest, policy) = inputs ?: return
        action.updateState { ProfileStateBuilder.build(settings, latest, policy, calendar.timeZone(), drafts, region.height(), back) }
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
    data object WeightTapped : ProfileAction
    data object BackTapped : ProfileAction
}
