package com.opson.libraries.nutrition

import kotlin.math.floor
import kotlin.math.roundToInt

/** How a height is typed. It is stored in centimetres either way. */
enum class HeightUnit { Cm, FtIn }

/** A height in feet and inches, inches to the nearest half. */
data class FeetInches(val feet: Int, val inches: Double)

object Heights {
    const val CM_PER_INCH = 2.54
    const val INCHES_PER_FOOT = 12

    /** What the resting energy equation is given: 100 to 250 cm, about 3'3" to 8'2". */
    val RANGE_CM: ClosedFloatingPointRange<Double> = 100.0..250.0

    fun cm(feet: Int, inches: Double): Double = (feet * INCHES_PER_FOOT + inches) * CM_PER_INCH

    /**
     * [cm] as feet and inches, rounded to the nearest half inch so 5'11.5" typed comes back as
     * 5'11.5". Rounding up to 12 inches carries into the next foot.
     */
    fun feetInches(cm: Double): FeetInches {
        val halfInches = (cm / CM_PER_INCH * 2).roundToInt()
        val feet = halfInches / (INCHES_PER_FOOT * 2)
        return FeetInches(feet, (halfInches - feet * INCHES_PER_FOOT * 2) / 2.0)
    }

    fun inRange(cm: Double): Double? = cm.takeIf { it in RANGE_CM }
}

/**
 * A height as typed, in [unit]: [cm] for centimetres, [feet] and [inches] for feet and inches.
 * Text, so a half-typed number is not lost. The caller sanitizes it first.
 */
data class HeightInput(
    val unit: HeightUnit,
    val cm: String = "",
    val feet: String = "",
    val inches: String = "",
) {
    /**
     * Centimetres, when what is typed is a height the equation takes. Blank inches are 0, so
     * "6" feet is 6'0". Inches have to be under 12 and feet a whole number, or it is a typo.
     */
    fun cmOrNull(): Double? = when (unit) {
        HeightUnit.Cm -> cm.toDoubleOrNull()?.let(Heights::inRange)
        HeightUnit.FtIn -> {
            val ft = feet.toIntOrNull()
            val inch = if (inches.isBlank()) 0.0 else inches.toDoubleOrNull()
            if (ft == null || inch == null || inch >= Heights.INCHES_PER_FOOT) null else Heights.inRange(Heights.cm(ft, inch))
        }
    }

    /** Nothing typed in [unit]'s fields. */
    val isBlank: Boolean get() = if (unit == HeightUnit.Cm) cm.isBlank() else feet.isBlank() && inches.isBlank()

    /**
     * The same height in [next]. A height that converts carries over, so switching units never
     * loses what was typed; anything else starts [next]'s fields blank.
     */
    fun inUnit(next: HeightUnit): HeightInput {
        if (next == unit) return this
        return cmOrNull()?.let { of(it, next) } ?: HeightInput(next)
    }

    companion object {
        /** [cm] (stored, or null for none yet) as [unit]'s fields. */
        fun of(cm: Double?, unit: HeightUnit): HeightInput {
            if (cm == null) return HeightInput(unit)
            return when (unit) {
                HeightUnit.Cm -> HeightInput(unit, cm = cm.roundToInt().toString())
                HeightUnit.FtIn -> Heights.feetInches(cm).let { HeightInput(unit, feet = it.feet.toString(), inches = it.inches.text()) }
            }
        }

        /** 11.0 as "11", 11.5 as "11.5". */
        private fun Double.text(): String = if (this == floor(this)) toInt().toString() else toString()
    }
}

/**
 * The units a region writes height and weight in. The United States, Liberia and Myanmar use
 * feet, inches and pounds; everywhere else starts metric. The person can switch either.
 */
object RegionUnits {
    private val imperial = setOf("US", "LR", "MM")

    fun height(countryCode: String?): HeightUnit = if (isImperial(countryCode)) HeightUnit.FtIn else HeightUnit.Cm

    fun weight(countryCode: String?): WeightUnit = if (isImperial(countryCode)) WeightUnit.Lb else WeightUnit.Kg

    private fun isImperial(countryCode: String?): Boolean = countryCode?.uppercase() in imperial
}
