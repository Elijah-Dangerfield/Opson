package com.opson.features.logger.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Day
import com.opson.libraries.diary.FoodUnit
import com.opson.libraries.diary.HouseholdAmount
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.diary.UsualMealSize
import com.opson.libraries.diary.eatenUncertainty
import com.opson.libraries.foods.AmountUnit
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.foods.PortionAmount
import com.opson.libraries.foods.amountText
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
import com.opson.libraries.nutrition.MicroGroup
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.TodayLayout
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.toMicronutrients
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlin.math.round
import kotlin.time.Instant

/**
 * A food's detail (design sync 4, 13.4d), a full screen. Opened from a row's name in search, a
 * barcode hit, and a food chosen on the Foods tab or Today, so the route lives in the impl and
 * the add sheet's `prePickFoodId` stands in for it from outside. [source] is where the food was
 * found, for the method and telemetry. [dayIso] and [slot] are where "Add to {meal}" logs; null
 * is the day Today shows and the meal picked from this person's logging times.
 */
@Serializable
data class FoodDetailRoute(
    val foodId: String,
    val isCustom: Boolean,
    val source: PickSource,
    val dayIso: String? = null,
    val slot: MealSlot? = null,
) : Route() {
    val ref: FoodRef get() = if (isCustom) FoodRef.Custom(foodId) else FoodRef.Catalog(foodId)
}

/**
 * What the food detail draws. Every number in it comes from the policy; unit weights, the
 * serving chips' grams and the source line are amounts of food, not nutrients, so they stay in
 * every mode.
 */
data class FoodDetailState(
    val loading: Boolean = true,
    /** The food could not be loaded: offline and never cached. */
    val missing: Boolean = false,
    val name: String = "",
    /** The brand or cuisine under the name, if any. */
    val tag: String? = null,
    /** Saved to favorites, from the ⋮ menu. */
    val favorite: Boolean = false,
    /** Where "Add to {meal}" logs, and the ⋮ menu's Meal: every meal, Uncategorized last. */
    val slot: MealSlot = MealSlot.Dinner,
    val slots: List<MealSlot> = MealSlot.entries,
    /** When it was eaten: now until the person changes it in the ⋮ menu. */
    val time: LocalTime = LocalTime(0, 0),
    val timeIsNow: Boolean = true,
    /** The typed amount, as typed: "32", "1.5", or empty while the field is cleared. */
    val amount: String = "",
    /** The unit dropdown, each with its weight ("tbsp", "16 g"); grams has none. */
    val units: List<UnitUi> = emptyList(),
    val unitIndex: Int = 0,
    /** "Custom size…" at the end of the menu: a food whose weight is known. */
    val canAddUnit: Boolean = false,
    /** "1 tbsp · 16 g", "2 tbsp · 32 g", "100 g". */
    val servings: List<ServingChip> = emptyList(),
    /** How sure: Estimate, Weighed, From label. */
    val precision: Precision = Precision.Estimate,
    /** kcal, the macro tiles and "Today with this": null when energy and every macro hide. */
    val energy: EnergyCard? = null,
    /**
     * All nutrients: null unless vitamins and minerals are visible in Modes and numbers and the
     * food carries at least one of them.
     */
    val nutrients: AllNutrients? = null,
    val source: SourceLine? = null,
    /** The Custom size dialog, while it is open. */
    val customSize: CustomSizeUi? = null,
    /** An amount above nothing: Add is off while the field is empty or zero. */
    val canAdd: Boolean = false,
    /** No numbers (13.5e): the amount and the hunger check only; no how sure, no energy, no grams. */
    val noNumbers: Boolean = false,
    /** No numbers' optional "How hungry were you?", null while the hunger questions are off. */
    val hunger: HungerCheck? = null,
) {
    val unit: UnitUi? get() = units.getOrNull(unitIndex)
}

data class UnitUi(val label: String, val grams: String?)

