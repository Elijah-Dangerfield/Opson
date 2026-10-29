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
import com.opson.libraries.foods.MeasureKind
import com.opson.libraries.foods.Portion
import com.opson.libraries.foods.amountText
import com.opson.libraries.foods.measureUnits
import com.opson.libraries.foods.ReviewStatus
import com.opson.libraries.foods.PortionAmount
import com.opson.libraries.foods.amountText
import com.opson.libraries.foods.chipLabel
import com.opson.libraries.foods.detailUnits
import com.opson.libraries.foods.draftFor
import com.opson.libraries.foods.gramsLabel
import com.opson.libraries.foods.initialQuantity
import com.opson.libraries.foods.isByWeight
import com.opson.libraries.foods.isMeasure
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
import com.opson.libraries.nutrition.WeightUnit
import com.opson.libraries.nutrition.macroEnergyShares
import com.opson.libraries.nutrition.share
import com.opson.libraries.nutrition.targetShare
import com.opson.libraries.nutrition.toMicronutrients
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.serialization.Serializable
import kotlin.math.round
import kotlin.time.Instant

/**
 * A food's detail, a full screen. Opened from a row's name in search, a barcode hit, and a food
 * chosen on the Foods tab or Today, so the route lives in the impl and the add sheet's
 * `prePickFoodId` stands in for it from outside. [source] is where the food was found, for the
 * method and telemetry. [dayIso] and [slot] are where "Add to {meal}" logs; null is the day Today
 * shows and the meal picked from this person's logging times. An entry already on the log opens
 * the same screen through `LoggedFoodRoute` instead ([DetailOpen.Entry]).
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

/** What the detail was opened on: a food to log, or one entry already on the log. */
sealed interface DetailOpen {
    /** A food to add, from search, a scan, Foods or a pre-pick. */
    data class Pick(val route: FoodDetailRoute) : DetailOpen

    /**
     * [entryId] on [day], from its row on Today. Everything the page saves or removes is that
     * entry alone, never "the food": the same food logged twice that day is a second entry.
     */
    data class Entry(val day: LocalDate, val entryId: String) : DetailOpen
}

/**
 * What the food detail draws: a list of rows (the amount and its unit, the meal, the time, how
 * sure), what is in the amount, how it fits today, and where the numbers come from. Every number
 * comes from the policy; unit weights and the weight of the amount are amounts of food, not
 * nutrients, so they stay in every mode but No numbers.
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
    /** On the Recent row now, so the ⋮ menu offers to take it off. */
    val inRecents: Boolean = false,
    /** Where "Add to {meal}" logs: every meal, Uncategorized last. One meal means meal groups are off, and no Meal row. */
    val slot: MealSlot = MealSlot.Dinner,
    val slots: List<MealSlot> = MealSlot.entries,
    /** When it was eaten: now until the person picks a time. */
    val time: LocalTime = LocalTime(0, 0),
    val timeIsNow: Boolean = true,
    /** The typed amount, as typed: "32", "1.5", or empty while the field is cleared. */
    val amount: String = "",
    /** The unit menu, each with its weight ("tbsp", "16 g"); weights and volumes themselves have none. */
    val units: List<UnitUi> = emptyList(),
    val unitIndex: Int = 0,
    /** "Custom size…" at the end of the menu: a food whose weight is known. */
    val canAddUnit: Boolean = false,
    /** "1 tbsp", "2 tbsp", "100 g": quick amounts, never with a weight beside them. */
    val servings: List<ServingChip> = emptyList(),
    /** "32 g": what the amount weighs, when it is counted in anything but grams and the weight is known. */
    val weight: String? = null,
    /** How sure: worked out from how the amount was given, unless the person picked one. */
    val precision: Precision = Precision.Estimate,
    /** The calories, each macro and its share of the energy: null when the policy leaves nothing in it. */
    val breakdown: Breakdown? = null,
    /** The day it logs to with this food in it, against the targets: null when the policy leaves nothing. */
    val targets: TodayTargets? = null,
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
    /** No numbers: the amount, the meal and the time, and the hunger check; no how sure, no grams. */
    val noNumbers: Boolean = false,
    /** No numbers' optional "How hungry were you?", null while the hunger questions are off. */
    val hunger: HungerCheck? = null,
    /** There is a food behind it to star or report: an entry that is only a name or a number has none. */
    val hasFood: Boolean = true,
    /** Opened on an entry already on the log: Save changes and Remove from log take Add's place. */
    val entry: EntryUi? = null,
) {
    val unit: UnitUi? get() = units.getOrNull(unitIndex)
}

