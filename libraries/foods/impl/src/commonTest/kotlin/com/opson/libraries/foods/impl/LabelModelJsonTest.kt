package com.opson.libraries.foods.impl

import com.opson.libraries.foods.LabelNutrient
import com.opson.libraries.foods.ServingUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LabelModelJsonTest {

    @Test
    fun theBridgesAnswerBecomesFacts() {
        val facts = LabelModelJson.read(
            """{"servingSize":55,"servingUnit":"g","servingsPerContainer":8,"perServing":{"energy_kcal":230,"fat_g":8},"per100":{}}""",
        )!!

        assertEquals(55.0, facts.servingSize)
        assertEquals(ServingUnit.Gram, facts.servingUnit)
        assertEquals(8.0, facts.servingsPerContainer)
        assertEquals(mapOf(LabelNutrient.Energy to 230.0, LabelNutrient.Fat to 8.0), facts.perServing)
    }

    @Test
    fun unknownKeysAndImpossibleNumbersAreLeftOut() {
        val facts = LabelModelJson.read("""{"servingSize":-3,"perServing":{"vibes_g":1,"protein_g":-2,"carbs_g":37},"mood":"good"}""")!!

        assertNull(facts.servingSize)
        assertEquals(mapOf(LabelNutrient.Carbs to 37.0), facts.perServing)
    }

    @Test
    fun somethingThatIsNotItsJsonIsNoAnswer() {
        assertNull(LabelModelJson.read("I could not read that label."))
    }
}
