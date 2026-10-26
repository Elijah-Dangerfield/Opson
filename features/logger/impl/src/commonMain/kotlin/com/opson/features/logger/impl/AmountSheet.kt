package com.opson.features.logger.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.FoodUnit
import com.opson.libraries.diary.HouseholdAmount
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.AmountUnit
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.PortionAmount
import com.opson.libraries.foods.amountUnits
import com.opson.libraries.foods.chipLabel
import com.opson.libraries.foods.draftFor
import com.opson.libraries.foods.gramsLabel
import com.opson.libraries.foods.initialQuantity
import com.opson.libraries.foods.isByWeight
import com.opson.libraries.foods.isUsda
import com.opson.libraries.navigation.Route
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.GoalStyle
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.fromWireKey
import com.opson.libraries.nutrition.share
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlin.math.round

/**
 * The amount sheet over the logger. Only the logger opens it, so the route
 * lives in the impl. [source] is where the food was picked, carried through
 * to the basket for the method and telemetry. [dayIso] and [slot] are where
 * its "Add to {meal}" logs; the sheet's Meal dropdown can change the slot.
 */
@Serializable
data class AmountSheetRoute(
    val foodId: String,
    val isCustom: Boolean,
    val source: PickSource,
    val dayIso: String,
    val slot: MealSlot,
) : Route() {
    val ref: FoodRef get() = if (isCustom) FoodRef.Custom(foodId) else FoodRef.Catalog(foodId)
}

/**
 * What the amount sheet draws (design sync 2, 3.3 and 3.3b). Every number in it comes from the
 * policy; unit weights and the source line are amounts of food, not nutrients, so they stay in
 * every mode.
 */
data class AmountState(
    val loading: Boolean = true,
    /** The food could not be loaded: offline and never cached. */
    val missing: Boolean = false,
    val name: String = "",
    /** The brand or cuisine under the name, if any. */
    val tag: String? = null,
    /** The star: saved to Favorites. */
    val favorite: Boolean = false,
    /** Where "Add to {meal}" logs, and the Meal dropdown: every meal, Uncategorized last. */
    val slot: MealSlot = MealSlot.Dinner,
    val slots: List<MealSlot> = MealSlot.entries,
    /** When it was eaten: now until the person changes it. */
    val time: LocalTime = LocalTime(0, 0),
    val timeIsNow: Boolean = true,
    val precision: Precision = Precision.Estimate,
    /** False for "Name only": no amount, so no stepper and no numbers. */
    val showAmount: Boolean = true,
    /** The stepper's count: "1", "1½", "150". */
    val count: String = "",
    val canLess: Boolean = false,
    /** The unit menu, each with its weight ("plate", "350 g"); grams has none. */
    val units: List<UnitUi> = emptyList(),
    val unitIndex: Int = 0,
    /** "Custom size…" at the end of the menu: a food whose weight is known. */
    val canAddUnit: Boolean = false,
    /** ½, 1, 1½, 2 under the stepper; none while counting grams. */
    val quick: List<QuickAmount> = emptyList(),
    /** "This portion": null when energy and every macro hide (Blind), or for Name only. */
    val portion: PortionCard? = null,
    /** "Your day with this": null when every row hides. */
    val day: DayWithThis? = null,
    /** All nutrients: null unless vitamins and minerals are visible in Modes and numbers. */
    val nutrients: AllNutrients? = null,
    val source: SourceLine? = null,
    /** The Custom size dialog, while it is open. */
    val customSize: CustomSizeUi? = null,
) {
    val unit: UnitUi? get() = units.getOrNull(unitIndex)
}

data class UnitUi(val label: String, val grams: String?)

data class QuickAmount(val label: String, val quantity: Double, val selected: Boolean)

/**
 * "This portion": the energy, the portion's weight, and each macro with its share of the
 * energy. A macro's [PortionMacro.amount] carries that share as its fraction, which draws the
 * split bar; the bar only shows when energy and all three macros do.
 */
