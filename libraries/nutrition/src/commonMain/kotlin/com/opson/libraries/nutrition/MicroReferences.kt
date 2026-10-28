package com.opson.libraries.nutrition

import kotlin.math.roundToLong

/** What a set of [MicroReferences] was picked from, for the settings page to say so. */
enum class ReferenceBasis {
    /** Age and sex: the intake for that age band and sex. */
    AgeAndSex,

    /** Age, with sex not given or "Prefer not to say": halfway between the two for that age. */
    Age,

    /** Sex without an age: that sex's 31 to 50 band, the middle of adult life. */
    Sex,

    /** Neither: the label's Daily Values ([Micronutrient.dailyValue]). */
    DailyValue,
}

/** The age bands the Dietary Reference Intakes are given in, from 9 (the app asks for 13 or older). */
enum class AgeBand(val from: Int) {
    Age9To13(9),
    Age14To18(14),
    Age19To30(19),
    Age31To50(31),
    Age51To70(51),
    Age71Plus(71),
    ;

    companion object {
        /** Younger than 9 reads as 9 to 13: the app is not for children that age, and the table starts there. */
        fun of(ageYears: Int): AgeBand = entries.lastOrNull { ageYears >= it.from } ?: Age9To13
    }
}

/**
 * The daily reference for each micronutrient for one person, and any number they set themselves.
 * Every percent of a vitamin or mineral is a percent of [of]. Built by [forPerson] from the sex
 * and birth year Resting energy collects; [DailyValues] for someone the app knows nothing about.
 */
data class MicroReferences(
    val basis: ReferenceBasis,
    private val suggested: Map<Micronutrient, Double>,
    /** What the person typed instead, in each micronutrient's own unit. */
    val own: Map<Micronutrient, Double> = emptyMap(),
) {
    /** What the table gives for this person, before their own number. */
    fun suggested(micro: Micronutrient): Double = suggested[micro] ?: micro.dailyValue

    fun of(micro: Micronutrient): Double = own[micro] ?: suggested(micro)

    fun isOwn(micro: Micronutrient): Boolean = micro in own

    /** [amount] as a percent of the reference, uncapped: 134 means a third over. */
    fun percentOf(micro: Micronutrient, amount: Double): Double = amount / of(micro) * PERCENT

    companion object {
        private const val PERCENT = 100.0

        /** The adult Daily Values, with nothing of the person's own. */
        val DailyValues = MicroReferences(ReferenceBasis.DailyValue, Micronutrient.entries.associateWith { it.dailyValue })

        /**
         * The references for someone of [sex] and [ageYears], with [own] over them. A sex of
         * [BodySex.Unsaid] counts as not given.
         *
         * - Age and sex: that band's RDA, or AI where there is no RDA.
         * - Age alone: halfway between the female and male values for the band, the way Resting
         *   energy treats "Prefer not to say". Where the two agree, that is just the value.
         * - Sex alone: that sex's 31 to 50 band, the longest stretch of adult life and the one
         *   most adults are nearest.
         * - Neither: the Daily Values, which match the "% Daily Value" on a label.
         */
        fun forPerson(sex: BodySex?, ageYears: Int?, own: Map<Micronutrient, Double> = emptyMap()): MicroReferences {
            val given = sex?.takeIf { it != BodySex.Unsaid }
            val valid = own.filter { (micro, amount) -> isValidOwn(micro, amount) }
            return when {
                ageYears != null && given != null ->
                    MicroReferences(ReferenceBasis.AgeAndSex, table { DietaryReferenceIntakes.of(it, AgeBand.of(ageYears), given) }, valid)
                ageYears != null ->
                    MicroReferences(ReferenceBasis.Age, table { DietaryReferenceIntakes.middle(it, AgeBand.of(ageYears)) }, valid)
                given != null ->
                    MicroReferences(ReferenceBasis.Sex, table { DietaryReferenceIntakes.of(it, AgeBand.Age31To50, given) }, valid)
                else -> DailyValues.copy(own = valid)
            }
        }

        /** A number of the person's own is used if it is a positive amount no more than [MAX_OWN_FACTOR] times the Daily Value. */
        fun isValidOwn(micro: Micronutrient, amount: Double): Boolean =
            amount.isFinite() && amount > 0.0 && amount <= micro.dailyValue * MAX_OWN_FACTOR

        /** A typo guard, not advice: someone typing 15000 for vitamin D's 15 µg meant something else. */
        const val MAX_OWN_FACTOR = 100.0

        private fun table(value: (Micronutrient) -> Double): Map<Micronutrient, Double> = Micronutrient.entries.associateWith(value)
    }
}

/**
 * The Dietary Reference Intakes for healthy people not pregnant or breastfeeding: the RDA, or
 * the AI where no RDA is set (vitamin K, choline, manganese, potassium), and for sodium the
 * Chronic Disease Risk Reduction intake, which is a ceiling. Units are each micronutrient's own:
 * vitamin A as RAE, folate as DFE, niacin as NE, vitamin E as alpha-tocopherol.
 *
 * Sources, read 2026-10-27, listed with the reasoning in `docs/research/nutrient-targets.md`:
 * - Institute of Medicine, Dietary Reference Intakes summary tables (vitamins; elements),
 *   National Academies Press, as reproduced by the NIH Office of Dietary Supplements fact
 *   sheets (ods.od.nih.gov/factsheets/<nutrient>-HealthProfessional).
 * - NASEM, Dietary Reference Intakes for Sodium and Potassium (2019), for the potassium AIs
 *   and the sodium CDRR.
 * - IOM, Dietary Reference Intakes for Calcium and Vitamin D (2011).
 *
 * Each row is six bands, [AgeBand] order: 9 to 13, 14 to 18, 19 to 30, 31 to 50, 51 to 70, 71+.
 *
 * TODO(dietitian): review every row and the halfway rule for "Prefer not to say" before 1.0, and
 * whether teenagers should see these at all.
 */
