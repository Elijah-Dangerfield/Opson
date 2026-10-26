package com.opson.libraries.nutrition

import kotlinx.datetime.LocalTime

/**
 * The only fasting windows offered. Nothing over 16 hours exists on purpose.
 * TODO(clinician): confirm which modes allow fasting and the cap.
 */
enum class FastingPlan(val key: String, val fastingHours: Int, val opens: LocalTime, val closes: LocalTime) {
    TwelveTwelve("12:12", 12, LocalTime(7, 0), LocalTime(19, 0)),
    FourteenTen("14:10", 14, LocalTime(9, 0), LocalTime(19, 0)),
    SixteenEight("16:8", 16, LocalTime(11, 0), LocalTime(19, 0)),
    ;

    val eatingHours: Int get() = 24 - fastingHours

    companion object {
        val Default = FourteenTen

        fun fromKey(key: String): FastingPlan? = entries.firstOrNull { it.key == key }
    }
}

object Fasting {
    const val MAX_FASTING_HOURS = 16

    /** Fasting is off by default everywhere and only offered at all in Full and Sprint. */
    fun allowed(preset: PresetId): Boolean = Presets[preset].fastingAllowed
}
