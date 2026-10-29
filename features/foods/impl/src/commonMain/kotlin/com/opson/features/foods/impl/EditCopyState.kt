package com.opson.features.foods.impl

import com.opson.libraries.diary.CustomFood
import com.opson.libraries.diary.CustomFoodDraft
import com.opson.libraries.diary.FoodUnit
import com.opson.libraries.diary.HouseholdAmount
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.foods.Food
import com.opson.libraries.foods.FoodAmount
import com.opson.libraries.foods.nutrientsFor
import com.opson.libraries.foods.withoutPrintedWeight
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.MacroEnergyCheck
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.MicroGroup
import com.opson.libraries.nutrition.MicroReferences
import com.opson.libraries.nutrition.MicroUnit
import com.opson.libraries.nutrition.Micronutrient
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.wireKey
import kotlin.math.round
import kotlin.math.roundToInt

/** Edit a copy: the whole label of a food of the person's own. Built by [EditCopyStateBuilder]. */
data class EditCopyState(
    val loading: Boolean = true,
    val name: String = "",
    val brand: String = "",
    /** The serving the label is for, first, then the other sizes. */
    val servings: List<ServingRow> = emptyList(),
    /** The label's serving has no weight yet, so nothing converts to grams: say how to fix that. */
    val weighHint: Boolean = false,
    /** "Nutrition per": the serving, each size with a weight, and 100 g once the serving weighs something. */
    val perOptions: List<String> = emptyList(),
    val perIndex: Int = 0,
    /** The label, then vitamins, then minerals; only what the mode shows in full. Empty in modes that show none. */
    val sections: List<CopySection> = emptyList(),
    /** Protein, carbs and fat come to far more or less energy than typed, at 4, 4 and 9 kcal a gram. */
    val energyCheck: EnergyCheck? = null,
    /** Started from a logged entry, which can switch to the copy. */
    val fromEntry: Boolean = false,
    /** "Use for this entry", on until switched off. */
    val useForEntry: Boolean = true,
    /** The entry's meal, for "Today's snack switches to your copy". */
    val slot: MealSlot = MealSlot.Uncategorized,
    val isToday: Boolean = true,
    /** A name is all a copy needs. */
    val canSave: Boolean = false,
) {
    val fields: List<CopyField> get() = sections.flatMap { it.fields }
}

/** One serving size: "1 tbsp" and "15" g. The first is the label's own serving and cannot be removed. */
data class ServingRow(val name: String, val grams: String, val removable: Boolean)

enum class CopySectionKind { Label, Vitamins, Minerals }

data class CopySection(val kind: CopySectionKind, val fields: List<CopyField>)

/**
 * One number of the copy. [text] is what is typed, for the serving picked in Nutrition per; [was]
 * is the food's own amount through the policy once [text] differs from it. [daily] is the share of
 * a day's reference, `12%`, for the nutrients that have one.
 */
data class CopyField(
    val nutrient: CopyNutrient,
    val text: String,
    val was: Display? = null,
    val daily: Display = Display.Hidden,
) {
    val metric: Metric get() = nutrient.metric
}

/** "Protein, carbs and fat come to about 210 kcal": [computed], and the number Use puts in the field. */
data class EnergyCheck(val computed: Display, val fix: String)

enum class NutrientUnit { Kcal, G, Mg, Ug }

/**
 * A number on a nutrition label. [key] is where the food keeps it: `kcal`, `protein`, `carbs`,
 * `fat`, `fiber` on the food itself, anything else in its micros by the log's key. [metric] is the
 * level that decides whether it shows. [sub] lines sit under their parent (saturated fat under fat).
 */