/**
 * An entry on the log, as the detail edits it. [changed]: the amount, meal, time, how sure or
 * hunger differ from how it was logged, so Save changes has something to save.
 */
data class EntryUi(val changed: Boolean, val repeat: EntryRepeat? = null)

/** The ⋮'s repeat line: a new repeat from this entry, or the repeat that logged it (`release.repeats`). */
enum class EntryRepeat { New, Edit }

data class UnitUi(val label: String, val grams: String?)

/** "How hungry were you?", with the answer picked so far, if any. */
data class HungerCheck(val picked: Hunger?)

/** A quick amount: "1 tbsp". [selected] while the amount is exactly it. */
data class ServingChip(val label: String, val selected: Boolean)

/**
 * What is in the amount: [energy] big, then a split bar and a line per macro with its grams and
 * its share of the energy. A macro the policy hides is left out, and the shares are of the macros
 * on show, so a hidden one can't be read off the gap.
 */
data class Breakdown(val energy: Display, val macros: List<MacroRow>)

/** "Protein 8 g 17%". [weight] is the share as a fraction of the split bar; 0 when the share is hidden. */
data class MacroRow(val metric: Metric, val amount: Display, val share: Display, val weight: Float)

/**
 * Today with this: a row per number the policy shows, each the day so far plus this food against
 * its target. In Macros only the rows are words and bars.
 */
data class TodayTargets(val rows: List<TargetRow>)

/**
 * One row. [total] is the day with this food ("44 / 120 g"); for energy [bare] and [plusMinus]
 * spell "1,063 ± 160 kcal" when the day has a ±. [adds] is this food's share of the target
 * ("12%"). [soFar] and [food] are fractions of the target for the bar, before and with this food.
 */
data class TargetRow(
    val metric: Metric,
    val total: Display,
    val adds: Display,
    val soFar: Float,
    val food: Float,
    val bare: Display = Display.Hidden,
    val plusMinus: Display = Display.Hidden,
)

/** Vitamins, then minerals, each in the table's order, every one of them once the food lists any. */
data class AllNutrients(val open: Boolean, val groups: List<NutrientGroup>)

data class NutrientGroup(val group: MicroGroup, val rows: List<NutrientRow>)

/**
 * [display] is the portion's share of the person's daily reference, `12%`. [notListed]: the food
 * says nothing about this one, which is not the same as none, so the row says "Not listed".
 */
data class NutrientRow(val micro: Micronutrient, val display: Display, val notListed: Boolean = false)

/**
 * "From USDA FoodData Central · 34 nutrients listed". [nutrientCount] is how many nutrients the
 * source gives, shown beside All nutrients only, so a mode without that link shows no count.
 * [review] is whether anyone checked the numbers, where that is worth saying.
 */
data class SourceLine(
    val source: FoodOrigin?,
    val nutrientCount: Int? = null,
    val review: SourceReview? = null,
    /** The source was tapped: a line on what it is shows under its name. */
    val explained: Boolean = false,
)

/**
 * Where a food's numbers come from. [Opson] is OPDB, Opson's own database of packaged foods read
 * from people's photos and checked by a reviewer. [Estimate] is an entry Describe it or a photo
 * worked out with no catalog food behind it; [Typed] is a number the person entered themselves.
 */
enum class FoodOrigin { Usda, OpenFoodFacts, Nevo, Opson, Nutritionix, Yours, Estimate, Typed }

/** What review says about a food's numbers. */
enum class SourceReview {
    /** A catalog food someone accountable stands behind: USDA's, a label's, or one a reviewer approved. */
    Verified,

    /** In Opson's food database and waiting for a reviewer: only the person who added it sees it. */
    Unverified,

    /** The person's own copy of a food they sent for review, still waiting. */
    YoursInReview,

    /** The person's own copy of a food a reviewer has since approved for everyone. */
    YoursChecked,
}