data class PortionCard(val energy: Display, val grams: String?, val macros: List<PortionMacro>) {
    val showsSplit: Boolean
        get() = energy is Display.Shown && macros.count { it.metric != Metric.Fiber && it.amount is Display.Shown } == SPLIT_MACROS
}

data class PortionMacro(val metric: Metric, val amount: Display, val share: Display)

/** Today so far, this food, and what the day would be: one row per metric that shows. */
data class DayWithThis(val rows: List<DayRow>)

/** [before] and [after] carry the bar's fractions; a softened row has only [after]'s word. */
data class DayRow(val metric: Metric, val before: Display, val added: Display, val after: Display)

data class AllNutrients(val open: Boolean, val rows: List<NutrientRow>)

data class NutrientRow(val micro: Micronutrient, val display: Display)

/** "Per 350 g plate · Source: USDA FoodData Central". [per] is null for grams. */
data class SourceLine(val per: String?, val source: FoodOrigin?)

enum class FoodOrigin { Usda, OpenFoodFacts, Yours }

/** What has been typed into the Custom size dialog. */
data class CustomSizeUi(val name: String = "", val grams: String = "") {
    val canSave: Boolean get() = name.isNotBlank() && (grams.toDoubleOrNull() ?: 0.0) > 0.0
}

private const val SPLIT_MACROS = 3

/** The food being sized, catalog or the user's own. */
sealed interface AmountFood {
    val name: String

    /** Its units, with the person's [saved] sizes for it. */
    fun units(saved: List<FoodUnit>): List<AmountUnit>

    data class Catalog(val food: Food) : AmountFood {
        override val name: String get() = food.name
        override fun units(saved: List<FoodUnit>): List<AmountUnit> = food.amountUnits(saved)
    }

    data class Custom(val food: CustomFood) : AmountFood {
        override val name: String get() = food.name
        override fun units(saved: List<FoodUnit>): List<AmountUnit> = food.amountUnits(saved)
    }

    val tag: String?
        get() = when (this) {
            is Catalog -> food.brand
            is Custom -> food.brand
        }

    /** A size in grams converts to it: every catalog food, and a custom food counted in grams. */
    val takesSizes: Boolean
        get() = when (this) {
            is Catalog -> true
            is Custom -> food.isByWeight
        }

    fun initialQuantity(unit: AmountUnit): Double = when (this) {
        is Catalog -> PortionAmount.initial(unit)
        is Custom -> if (unit is AmountUnit.Saved) 1.0 else food.initialQuantity
    }
}

/** What the person has chosen on the sheet so far. [unit] null is the food's first unit. */
data class AmountChoice(
    val precision: Precision = Precision.Estimate,
    val unit: AmountUnit? = null,
    val quantity: Double = 1.0,
    val slot: MealSlot = MealSlot.Dinner,
    /** Null is now. */
    val time: LocalTime? = null,
    val nutrientsOpen: Boolean = false,
    val customSize: CustomSizeUi? = null,
) {
    /** The unit in force among [units]: the chosen one if it is still there, else the first. */
    fun unitIn(units: List<AmountUnit>): AmountUnit = unit?.takeIf { it in units } ?: units.first()

    companion object {
        fun initial(food: AmountFood, slot: MealSlot, saved: List<FoodUnit> = emptyList()): AmountChoice {
            val unit = food.units(saved).first()
            return AmountChoice(unit = unit, quantity = food.initialQuantity(unit), slot = slot)
        }

        /** Reopening a picked food starts from what was picked. */
        fun from(food: AmountFood, item: BasketItem, slot: MealSlot, saved: List<FoodUnit> = emptyList(), time: LocalTime? = null): AmountChoice {
            val fresh = initial(food, slot, saved)
            val unit = food.units(saved).firstOrNull { it.label == item.draft.unit || it.chipLabel == item.draft.unit }
            val quantity = item.draft.quantity
            val known = unit != null && quantity != null
            return fresh.copy(
                precision = item.draft.precision,
                unit = if (known) unit else fresh.unit,
                quantity = if (known) quantity!! else fresh.quantity,
                time = time,
            )
        }
    }
}