internal object DietaryReferenceIntakes {

    private class Row(val female: List<Double>, val male: List<Double>)

    private fun both(vararg values: Double) = Row(values.toList(), values.toList())

    private fun split(female: List<Double>, male: List<Double>) = Row(female, male)

    private val rows: Map<Micronutrient, Row> = mapOf(
        Micronutrient.VitaminA to split(listOf(600.0, 700.0, 700.0, 700.0, 700.0, 700.0), listOf(600.0, 900.0, 900.0, 900.0, 900.0, 900.0)),
        Micronutrient.Thiamin to split(listOf(0.9, 1.0, 1.1, 1.1, 1.1, 1.1), listOf(0.9, 1.2, 1.2, 1.2, 1.2, 1.2)),
        Micronutrient.Riboflavin to split(listOf(0.9, 1.0, 1.1, 1.1, 1.1, 1.1), listOf(0.9, 1.3, 1.3, 1.3, 1.3, 1.3)),
        Micronutrient.Niacin to split(listOf(12.0, 14.0, 14.0, 14.0, 14.0, 14.0), listOf(12.0, 16.0, 16.0, 16.0, 16.0, 16.0)),
        Micronutrient.VitaminB6 to split(listOf(1.0, 1.2, 1.3, 1.3, 1.5, 1.5), listOf(1.0, 1.3, 1.3, 1.3, 1.7, 1.7)),
        Micronutrient.Folate to both(300.0, 400.0, 400.0, 400.0, 400.0, 400.0),
        Micronutrient.VitaminB12 to both(1.8, 2.4, 2.4, 2.4, 2.4, 2.4),
        Micronutrient.VitaminC to split(listOf(45.0, 65.0, 75.0, 75.0, 75.0, 75.0), listOf(45.0, 75.0, 90.0, 90.0, 90.0, 90.0)),
        Micronutrient.VitaminD to both(15.0, 15.0, 15.0, 15.0, 15.0, 20.0),
        Micronutrient.VitaminE to both(11.0, 15.0, 15.0, 15.0, 15.0, 15.0),
        Micronutrient.VitaminK to split(listOf(60.0, 75.0, 90.0, 90.0, 90.0, 90.0), listOf(60.0, 75.0, 120.0, 120.0, 120.0, 120.0)),
        Micronutrient.Choline to split(listOf(375.0, 400.0, 425.0, 425.0, 425.0, 425.0), listOf(375.0, 550.0, 550.0, 550.0, 550.0, 550.0)),
        Micronutrient.Calcium to split(listOf(1300.0, 1300.0, 1000.0, 1000.0, 1200.0, 1200.0), listOf(1300.0, 1300.0, 1000.0, 1000.0, 1000.0, 1200.0)),
        Micronutrient.Copper to both(0.7, 0.89, 0.9, 0.9, 0.9, 0.9),
        Micronutrient.Iron to split(listOf(8.0, 15.0, 18.0, 18.0, 8.0, 8.0), listOf(8.0, 11.0, 8.0, 8.0, 8.0, 8.0)),
        Micronutrient.Magnesium to split(listOf(240.0, 360.0, 310.0, 320.0, 320.0, 320.0), listOf(240.0, 410.0, 400.0, 420.0, 420.0, 420.0)),
        Micronutrient.Manganese to split(listOf(1.6, 1.6, 1.8, 1.8, 1.8, 1.8), listOf(1.9, 2.2, 2.3, 2.3, 2.3, 2.3)),
        Micronutrient.Phosphorus to both(1250.0, 1250.0, 700.0, 700.0, 700.0, 700.0),
        Micronutrient.Potassium to split(listOf(2300.0, 2300.0, 2600.0, 2600.0, 2600.0, 2600.0), listOf(2500.0, 3000.0, 3400.0, 3400.0, 3400.0, 3400.0)),
        Micronutrient.Selenium to both(40.0, 55.0, 55.0, 55.0, 55.0, 55.0),
        Micronutrient.Sodium to both(1800.0, 2300.0, 2300.0, 2300.0, 2300.0, 2300.0),
        Micronutrient.Zinc to split(listOf(8.0, 9.0, 8.0, 8.0, 8.0, 8.0), listOf(8.0, 11.0, 11.0, 11.0, 11.0, 11.0)),
    )

    /** [sex] is Female or Male; the caller maps Unsaid to [middle]. */
    fun of(micro: Micronutrient, band: AgeBand, sex: BodySex): Double {
        val row = rows.getValue(micro)
        return (if (sex == BodySex.Male) row.male else row.female)[band.ordinal]
    }

    /** Halfway between female and male, to two places under 10 and a whole number above. */
    fun middle(micro: Micronutrient, band: AgeBand): Double {
        val row = rows.getValue(micro)
        val half = (row.female[band.ordinal] + row.male[band.ordinal]) / 2
        return if (half < WHOLE_FROM) (half * HUNDREDTHS).roundToLong() / HUNDREDTHS else half.roundToLong().toDouble()
    }

    private const val WHOLE_FROM = 10.0
    private const val HUNDREDTHS = 100.0
}