/** "How hungry were you?", with the answer picked so far, if any. */
data class HungerCheck(val picked: Hunger?)

/** A serving chip: "1 tbsp" and "16 g". [selected] while the amount is exactly it. */
data class ServingChip(val label: String, val grams: String?, val selected: Boolean)

/**
 * The energy card: [energy] big, a tile per macro in its own colour, and the day with this food
 * in it. A tile the policy hides is left out.
 */
data class EnergyCard(val energy: Display, val macros: List<MacroTile>, val today: TodayWithThis?)

data class MacroTile(val metric: Metric, val amount: Display)

/**
 * "Today with this 1,435 ± 100 kcal": [total] with its unit, or as [bare] beside [plusMinus], the
 * day's ± from how each food was logged, this one at the how-sure picked (`eatenUncertainty`).
 * [plusMinus] is hidden while nothing in the day is uncertain, or the policy hides it.
 */
data class TodayWithThis(val total: Display, val bare: Display = Display.Hidden, val plusMinus: Display = Display.Hidden)

/** Vitamins, then minerals, each in the table's order; a group the food has nothing in is left out. */
data class AllNutrients(val open: Boolean, val groups: List<NutrientGroup>)

data class NutrientGroup(val group: MicroGroup, val rows: List<NutrientRow>)

/** [display] is the portion's share of the daily reference, `12%`. */
data class NutrientRow(val micro: Micronutrient, val display: Display)

/** "Per 350 g plate · Source: USDA FoodData Central". [per] is null for grams. */
data class SourceLine(val per: String?, val source: FoodOrigin?)

enum class FoodOrigin { Usda, OpenFoodFacts, Nevo, Yours }

/** What has been typed into the Custom size dialog. */
data class CustomSizeUi(val name: String = "", val grams: String = "") {
    val canSave: Boolean get() = name.isNotBlank() && (grams.toDoubleOrNull() ?: 0.0) > 0.0
}

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

/**
 * What the person has chosen on the detail so far. [unit] null is the food's first unit.
 * [typed] is the field's text while the person is typing in it; null shows [quantity].
 * [precisionPicked]: the person chose how sure themselves, so typing no longer changes it.
 */
data class DetailChoice(
    val precision: Precision = Precision.Estimate,
    val precisionPicked: Boolean = false,
    val unit: AmountUnit? = null,
    val quantity: Double = 1.0,
    val typed: String? = null,
    val slot: MealSlot = MealSlot.Dinner,
    /** Null is now. */
    val time: LocalTime? = null,
    val nutrientsOpen: Boolean = false,
    val customSize: CustomSizeUi? = null,
    /** No numbers' optional hunger check, saved to the meal when the food is added. */
    val hunger: Hunger? = null,
) {
    /** The unit in force among [units]: the chosen one if it is still there, else the first. */
    fun unitIn(units: List<AmountUnit>): AmountUnit = unit?.takeIf { it in units } ?: units.first()

    /**
     * The amount typed: Estimate becomes Weighed, since a typed number is a measured one, unless
     * the person already picked how sure they are. A chip or the unit menu is not typing. In No
     * numbers ([weighs] false) the amount is a household count and how sure is not asked, so it stays.
     */
    fun typed(text: String, weighs: Boolean = true): DetailChoice {
        val weighed = if (weighs && !precisionPicked && precision == Precision.Estimate) Precision.Weighed else precision
        return copy(typed = text, quantity = text.toDoubleOrNull() ?: 0.0, precision = weighed)
    }

    companion object {
        fun initial(food: AmountFood, slot: MealSlot, saved: List<FoodUnit> = emptyList()): DetailChoice {
            val unit = food.units(saved).first()
            return DetailChoice(unit = unit, quantity = food.initialQuantity(unit), slot = slot)
        }

        /** Opens at the person's last amount of this food, an estimate again however it was logged. */
        fun lastUsed(food: AmountFood, item: LogItem, slot: MealSlot, saved: List<FoodUnit> = emptyList()): DetailChoice {
            val fresh = initial(food, slot, saved)
            val unit = food.units(saved).firstOrNull { it.label == item.unit || it.chipLabel == item.unit }
            val quantity = item.quantity
            return if (unit != null && quantity != null && quantity > 0.0) fresh.copy(unit = unit, quantity = quantity) else fresh
        }
    }
}

