package com.opson.server.data

import com.opson.server.domain.CarbBasis
import com.opson.server.domain.LabelCheckCode
import com.opson.server.domain.LabelParseResult
import com.opson.server.domain.LabelReading
import com.opson.server.domain.NutrientKey
import com.opson.server.domain.NutrientUnit
import com.opson.server.domain.ServingUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.round
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The model's answer to the catalog's units, on answers in the Messages API's
 * own shape (`src/test/resources/labels/`): a US bar, an EU yogurt in kJ and
 * kcal with salt, a cereal whose kJ line was read as kcal, almonds in ounces
 * with %DV and IU, a photo with no label. Nothing here calls a model.
 */
class LabelOutputReaderTest {

    private fun fixture(name: String): LabelReading {
        val message = Json.parseToJsonElement(checkNotNull(javaClass.getResource("/labels/$name.message.json")).readText()).jsonObject
        val text = message["content"]!!.jsonArray.first().jsonObject["text"]!!.jsonPrimitive.content
        return assertIs<LabelParseResult.Read>(AnthropicLabelParser.parseText(text, "claude-fable-5-1")).reading
    }

    @Test
    fun aUsBarReadsAsPrintedAndPassesItsChecks() {
        val reading = fixture("us_granola_bar")
        val facts = reading.facts

        assertEquals("Oats & honey granola bar", facts.name)
        assertEquals("Hillside", facts.brand)
        assertEquals("012345678905", facts.barcode)
        assertEquals(40.0, facts.servingSize)
        assertEquals(ServingUnit.Gram, facts.servingUnit)
        assertEquals("1 bar", facts.servingLabel)
        assertEquals(6.0, facts.servingsPerContainer)
        assertEquals(CarbBasis.Us, facts.basis)
        assertEquals(190.0, facts.perServing[NutrientKey.Energy])
        assertEquals(150.0, facts.perServing[NutrientKey.Sodium])
        assertEquals(0.0, facts.perServing[NutrientKey.VitaminD])
        // 10% of the FDA's 900 mcg.
        assertEquals(90.0, facts.perServing[NutrientKey.VitaminA])
        assertTrue(facts.per100.isEmpty())
        assertEquals(0.6, reading.confidence["protein_g"])
        assertTrue(reading.readable)
        assertEquals(emptyList(), reading.checks)
    }

    @Test
    fun anEuTableTakesKcalOverKjAndSaltBecomesSodium() {
        val reading = fixture("eu_yogurt")
        val facts = reading.facts

        assertEquals(CarbBasis.Eu, facts.basis)
        assertNull(facts.barcode)
        assertEquals(146.0, facts.perServing[NutrientKey.Energy])
        assertEquals(97.0, facts.per100[NutrientKey.Energy])
        // 0.18 g of salt is 72 mg of sodium.
        assertEquals(72.0, round(facts.perServing.getValue(NutrientKey.Sodium)))
        assertEquals(48.0, round(facts.per100.getValue(NutrientKey.Sodium)))
        // %RI on an EU label is not the FDA's %DV, so it is not turned into an amount.
        assertNull(facts.perServing[NutrientKey.VitaminA])
        assertEquals(emptyList(), reading.checks)
    }

    @Test
    fun aKilojouleLineReadAsKcalIsCaughtByTheChecks() {
        val reading = fixture("kj_read_as_kcal")

        val check = reading.checks.single()
        assertEquals(LabelCheckCode.EnergyLooksLikeKj, check.code)
        assertEquals(380.0, round(check.expected!!))
        assertEquals(0.5, reading.confidence["energy_kcal"])
    }

