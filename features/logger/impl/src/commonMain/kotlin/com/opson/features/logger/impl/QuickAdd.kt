package com.opson.features.logger.impl

import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.Precision
import com.opson.libraries.nutrition.Display
import com.opson.libraries.nutrition.FormatContext
import com.opson.libraries.nutrition.MacroEnergyCheck
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Surface
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.ui.components.text.NumberInput
import kotlin.math.roundToInt

/** Describe's quick add as typed: an optional name and the numbers, each as the person typed it. */
data class QuickAddForm(
    val name: String = "",
    val numbers: Map<Metric, String> = emptyMap(),
)

/** "Or just the numbers". Null in a mode that takes none of them (Macros only, No numbers). */
data class QuickAddUi(
    val name: String,
    /** Only the numbers this mode shows as numbers, in label order. */
    val fields: List<QuickAddField>,
    /** The energy and the macros disagree; null when they agree, or when either is not shown. */
    val mismatch: QuickAddMismatch?,
    val canAdd: Boolean,
)

/** One field. [text] is what the person typed, never a number the policy formatted. */
data class QuickAddField(val metric: Metric, val text: String)

/** "These come to 500 kcal, not the 400 you typed." Both policy-resolved, and never [Display.Hidden]. */
data class QuickAddMismatch(val macroKcal: Display, val kcal: Display)

sealed interface QuickAddAction : LoggerAction {
    data class NameTyped(val text: String) : QuickAddAction
    data class NumberTyped(val metric: Metric, val text: String) : QuickAddAction

    /** The macros drive: the energy becomes what they come to. */
    data object UseMacroEnergyTapped : QuickAddAction

    /** The energy drives: protein, carbs and fat scale together until they come to it. */
    data object FitMacrosTapped : QuickAddAction
    data object AddTapped : QuickAddAction
}

/**
 * Quick add's rules, pure so the builder, the view model and the leak test share them.
 *
 * A field shows the number typed into it, so it is offered only for a number the mode shows as a
 * number: shown, or held for Later (the person is copying their own label, as on Add a food).
 * So All numbers takes all five, Macros with grams the four in grams, and Macros only (words for
 * the macros) and No numbers none, which leaves Describe's "Just save the name".
 */
object QuickAdd {
    /**
     * The stored name of a quick add nobody named, as the prototype logs it. It is the item's name
     * in the diary (and on the server), not screen copy, so it is a constant rather than a resource.
     */
    const val DEFAULT_NAME = "Quick add"

    val FIELD_ORDER: List<Metric> = listOf(Metric.Calories, Metric.Protein, Metric.Carbs, Metric.Fat, Metric.Fiber)

    fun offered(policy: VisibilityPolicy): List<Metric> =
        FIELD_ORDER.filter { policy.level(it) == Visibility.Show || policy.level(it) == Visibility.Delay }

    fun sanitize(metric: Metric, text: String): String = NumberInput.sanitize(text, allowDecimal = metric != Metric.Calories)

    fun build(form: QuickAddForm, policy: VisibilityPolicy, context: FormatContext): QuickAddUi? {
        val offered = offered(policy).ifEmpty { return null }
        return QuickAddUi(
            name = form.name,
            fields = offered.map { QuickAddField(it, form.numbers[it].orEmpty()) },
            mismatch = mismatch(form, policy, context),
            canAdd = draft(form, policy) != null,
        )
    }

    /**
     * What Add writes, or null with no number above 0. A field the mode does not offer is unknown,
     * never 0. The energy is the one typed; without one (or in a mode with calories hidden) it is
     * what the macros come to at 4/4/9, so the day's totals add up the same as for any other food.
     */
    fun draft(form: QuickAddForm, policy: VisibilityPolicy): LogDraft? {
        val values = values(form, policy)
        if (values.values.none { it > 0.0 }) return null
        val grams = values - Metric.Calories
        return LogDraft(
            name = form.name.trim().ifEmpty { DEFAULT_NAME },
            precision = Precision.Named,
            kcal = values[Metric.Calories]?.takeIf { it > 0.0 } ?: MacroEnergyCheck.macroKcal(grams),
            protein = grams[Metric.Protein],
            carbs = grams[Metric.Carbs],
            fat = grams[Metric.Fat],
            fiber = grams[Metric.Fiber],
            method = LogMethod.QuickNumber,
        )
    }

    /** The energy field set to what the macros come to, rounded to the kcal. */
    fun macroEnergyUsed(form: QuickAddForm, policy: VisibilityPolicy): QuickAddForm {
        val macroKcal = MacroEnergyCheck.macroKcal(values(form, policy) - Metric.Calories) ?: return form
        return form.copy(numbers = form.numbers + (Metric.Calories to macroKcal.roundToInt().toString()))
    }

    /** Protein, carbs and fat scaled to the typed energy, each to a tenth of a gram. */
    fun macrosFitted(form: QuickAddForm, policy: VisibilityPolicy): QuickAddForm {
        val values = values(form, policy)
        val kcal = values[Metric.Calories] ?: return form
        val fitted = MacroEnergyCheck.fitted(kcal, values - Metric.Calories)
        return form.copy(numbers = form.numbers + fitted.mapValues { (_, grams) -> tenths(grams) })
    }

    private fun mismatch(form: QuickAddForm, policy: VisibilityPolicy, context: FormatContext): QuickAddMismatch? {
        val values = values(form, policy)
        val kcal = values[Metric.Calories]?.takeIf { it > 0.0 } ?: return null
        val mismatch = MacroEnergyCheck.mismatch(kcal, values - Metric.Calories) ?: return null
        val macroKcal = policy.resolve(Metric.Calories, mismatch.macroKcal.roundToInt().toDouble(), Surface.Logger, context)
        val typed = policy.resolve(Metric.Calories, kcal, Surface.Logger, context)
        // Later before the reveal: the energy the macros come to is a calorie amount nobody typed.
        if (macroKcal !is Display.Shown || typed !is Display.Shown) return null
        return QuickAddMismatch(macroKcal = macroKcal, kcal = typed)
    }

    private fun values(form: QuickAddForm, policy: VisibilityPolicy): Map<Metric, Double> {
        val offered = offered(policy)
        return form.numbers
            .filterKeys { it in offered }
            .mapNotNull { (metric, text) -> text.toDoubleOrNull()?.takeIf { it.isFinite() }?.let { metric to it } }
            .toMap()
    }

    private fun tenths(grams: Double): String {
        val tenths = (grams * TENTHS).roundToInt()
        return if (tenths % TENTHS == 0) (tenths / TENTHS).toString() else "${tenths / TENTHS}.${tenths % TENTHS}"
    }

    private const val TENTHS = 10
}