/** What has been typed into the Custom size dialog. */
data class CustomSizeUi(val name: String = "", val grams: String = "") {
    val canSave: Boolean get() = name.isNotBlank() && (grams.toDoubleOrNull() ?: 0.0) > 0.0
}

/** The food being sized, catalog or the user's own. */
sealed interface AmountFood {
    val name: String

    /** Its units, with the person's [saved] sizes for it; ounces first when they weigh themselves in pounds. */
    fun units(saved: List<FoodUnit>, prefersOunces: Boolean = false): List<AmountUnit>

    data class Catalog(val food: Food) : AmountFood {
        override val name: String get() = food.name
        override fun units(saved: List<FoodUnit>, prefersOunces: Boolean): List<AmountUnit> = food.detailUnits(saved, prefersOunces)
    }

    data class Custom(val food: CustomFood) : AmountFood {
        override val name: String get() = food.name
        override fun units(saved: List<FoodUnit>, prefersOunces: Boolean): List<AmountUnit> = food.detailUnits(saved, prefersOunces)
    }

    /**
     * One entry already on the log. Its numbers are the snapshot it was logged with, scaled to a
     * new amount; [food], the catalog or custom food it came from while it is still there, only
     * lends its units. So a food corrected in the catalog since, or deleted, never changes what
     * this entry counted, and changing nothing saves nothing.
     */
    data class Logged(val item: LogItem, val food: AmountFood? = null) : AmountFood {
        override val name: String get() = item.name

        /** The unit it was logged in, as its food names it when it can. Null with no amount to scale: a quick number, a name. */
        val own: AmountUnit? = ownUnit()

        private val loggedGrams: Double? get() = item.grams?.takeIf { it > 0.0 }

        /**
         * The logged unit, and the food's other units when the entry's weight is known, since any
         * other unit converts through that weight. With no food, the weights alone.
         */
        override fun units(saved: List<FoodUnit>, prefersOunces: Boolean): List<AmountUnit> {
            val own = own ?: return emptyList()
            if (loggedGrams == null) return listOf(own)
            val others = (food?.units(saved, prefersOunces) ?: measureUnits(gramsPerMl = null, prefersOunces = prefersOunces))
                .filter { it.gramsEach != null }
            return if (own in others) others else listOf(own) + others
        }

        /**
         * [quantity] of [unit] at [precision]: the entry's own numbers times the new amount over
         * the old in its own unit, or the new weight over the old in any other. As logged, it is
         * exactly the entry.
         */
        fun draftFor(unit: AmountUnit, quantity: Double, precision: Precision): LogDraft {
            val asLogged = item.toDraft().copy(precision = precision)
            val from = item.quantity ?: return asLogged
            if (unit == own) {
                if (quantity == from) return asLogged
                return item.withQuantity(quantity)?.toDraft()?.copy(precision = precision) ?: asLogged
            }
            val grams = loggedGrams ?: return asLogged
            val newGrams = quantity * (unit.gramsEach ?: return asLogged)
            val amount = amountFields(unit, quantity)
            return item.scaled(newGrams / grams).toDraft().copy(
                precision = precision,
                amountText = amount.amountText,
                quantity = amount.quantity,
                unit = amount.unit,
                grams = newGrams,
            )
        }

        /** The amount written as a fresh log of the food would write it, so the row reads the same. */
        private fun amountFields(unit: AmountUnit, quantity: Double): LogDraft = when (val from = food) {
            is Catalog -> from.food.draftFor(unit, quantity)
            is Custom -> from.food.draftFor(unit, quantity)
            else -> LogDraft(name = name, precision = item.precision, amountText = unit.amountText(quantity), quantity = quantity, unit = unit.label)
        }

        private fun ownUnit(): AmountUnit? {
            val quantity = item.quantity?.takeIf { it > 0.0 } ?: return null
            val label = item.unit?.takeIf { it.isNotBlank() } ?: return null
            food?.units(emptyList(), prefersOunces = false)?.firstOrNull { it.label == label || it.chipLabel == label }?.let { return it }
            val each = loggedGrams?.div(quantity)
            if (label == AmountUnit.Grams.label) return AmountUnit.Grams
            MeasureKind.entries.firstOrNull { it.label == label }?.let { kind ->
                return AmountUnit.Measure(kind, if (kind.isVolume && each != null) each / kind.base else 1.0)
            }
            // A portion's label ("1 cup") counts portions; anything else ("bar") is a unit of its own.
            return if (each != null && label.first().isDigit()) AmountUnit.OfPortion(Portion(label, each)) else AmountUnit.Serving(label, each)
        }
    }