    @Test
    fun ouncesBecomeGramsAndPercentDvAndIuBecomeAmountsOnAUsPanel() {
        val facts = fixture("us_almonds_oz").facts

        assertEquals(28.3, round(facts.servingSize!! * 10) / 10)
        assertEquals(ServingUnit.Gram, facts.servingUnit)
        assertEquals("about 23 almonds", facts.servingLabel)
        // "Less than 1 g" is taken as half a gram.
        assertEquals(0.5, facts.perServing[NutrientKey.Sugars])
        // 40 IU of vitamin D is 1 mcg; 50% DV of vitamin E is 7.5 mg; 6% DV of iron is 1.08 mg.
        assertEquals(1.0, facts.perServing[NutrientKey.VitaminD])
        assertEquals(7.5, facts.perServing[NutrientKey.VitaminE])
        assertEquals(1.08, facts.perServing.getValue(NutrientKey.Iron), 1e-9)
        assertNull(facts.brand)
    }

    @Test
    fun aPhotoWithNoLabelSaysSo() {
        val reading = fixture("no_label")
        assertFalse(reading.readable)
        assertEquals(LabelCheckCode.MissingRequired, reading.checks.single().code)
    }

    @Test
    fun aBarcodeWithABadCheckDigitIsDropped() {
        val reading = LabelOutputReader.read(LabelOutput(barcode = LabelOutput.TextField("012345678901", 0.9)))
        assertNull(reading.facts.barcode)
    }

    @Test
    fun negativeAndUnknownRowsAreDroppedAndKjAndKcalThatDisagreeAreFlagged() {
        val reading = LabelOutputReader.read(
            LabelOutput(
                nutrients = listOf(
                    LabelOutput.NutrientLine("energy", perServing = 615.0, unit = "kj"),
                    LabelOutput.NutrientLine("energy", perServing = 200.0, unit = "kcal"),
                    LabelOutput.NutrientLine("protein", perServing = -1.0, unit = "g"),
                    LabelOutput.NutrientLine("glitter", perServing = 3.0, unit = "g"),
                    LabelOutput.NutrientLine("fat", perServing = 3.0, unit = "kcal"),
                ),
            ),
        )
        assertEquals(mapOf(NutrientKey.Energy to 200.0), reading.facts.perServing)
        assertEquals(LabelCheckCode.EnergyUnitsDisagree, reading.unitChecks.single().code)
    }

    @Test
    fun kjAloneBecomesKcal() {
        val reading = LabelOutputReader.read(
            LabelOutput(nutrients = listOf(LabelOutput.NutrientLine("energy", per100 = 1674.0, unit = "kj"))),
        )
        assertEquals(400.0, round(reading.facts.per100.getValue(NutrientKey.Energy)))
    }

    @Test
    fun servingUnitsConvert() {
        assertEquals(250.0 to ServingUnit.Milliliter, LabelOutputReader.serving(25.0, "cl"))
        assertEquals(1000.0 to ServingUnit.Milliliter, LabelOutputReader.serving(1.0, "L"))
        assertEquals(236.6 to ServingUnit.Milliliter, LabelOutputReader.serving(8.0, "fl. oz").let { round(it.first!! * 10) / 10 to it.second })
        assertEquals(null to null, LabelOutputReader.serving(1.0, "cup"))
        assertEquals(null to null, LabelOutputReader.serving(-3.0, "g"))
    }

    /** Any amount in g, mg or mcg lands on the same quantity in the key's own unit. */
    @Test
    fun weightUnitsRoundTripForEveryNutrient() {
        val random = Random(SEED)
        NutrientKey.entries.filter { it.unit != NutrientUnit.Kcal }.forEach { key ->
            repeat(RUNS) {
                val grams = random.nextDouble(0.0, 50.0)
                val fromG = LabelOutputReader.toUnit(grams, "g", key.unit, key, CarbBasis.Us)!!
                val fromMg = LabelOutputReader.toUnit(grams * 1000, "mg", key.unit, key, CarbBasis.Us)!!
                val fromMcg = LabelOutputReader.toUnit(grams * 1_000_000, "mcg", key.unit, key, CarbBasis.Us)!!
                assertEquals(fromG, fromMg, 1e-9 * (1 + fromG))
                assertEquals(fromG, fromMcg, 1e-6 * (1 + fromG))
                assertEquals(grams, fromG * key.unit.gramsPer!!, 1e-9 * (1 + grams))
            }
        }
    }

    private companion object {
        const val SEED = 7
        const val RUNS = 50
    }
}
