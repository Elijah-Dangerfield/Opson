package com.opson.libraries.nutrition

/**
 * Body mass index: weight in kilograms over height in metres, squared. More › Profile shows it with
 * a line saying what it is, a rough measure made for populations that knows nothing of build.
 * Nothing in the app decides anything from it.
 */
object BodyMassIndex {
    private val WEIGHT_KG = 25.0..400.0

    /** Null without both, or for a weight or height outside what the resting energy equation takes. */
    fun of(weightKg: Double?, heightCm: Double?): Double? {
        val kg = weightKg?.takeIf { it in WEIGHT_KG } ?: return null
        val metres = heightCm?.let(Heights::inRange)?.div(CM_PER_M) ?: return null
        return kg / (metres * metres)
    }

    /** "23.4". */
    fun text(bmi: Double): String = oneDecimal(bmi)

    private const val CM_PER_M = 100.0
}

/** Body fat as a percentage of weight, as the person typed it on Profile. Outside [RANGE] is a typo. */
object BodyFat {
    val RANGE: ClosedFloatingPointRange<Double> = 3.0..75.0

    fun inRange(percent: Double): Double? = percent.takeIf { it in RANGE }

    /** "18.5", or "18" for a whole number, as it goes back into the field. */
    fun text(percent: Double): String = upToTwoDecimals(percent)
}