    val tag: String?
        get() = when (this) {
            is Catalog -> food.brand
            is Custom -> food.brand
            is Logged -> food?.tag
        }

    /** A size in grams converts to it: every catalog food, and a custom food whose serving has a weight. Not an entry. */
    val takesSizes: Boolean
        get() = when (this) {
            is Catalog -> true
            is Custom -> food.isByWeight
            is Logged -> false
        }

    fun initialQuantity(unit: AmountUnit): Double = when (this) {
        // A weight or volume starts at about 100 g of the food, as grams do.
        is Catalog -> if (unit is AmountUnit.Measure) PortionAmount.convert(GRAMS_START, AmountUnit.Grams, unit) else PortionAmount.initial(unit)
        is Custom -> when (unit) {
            is AmountUnit.Saved -> 1.0
            is AmountUnit.Serving -> food.initialQuantity
            else -> food.gramsPerServing?.let { PortionAmount.convert(it, AmountUnit.Grams, unit) } ?: food.initialQuantity
        }
        is Logged -> {
            val quantity = item.quantity ?: 1.0
            own?.let { PortionAmount.convert(quantity, it, unit) } ?: quantity
        }
    }

    private companion object {
        const val GRAMS_START = 100.0
    }
}

/**
 * What the person has chosen on the detail so far. [unit] null is the food's first unit.
 * [typed] is the field's text while the person is typing in it; null shows [quantity], and means
 * the amount came from a chip, the menu or the last time. [picked] is a how-sure the person chose.
 */
data class DetailChoice(
    val picked: Precision? = null,
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
    /** The source line tapped open, to say what the source is. */
    val sourceOpen: Boolean = false,
) {
    /** The unit in force among [units]: the chosen one if it is still there, else the first. */
    fun unitIn(units: List<AmountUnit>): AmountUnit = unit?.takeIf { it in units } ?: units.first()

    /**
     * How sure, unless the person picked: an amount typed in grams or another weight or volume
     * was measured, so Weighed; a scanned label's serving is From label; anything else is an
     * Estimate. A chip or the menu is not typing.
     */
    fun precision(unit: AmountUnit, source: PickSource): Precision = picked ?: when {
        typed != null && unit.isMeasure -> Precision.Weighed
        source == PickSource.Barcode && !unit.isMeasure -> Precision.Label
        else -> Precision.Estimate
    }

    fun typed(text: String): DetailChoice = copy(typed = text, quantity = text.toDoubleOrNull() ?: 0.0)

    companion object {
        fun initial(food: AmountFood, slot: MealSlot, saved: List<FoodUnit> = emptyList(), prefersOunces: Boolean = false): DetailChoice {
            val unit = food.units(saved, prefersOunces).first()
            return DetailChoice(unit = unit, quantity = food.initialQuantity(unit), slot = slot)
        }

        /**
         * An entry as it was logged: its amount in its own unit, its how-sure, meal and time, and
         * the meal's hunger answer. How sure stays as it was logged until the person picks
         * another or types a weight.
         */
        fun forEntry(food: AmountFood.Logged, time: LocalTime, hunger: Hunger?): DetailChoice = DetailChoice(
            unit = food.own,
            quantity = food.item.quantity ?: 1.0,
            slot = food.item.slot,
            time = time,
            hunger = hunger,
        )

        /** Opens at the person's last amount of this food, worked out again however it was logged. */
        fun lastUsed(food: AmountFood, item: LogItem, slot: MealSlot, saved: List<FoodUnit> = emptyList(), prefersOunces: Boolean = false): DetailChoice {
            val fresh = initial(food, slot, saved, prefersOunces)
            val unit = food.units(saved, prefersOunces).firstOrNull { it.label == item.unit || it.chipLabel == item.unit }
            val quantity = item.quantity
            return if (unit != null && quantity != null && quantity > 0.0) fresh.copy(unit = unit, quantity = quantity) else fresh
        }
    }
}

