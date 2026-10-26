package com.opson.features.logger.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodAmount
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.draftFor
import com.opson.libraries.foods.foodRef
import com.opson.libraries.foods.initialQuantity
import com.opson.libraries.foods.toLogDraft
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.macroLine
import com.opson.libraries.ui.time.RelativeDay

/** A food the logger can put in the basket as-is: a row's default amount, ready to log. */
data class FoodPick(
    val key: String,
    val ref: FoodRef,
    val draft: LogDraft,
    val source: PickSource,
    val food: Food? = null,
    val subtitle: String? = null,
) {
    fun toBasketItem(): BasketItem = BasketItem(key = key, ref = ref, draft = draft, source = source, food = food)
}

/** What the person has done on the sheet so far, as opposed to what it read. */
data class LoggerChoices(
    val slot: MealSlot,
    val pane: LoggerPane = LoggerPane.Search,
    val query: String = "",
    val search: SearchOutcome? = null,
    val describeText: String = "",
    val describeStatus: DescribeStatus = DescribeStatus.Idle,
    val number: String = "",
    val focusSearch: Boolean = false,
    val focusNumber: Boolean = false,
    /** Picking for a meal Create a meal is putting together: search only, nothing written to the diary. */
    val pickingForMeal: Boolean = false,
    /** `release.barcode`, read once when the sheet opens. */
    val scanAvailable: Boolean = false,
    /** `release.voice`, read once when the sheet opens. */
    val voiceEnabled: Boolean = false,
    val voice: VoiceCapture = VoiceCapture.Idle(),
)

/**
 * The logger sheet's state as a pure function of what it reads and what the
 * user has done, so previews and the Blind leak test run the same mapping as
 * the app. Every number goes through [VisibilityPolicy] on [Surface.Logger].
 */
object LoggerStateBuilder {

    fun build(inputs: LoggerInputs, policy: VisibilityPolicy, choices: LoggerChoices): LoggerState {
        val groupsOn = inputs.day.mealGroupsOn(inputs.settings)
        val slot = effectiveSlot(inputs, choices.slot)
        val context = inputs.day.formatContext(inputs.today)
        val bare = inputs.day.formatContext(inputs.today, bare = true)
        val typing = choices.query.isNotBlank()
        val canNumber = policy.level(Metric.Calories) != Visibility.Hide && !choices.pickingForMeal
        return LoggerState(
            loading = false,
            day = RelativeDay.of(inputs.day.date, inputs.today),
            slot = slot,
            slots = if (groupsOn) MealSlot.entries else listOf(MealSlot.Uncategorized),
            pane = choices.pane,
            query = choices.query,
            focusSearch = choices.focusSearch,
            pickingForMeal = choices.pickingForMeal,
            // A scan logs into this meal, so a meal being put together has no scanner.
            canScan = choices.scanAvailable && !choices.pickingForMeal,
            // The method chips all log straight to the diary, so pick mode has none.
            methods = if (typing || choices.pickingForMeal) emptyList() else methods(inputs, policy, slot),
            body = body(inputs, policy, slot, choices, bare),
            describe = DescribeUi(
                text = choices.describeText,
                status = choices.describeStatus,
                lines = inputs.basket.filter { it.source == PickSource.Describe }.map { item ->
                    DescribedLine(
                        key = item.key,
                        ref = item.ref,
                        name = item.draft.name,
                        amount = item.draft.amountText.orEmpty(),
                        calories = policy.resolve(Metric.Calories, item.draft.kcal, Surface.Logger, bare),
                    )
                },
                canNumber = canNumber,
                number = if (canNumber) choices.number else "",
                focusNumber = choices.focusNumber && canNumber,
                // A spoken description becomes the same text and the same lines as a typed one.
                voice = VoiceStateBuilder.build(choices.voiceEnabled && !choices.pickingForMeal, choices.voice),
            ),
            saved = SavedUi(
                meals = inputs.savedMeals.map { meal ->
                    SavedMealUi(
                        id = meal.id,
                        name = meal.name,
                        foods = meal.items.joinToString(", ") { shortName(it.name) },
                        calories = policy.resolve(Metric.Calories, meal.totals.kcal.value, Surface.Logger, context),
                    )
                },
            ),
            basket = if (choices.pane == LoggerPane.Search) basket(inputs, policy, slot, context) else null,
        )
    }

    /** The slot a commit lands in: [slot], or Uncategorized when the day has meal groups off. */
    fun effectiveSlot(inputs: LoggerInputs, slot: MealSlot): MealSlot =
        if (inputs.day.mealGroupsOn(inputs.settings)) slot else MealSlot.Uncategorized

