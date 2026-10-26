package com.opson.libraries.nutrition

enum class WaterUnit { Ml, FlOz }

enum class WeightUnit { Kg, Lb }

/** Water is stored in ml and weight in kg; these convert for display and input. */
object Units {
    const val ML_PER_FL_OZ = 29.57
    const val KG_PER_LB = 0.45359237

    fun mlToFlOz(ml: Double): Double = ml / ML_PER_FL_OZ
    fun flOzToMl(flOz: Double): Double = flOz * ML_PER_FL_OZ
    fun kgToLb(kg: Double): Double = kg / KG_PER_LB
    fun lbToKg(lb: Double): Double = lb * KG_PER_LB
}

object WaterDefaults {
    const val GLASSES = 8
    const val GLASS_ML = 250
    const val GOAL_ML = GLASSES * GLASS_ML
}