/** The draft for [choice] in [unit]: what the detail shows and Add logs. */
fun AmountFood.draftFor(choice: DetailChoice, unit: AmountUnit, source: PickSource, ref: FoodRef?): LogDraft {
    val method = source.methodFor(ref)
    return when (this) {
        is AmountFood.Catalog -> food.draftFor(unit, choice.quantity, choice.precision(unit, source), method)
        is AmountFood.Custom -> food.draftFor(unit, choice.quantity, choice.precision(unit, source), method)
        // An entry keeps how sure it was logged as, unless the person picks another or weighs it now.
        is AmountFood.Logged -> draftFor(unit, choice.quantity, choice.picked ?: if (choice.typed != null && unit.isMeasure) Precision.Weighed else item.precision)
    }
}

/** An entry with no amount to change (a quick number, a name): itself, at the how-sure picked. */
private fun AmountFood.Logged.asLogged(choice: DetailChoice): LogDraft = item.toDraft().copy(precision = choice.picked ?: item.precision)

/** Every amount and nutrient times [factor]; for an amount in a unit the entry was not logged in. */
private fun LogItem.scaled(factor: Double): LogItem = copy(
    kcal = kcal?.times(factor),
    protein = protein?.times(factor),
    carbs = carbs?.times(factor),
    fat = fat?.times(factor),
    fiber = fiber?.times(factor),
    micros = micros?.mapValues { it.value * factor },
)

/** Everything the detail reads besides the food and the choice. */
data class DetailInputs(
    val settings: Settings,
    val policy: VisibilityPolicy,
    /** The day it logs to as it stands, for Today with this. */
    val day: Day?,
    val today: LocalDate,
    /** The clock now, shown while the time is now. */
    val now: LocalTime,
    /** The last 14 days, for the usual size of a meal eaten with no details in the ±. */
    val history: List<Day> = emptyList(),
    /** On the Recent row now. */
    val inRecents: Boolean = false,
    /** Where review stands for the person's own food, when they sent it to Opson's food database. */
    val review: ReviewStatus? = null,
    /** Opened on an entry: whether it differs from how it was logged, and its repeat. */
    val entry: EntryUi? = null,
)

/** The food detail's state as a pure function, like [LoggerStateBuilder]. Surface [Surface.Logger]. */
object FoodDetailStateBuilder {

    /** How sure, in the menu's order. Name only is not offered here: "Save the name" makes those. */
    val PRECISIONS = listOf(Precision.Estimate, Precision.Weighed, Precision.Label)

    /**
     * No numbers: the mode lays Today out as a meal timeline and every nutrient hides, so the
     * detail is the amount in household measures, the meal, the time and the optional hunger
     * check, nothing else. A number the person turns back on brings the full detail back.
     */
    fun isNoNumbers(policy: VisibilityPolicy): Boolean =
        policy.preset.today == TodayLayout.MealTimeline && NUTRITION.all { policy.level(it) == Visibility.Hide }

    /** Someone who weighs themselves in pounds sees ounces before grams. */
    fun prefersOunces(settings: Settings): Boolean = settings.weightUnit == WeightUnit.Lb

    /** The unit menu: in No numbers household measures only, unless a weight is all the food has. */
    fun shownUnits(food: AmountFood, settings: Settings, ref: FoodRef?, noNumbers: Boolean): List<AmountUnit> {
        val units = food.units(ref?.let { settings.unitsFor(it.key) }.orEmpty(), prefersOunces(settings))
        if (!noNumbers) return units
        // An entry logged in grams keeps its own unit: the row already says it.
        val own = (food as? AmountFood.Logged)?.own
        return units.filter { !it.isMeasure || it == own }.ifEmpty { units.take(1) }
    }

    /** The unit in force, or null for an entry with no amount to scale: no Amount row, and its draft is itself. */
    fun unitFor(food: AmountFood, choice: DetailChoice, units: List<AmountUnit>): AmountUnit? =
        if (units.isEmpty() && food is AmountFood.Logged) null else choice.unitIn(units)