/** The draft for [choice] with [saved] sizes, which the sheet shows and both of its buttons put in the basket. */
fun AmountFood.draftFor(choice: AmountChoice, source: PickSource, ref: FoodRef, saved: List<FoodUnit> = emptyList()): LogDraft {
    val unit = choice.unitIn(units(saved))
    val method = source.methodFor(ref)
    val draft = when (this) {
        is AmountFood.Catalog -> food.draftFor(unit, choice.quantity, choice.precision, method)
        is AmountFood.Custom -> food.draftFor(unit, choice.quantity, choice.precision, method)
    }
    return if (choice.precision == Precision.Named) draft.nameOnly() else draft
}

/** Everything the amount sheet reads besides the food and the choice. */
data class AmountInputs(
    val settings: Settings,
    val policy: VisibilityPolicy,
    /** The day it logs to as it stands, for "Your day with this". */
    val day: Day?,
    val today: LocalDate,
    /** The clock now, shown while the time is now. */
    val now: LocalTime,
)

/** The amount sheet's state as a pure function, like [LoggerStateBuilder]. Surface [Surface.Logger]. */
object AmountStateBuilder {

    private val QUICK = listOf("½" to 0.5, "1" to 1.0, "1½" to 1.5, "2" to 2.0)

    fun build(food: AmountFood, choice: AmountChoice, inputs: AmountInputs, ref: FoodRef, source: PickSource): AmountState {
        val saved = inputs.settings.unitsFor(ref.key)
        val units = food.units(saved)
        val unit = choice.unitIn(units)
        val draft = food.draftFor(choice, source, ref, saved)
        val named = choice.precision == Precision.Named
        val groupsOn = inputs.day?.mealGroupsOn(inputs.settings) ?: inputs.settings.mealGroups
        return AmountState(
            loading = false,
            name = food.name,
            tag = food.tag,
            favorite = inputs.settings.isFavorite(ref.key),
            slot = if (groupsOn) choice.slot else MealSlot.Uncategorized,
            slots = if (groupsOn) MealSlot.entries else listOf(MealSlot.Uncategorized),
            time = choice.time ?: inputs.now,
            timeIsNow = choice.time == null,
            precision = choice.precision,
            showAmount = !named,
            count = if (named) "" else HouseholdAmount.number(choice.quantity),
            canLess = PortionAmount.canLess(unit, choice.quantity),
            units = units.map { UnitUi(it.chipLabel, it.gramsLabel) },
            unitIndex = units.indexOf(unit),
            canAddUnit = food.takesSizes,
            quick = if (unit is AmountUnit.Grams) {
                emptyList()
            } else {
                QUICK.map { (label, q) -> QuickAmount(label, q, selected = q == choice.quantity) }
            },
            portion = if (named) null else portion(draft, inputs.policy),
            day = if (named) null else dayWithThis(draft, inputs),
            nutrients = if (named) null else nutrients(draft, inputs.policy, choice.nutrientsOpen),
            source = sourceLine(food, unit),
            customSize = choice.customSize,
        )
    }

    private fun portion(draft: LogDraft, policy: VisibilityPolicy): PortionCard? {
        // Soften needs a goal and a portion has none, so a softened metric hides here.
        val kcal = draft.kcal
        val energy = policy.resolve(Metric.Calories, kcal, Surface.Logger, FormatContext(bare = true))
        val macros = listOf(
            Triple(Metric.Protein, draft.protein, KCAL_PER_G_PROTEIN),
            Triple(Metric.Carbs, draft.carbs, KCAL_PER_G_CARBS),
            Triple(Metric.Fat, draft.fat, KCAL_PER_G_FAT),
        ).map { (metric, grams, perGram) ->
            // The goal is the grams that would make up all the energy, so the fraction is this macro's share of it.
            val whole = kcal?.takeIf { it > 0.0 }?.div(perGram)
            val amount = policy.resolve(metric, grams, Surface.Logger, FormatContext(of = whole, goal = GoalStyle.None))
            val share = (amount as? Display.Shown)?.fraction?.let { policy.share(metric, it * PERCENT, Surface.Logger) } ?: Display.Hidden
            PortionMacro(metric, amount, share)
        } + PortionMacro(Metric.Fiber, policy.resolve(Metric.Fiber, draft.fiber, Surface.Logger), Display.Hidden)
        val shown = macros.filter { it.amount !is Display.Hidden }
        if (energy is Display.Hidden && shown.isEmpty()) return null
        return PortionCard(energy = energy, grams = draft.grams?.let { HouseholdAmount.of(round(it), "g") }, macros = shown)
    }