data class CopyNutrient(
    val key: String,
    val metric: Metric,
    val unit: NutrientUnit,
    val sub: Boolean = false,
    val micro: Micronutrient? = null,
) {
    val onFood: Boolean get() = key in FOOD_KEYS

    companion object {
        const val KCAL = "kcal"
        const val PROTEIN = "protein"
        const val CARBS = "carbs"
        const val FAT = "fat"
        const val FIBER = "fiber"
        private val FOOD_KEYS = setOf(KCAL, PROTEIN, CARBS, FAT, FIBER)

        fun of(micro: Micronutrient): CopyNutrient =
            CopyNutrient(micro.wireKey, Metric.Micros, if (micro.unit == MicroUnit.Ug) NutrientUnit.Ug else NutrientUnit.Mg, micro = micro)

        /** A US label's order: energy, fat and its kinds, cholesterol, sodium, carbs and theirs, protein. */
        val LABEL: List<CopyNutrient> = listOf(
            CopyNutrient(KCAL, Metric.Calories, NutrientUnit.Kcal),
            CopyNutrient(FAT, Metric.Fat, NutrientUnit.G),
            CopyNutrient("saturated_fat_g", Metric.Fat, NutrientUnit.G, sub = true),
            CopyNutrient("trans_fat_g", Metric.Fat, NutrientUnit.G, sub = true),
            CopyNutrient("cholesterol_mg", Metric.Micros, NutrientUnit.Mg),
            of(Micronutrient.Sodium),
            CopyNutrient(CARBS, Metric.Carbs, NutrientUnit.G),
            CopyNutrient(FIBER, Metric.Fiber, NutrientUnit.G, sub = true),
            CopyNutrient("sugars_g", Metric.Carbs, NutrientUnit.G, sub = true),
            CopyNutrient("added_sugars_g", Metric.Carbs, NutrientUnit.G, sub = true),
            CopyNutrient(PROTEIN, Metric.Protein, NutrientUnit.G),
        )

        val VITAMINS: List<CopyNutrient> = Micronutrient.inGroup(MicroGroup.Vitamins).map(::of)

        /** Sodium is on the label itself. */
        val MINERALS: List<CopyNutrient> = Micronutrient.inGroup(MicroGroup.Minerals).filterNot { it == Micronutrient.Sodium }.map(::of)

        val ALL: List<CopyNutrient> = LABEL + VITAMINS + MINERALS
    }
}

/**
 * What a copy starts as: one food of the person's own, its serving, sizes and every number per
 * serving. [entry] is the logged entry it came from, if it came from one. [references] are the
 * person's own daily references, so a field's percent matches the food detail's.
 */
data class CopySeed(
    val food: CustomFoodDraft,
    val entry: LogItem? = null,
    val isToday: Boolean = true,
    val references: MicroReferences = MicroReferences.DailyValues,
)

/** What the person has typed. The view model holds it; [EditCopyStateBuilder] draws it. */
data class CopyForm(
    val name: String,
    val brand: String,
    /** The label's serving first; a size needs a name and a weight to be kept. */
    val servings: List<ServingRow>,
    /** Which row the numbers are typed per, or [PER_100_G]. */
    val per: Int = 0,
    /** Typed per [per], by [CopyNutrient.key]. Only what the mode shows is here. */
    val values: Map<String, String> = emptyMap(),
    val useForEntry: Boolean = true,
) {
    companion object {
        const val PER_100_G = -1
    }
}

sealed interface EditCopyAction {
    data class SeedArrived(val seed: CopySeed, val policy: VisibilityPolicy) : EditCopyAction
    data class NameTyped(val text: String) : EditCopyAction
    data class BrandTyped(val text: String) : EditCopyAction
    data class NutritionTyped(val key: String, val text: String) : EditCopyAction
    data class ServingNameTyped(val index: Int, val text: String) : EditCopyAction
    data class ServingGramsTyped(val index: Int, val text: String) : EditCopyAction
    data object ServingAdded : EditCopyAction
    data class ServingRemoved(val index: Int) : EditCopyAction
    data class PerPicked(val index: Int) : EditCopyAction
    data object EnergyFixTapped : EditCopyAction
    data object UseForEntryToggled : EditCopyAction
    data object SaveTapped : EditCopyAction
    data object CloseTapped : EditCopyAction
}