/** The draft for [choice] with [saved] sizes: what the detail shows and Add logs. */
fun AmountFood.draftFor(choice: DetailChoice, source: PickSource, ref: FoodRef, saved: List<FoodUnit> = emptyList()): LogDraft {
    val unit = choice.unitIn(units(saved))
    val method = source.methodFor(ref)
    return when (this) {
        is AmountFood.Catalog -> food.draftFor(unit, choice.quantity, choice.precision, method)
        is AmountFood.Custom -> food.draftFor(unit, choice.quantity, choice.precision, method)
    }
}

/** Everything the detail reads besides the food and the choice. */
data class DetailInputs(
    val settings: Settings,
    val policy: VisibilityPolicy,
    /** The day it logs to as it stands, for "Today with this". */
    val day: Day?,
    val today: LocalDate,
    /** The clock now, shown while the time is now. */
    val now: LocalTime,
    /** The last 14 days, for the usual size of a meal eaten with no details in the ±. */
    val history: List<Day> = emptyList(),
)

/** The food detail's state as a pure function, like [LoggerStateBuilder]. Surface [Surface.Logger]. */
object FoodDetailStateBuilder {

    /** How sure, in the design's order. Name only is not offered here: "Save the name" makes those. */
    val PRECISIONS = listOf(Precision.Estimate, Precision.Weighed, Precision.Label)

    /**
     * No numbers (13.5e): the mode lays Today out as a meal timeline and every nutrient hides, so
     * the detail is the amount in household measures and the optional hunger check, nothing else.
     * A number the person turns back on brings the full detail back.
     */
    fun isNoNumbers(policy: VisibilityPolicy): Boolean =
        policy.preset.today == TodayLayout.MealTimeline && NUTRITION.all { policy.level(it) == Visibility.Hide }

    /** The unit menu: in No numbers household measures only, unless grams is all the food has. */
    fun shownUnits(food: AmountFood, saved: List<FoodUnit>, noNumbers: Boolean): List<AmountUnit> {
        val units = food.units(saved)
        if (!noNumbers) return units
        return units.filterNot { it is AmountUnit.Grams }.ifEmpty { units }
    }

    fun build(food: AmountFood, choice: DetailChoice, inputs: DetailInputs, ref: FoodRef, source: PickSource): FoodDetailState {
        val saved = inputs.settings.unitsFor(ref.key)
        val noNumbers = isNoNumbers(inputs.policy)
        val units = shownUnits(food, saved, noNumbers)
        val unit = choice.unitIn(units)
        val draft = food.draftFor(choice.copy(unit = unit), source, ref, saved)
        val groupsOn = inputs.day?.mealGroupsOn(inputs.settings) ?: inputs.settings.mealGroups
        if (noNumbers) return noNumbersState(food, choice, inputs, ref, units, unit, groupsOn)
        return FoodDetailState(
            loading = false,
            name = food.name,
            tag = food.tag,
            favorite = inputs.settings.isFavorite(ref.key),
            slot = if (groupsOn) choice.slot else MealSlot.Uncategorized,
            slots = if (groupsOn) MealSlot.entries else listOf(MealSlot.Uncategorized),
            time = choice.time ?: inputs.now,
            timeIsNow = choice.time == null,
            amount = choice.typed ?: number(choice.quantity),
            units = units.map { UnitUi(it.chipLabel, it.gramsLabel) },
            unitIndex = units.indexOf(unit),
            canAddUnit = food.takesSizes,
            servings = servings(units, unit, choice.quantity),
            precision = choice.precision,
            energy = energy(draft, inputs),
            nutrients = nutrients(draft, inputs.policy, choice.nutrientsOpen),
            source = sourceLine(food, unit),
            customSize = choice.customSize,
            canAdd = choice.quantity > 0.0,
        )
    }

