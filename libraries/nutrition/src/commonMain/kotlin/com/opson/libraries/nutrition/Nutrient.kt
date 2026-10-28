package com.opson.libraries.nutrition

/**
 * One nutrient a person can ask "which foods gave it?" about: a macro or a vitamin or mineral.
 * Energy is not one: the foods behind a calorie total are the list Today already shows, and a
 * ranking of them is a list of foods to cut. [key] is the route's spelling.
 */
sealed interface Nutrient {
    val key: String

    /** The level that decides whether its amounts show. */
    val metric: Metric

    data class Macro(override val metric: Metric) : Nutrient {
        init {
            require(metric.isMacro) { "$metric is not a macro" }
        }

        override val key: String get() = metric.key
    }

    data class Micro(val micro: Micronutrient) : Nutrient {
        override val key: String get() = micro.key
        override val metric: Metric get() = Metric.Micros
    }

    companion object {
        fun fromKey(key: String): Nutrient? =
            Micronutrient.fromKey(key)?.let(::Micro)
                ?: Metric.entries.firstOrNull { it.isMacro && it.key == key }?.let(::Macro)
    }
}

/**
 * A micronutrient's daily reference for this person, `700 µg` or `2,300 mg limit`, spelled by
 * the policy's own formatter. It is a reference, not something eaten, so it is not gated by
 * visibility; screens only show it beside a visible micro row, or on the page that sets it.
 */
fun VisibilityPolicy.microReference(micro: Micronutrient, references: MicroReferences): String =
    formatter.microReference(micro, references.of(micro))

/** [amount] as a reference for [micro]: `700 µg`, `2,300 mg limit`. For a suggestion shown beside their own number. */
fun VisibilityPolicy.microReference(micro: Micronutrient, amount: Double): String = formatter.microReference(micro, amount)

/**
 * How much of [nutrient] one food gave: `12 g` of protein, `2.1 mg` of iron. Only where its level
 * shows numbers: a softened level ranks foods without amounts, so there is no word for this.
 */
fun VisibilityPolicy.resolveContribution(nutrient: Nutrient, amount: Double, surface: Surface, context: FormatContext = FormatContext()): Display {
    if (!amount.isFinite() || effectiveLevel(nutrient.metric, surface, context) != Visibility.Show) return Display.Hidden
    return when (nutrient) {
        is Nutrient.Macro -> resolve(nutrient.metric, amount, surface, context.copy(of = null, goal = GoalStyle.None))
        is Nutrient.Micro -> Display.Shown(formatter.microAmount(nutrient.micro, amount))
    }
}

/** One food's share of a total, `40%`, with the bar filled that far. Shown only where the level shows numbers. */
fun VisibilityPolicy.resolveShare(nutrient: Nutrient, percent: Double, surface: Surface, context: FormatContext = FormatContext()): Display {
    if (!percent.isFinite() || effectiveLevel(nutrient.metric, surface, context) != Visibility.Show) return Display.Hidden
    return Display.Shown(formatter.percent(percent), (percent / PERCENT).toFloat())
}

private const val PERCENT = 100.0
