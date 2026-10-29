@file:OptIn(ExperimentalObjCRefinement::class)

package com.opson.features.foods.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.foods.EditCopyRoute
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.FoodRef
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
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Edit a copy: a food as a new food of the person's own, saved with [CustomFoodRepository] so it
 * syncs and shows in Your foods and search. From a logged entry, with "Use for this entry" on, the
 * entry then points at the copy through [DiaryRepository.updateItem]. From a food's detail, the
 * copy's detail opens next. The food it came from, catalog or custom, is never written.
 *
 * [copyName] words the copy's name from the food's, "Peanut butter (mine)", with the entry point's
 * resources so this needs none to open.
 */
@Inject
@Suppress("LongParameterList", "TooManyFunctions")
class EditCopyViewModel(
    @Assisted private val route: EditCopyRoute,
    @Assisted private val copyName: CopyName,
    private val diary: DiaryRepository,
    private val customFoods: CustomFoodRepository,
    private val foodCatalog: FoodCatalog,
    private val settingsRepository: SettingsRepository,
    private val calendar: LocalCalendar,
    private val policySource: VisibilityPolicySource,
) : SEAViewModel<EditCopyState, EditCopyEvent, EditCopyAction>(initialStateArg = EditCopyState()) {

    private var seed: CopySeed? = null
    private var form: CopyForm? = null
    private var policy: VisibilityPolicy = policySource.policy.value

    /** Set once the copy is written, so a second Save queued behind the first adds nothing. */
    private var saved = false

    init {
        viewModelScope.launch {
            val found = load()
            if (found == null) {
                sendEvent(EditCopyEvent.Close)
                return@launch
            }
            val references = settingsRepository.settings.value.microReferences(calendar.today())
            takeAction(EditCopyAction.SeedArrived(found.copy(references = references), policySource.policy.value))
        }
    }

    /** The entry or the food the copy starts from, or null when it is gone (deleted, or never cached offline). */
    private suspend fun load(): CopySeed? {
        val day = LocalDate.parse(route.dayIso)
        route.entryId?.let { entryId ->
            val entry = diary.observeDay(day).first().allItems.firstOrNull { it.id == entryId } ?: return null
            val brand = entry.customFoodId?.let { customFoods.get(it) }?.brand
            return EditCopyStateBuilder.seed(entry, brand, isToday = day == calendar.today())
        }
        val foodId = route.foodId ?: return null
        val ref = if (route.isCustom) FoodRef.Custom(foodId) else FoodRef.Catalog(foodId)
        val sizes = settingsRepository.settings.value.unitsFor(ref.key)
        return if (route.isCustom) {
            customFoods.get(foodId)?.let { EditCopyStateBuilder.seed(it, sizes) }
        } else {
            foodCatalog.detail(foodId).getOrNull()?.let { EditCopyStateBuilder.seed(it, sizes) }
        }
    }

    override suspend fun handleAction(action: EditCopyAction) {
        when (action) {
            is EditCopyAction.SeedArrived -> {
                seed = action.seed
                policy = action.policy
                // Opened once: the policy is read as it was then, so a field never vanishes mid-edit.
                form = EditCopyStateBuilder.form(action.seed, copyName.of(action.seed.food.name), action.policy)
                action.rebuild()
            }
            is EditCopyAction.NameTyped -> action.change { copy(name = action.text) }
            is EditCopyAction.BrandTyped -> action.change { copy(brand = action.text) }
            is EditCopyAction.NutritionTyped -> action.change {
                copy(values = values + (action.key to NumberInput.sanitize(action.text, allowDecimal = true)))
            }
            is EditCopyAction.ServingNameTyped -> action.changeServing(action.index) { copy(name = action.text) }
            is EditCopyAction.ServingGramsTyped -> action.changeServing(action.index) {
                copy(grams = NumberInput.sanitize(action.text, allowDecimal = true))
            }
            EditCopyAction.ServingAdded -> action.change {
                copy(servings = servings + ServingRow(name = "", grams = "", removable = true)).takeIf { servings.size < MAX_SIZES } ?: this
            }
            is EditCopyAction.ServingRemoved -> action.change { EditCopyStateBuilder.withoutServing(this, action.index) }
            is EditCopyAction.PerPicked -> action.change {
                val to = EditCopyStateBuilder.perOptions(this).getOrNull(action.index)?.row ?: per
                EditCopyStateBuilder.rePer(this, to)
            }
            EditCopyAction.EnergyFixTapped -> {
                val fix = state.energyCheck?.fix ?: return
                action.change { copy(values = values + (CopyNutrient.KCAL to fix)) }
            }
            EditCopyAction.UseForEntryToggled -> action.change { copy(useForEntry = !useForEntry) }
            EditCopyAction.SaveTapped -> save()
            EditCopyAction.CloseTapped -> sendEvent(EditCopyEvent.Close)
        }
    }

    private suspend fun save() {
        val from = seed ?: return
        val current = form ?: return
        if (saved || current.name.isBlank()) return
        val food = customFoods.create(EditCopyStateBuilder.draft(from, current, policy))
        saved = true
        val entry = from.entry
        if (entry != null && current.useForEntry) diary.updateItem(EditCopyStateBuilder.switched(entry, food))
        sendEvent(EditCopyEvent.ShowToast(Toast(Res.string.foods_copy_saved, listOf(food.name))))
        if (entry == null) {
            sendEvent(EditCopyEvent.OpenCopy(food.id, route.dayIso, route.slot))
        } else {
            sendEvent(EditCopyEvent.Close)
        }
    }

    private suspend fun EditCopyAction.changeServing(index: Int, transform: ServingRow.() -> ServingRow) = change {
        if (index !in servings.indices) this else copy(servings = servings.mapIndexed { i, row -> if (i == index) row.transform() else row })
    }

    private suspend fun EditCopyAction.change(transform: CopyForm.() -> CopyForm) {
        form = form?.transform() ?: return
        rebuild()
    }

    private suspend fun EditCopyAction.rebuild() {
        val from = seed ?: return
        val current = form ?: return
        val currentPolicy = policy
        updateState { EditCopyStateBuilder.build(from, current, currentPolicy) }
    }

    private companion object {
        /** The label's serving and the server's cap on sizes (`DiaryRules.MAX_SIZES`). */
        const val MAX_SIZES = 21
    }
}

/** The copy's name from the food's: "Peanut butter (mine)". The entry point words it from its resources. */
@HiddenFromObjC
fun interface CopyName {
    suspend fun of(foodName: String): String
}
