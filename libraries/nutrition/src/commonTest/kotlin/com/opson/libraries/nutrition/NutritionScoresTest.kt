package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals

/** The prototype's `MICROS` table and `scoreRows`, checked against its own mock day. */
class NutritionScoresTest {

    /** The prototype's "today" column. */
    private val prototypeDay = mapOf(
        Micronutrient.VitaminA to 62.0,
        Micronutrient.VitaminB12 to 88.0,
        Micronutrient.VitaminC to 134.0,
        Micronutrient.VitaminD to 22.0,
        Micronutrient.VitaminE to 41.0,
        Micronutrient.VitaminK to 73.0,
        Micronutrient.Folate to 57.0,
        Micronutrient.Calcium to 46.0,
        Micronutrient.Iron to 64.0,
        Micronutrient.Magnesium to 52.0,
        Micronutrient.Potassium to 49.0,
        Micronutrient.Sodium to 81.0,
        Micronutrient.Zinc to 70.0,
    )

    @Test
    fun table_thirteenInPrototypeOrder() {
        assertEquals(13, Micronutrient.entries.size)
        assertEquals(7, Micronutrient.inGroup(MicroGroup.Vitamins).size)
        assertEquals(
            listOf("Calcium", "Iron", "Magnesium", "Potassium", "Sodium", "Zinc"),
            Micronutrient.inGroup(MicroGroup.Minerals).map { it.name },
        )
    }

    @Test
    fun references_spelledAsThePrototype() {
        val f = EnglishNutritionFormatter
        assertEquals("700 µg", f.microReference(Micronutrient.VitaminA))
        assertEquals("2.4 µg", f.microReference(Micronutrient.VitaminB12))
        assertEquals("90 mg", f.microReference(Micronutrient.VitaminC))
        assertEquals("1,000 mg", f.microReference(Micronutrient.Calcium))
        assertEquals("4,700 mg", f.microReference(Micronutrient.Potassium))
        assertEquals("2,300 mg limit", f.microReference(Micronutrient.Sodium))
    }

    @Test
    fun scores_areTheMeanOfCappedPercents() {
        val scores = NutritionScores.compute(prototypeDay)
        // Vitamin C's 134 counts as 100.
        assertEquals((62 + 88 + 100 + 22 + 41 + 73 + 57) / 7.0, scores.getValue(NutritionScore.Vitamins), 1e-9)
        assertEquals((46 + 64 + 52 + 49 + 81 + 70) / 6.0, scores.getValue(NutritionScore.Minerals), 1e-9)
        assertEquals((46 + 22 + 73 + 52) / 4.0, scores.getValue(NutritionScore.BoneHealth), 1e-9)
        assertEquals((62 + 100 + 22 + 70) / 4.0, scores.getValue(NutritionScore.ImmuneSupport), 1e-9)
        assertEquals((88 + 64 + 52 + 57) / 4.0, scores.getValue(NutritionScore.EnergyAndMetabolism), 1e-9)
        assertEquals((62 + 88 + 100 + 22 + 41 + 73 + 57 + 46 + 64 + 52 + 49 + 81 + 70) / 13.0, scores.getValue(NutritionScore.AllTargets), 1e-9)
    }

    @Test
    fun scores_inDisplayOrder() {
        assertEquals(
            listOf(
                NutritionScore.AllTargets,
                NutritionScore.Vitamins,
                NutritionScore.Minerals,
                NutritionScore.BoneHealth,
                NutritionScore.ImmuneSupport,
                NutritionScore.EnergyAndMetabolism,
            ),
            NutritionScores.compute(prototypeDay).keys.toList(),
        )
    }

    @Test
    fun scores_missingMicrosCountAsZero() {
        val scores = NutritionScores.compute(mapOf(Micronutrient.Calcium to 100.0))
        assertEquals(25.0, scores.getValue(NutritionScore.BoneHealth), 1e-9)
        assertEquals(0.0, scores.getValue(NutritionScore.ImmuneSupport), 1e-9)
    }

    @Test
    fun scores_resolveAsMicrosAgainst100() {
        val score = NutritionScores.compute(prototypeDay).getValue(NutritionScore.BoneHealth)
        assertEquals("48%", showAll().text(Metric.Micros, score, FormatContext(of = 100.0)))
        // Micros are Hide in Full by default: the scores page is off.
        assertEquals(Display.Hidden, policy(PresetId.Full).resolve(Metric.Micros, score, Surface.Today))
    }

    @Test
    fun percents_sumEntriesAndDivideByTheReference() {
        val percents = NutritionScores.percents(
            listOf(
                mapOf(Micronutrient.Calcium to 300.0, Micronutrient.VitaminC to 45.0),
                mapOf(Micronutrient.Calcium to 160.0),
                emptyMap(),
            ),
        )
        assertEquals(46.0, percents.getValue(Micronutrient.Calcium), 1e-9)
        assertEquals(50.0, percents.getValue(Micronutrient.VitaminC), 1e-9)
        assertEquals(0.0, percents.getValue(Micronutrient.Zinc), 1e-9)
        assertEquals(13, percents.size)
    }
}