    /** What the detail shows and Add or Save changes writes, in [unit] ([unitFor]). */
    fun draft(food: AmountFood, choice: DetailChoice, unit: AmountUnit?, source: PickSource, ref: FoodRef?): LogDraft =
        if (unit == null && food is AmountFood.Logged) food.asLogged(choice) else food.draftFor(choice, unit ?: AmountUnit.Grams, source, ref)

    /** [ref] is the food behind it: null for an entry that is only a name or a number. */
    @Suppress("LongMethod")
    fun build(food: AmountFood, choice: DetailChoice, inputs: DetailInputs, ref: FoodRef?, source: PickSource): FoodDetailState {
        val noNumbers = isNoNumbers(inputs.policy)
        val units = shownUnits(food, inputs.settings, ref, noNumbers)
        val unit = unitFor(food, choice, units)
        val draft = draft(food, choice, unit, source, ref)
        val groupsOn = inputs.day?.mealGroupsOn(inputs.settings) ?: inputs.settings.mealGroups
        val entry = inputs.entry
        val base = FoodDetailState(
            loading = false,
            name = food.name,
            tag = food.tag,
            favorite = ref?.let { inputs.settings.isFavorite(it.key) } ?: false,
            inRecents = inputs.inRecents && entry == null,
            slot = if (groupsOn) choice.slot else MealSlot.Uncategorized,
            slots = if (groupsOn) MealSlot.entries else listOf(MealSlot.Uncategorized),
            time = choice.time ?: inputs.now,
            timeIsNow = choice.time == null,
            amount = choice.typed ?: number(choice.quantity),
            unitIndex = unit?.let(units::indexOf) ?: -1,
            servings = unit?.let { servings(units, it, choice) }.orEmpty(),
            precision = draft.precision,
            canAdd = choice.quantity > 0.0 && (entry == null || entry.changed),
            hasFood = ref != null,
            entry = entry,
        )
        val origin = origin(food)
        val review = review(food, inputs.review)
        if (noNumbers) {
            return base.copy(
                // A weight is a number too: the menu and the chips are measures, with no grams beside them.
                units = units.map { UnitUi(it.chipLabel, grams = null) },
                noNumbers = true,
                hunger = HungerCheck(choice.hunger),
                // The source still names itself (NEVO asks to be cited wherever its foods show).
                source = SourceLine(origin, review = review, explained = choice.sourceOpen),
            )
        }
        val nutrients = nutrients(draft, inputs, choice.nutrientsOpen)
        return base.copy(
            units = units.map { UnitUi(it.chipLabel, it.gramsLabel) },
            canAddUnit = food.takesSizes,
            weight = unit?.let { weight(it, choice.quantity, draft) },
            breakdown = breakdown(draft, inputs.policy),
            targets = targets(draft, inputs),
            nutrients = nutrients,
            source = SourceLine(origin, nutrientCount = nutrients?.let { draft.nutrientCount() }, review = review, explained = choice.sourceOpen),
            customSize = choice.customSize,
        )
    }

    /** The quick amounts: one and two of the food's first household measure, and 100 g when it weighs anything. */
    fun servingAmounts(units: List<AmountUnit>): List<Pair<AmountUnit, Double>> {
        val household = units.firstOrNull { !it.isMeasure }
        return buildList {
            if (household != null) {
                add(household to 1.0)
                add(household to 2.0)
            }
            if (AmountUnit.Grams in units) add(AmountUnit.Grams to GRAMS_CHIP)
        }
    }

    private fun servings(units: List<AmountUnit>, unit: AmountUnit, choice: DetailChoice): List<ServingChip> =
        servingAmounts(units).map { (chipUnit, count) ->
            ServingChip(label = chipUnit.amountText(count), selected = chipUnit == unit && count == choice.quantity)
        }

    /** What the amount weighs, said once: "32 g" under "2 tbsp". Grams already say it, and an unknown weight says nothing. */
    private fun weight(unit: AmountUnit, quantity: Double, draft: LogDraft): String? {
        if (unit == AmountUnit.Grams || quantity <= 0.0) return null
        val grams = draft.grams?.takeIf { unit.gramsEach != null && it > 0.0 } ?: return null
        return HouseholdAmount.of(round(grams), "g")
    }

