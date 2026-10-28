package com.opson.libraries.nutrition

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Which reference a person gets from their age and sex, at the band edges, and their own number over it. */
class MicroReferencesTest {

    private fun ref(sex: BodySex?, age: Int?, micro: Micronutrient) = MicroReferences.forPerson(sex, age).of(micro)

    @Test
    fun iron_followsTheBands_forWomen() {
        assertEquals(15.0, ref(BodySex.Female, 18, Micronutrient.Iron))
        assertEquals(18.0, ref(BodySex.Female, 19, Micronutrient.Iron))
        assertEquals(18.0, ref(BodySex.Female, 50, Micronutrient.Iron))
        assertEquals(8.0, ref(BodySex.Female, 51, Micronutrient.Iron))
    }

    @Test
    fun calcium_risesAt51ForWomen_andAt71ForMen() {
        assertEquals(1000.0, ref(BodySex.Female, 50, Micronutrient.Calcium))
        assertEquals(1200.0, ref(BodySex.Female, 51, Micronutrient.Calcium))
        assertEquals(1000.0, ref(BodySex.Male, 70, Micronutrient.Calcium))
        assertEquals(1200.0, ref(BodySex.Male, 71, Micronutrient.Calcium))
    }

    @Test
    fun teenagers_getTheTeenBands() {
        assertEquals(1300.0, ref(BodySex.Male, 13, Micronutrient.Calcium))
        assertEquals(600.0, ref(BodySex.Male, 13, Micronutrient.VitaminA))
        assertEquals(900.0, ref(BodySex.Male, 14, Micronutrient.VitaminA))
        assertEquals(1250.0, ref(BodySex.Female, 18, Micronutrient.Phosphorus))
        assertEquals(700.0, ref(BodySex.Female, 19, Micronutrient.Phosphorus))
        assertEquals(1800.0, ref(BodySex.Female, 13, Micronutrient.Sodium))
    }

    @Test
    fun vitaminD_risesAt71_forEveryone() {
        assertEquals(15.0, ref(BodySex.Female, 70, Micronutrient.VitaminD))
        assertEquals(20.0, ref(BodySex.Male, 71, Micronutrient.VitaminD))
        assertEquals(20.0, ref(null, 90, Micronutrient.VitaminD))
    }

    @Test
    fun magnesium_changesAt31() {
        assertEquals(400.0, ref(BodySex.Male, 30, Micronutrient.Magnesium))
        assertEquals(420.0, ref(BodySex.Male, 31, Micronutrient.Magnesium))
    }

    @Test
    fun ageBand_edges() {
        assertEquals(AgeBand.Age9To13, AgeBand.of(5))
        assertEquals(AgeBand.Age9To13, AgeBand.of(13))
        assertEquals(AgeBand.Age14To18, AgeBand.of(14))
        assertEquals(AgeBand.Age19To30, AgeBand.of(30))
        assertEquals(AgeBand.Age31To50, AgeBand.of(31))
        assertEquals(AgeBand.Age71Plus, AgeBand.of(120))
    }

    @Test
    fun unsaid_withAnAge_isHalfwayBetween() {
        val references = MicroReferences.forPerson(BodySex.Unsaid, 35)
        assertEquals(ReferenceBasis.Age, references.basis)
        assertEquals(13.0, references.of(Micronutrient.Iron))
        assertEquals(800.0, references.of(Micronutrient.VitaminA))
        assertEquals(2.05, references.of(Micronutrient.Manganese))
        assertEquals(83.0, references.of(Micronutrient.VitaminC))
        // Where the sexes agree, halfway is the value itself.
        assertEquals(400.0, references.of(Micronutrient.Folate))
    }

    @Test
    fun noSex_withAnAge_isTheSameAsUnsaid() {
        assertEquals(MicroReferences.forPerson(BodySex.Unsaid, 60), MicroReferences.forPerson(null, 60))
    }

    @Test
    fun sexWithoutAnAge_usesTheMiddleAdultBand() {
        val references = MicroReferences.forPerson(BodySex.Female, null)
        assertEquals(ReferenceBasis.Sex, references.basis)
        assertEquals(18.0, references.of(Micronutrient.Iron))
        assertEquals(320.0, references.of(Micronutrient.Magnesium))
    }

    @Test
    fun nothingKnown_orUnsaidWithoutAnAge_isTheDailyValue() {
        listOf(MicroReferences.forPerson(null, null), MicroReferences.forPerson(BodySex.Unsaid, null)).forEach { references ->
            assertEquals(ReferenceBasis.DailyValue, references.basis)
            Micronutrient.entries.forEach { assertEquals(it.dailyValue, references.of(it), "$it") }
        }
    }

    @Test
    fun everyMicro_hasAPositiveValue_inEveryBand_forEverySex() {
        AgeBand.entries.forEach { band ->
            listOf(BodySex.Female, BodySex.Male, BodySex.Unsaid).forEach { sex ->
                val references = MicroReferences.forPerson(sex, band.from)
                Micronutrient.entries.forEach { micro -> assertTrue(references.of(micro) > 0.0, "$micro $sex $band") }
            }
        }
    }

    @Test
    fun ownNumber_winsOverTheTable_andKeepsTheSuggestion() {
        val references = MicroReferences.forPerson(BodySex.Female, 40, own = mapOf(Micronutrient.VitaminD to 25.0))
        assertEquals(25.0, references.of(Micronutrient.VitaminD))
        assertEquals(15.0, references.suggested(Micronutrient.VitaminD))
        assertTrue(references.isOwn(Micronutrient.VitaminD))
        assertFalse(references.isOwn(Micronutrient.Iron))
    }

    @Test
    fun ownNumber_appliesWithNothingElseKnown() {
        val references = MicroReferences.forPerson(null, null, own = mapOf(Micronutrient.Calcium to 1000.0))
        assertEquals(1000.0, references.of(Micronutrient.Calcium))
        assertEquals(ReferenceBasis.DailyValue, references.basis)
    }

    @Test
    fun ownNumber_thatIsNotSensible_isIgnored() {
        val own = mapOf(
            Micronutrient.VitaminD to 0.0,
            Micronutrient.Iron to -4.0,
            Micronutrient.Zinc to Double.NaN,
            Micronutrient.VitaminC to 90.0 * 101,
        )
        val references = MicroReferences.forPerson(BodySex.Male, 30, own)
        assertEquals(emptyMap(), references.own)
        assertEquals(15.0, references.of(Micronutrient.VitaminD))
    }
}
