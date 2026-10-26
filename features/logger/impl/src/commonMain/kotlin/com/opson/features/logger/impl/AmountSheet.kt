package com.opson.features.logger.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.Fullness
import com.opson.libraries.diary.Hunger
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.Settings
import com.opson.libraries.foods.AmountUnit
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.PortionAmount
import com.opson.libraries.foods.amountUnits
import com.opson.libraries.foods.draftFor
import com.opson.libraries.foods.initialQuantity
import com.opson.libraries.navigation.Route
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.MicroUnit
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.VisibilityPolicy
import kotlinx.serialization.Serializable
import opson.libraries.resources.generated.resources.Res
import opson.libraries.resources.generated.resources.logger_amount_calcium
import opson.libraries.resources.generated.resources.logger_amount_energy
import opson.libraries.resources.generated.resources.logger_amount_fiber
import opson.libraries.resources.generated.resources.logger_amount_protein
import org.jetbrains.compose.resources.StringResource

/**
 * The amount sheet over the logger. Only the logger opens it, so the route
 * lives in the impl. [source] is where the food was picked, carried through
 * to the basket for the method and telemetry.
 */
@Serializable
data class AmountSheetRoute(val foodId: String, val isCustom: Boolean, val source: PickSource) : Route() {
    val ref: FoodRef get() = if (isCustom) FoodRef.Custom(foodId) else FoodRef.Catalog(foodId)
}

/** What the amount sheet draws. */
data class AmountState(
    val loading: Boolean = true,
    /** The food could not be loaded: offline and never cached. */
    val missing: Boolean = false,
    val name: String = "",
    val tag: String? = null,
    val precision: Precision = Precision.Estimate,
    /** False for "Just the name": no amount, so no stepper and no numbers. */
    val showAmount: Boolean = true,
    val amountText: String = "",
    val units: List<String> = emptyList(),
    val unitIndex: Int = 0,
    val canLess: Boolean = false,
    /** Energy, Protein, Fiber, Calcium, hidden ones left out. Empty hides the block. */
    val nutrition: List<NutritionTile> = emptyList(),
    val askTags: Boolean = false,
    val hunger: Hunger? = null,
    val fullness: Fullness? = null,
)

data class NutritionTile(val metric: Metric, val label: StringResource, val display: Display)

/** The food being sized, catalog or the user's own. */
sealed interface AmountFood {
    val name: String
    val units: List<AmountUnit>

    data class Catalog(val food: Food) : AmountFood {
        override val name: String get() = food.name
        override val units: List<AmountUnit> get() = food.amountUnits()
    }

    data class Custom(val food: CustomFood) : AmountFood {
        override val name: String get() = food.name
        override val units: List<AmountUnit> get() = food.amountUnits()
    }

    val tag: String?
        get() = when (this) {
            is Catalog -> food.brand
            is Custom -> food.brand
        }

    fun initialQuantity(unit: AmountUnit): Double = when (this) {
        is Catalog -> PortionAmount.initial(unit)
        is Custom -> food.initialQuantity
    }
}

/** What the user has chosen on the sheet so far. */
data class AmountChoice(
    val precision: Precision = Precision.Estimate,
    val unitIndex: Int = 0,
    val quantity: Double = 1.0,
    val hunger: Hunger? = null,
    val fullness: Fullness? = null,
) {
    companion object {
        fun initial(food: AmountFood): AmountChoice {
            val unit = food.units.first()
            return AmountChoice(unitIndex = 0, quantity = food.initialQuantity(unit))
        }

        /** Reopening a picked food starts from what was picked. */
        fun from(food: AmountFood, item: BasketItem): AmountChoice {
            val fresh = initial(food)
            val unitIndex = food.units.indexOfFirst { it.label == item.draft.unit }
            val quantity = item.draft.quantity
            return fresh.copy(
                precision = item.draft.precision,
                unitIndex = if (unitIndex >= 0 && quantity != null) unitIndex else fresh.unitIndex,
                quantity = if (unitIndex >= 0 && quantity != null) quantity else fresh.quantity,
                hunger = item.hunger,
                fullness = item.fullness,
            )
        }
    }
}

/** The draft for [choice], which the sheet shows and "Pick this amount" puts in the basket. */
fun AmountFood.draftFor(choice: AmountChoice, source: PickSource, ref: FoodRef): LogDraft {
    val unit = units.getOrElse(choice.unitIndex) { units.first() }
    val method = source.methodFor(ref)
    val draft = when (this) {
        is AmountFood.Catalog -> food.draftFor(unit, choice.quantity, choice.precision, method)
        is AmountFood.Custom -> food.draftFor(choice.quantity, choice.precision, method)
    }
    return if (choice.precision == Precision.Named) draft.nameOnly() else draft
}

/** The amount sheet's state as a pure function, like [LoggerStateBuilder]. Surface [Surface.Logger]. */
object AmountStateBuilder {
    private val calciumKey = Micronutrient.Calcium.let { "${it.key}_${if (it.unit == MicroUnit.Mg) "mg" else "ug"}" }

    fun build(
        food: AmountFood,
        choice: AmountChoice,
        settings: Settings,
        policy: VisibilityPolicy,
        ref: FoodRef,
        source: PickSource,
    ): AmountState {
        val unit = food.units.getOrElse(choice.unitIndex) { food.units.first() }
        val draft = food.draftFor(choice, source, ref)
        val named = choice.precision == Precision.Named
        return AmountState(
            loading = false,
            name = food.name,
            tag = food.tag,
            precision = choice.precision,
            showAmount = !named,
            amountText = draft.amountText.orEmpty().takeUnless { named }.orEmpty(),
            units = food.units.map { it.label },
            unitIndex = choice.unitIndex,
            canLess = PortionAmount.canLess(unit, choice.quantity),
            nutrition = if (named) emptyList() else tiles(draft, policy),
            askTags = settings.askTags,
            hunger = choice.hunger.takeIf { settings.askTags },
            fullness = choice.fullness.takeIf { settings.askTags },
        )
    }

    private fun tiles(draft: LogDraft, policy: VisibilityPolicy): List<NutritionTile> {
        // Soften needs a goal and an item has none, so a softened metric hides here.
        val context = FormatContext()
        val calcium = draft.micros?.get(calciumKey)?.let(Micronutrient.Calcium::percentOf)
        return listOf(
            NutritionTile(Metric.Calories, Res.string.logger_amount_energy, policy.resolve(Metric.Calories, draft.kcal, Surface.Logger, context)),
            NutritionTile(Metric.Protein, Res.string.logger_amount_protein, policy.resolve(Metric.Protein, draft.protein, Surface.Logger, context)),
            NutritionTile(Metric.Fiber, Res.string.logger_amount_fiber, policy.resolve(Metric.Fiber, draft.fiber, Surface.Logger, context)),
            NutritionTile(Metric.Micros, Res.string.logger_amount_calcium, policy.resolve(Metric.Micros, calcium, Surface.Logger, context)),
        ).filter { it.display !is Display.Hidden }
    }
}