sealed interface EditCopyEvent {
    data class ShowToast(val toast: Toast) : EditCopyEvent
    data object Close : EditCopyEvent

    /** Saved from a food's detail: its copy's detail opens in its place, to log on [dayIso] in [slot]. */
    data class OpenCopy(val foodId: String, val dayIso: String, val slot: MealSlot?) : EditCopyEvent
}

/**
 * The copy as a pure function of the seed, what was typed and the policy, so the previews and
 * the leak test draw what the app does.
 *
 * A number shows only where the mode shows it in full on Foods: anything softer would put an
 * amount on screen the mode keeps back. What is not shown is copied as it was. Every number is
 * kept per the label's serving; Nutrition per only changes which serving the fields are typed for.
 */
object EditCopyStateBuilder {

    fun shown(policy: VisibilityPolicy): List<CopyNutrient> =
        CopyNutrient.ALL.filter { policy.effectiveLevel(it.metric, Surface.Foods) == Visibility.Show }

    // ── Seeds ───────────────────────────────────────────────────────────────

    /** A logged entry: one serving of the copy is the amount logged, so its numbers start as the entry's. */
    fun seed(item: LogItem, brand: String?, isToday: Boolean): CopySeed {
        val unit = item.unit
        val byWeight = unit?.equals(GRAMS, ignoreCase = true) == true
        return CopySeed(
            food = CustomFoodDraft(
                name = item.name,
                // A portion label carries its own amount: 2 of "1 cup" is 2 cup, so the copy reads "2 cups".
                servingAmount = if (byWeight) item.grams ?: item.quantity ?: 1.0 else (item.quantity ?: 1.0) * (unit?.let(HouseholdAmount::amountOf) ?: 1.0),
                servingUnit = if (byWeight) GRAMS else unit?.let { withoutPrintedWeight(HouseholdAmount.unitOf(it)) }?.ifBlank { null } ?: SERVING,
                brand = brand,
                kcal = item.kcal,
                protein = item.protein,
                carbs = item.carbs,
                fat = item.fat,
                fiber = item.fiber,
                servingGrams = item.grams.takeUnless { byWeight },
                micros = item.micros.orEmpty(),
            ),
            entry = item,
            isToday = isToday,
        )
    }

    /** A catalog food: its first portion is the serving (100 g with none), its other portions and [saved] sizes come too. */
    fun seed(food: Food, saved: List<FoodUnit>): CopySeed {
        val portion = food.portions.firstOrNull()
        val amount = portion?.let { FoodAmount.OfPortion(it) } ?: FoodAmount.Grams(HUNDRED)
        val scaled = food.nutrientsFor(amount)
        val label = portion?.label?.let(::withoutPrintedWeight)
        val sizes = food.portions.drop(1).map { it.label to it.grams }.map { (text, grams) -> perOne(withoutPrintedWeight(text), grams) } + saved
        return CopySeed(
            CustomFoodDraft(
                name = food.name,
                servingAmount = label?.let(HouseholdAmount::amountOf) ?: HUNDRED,
                servingUnit = label?.let(HouseholdAmount::unitOf)?.ifBlank { null } ?: if (portion == null) GRAMS else SERVING,
                brand = food.brand,
                kcal = scaled.kcal,
                protein = scaled.protein,
                carbs = scaled.carbs,
                fat = scaled.fat,
                fiber = scaled.fiber,
                servingGrams = portion?.grams,
                sizes = sizes.distinctBy { it.label.lowercase() },
                micros = scaled.micros,
            ),
        )
    }

    /** A food of the person's own, with the [saved] sizes they gave it on its detail. */
    fun seed(food: CustomFood, saved: List<FoodUnit>): CopySeed = CopySeed(
        CustomFoodDraft(
            name = food.name,
            servingAmount = food.servingAmount,
            servingUnit = food.servingUnit,
            brand = food.brand,
            kcal = food.kcal,
            protein = food.protein,
            carbs = food.carbs,
            fat = food.fat,
            fiber = food.fiber,
            servingGrams = food.servingGrams,
            sizes = (food.sizes + saved).distinctBy { it.label.lowercase() },
            micros = food.micros,
        ),
    )

