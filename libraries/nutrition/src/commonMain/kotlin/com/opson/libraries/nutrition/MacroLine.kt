package com.opson.libraries.nutrition

/** One macro in a line of text, tagged with its [metric] when it renders. */
data class MacroPart(val metric: Metric, val text: String)

enum class MacroLineStyle {
    /** `47 g protein`, for meal cards. Joined with ` · `. */
    Long,

    /** `47P`, for food rows. Joined with a space. */
    Compact,
}

/**
 * Protein, carbs and fat as short text, in that order, for meal cards, food
 * rows and Create a meal. A port of `macroLine` and `macroText` in the
 * prototype, which leave fiber out of both.
 *
 * Only macros effectively at [Visibility.Show] appear. A softened macro is a
 * word and a bar elsewhere, and "Plenty today protein" means nothing, so it is
 * left out here rather than spelled. Hidden macros never appear, so the list
 * can never leak one; an empty list renders nothing.
 */
fun VisibilityPolicy.macroLine(
    amounts: NutrientAmounts,
    surface: Surface,
    context: FormatContext = FormatContext(),
    style: MacroLineStyle = MacroLineStyle.Long,
): List<MacroPart> = LINE_MACROS.mapNotNull { metric ->
    val grams = amounts.of(metric) ?: return@mapNotNull null
    if (effectiveLevel(metric, surface, context) != Visibility.Show || !grams.isFinite()) return@mapNotNull null
    val text = when (style) {
        MacroLineStyle.Long -> formatter.macroLong(metric, grams)
        MacroLineStyle.Compact -> formatter.macroCompact(metric, grams)
    }
    MacroPart(metric, text)
}

private val LINE_MACROS = listOf(Metric.Protein, Metric.Carbs, Metric.Fat)