    /** A typed field's number: "32", "1.5", never "1½", which nobody can type. */
    fun number(quantity: Double): String {
        val rounded = round(quantity * HUNDRED) / HUNDRED
        return if (rounded == round(rounded)) rounded.toLong().toString() else rounded.toString()
    }

    private fun breakdown(draft: LogDraft, policy: VisibilityPolicy): Breakdown? {
        // Soften needs a goal and a portion has none, so a softened metric hides here.
        val energy = policy.resolve(Metric.Calories, draft.kcal, Surface.Logger, FormatContext(bare = true))
        val amounts = mapOf(Metric.Protein to draft.protein, Metric.Carbs to draft.carbs, Metric.Fat to draft.fat)
        val shares = macroEnergyShares(draft.protein, draft.carbs, draft.fat) {
            policy.effectiveLevel(it, Surface.Logger) == Visibility.Show
        }
        val macros = amounts.mapNotNull { (metric, grams) ->
            val amount = policy.resolve(metric, grams, Surface.Logger, FormatContext(goal = GoalStyle.None))
            if (amount is Display.Hidden) return@mapNotNull null
            val percent = shares[metric]
            val share = percent?.let { policy.share(metric, it, Surface.Logger) } ?: Display.Hidden
            MacroRow(metric, amount, share, weight = if (share is Display.Shown && percent != null) percent.toFloat() else 0f)
        }
        if (energy is Display.Hidden && macros.isEmpty()) return null
        return Breakdown(energy = energy, macros = macros)
    }

    /** The day it logs to with this food in it, against each target, as Today reads it. */
    private fun targets(draft: LogDraft, inputs: DetailInputs): TodayTargets? {
        val day = inputs.day ?: return null
        val targets = inputs.settings.targets
        val totals = day.totals()
        val rows = listOfNotNull(
            energyRow(draft, day, inputs),
            macroRow(Metric.Protein, totals.protein.known, draft.protein, targets.proteinG, day, inputs),
            macroRow(Metric.Carbs, totals.carbs.known, draft.carbs, targets.carbsG, day, inputs),
            macroRow(Metric.Fat, totals.fat.known, draft.fat, targets.fatG, day, inputs),
        )
        return rows.takeIf { it.isNotEmpty() }?.let(::TodayTargets)
    }

    private fun energyRow(draft: LogDraft, day: Day, inputs: DetailInputs): TargetRow? {
        val added = draft.kcal ?: return null
        val policy = inputs.policy
        val of = inputs.settings.targets.rangeFor(day.date, policy.settings.preset).maxKcal.toDouble()
        val context = day.formatContext(inputs.today, of = of)
        val soFar = day.totals().kcal.value ?: 0.0
        val total = policy.resolve(Metric.Calories, soFar + added, Surface.Logger, context)
        if (total is Display.Hidden) return null
        val withThis = day.copy(itemsBySlot = day.itemsBySlot + (MealSlot.Uncategorized to day.items(MealSlot.Uncategorized) + draft.asItem(day.date)))
        val spread = withThis.eatenUncertainty(UsualMealSize.from(inputs.history, inputs.settings.targets.energyKcal)).plusMinusKcal
        val bare = context.copy(bare = true)
        return TargetRow(
            metric = Metric.Calories,
            total = total,
            adds = policy.targetShare(Metric.Calories, added / of * PERCENT, Surface.Logger, context),
            soFar = fraction(soFar, of),
            food = fraction(added, of),
            bare = policy.resolve(Metric.Calories, soFar + added, Surface.Logger, bare),
            // A softened day reads as a word, and a word has no ±.
            plusMinus = if (spread > 0 && total is Display.Shown) {
                policy.resolve(Metric.Calories, spread.toDouble(), Surface.Logger, bare.copy(of = null))
            } else {
                Display.Hidden
            },
        )
    }

    @Suppress("LongParameterList")
    private fun macroRow(metric: Metric, soFar: Double, added: Double?, target: Int, day: Day, inputs: DetailInputs): TargetRow? {
        val grams = added ?: return null
        val of = target.toDouble().takeIf { it > 0.0 } ?: return null
        val context = day.formatContext(inputs.today, of = of).copy(goal = GoalStyle.Slash)
        val total = inputs.policy.resolve(metric, soFar + grams, Surface.Logger, context)
        if (total is Display.Hidden) return null
        return TargetRow(
            metric = metric,
            total = total,
            adds = inputs.policy.targetShare(metric, grams / of * PERCENT, Surface.Logger, context),
            soFar = fraction(soFar, of),
            food = fraction(grams, of),
        )
    }