    // ── The form ────────────────────────────────────────────────────────────

    /** The form as it opens: [name] ("Peanut butter (mine)"), the brand, the serving and sizes, the numbers that show. */
    fun form(seed: CopySeed, name: String, policy: VisibilityPolicy): CopyForm {
        val food = seed.food
        val serving = ServingRow(
            name = HouseholdAmount.of(food.servingAmount, food.servingUnit),
            grams = food.servingGrams?.let(::typed).orEmpty(),
            removable = false,
        )
        return CopyForm(
            name = name,
            brand = food.brand.orEmpty(),
            servings = listOf(serving) + food.sizes.map { ServingRow(HouseholdAmount.of(1.0, it.label), typed(it.grams), removable = true) },
            values = shown(policy).associate { it.key to original(food, it)?.let(::typed).orEmpty() },
        )
    }

    fun build(seed: CopySeed, form: CopyForm, policy: VisibilityPolicy): EditCopyState {
        val shown = shown(policy).toSet()
        val factor = perFactor(form)
        val sections = listOf(
            CopySectionKind.Label to CopyNutrient.LABEL,
            CopySectionKind.Vitamins to CopyNutrient.VITAMINS,
            CopySectionKind.Minerals to CopyNutrient.MINERALS,
        ).mapNotNull { (kind, nutrients) ->
            val fields = nutrients.filter { it in shown }.map { field(seed, form, it, factor, policy) }
            fields.takeIf { it.isNotEmpty() }?.let { CopySection(kind, it) }
        }
        val options = perOptions(form)
        return EditCopyState(
            loading = false,
            name = form.name,
            brand = form.brand,
            servings = form.servings,
            weighHint = baseGrams(form) == null && !isGrams(form.servings.first().name),
            perOptions = options.map { HouseholdAmount.display(it.label) }.takeIf { sections.isNotEmpty() }.orEmpty(),
            perIndex = options.indexOfFirst { it.row == form.per }.coerceAtLeast(0),
            sections = sections,
            energyCheck = energyCheck(form, policy),
            fromEntry = seed.entry != null,
            useForEntry = form.useForEntry,
            slot = seed.entry?.slot ?: MealSlot.Uncategorized,
            isToday = seed.isToday,
            canSave = form.name.isNotBlank(),
        )
    }

    private fun field(seed: CopySeed, form: CopyForm, nutrient: CopyNutrient, factor: Double?, policy: VisibilityPolicy): CopyField {
        val text = form.values[nutrient.key].orEmpty()
        val before = original(seed.food, nutrient)?.let { value -> factor?.let { value * it } }
        val typedValue = text.toDoubleOrNull()
        val changed = typedValue?.let { tidy(it) } != before?.let { tidy(it) }
        // A micronutrient's own formatting is a percent of a day, so the "Was" hint is for the label's grams and energy.
        val was = if (changed && before != null && nutrient.metric != Metric.Micros) {
            policy.resolve(nutrient.metric, before, Surface.Foods, FormatContext(bare = true)).takeIf { it is Display.Shown }
        } else {
            null
        }
        val daily = nutrient.micro?.let { micro ->
            typedValue?.let { policy.resolve(Metric.Micros, seed.references.percentOf(micro, it), Surface.Foods, FormatContext(of = PERCENT)) }
        } ?: Display.Hidden
        return CopyField(nutrient, text, was, daily)
    }

    /** One choice in Nutrition per. [row] is the serving row, or [CopyForm.PER_100_G]. */
    data class PerOption(val row: Int, val label: String, val grams: Double?)