    /** 13.5e: the amount, its unit and household chips, and the hunger check while the hunger questions are on. */
    @Suppress("LongParameterList")
    private fun noNumbersState(
        food: AmountFood,
        choice: DetailChoice,
        inputs: DetailInputs,
        ref: FoodRef,
        units: List<AmountUnit>,
        unit: AmountUnit,
        groupsOn: Boolean,
    ): FoodDetailState = FoodDetailState(
        loading = false,
        name = food.name,
        tag = food.tag,
        favorite = inputs.settings.isFavorite(ref.key),
        slot = if (groupsOn) choice.slot else MealSlot.Uncategorized,
        slots = if (groupsOn) MealSlot.entries else listOf(MealSlot.Uncategorized),
        time = choice.time ?: inputs.now,
        timeIsNow = choice.time == null,
        amount = choice.typed ?: number(choice.quantity),
        // A weight is a number too: the menu and the chips are measures, with no grams beside them.
        units = units.map { UnitUi(it.chipLabel, grams = null) },
        unitIndex = units.indexOf(unit),
        canAddUnit = false,
        servings = servings(units, unit, choice.quantity).map { it.copy(grams = null) },
        precision = choice.precision,
        noNumbers = true,
        hunger = HungerCheck(choice.hunger).takeIf { inputs.settings.askTags },
        // The source still names itself (NEVO asks to be cited wherever its foods show), without a weight.
        source = sourceLine(food, unit).copy(per = null),
        canAdd = choice.quantity > 0.0,
    )

    /** The serving chips: one and two of the food's first measure, and 100 g when it weighs anything. */
    fun servingAmounts(units: List<AmountUnit>): List<Pair<AmountUnit, Double>> {
        val household = units.firstOrNull { it !is AmountUnit.Grams }
        return buildList {
            if (household != null) {
                add(household to 1.0)
                add(household to 2.0)
            }
            if (AmountUnit.Grams in units) add(AmountUnit.Grams to GRAMS_CHIP)
        }
    }

    private fun servings(units: List<AmountUnit>, unit: AmountUnit, quantity: Double): List<ServingChip> =
        servingAmounts(units).map { (chipUnit, count) ->
            ServingChip(
                label = chipUnit.amountText(count),
                grams = if (chipUnit is AmountUnit.Grams) null else chipUnit.gramsEach?.let { HouseholdAmount.of(round(it * count), "g") },
                selected = chipUnit == unit && count == quantity,
            )
        }

    /** A typed field's number: "32", "1.5", never "1½", which nobody can type. */
    fun number(quantity: Double): String {
        val rounded = round(quantity * HUNDRED) / HUNDRED
        return if (rounded == round(rounded)) rounded.toLong().toString() else rounded.toString()
    }

    private fun energy(draft: LogDraft, inputs: DetailInputs): EnergyCard? {
        val policy = inputs.policy
        // Soften needs a goal and a portion has none, so a softened metric hides here.
        val energy = policy.resolve(Metric.Calories, draft.kcal, Surface.Logger, FormatContext(bare = true))
        val macros = listOf(Metric.Protein to draft.protein, Metric.Carbs to draft.carbs, Metric.Fat to draft.fat)
            .map { (metric, grams) -> MacroTile(metric, policy.resolve(metric, grams, Surface.Logger, FormatContext(goal = GoalStyle.None))) }
            .filter { it.amount !is Display.Hidden }
        if (energy is Display.Hidden && macros.isEmpty()) return null
        return EnergyCard(energy = energy, macros = macros, today = today(draft, inputs))
    }

