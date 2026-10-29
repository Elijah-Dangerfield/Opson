package com.opson.libraries.foods

/**
 * What the server's energy check found on a food's numbers. They are already as served: a 0 the
 * macros contradict comes as an estimate, and a missing energy as the macros' sum.
 */
enum class EnergyCheck(val wire: String) {
    /** The source said 0 where the macros say more. */
    ZeroWithMacros("zero_with_macros"),

    /** The source gave no energy, so it was worked out from the macros. */
    FromMacros("from_macros"),

    /** Energy with no protein, carbs or fat: right for spirits, wrong for most foods. */
    EnergyWithoutMacros("energy_without_macros"),
    ;

    companion object {
        fun fromWire(value: String): EnergyCheck? = entries.firstOrNull { it.wire == value }
    }
}

/**
 * One food a scanned code matched. A code can be in more than one database (a manufacturer's
 * label in USDA Branded, Open Food Facts, Opson's own), and the server lists them all, its own
 * answer first. [corrected] when a reviewer fixed the source's numbers.
 */
data class BarcodeMatch(
    val food: Food,
    val energyCheck: Set<EnergyCheck> = emptySet(),
    val corrected: Boolean = false,
) {
    /** The source's energy is in doubt: the reason the server would pick another match over this one. */
    val numbersLookOff: Boolean
        get() = EnergyCheck.ZeroWithMacros in energyCheck || EnergyCheck.EnergyWithoutMacros in energyCheck
}