    /** The label's serving, every size with a weight, and 100 g, once the label's serving weighs something. */
    fun perOptions(form: CopyForm): List<PerOption> {
        val base = PerOption(0, form.servings.first().name.ifBlank { SERVING }, baseGrams(form))
        if (base.grams == null) return listOf(base)
        val sizes = form.servings.withIndex().drop(1).mapNotNull { (index, row) ->
            row.grams.toDoubleOrNull()?.takeIf { it > 0.0 && row.name.isNotBlank() }?.let { PerOption(index, row.name, it) }
        }
        val hundred = PerOption(CopyForm.PER_100_G, HouseholdAmount.of(HUNDRED, GRAMS), HUNDRED).takeUnless { isGrams(base.label) && base.grams == HUNDRED }
        return listOf(base) + sizes + listOfNotNull(hundred)
    }

    /** The grams of the serving the fields are typed for over the label serving's: 1 for the label's own. */
    fun perFactor(form: CopyForm): Double? {
        if (form.per == 0) return 1.0
        val base = baseGrams(form) ?: return null
        val per = perOptions(form).firstOrNull { it.row == form.per }?.grams ?: return null
        return per / base
    }

    /** The fields retyped for [to]: each number scales by the weights, so nothing changes but the serving it is for. */
    fun rePer(form: CopyForm, to: Int): CopyForm {
        val from = perFactor(form) ?: return form
        val next = form.copy(per = to)
        val toFactor = perFactor(next) ?: return form
        val values = form.values.mapValues { (_, text) -> text.toDoubleOrNull()?.let { typed(it / from * toFactor) } ?: text }
        return next.copy(values = values)
    }

    /** A row taken out. The numbers were typed per it, they are retyped for the label's serving first. */
    fun withoutServing(form: CopyForm, index: Int): CopyForm {
        if (index <= 0 || index > form.servings.lastIndex) return form
        val base = if (form.per == index) rePer(form, 0) else form
        val per = if (base.per > index) base.per - 1 else base.per
        return base.copy(servings = base.servings.filterIndexed { i, _ -> i != index }, per = per)
    }

    private fun baseGrams(form: CopyForm): Double? {
        val base = form.servings.first()
        if (isGrams(base.name)) return HouseholdAmount.amountOf(base.name)
        return base.grams.toDoubleOrNull()?.takeIf { it > 0.0 }
    }

    private fun isGrams(name: String): Boolean = HouseholdAmount.unitOf(name).equals(GRAMS, ignoreCase = true)

    /**
     * The 4/4/9 check, [MacroEnergyCheck]'s, so a label's rounding and the ways it counts fiber
     * are not a typo. Only where energy and all three macros show.
     */
    private fun energyCheck(form: CopyForm, policy: VisibilityPolicy): EnergyCheck? {
        val needs = listOf(Metric.Calories, Metric.Protein, Metric.Carbs, Metric.Fat)
        if (needs.any { policy.effectiveLevel(it, Surface.Foods) != Visibility.Show }) return null
        val kcal = form.values[CopyNutrient.KCAL]?.toDoubleOrNull() ?: return null
        val grams = listOf(
            Metric.Protein to CopyNutrient.PROTEIN,
            Metric.Carbs to CopyNutrient.CARBS,
            Metric.Fat to CopyNutrient.FAT,
            Metric.Fiber to CopyNutrient.FIBER,
        ).mapNotNull { (metric, key) -> form.values[key]?.toDoubleOrNull()?.let { metric to it } }.toMap()
        val mismatch = MacroEnergyCheck.mismatch(kcal, grams) ?: return null
        val shown = policy.resolve(Metric.Calories, mismatch.macroKcal, Surface.Foods, FormatContext(bare = true)).takeIf { it is Display.Shown } ?: return null
        return EnergyCheck(computed = shown, fix = mismatch.macroKcal.roundToInt().toString())
    }

    // ── Saving ──────────────────────────────────────────────────────────────

