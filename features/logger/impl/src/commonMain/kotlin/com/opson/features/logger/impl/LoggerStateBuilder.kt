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
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalStyle
import com.opson.libraries.nutrition.MacroLineStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.macroLine
import com.opson.libraries.ui.time.RelativeDay
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_totals_carbs
import opson.libraries.resources.generated.resources.logger_totals_energy
import opson.libraries.resources.generated.resources.logger_totals_fat
import opson.libraries.resources.generated.resources.logger_totals_protein

/** A food the logger can put in the basket as-is: a row's default amount, ready to log. */
data class FoodPick(
    val key: String,
    val ref: FoodRef,
    val draft: LogDraft,
    val source: PickSource,
    val food: Food? = null,
) {
    fun toBasketItem(): BasketItem = BasketItem(key = key, ref = ref, draft = draft, source = source, food = food)
}

/**
 * The logger sheet's state as a pure function of what it reads and what the
 * user has done, so previews and the Blind leak test run the same mapping as
 * the app. Every number goes through [VisibilityPolicy] on [Surface.Logger].
 */
object LoggerStateBuilder {
    private const val SUGGESTED_COUNT = 3

    fun build(
        inputs: LoggerInputs,
        policy: VisibilityPolicy,
        slot: MealSlot,
        query: String,
        search: SearchOutcome?,
        focusSearch: Boolean = false,
    ): LoggerState {
        val groupsOn = inputs.day.mealGroupsOn(inputs.settings)
        val effectiveSlot = effectiveSlot(inputs, slot)
        val totals = totals(inputs, policy)
        val context = inputs.day.formatContext(inputs.today)
        return LoggerState(
            loading = false,
            otherDay = RelativeDay.of(inputs.day.date, inputs.today).takeIf { it != RelativeDay.Today },
            slot = effectiveSlot,
            slots = if (groupsOn) MealSlot.entries else listOf(MealSlot.Uncategorized),
            totals = totals,
            mealChecks = if (totals == null) mealChecks(inputs, groupsOn) else null,
            query = query,
            focusSearch = focusSearch,
            methods = methods(inputs, policy, effectiveSlot),
            body = body(inputs, policy, effectiveSlot, query, search),
            basket = basket(inputs, policy, effectiveSlot, context),
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
        return sections.favorites + sections.usual + sections.recent
    }

    private fun totals(inputs: LoggerInputs, policy: VisibilityPolicy): List<TotalsColumn>? {
        val totals = inputs.day.totals()
        val targets = inputs.settings.targets
        fun column(metric: Metric, value: Double?, goal: Int) = TotalsColumn(
            metric = metric,
            label = when (metric) {
                Metric.Protein -> Res.string.logger_totals_protein
                Metric.Carbs -> Res.string.logger_totals_carbs
                Metric.Fat -> Res.string.logger_totals_fat
                else -> Res.string.logger_totals_energy
            },
            display = policy.resolve(
                metric,
                value,
                Surface.Logger,
                inputs.day.formatContext(inputs.today, of = goal.toDouble(), bare = true).copy(goal = GoalStyle.Slash),
            ),
        )
        return listOf(
            column(Metric.Calories, totals.kcal.value, targets.energyKcal),
            column(Metric.Protein, totals.protein.value, targets.proteinG),
            column(Metric.Carbs, totals.carbs.value, targets.carbsG),
            column(Metric.Fat, totals.fat.value, targets.fatG),
        ).filter { it.display !is Display.Hidden }.ifEmpty { null }
    }

    private fun mealChecks(inputs: LoggerInputs, groupsOn: Boolean): List<MealCheck> =
        (if (groupsOn) MealSlot.meals else listOf(MealSlot.Uncategorized))
            .map { MealCheck(it, inputs.day.isMealDone(it)) }

    private fun methods(inputs: LoggerInputs, policy: VisibilityPolicy, slot: MealSlot): List<LoggerMethod> = buildList {
        add(LoggerMethod.DescribeIt)
        if (policy.level(Metric.Water) != Visibility.Hide) add(LoggerMethod.Water)
        add(LoggerMethod.SavedMeals)
        if (inputs.dayBefore.items(slot).isNotEmpty()) add(LoggerMethod.Yesterday(slot))
        if (slot.isMeal) add(LoggerMethod.AteNoDetails)
        add(LoggerMethod.WeighIn)
        add(if (policy.level(Metric.Calories) != Visibility.Hide) LoggerMethod.JustANumber else LoggerMethod.AddANote)
    }

    private fun body(
        inputs: LoggerInputs,
        policy: VisibilityPolicy,
        slot: MealSlot,
        query: String,
        search: SearchOutcome?,
    ): LoggerBody {
        val picked = inputs.basket.mapTo(HashSet()) { it.key }
        val context = inputs.day.formatContext(inputs.today)
        fun rows(picks: List<FoodPick>) = picks.map { it.toRow(policy, context, it.key in picked) }

        val trimmed = query.trim()
        if (trimmed.isNotEmpty()) {
            val hits = searchPicks(inputs, trimmed, search) ?: return LoggerBody.Searching
            val offline = search?.offline == true
            return if (hits.isEmpty()) LoggerBody.NoResults(offline) else LoggerBody.Results(rows(hits), offline)
        }

        val sections = sections(inputs, slot)
        if (sections.favorites.isEmpty() && sections.usual.isEmpty() && sections.recent.isEmpty()) return LoggerBody.FirstRun
        return LoggerBody.Browse(
            favorites = rows(sections.favorites),
            usualTitle = sections.usualTitle,
            usual = rows(sections.usual),
            recent = rows(sections.recent),
        )
    }

    private class Sections(
        val favorites: List<FoodPick>,
        val usualTitle: UsualTitle,
        val usual: List<FoodPick>,
        val recent: List<FoodPick>,
    )

    private fun sections(inputs: LoggerInputs, slot: MealSlot): Sections {
        val favorites = inputs.favorites.mapNotNull { it.toPick(PickSource.Favorite) }
        val usual = if (slot.isMeal) inputs.usualBySlot[slot].orEmpty().mapNotNull { it.toPick(PickSource.Usual) } else emptyList()
        return Sections(
            favorites = favorites,
            usualTitle = if (usual.isNotEmpty()) UsualTitle.UsualAt(slot) else UsualTitle.Suggested,
            usual = usual.ifEmpty { favorites.take(SUGGESTED_COUNT) },
            recent = inputs.recent.mapNotNull { it.toPick(PickSource.Recent) },
        )
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
        return FoodPick(key = ref.key, ref = ref, draft = draft, source = PickSource.Search, food = this)
    }

    private fun CustomFood.toPick(): FoodPick {
        val ref = FoodRef.Custom(id)
        return FoodPick(key = ref.key, ref = ref, draft = draftFor(initialQuantity), source = PickSource.Search)
    }

    private fun FoodPick.toRow(policy: VisibilityPolicy, context: FormatContext, picked: Boolean) = FoodRowUi(
        key = key,
        ref = ref,
        source = source,
        name = draft.name,
        shortName = shortName(draft.name),
        amount = draft.amountText.orEmpty(),
        macros = if (draft.hasMacros) {
            policy.macroLine(draft.amounts(), Surface.Logger, context, MacroLineStyle.Compact)
        } else {
            emptyList()
        },
        calories = policy.resolve(Metric.Calories, draft.kcal, Surface.Logger, context),
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