    private fun dayWithThis(draft: LogDraft, inputs: AmountInputs): DayWithThis? {
        val day = inputs.day ?: return null
        val policy = inputs.policy
        val targets = inputs.settings.targets
        val totals = day.totals()
        val goal = targets.rangeFor(day.date, policy.settings.preset).maxKcal.toDouble()
        val rows = listOf(
            Triple(Metric.Calories, totals.kcal.value, draft.kcal) to goal,
            Triple(Metric.Protein, totals.protein.value, draft.protein) to targets.proteinG.toDouble(),
            Triple(Metric.Carbs, totals.carbs.value, draft.carbs) to targets.carbsG.toDouble(),
            Triple(Metric.Fat, totals.fat.value, draft.fat) to targets.fatG.toDouble(),
        ).mapNotNull { (values, of) ->
            val (metric, before, added) = values
            if (added == null) return@mapNotNull null
            val so = before ?: 0.0
            val context = day.formatContext(inputs.today, of = of)
            // Energy reads "1,245 + 525 → 1,770 kcal", macros "51 g + 32 g → 83 of 120 g".
            val part = if (metric == Metric.Calories) context.copy(bare = true) else context.copy(goal = GoalStyle.None)
            val after = policy.resolve(metric, so + added, Surface.Logger, context)
            if (after is Display.Hidden) return@mapNotNull null
            DayRow(
                metric = metric,
                before = policy.resolve(metric, so, Surface.Logger, part),
                added = policy.resolve(metric, added, Surface.Logger, part.copy(of = null)),
                after = after,
            )
        }
        return rows.takeIf { it.isNotEmpty() }?.let(::DayWithThis)
    }

    private fun nutrients(draft: LogDraft, policy: VisibilityPolicy, open: Boolean): AllNutrients? {
        // Hidden unless vitamins and minerals are on in Modes and numbers.
        if (policy.level(Metric.Micros) == Visibility.Hide) return null
        val micros = draft.micros.orEmpty().mapNotNull { (key, amount) -> Micronutrient.fromWireKey(key)?.let { it to amount } }.toMap()
        val rows = Micronutrient.entries.mapNotNull { micro ->
            val amount = micros[micro] ?: return@mapNotNull null
            NutrientRow(micro, policy.resolve(Metric.Micros, micro.percentOf(amount), Surface.Logger, FormatContext(of = PERCENT)))
        }.filter { it.display !is Display.Hidden }
        return AllNutrients(open = open, rows = rows)
    }

    private fun sourceLine(food: AmountFood, unit: AmountUnit): SourceLine {
        val per = unit.gramsLabel?.let { "$it ${unit.chipLabel}" }
        val origin = when (food) {
            is AmountFood.Custom -> FoodOrigin.Yours
            is AmountFood.Catalog -> when {
                food.food.source.isUsda -> FoodOrigin.Usda
                food.food.source == FoodSource.OpenFoodFacts -> FoodOrigin.OpenFoodFacts
                else -> null
            }
        }
        return SourceLine(per, origin)
    }

    private const val KCAL_PER_G_PROTEIN = 4.0
    private const val KCAL_PER_G_CARBS = 4.0
    private const val KCAL_PER_G_FAT = 9.0
    private const val PERCENT = 100.0
}
