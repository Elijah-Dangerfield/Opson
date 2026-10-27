package com.opson.features.logger.impl

import com.opson.libraries.diary.MealSlot
import com.opson.libraries.foods.FoodRef
import com.opson.libraries.foods.FoodSource
import com.opson.libraries.nutrition.PresetId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** RIVM allows NEVO's foods only with its citation, so a NEVO food names it in search and on the food detail. */
class NevoCitationTest {
    private val full = policy(PresetId.Full)
    private val nevoRice = LoggerFixtures.rice.copy(id = "rice-nevo", name = "Rice white boiled", source = FoodSource.Nevo)

    @Test
    fun aNevoRowCarriesTheNevoBadge() {
        assertEquals(SourceBadge.Nevo, SourceBadge.of(FoodSource.Nevo))
    }

    @Test
    fun aNevoSearchRowIsBadgedAndTheResultsCiteItEvenWithTheSearchOptionsOff() {
        val search = SearchOutcome("rice", listOf(LoggerFixtures.rice, nevoRice), offline = false)
        val results = assertIs<SearchBody.Results>(LoggerFixtures.searchState(full, search = search, added = false).body)

        assertEquals(SourceBadge.Nevo, results.rows.single { it.key == "f:rice-nevo" }.badge)
        assertNull(results.rows.single { it.key == "f:rice" }.badge, "only NEVO's licence asks for a badge with the options off")
        assertTrue(results.nevo)
        assertEquals(false, assertIs<SearchBody.Results>(LoggerFixtures.searchState(full, added = false).body).nevo)
    }

    @Test
    fun theFoodDetailCitesNevo() {
        val food = AmountFood.Catalog(LoggerFixtures.arroz.copy(source = FoodSource.Nevo))
        val state = FoodDetailStateBuilder.build(
            food,
            DetailChoice.initial(food, MealSlot.Dinner),
            LoggerFixtures.detailInputs(full),
            FoodRef.Catalog("arroz"),
            PickSource.Search,
        )

        assertEquals(FoodOrigin.Nevo, state.source?.source)
    }
}