    /**
     * The new food. A shown field left blank is unknown, never 0; a field not shown keeps the
     * food's own number, and so does every nutrient the editor has no line for. A shown field
     * left as it opened keeps the food's own number too, unrounded: a weighed food's 219.667 kcal
     * opens as "219.67", and saving that text would round it.
     */
    fun draft(seed: CopySeed, form: CopyForm, policy: VisibilityPolicy): CustomFoodDraft {
        val shown = shown(policy).toSet()
        val factor = perFactor(form) ?: 1.0
        fun value(nutrient: CopyNutrient): Double? {
            val before = original(seed.food, nutrient)
            if (nutrient !in shown) return before
            val typed = form.values[nutrient.key]?.toDoubleOrNull() ?: return null
            return if (before != null && tidy(typed) == tidy(before * factor)) before else typed / factor
        }
        val base = form.servings.first()
        val baseName = base.name.trim().ifEmpty { HouseholdAmount.of(1.0, SERVING) }
        val baseUnit = HouseholdAmount.unitOf(baseName).ifBlank { SERVING }
        val micros = seed.food.micros.toMutableMap()
        CopyNutrient.ALL.filterNot { it.onFood }.forEach { nutrient ->
            val amount = value(nutrient)
            if (amount == null) micros.remove(nutrient.key) else micros[nutrient.key] = amount
        }
        return CustomFoodDraft(
            name = form.name.trim(),
            servingAmount = HouseholdAmount.amountOf(baseName),
            servingUnit = baseUnit,
            brand = form.brand.trim().ifEmpty { null },
            kcal = value(CopyNutrient.LABEL.first { it.key == CopyNutrient.KCAL }),
            protein = value(CopyNutrient.LABEL.first { it.key == CopyNutrient.PROTEIN }),
            carbs = value(CopyNutrient.LABEL.first { it.key == CopyNutrient.CARBS }),
            fat = value(CopyNutrient.LABEL.first { it.key == CopyNutrient.FAT }),
            fiber = value(CopyNutrient.LABEL.first { it.key == CopyNutrient.FIBER }),
            servingGrams = base.grams.toDoubleOrNull()?.takeIf { it > 0.0 && !baseUnit.equals(GRAMS, ignoreCase = true) },
            sizes = form.servings.drop(1).mapNotNull { row ->
                val grams = row.grams.toDoubleOrNull()?.takeIf { it > 0.0 } ?: return@mapNotNull null
                row.name.trim().takeIf { it.isNotEmpty() }?.let { perOne(it, grams) }
            }.distinctBy { it.label.lowercase() },
            micros = micros,
        )
    }

    /** The entry pointed at [food], its numbers now the copy's. Its amount, time and meal stay. */
    fun switched(item: LogItem, food: CustomFood): LogItem = item.copy(
        name = food.name,
        foodId = null,
        customFoodId = food.id,
        kcal = food.kcal,
        protein = food.protein,
        carbs = food.carbs,
        fat = food.fat,
        fiber = food.fiber,
        micros = food.micros.takeIf { it.isNotEmpty() },
    )

    /** "2 cookies" at 28 g is a cookie at 14 g: a size is one of its unit, so a count converts. */
    private fun perOne(name: String, grams: Double): FoodUnit {
        val amount = HouseholdAmount.amountOf(name)
        val unit = HouseholdAmount.unitOf(name).ifBlank { name }
        return FoodUnit(unit, grams / amount)
    }

    private fun original(food: CustomFoodDraft, nutrient: CopyNutrient): Double? = when (nutrient.key) {
        CopyNutrient.KCAL -> food.kcal
        CopyNutrient.PROTEIN -> food.protein
        CopyNutrient.CARBS -> food.carbs
        CopyNutrient.FAT -> food.fat
        CopyNutrient.FIBER -> food.fiber
        else -> food.micros[nutrient.key]
    }

    /** A number as it would be typed: "190", "7.5", "0.04". */
    fun typed(value: Double): String {
        val rounded = tidy(value)
        return if (rounded == round(rounded)) rounded.toLong().toString() else rounded.toString()
    }

    /** Two decimals: enough for a vitamin in milligrams, and what a typed number is compared at. */
    private fun tidy(value: Double): Double = round(value * HUNDRED) / HUNDRED

    private const val SERVING = "serving"
    private const val GRAMS = "g"
    private const val HUNDRED = 100.0
    private const val PERCENT = 100.0
}