    /**
     * Every food the body can show for this slot and query, in section order.
     * The view model finds a tapped row's draft here, so the state never has
     * to carry one.
     */
    fun picks(inputs: LoggerInputs, slot: MealSlot, query: String, search: SearchOutcome?): List<FoodPick> {
        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) return searchPicks(inputs, trimmed, search).orEmpty()
        val sections = sections(inputs, effectiveSlot(inputs, slot))
        return sections.usual + sections.recent
    }

    private fun methods(inputs: LoggerInputs, policy: VisibilityPolicy, slot: MealSlot): List<LoggerMethod> = buildList {
        add(LoggerMethod.DescribeIt)
        if (inputs.dayBefore.items(slot).isNotEmpty()) add(LoggerMethod.Yesterday(slot))
        add(LoggerMethod.SavedMeals)
        if (slot.isMeal) add(LoggerMethod.AteNoDetails)
        add(if (policy.level(Metric.Calories) != Visibility.Hide) LoggerMethod.JustANumber else LoggerMethod.AddANote)
    }

    private fun body(
        inputs: LoggerInputs,
        policy: VisibilityPolicy,
        slot: MealSlot,
        choices: LoggerChoices,
        bare: FormatContext,
    ): LoggerBody {
        val picked = inputs.basket.mapTo(HashSet()) { it.key }
        fun rows(picks: List<FoodPick>) = picks.map { it.toRow(policy, bare, it.key in picked) }

        val trimmed = choices.query.trim()
        if (trimmed.isNotEmpty()) {
            val hits = searchPicks(inputs, trimmed, choices.search) ?: return LoggerBody.Searching
            val offline = choices.search?.offline == true
            return if (hits.isEmpty()) LoggerBody.NoResults(trimmed, offline) else LoggerBody.Results(rows(hits), offline)
        }

        val sections = sections(inputs, slot)
        if (sections.usual.isEmpty() && sections.recent.isEmpty()) return LoggerBody.FirstRun
        return LoggerBody.Browse(slot = slot, usual = rows(sections.usual), recent = rows(sections.recent))
    }

    private class Sections(val usual: List<FoodPick>, val recent: List<FoodPick>)

    private fun sections(inputs: LoggerInputs, slot: MealSlot): Sections {
        val usual = if (slot.isMeal) inputs.usualBySlot[slot].orEmpty().mapNotNull { it.toPick(PickSource.Usual) } else emptyList()
        val usualKeys = usual.mapTo(HashSet()) { it.key }
        // A food already under "Usual at dinner" is not repeated under Recent.
        val recent = inputs.recent.mapNotNull { it.toPick(PickSource.Recent) }.filterNot { it.key in usualKeys }
        return Sections(usual = usual, recent = recent)
    }

    /**
     * The user's own foods whose name or brand matches go first, then the
     * catalog's. Null while the catalog has not answered this query yet.
     */
    private fun searchPicks(inputs: LoggerInputs, query: String, search: SearchOutcome?): List<FoodPick>? {
        if (search == null || search.query != query) return null
        val custom = inputs.customFoods
            .filter { it.name.contains(query, ignoreCase = true) || it.brand?.contains(query, ignoreCase = true) == true }
            .map { it.toPick() }
        val catalog = search.foods.map { it.toPick() }
        return (custom + catalog).distinctBy { it.key }
    }

    private fun LogItem.toPick(source: PickSource): FoodPick? {
        val ref = foodRef() ?: return null
        return FoodPick(key = ref.key, ref = ref, draft = againDraft().copy(method = source.methodFor(ref)), source = source)
    }

    private fun Food.toPick(): FoodPick {
        val ref = FoodRef.Catalog(id)
        val draft = toLogDraft(FoodAmount.Servings(1.0), method = PickSource.Search.methodFor(ref))
        return FoodPick(key = ref.key, ref = ref, draft = draft, source = PickSource.Search, food = this, subtitle = brand)
    }

    private fun CustomFood.toPick(): FoodPick {
        val ref = FoodRef.Custom(id)
        return FoodPick(key = ref.key, ref = ref, draft = draftFor(initialQuantity), source = PickSource.Search, subtitle = brand)
    }

    private fun FoodPick.toRow(policy: VisibilityPolicy, bare: FormatContext, picked: Boolean) = FoodRowUi(
        key = key,
        ref = ref,
        source = source,
        name = draft.name,
        shortName = shortName(draft.name),
        subtitle = subtitle,
        amount = draft.amountText.orEmpty(),
        macros = if (draft.hasMacros) {
            policy.macroLine(draft.amounts(), Surface.Logger, bare, MacroLineStyle.Compact)
        } else {
            emptyList()
        },
        calories = policy.resolve(Metric.Calories, draft.kcal, Surface.Logger, bare),
        picked = picked,
    )

    private fun basket(inputs: LoggerInputs, policy: VisibilityPolicy, slot: MealSlot, context: FormatContext): BasketFooter? {
        val items = inputs.basket.ifEmpty { return null }
        return BasketFooter(
            count = items.size,
            names = items.joinToString(", ") { shortName(it.draft.name) },
            calories = policy.resolve(Metric.Calories, items.map { it.draft }.totalKcal(), Surface.Logger, context),
            slot = slot,
        )
    }
}
