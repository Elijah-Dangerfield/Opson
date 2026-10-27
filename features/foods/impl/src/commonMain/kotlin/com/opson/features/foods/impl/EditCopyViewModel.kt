package com.opson.features.foods.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.foods.EditCopyRoute
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogItem
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.ui.components.text.NumberInput
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.foods_copy_saved

/**
 * Edit a copy: the entry's food as a new food of the person's own, saved with
 * [CustomFoodRepository] so it syncs and shows in Your foods and search. With "Use for this entry"
 * on, the entry then points at the copy through [DiaryRepository.updateItem]. The food the entry
 * came from, catalog or custom, is never written.
 *
 * [mine] is " (mine)"'s word, resolved by the entry point so this needs no resources to open.
 */
@Inject
class EditCopyViewModel(
    @Assisted private val route: EditCopyRoute,
    @Assisted private val mine: String,
    private val diary: DiaryRepository,
    private val customFoods: CustomFoodRepository,
    private val calendar: LocalCalendar,
    private val policySource: VisibilityPolicySource,
) : SEAViewModel<EditCopyState, EditCopyEvent, EditCopyAction>(initialStateArg = EditCopyState()) {

    private var item: LogItem? = null
    private var form: CopyForm? = null
    private var isToday = true
    private var policy: VisibilityPolicy = policySource.policy.value

    /** Set once the copy is written, so a second Save queued behind the first adds nothing. */
    private var saved = false

    init {
        viewModelScope.launch {
            val day = LocalDate.parse(route.dayIso)
            val entry = diary.observeDay(day).first().allItems.firstOrNull { it.id == route.entryId }
            if (entry == null) {
                sendEvent(EditCopyEvent.Close)
                return@launch
            }
            val brand = entry.customFoodId?.let { customFoods.get(it) }?.brand
            takeAction(EditCopyAction.EntryArrived(entry, brand, day == calendar.today(), policySource.policy.value))
        }
    }

    override suspend fun handleAction(action: EditCopyAction) {
        when (action) {
            is EditCopyAction.EntryArrived -> {
                item = action.item
                isToday = action.isToday
                policy = action.policy
                // Opened once: the policy is read as it was then, so a field never vanishes mid-edit.
                form = EditCopyStateBuilder.form(action.item, action.brand, mine, action.policy)
                action.rebuild()
            }
            is EditCopyAction.NameTyped -> action.change { copy(name = action.text) }
            is EditCopyAction.BrandTyped -> action.change { copy(brand = action.text) }
            is EditCopyAction.NutritionTyped -> action.change {
                copy(nutrition = nutrition + (action.metric to NumberInput.sanitize(action.text, allowDecimal = true)))
            }
            EditCopyAction.UseForEntryToggled -> action.change { copy(useForEntry = !useForEntry) }
            EditCopyAction.SaveTapped -> save()
            EditCopyAction.CloseTapped -> sendEvent(EditCopyEvent.Close)
        }
    }

    private suspend fun save() {
        val entry = item ?: return
        val current = form ?: return
        if (saved || current.name.isBlank()) return
        val food = customFoods.create(EditCopyStateBuilder.draft(entry, current, policy))
        saved = true
        if (current.useForEntry) diary.updateItem(EditCopyStateBuilder.switched(entry, food))
        sendEvent(EditCopyEvent.ShowToast(Toast(Res.string.foods_copy_saved, listOf(food.name))))
        sendEvent(EditCopyEvent.Close)
    }

    private suspend fun EditCopyAction.change(transform: CopyForm.() -> CopyForm) {
        form = form?.transform() ?: return
        rebuild()
    }

    private suspend fun EditCopyAction.rebuild() {
        val entry = item ?: return
        val current = form ?: return
        val today = isToday
        val currentPolicy = policy
        updateState { EditCopyStateBuilder.build(entry, current, today, currentPolicy) }
    }
}
