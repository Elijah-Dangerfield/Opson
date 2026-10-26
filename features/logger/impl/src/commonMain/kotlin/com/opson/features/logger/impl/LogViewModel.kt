package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.logger.LogRoute
import com.opson.features.logger.LogTab
import com.opson.libraries.core.AppState
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.SavedMealRepository
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.toLogDraft
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.ui.components.text.NumberInput
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_toast_added_to

/**
 * The Log screen behind Describe it, Saved meals and Just a number. Every way
 * out logs to [day] and the chosen slot in one write, then closes back to
 * where the logger was opened.
 */
@Inject
class LogViewModel(
    @Assisted route: LogRoute,
    private val diary: DiaryRepository,
    savedMeals: SavedMealRepository,
    private val foodCatalog: FoodCatalog,
    settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    calendar: LocalCalendar,
    private val appState: AppState,
    private val telemetry: LoggerTelemetry,
) : SEAViewModel<LogState, LogEvent, LogAction>(
    initialStateArg = LogState(slot = route.slot, tab = route.tab),
) {
    private val day: LocalDate = LocalDate.parse(route.dayIso)

    private var model = Model(
        slot = route.slot,
        tab = route.tab,
        focusNumber = route.focusNumber,
        policy = policySource.policy.value,
    )

    private data class Model(
        val slot: MealSlot,
        val tab: LogTab,
        val focusNumber: Boolean,
        val policy: VisibilityPolicy,
        val inputs: LogInputs? = null,
        val text: String = "",
        val describe: DescribeModel = DescribeModel.Idle,
        val number: String = "",
    )

    init {
        viewModelScope.launch {
            combine(
                diary.observeDay(day),
                diary.observeDay(day.minus(1, DateTimeUnit.DAY)),
                settingsRepository.settings,
                savedMeals.observeAll(),
            ) { d, before, settings, meals ->
                LogInputs(today = calendar.today(), day = d, dayBefore = before, settings = settings, savedMeals = meals)
            }.collect { takeAction(LogAction.InputsArrived(it)) }
        }
        viewModelScope.launch {
            policySource.policy.collect { takeAction(LogAction.PolicyArrived(it)) }
        }
    }

    override suspend fun handleAction(action: LogAction) {
        when (action) {
            is LogAction.InputsArrived -> action.rebuild { copy(inputs = action.inputs) }
            is LogAction.PolicyArrived -> action.rebuild { copy(policy = action.policy) }
            is LogAction.SlotPicked -> action.rebuild { copy(slot = action.slot) }
            is LogAction.TabPicked -> action.rebuild { copy(tab = action.tab, focusNumber = false) }
            is LogAction.DescribeTyped -> action.rebuild { copy(text = action.text, describe = DescribeModel.Idle) }
            LogAction.GoTapped -> parse(action)
            is LogAction.DescribeArrived -> action.rebuild {
                copy(describe = if (action.items.isEmpty()) DescribeModel.Failed else DescribeModel.Parsed(action.items))
            }
            LogAction.DescribeFailed -> action.rebuild {
                copy(describe = if (appState.isOffline.value) DescribeModel.Offline else DescribeModel.Failed)
            }
            LogAction.AddParsedTapped -> addParsed()
            is LogAction.NumberTyped -> action.rebuild { copy(number = NumberInput.sanitize(action.text, allowDecimal = false)) }
            LogAction.AddNumberTapped -> addNumber()
            LogAction.SaveNameOnlyTapped -> saveNameOnly()
            LogAction.CopyDayBeforeTapped -> copyDayBefore()
            is LogAction.SavedMealAddTapped -> addSavedMeal(action.id)
            LogAction.BackTapped -> sendEvent(LogEvent.Close)
        }
    }

    private fun slot(): MealSlot {
        val inputs = model.inputs ?: return model.slot
        return if (inputs.day.mealGroupsOn(inputs.settings)) model.slot else MealSlot.Uncategorized
    }

    private suspend fun parse(action: LogAction) {
        val text = model.text.trim()
        if (text.isEmpty() || model.describe == DescribeModel.Parsing) return
        action.rebuild { copy(describe = DescribeModel.Parsing) }
        viewModelScope.launch {
            val result = foodCatalog.describe(text)
            takeAction(result.getOrNull()?.let { LogAction.DescribeArrived(it) } ?: LogAction.DescribeFailed)
        }
    }

    private suspend fun addParsed() {
        val items = (model.describe as? DescribeModel.Parsed)?.items ?: return
        log(items.map { it.toLogDraft() }, LogMethod.Describe, LogVia.Describe)
    }

    private suspend fun addNumber() {
        // Typing a number is a calorie entry, so the policy has the last word even if the field was showing.
        if (model.policy.level(Metric.Calories) == Visibility.Hide) return
        val kcal = model.number.toIntOrNull()?.takeIf { it > 0 } ?: return
        val draft = LogDraft(name = QUICK_ADD_NAME, precision = Precision.Named, kcal = kcal.toDouble(), method = LogMethod.QuickNumber)
        log(listOf(draft), LogMethod.QuickNumber, LogVia.QuickNumber)
    }

    private suspend fun saveNameOnly() {
        val name = model.text.trim().ifEmpty { return }
        log(listOf(LogDraft(name = name, precision = Precision.Named)), LogMethod.Describe, LogVia.NameOnly)
    }

    private suspend fun copyDayBefore() {
        val slot = slot()
        val copied = diary.copySlot(day.minus(1, DateTimeUnit.DAY), day, slot)
        telemetry.methodPicked(LogVia.Yesterday, copied.size)
        close(slot)
    }

    private suspend fun addSavedMeal(id: String) {
        val meal = model.inputs?.savedMeals?.firstOrNull { it.id == id } ?: return
        log(meal.items.map { it.copy(method = LogMethod.Saved) }, LogMethod.Saved, LogVia.Saved)
    }

    private suspend fun log(drafts: List<LogDraft>, method: LogMethod, via: LogVia) {
        if (drafts.isEmpty()) return
        val slot = slot()
        diary.addItems(day, slot, drafts, method)
        telemetry.methodPicked(via, drafts.size)
        close(slot)
    }

    private fun close(slot: MealSlot) {
        sendEvent(LogEvent.ShowToast(Toast(Res.string.logger_toast_added_to, listOf(slot.sentenceName))))
        sendEvent(LogEvent.Close)
    }

    private suspend fun LogAction.rebuild(change: Model.() -> Model) {
        model = model.change()
        val current = model
        val inputs = current.inputs
        if (inputs == null) {
            updateState { it.copy(slot = current.slot, tab = current.tab) }
            return
        }
        updateState {
            LogStateBuilder.build(
                inputs = inputs,
                policy = current.policy,
                slot = current.slot,
                tab = current.tab,
                text = current.text,
                describe = current.describe,
                number = current.number,
                focusNumber = current.focusNumber,
            )
        }
    }

    companion object {
        /**
         * The stored name of a "just a number" entry, as the prototype logs it.
         * It is the item's name in the diary (and on the server), not screen
         * copy, so it is a constant rather than a resource.
         */
        const val QUICK_ADD_NAME = "Quick add"
    }
}