    private fun fraction(amount: Double, of: Double): Float = (amount / of).toFloat().coerceAtLeast(0f)

    private fun nutrients(draft: LogDraft, inputs: DetailInputs, open: Boolean): AllNutrients? {
        val policy = inputs.policy
        // Hidden unless vitamins and minerals are on in Modes and numbers.
        if (policy.level(Metric.Micros) == Visibility.Hide) return null
        val micros = draft.micros.orEmpty().toMicronutrients()
        // A link that opens onto nothing but "Not listed" reads as broken; a food with no micro data has none.
        if (micros.isEmpty()) return null
        val references = inputs.settings.microReferences(inputs.today)
        val groups = MicroGroup.entries.map { group ->
            val rows = Micronutrient.inGroup(group).mapNotNull { micro ->
                // A micronutrient the food has no value for is unknown, not 0%: it says so.
                val amount = micros[micro] ?: return@mapNotNull NutrientRow(micro, Display.Hidden, notListed = true)
                val display = policy.resolve(Metric.Micros, references.percentOf(micro, amount), Surface.Logger, FormatContext(of = PERCENT))
                NutrientRow(micro, display).takeIf { display !is Display.Hidden }
            }
            NutrientGroup(group, rows)
        }.filter { it.rows.isNotEmpty() }
        // Every row not listed means the level hid the rest (delayed, say): nothing to open.
        return groups.takeIf { it.any { group -> group.rows.any { row -> !row.notListed } } }?.let { AllNutrients(open = open, groups = it) }
    }

    /**
     * Where the numbers come from. An entry names its food's source while the food is still
     * here. Without it, an entry of the person's own food is still theirs, one Describe it or a
     * photo worked out with no catalog food is an estimate, and a quick number is theirs too.
     */
    fun origin(food: AmountFood): FoodOrigin? = when (food) {
        is AmountFood.Custom -> FoodOrigin.Yours
        is AmountFood.Catalog -> when {
            food.food.source.isUsda -> FoodOrigin.Usda
            food.food.source == FoodSource.OpenFoodFacts -> FoodOrigin.OpenFoodFacts
            food.food.source == FoodSource.Nevo -> FoodOrigin.Nevo
            food.food.source == FoodSource.UserSubmitted -> FoodOrigin.Opson
            food.food.source == FoodSource.Nutritionix -> FoodOrigin.Nutritionix
            else -> null
        }
        is AmountFood.Logged -> food.food?.let(::origin) ?: when {
            food.item.customFoodId != null -> FoodOrigin.Yours
            food.item.method == LogMethod.QuickNumber -> FoodOrigin.Typed
            food.item.method == LogMethod.Describe && food.item.foodId == null -> FoodOrigin.Estimate
            else -> null
        }
    }

    /** [yours] is the review of the person's own food, when they sent it to Opson's food database. */
    private fun review(food: AmountFood, yours: ReviewStatus?): SourceReview? = when (food) {
        is AmountFood.Logged -> food.food?.let { review(it, yours) }
        is AmountFood.Custom -> when (yours) {
            ReviewStatus.Pending -> SourceReview.YoursInReview
            ReviewStatus.Approved -> SourceReview.YoursChecked
            ReviewStatus.Rejected, null -> null
        }
        is AmountFood.Catalog -> when {
            food.food.unverified -> SourceReview.Unverified
            food.food.verified -> SourceReview.Verified
            else -> null
        }
    }

    /** Energy, the four macros and everything else the source gives a number for. */
    private fun LogDraft.nutrientCount(): Int =
        listOfNotNull(kcal, protein, carbs, fat, fiber).size + micros.orEmpty().count { it.value.isFinite() }

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

/** The day without [entryId]: Today with this, on an entry being edited, counts it once, as it would be saved. */
internal fun Day.without(entryId: String): Day = copy(itemsBySlot = itemsBySlot.mapValues { (_, items) -> items.filterNot { it.id == entryId } })