    /** The day it logs to with this food in it, against the day's range, as Today reads it. */
    private fun today(draft: LogDraft, inputs: DetailInputs): TodayWithThis? {
        val day = inputs.day ?: return null
        val added = draft.kcal ?: return null
        val policy = inputs.policy
        val range = inputs.settings.targets.rangeFor(day.date, policy.settings.preset).maxKcal.toDouble()
        val context = day.formatContext(inputs.today, of = range)
        val sum = (day.totals().kcal.value ?: 0.0) + added
        val total = policy.resolve(Metric.Calories, sum, Surface.Logger, context)
        if (total is Display.Hidden) return null
        val withThis = day.copy(itemsBySlot = day.itemsBySlot + (MealSlot.Uncategorized to day.items(MealSlot.Uncategorized) + draft.asItem(day.date)))
        val spread = withThis.eatenUncertainty(UsualMealSize.from(inputs.history, inputs.settings.targets.energyKcal)).plusMinusKcal
        val bare = context.copy(bare = true)
        return TodayWithThis(
            total = total,
            bare = policy.resolve(Metric.Calories, sum, Surface.Logger, bare),
            // A softened day reads as a word, and a word has no ±.
            plusMinus = if (spread > 0 && total is Display.Shown) {
                policy.resolve(Metric.Calories, spread.toDouble(), Surface.Logger, bare.copy(of = null))
            } else {
                Display.Hidden
            },
        )
    }

    private fun nutrients(draft: LogDraft, policy: VisibilityPolicy, open: Boolean): AllNutrients? {
        // Hidden unless vitamins and minerals are on in Modes and numbers.
        if (policy.level(Metric.Micros) == Visibility.Hide) return null
        // Only what the food carries: a micronutrient it has no value for is unknown, not 0%.
        val micros = draft.micros.orEmpty().toMicronutrients()
        val groups = MicroGroup.entries.mapNotNull { group ->
            val rows = Micronutrient.inGroup(group).mapNotNull { micro ->
                val amount = micros[micro] ?: return@mapNotNull null
                NutrientRow(micro, policy.resolve(Metric.Micros, micro.percentOf(amount), Surface.Logger, FormatContext(of = PERCENT)))
            }.filter { it.display !is Display.Hidden }
            rows.takeIf { it.isNotEmpty() }?.let { NutrientGroup(group, it) }
        }
        // A link that opens onto nothing reads as broken; a food with no micro data has none.
        return groups.takeIf { it.isNotEmpty() }?.let { AllNutrients(open = open, groups = it) }
    }

    private fun sourceLine(food: AmountFood, unit: AmountUnit): SourceLine {
        val per = unit.gramsLabel?.let { "$it ${unit.chipLabel}" }
        val origin = when (food) {
            is AmountFood.Custom -> FoodOrigin.Yours
            is AmountFood.Catalog -> when {
                food.food.source.isUsda -> FoodOrigin.Usda
                food.food.source == FoodSource.OpenFoodFacts -> FoodOrigin.OpenFoodFacts
                food.food.source == FoodSource.Nevo -> FoodOrigin.Nevo
                else -> null
            }
        }
        return SourceLine(per, origin)
    }

    private const val PERCENT = 100.0
    private const val HUNDRED = 100.0
    private const val GRAMS_CHIP = 100.0
    private val NUTRITION = listOf(Metric.Calories, Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber, Metric.Micros)
}

/** This draft as if it were logged, so the day's ± counts it at its how-sure. Never written. */
private fun LogDraft.asItem(day: LocalDate): LogItem = LogItem(
    id = "food-detail",
    day = day,
    slot = MealSlot.Uncategorized,
    loggedAt = Instant.DISTANT_PAST,
    name = name,
    precision = precision,
    method = method ?: LogMethod.Search,
    kcal = kcal,
)
