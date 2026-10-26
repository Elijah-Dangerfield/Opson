package com.opson.server.data

import com.opson.server.domain.CatalogSource
import com.opson.server.domain.Food
import com.opson.server.domain.FoodMatch
import com.opson.server.domain.MatchTier
import com.opson.server.domain.Nutrients
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OpenFoodFactsSearchTest {

    private fun match(name: String, source: CatalogSource, tier: MatchTier, barcode: String? = null) = FoodMatch(
        Food(UUID.randomUUID(), source, name, name, null, barcode, Nutrients(kcal = 100.0), emptyMap(), emptyList()),
        tier,
        1.0,
    )

    private fun generic(name: String, tier: MatchTier) = match(name, CatalogSource.UsdaFndds, tier)
    private fun branded(name: String, tier: MatchTier, barcode: String? = null) = match(name, CatalogSource.UsdaBranded, tier, barcode)
    private fun off(name: String, tier: MatchTier, barcode: String? = null) = match(name, CatalogSource.OpenFoodFacts, tier, barcode)

    private fun names(matches: List<FoodMatch>) = matches.map { it.food.name }

    @Test
    fun openFoodFactsNeverPassesAGenericFood() {
        val merged = OpenFoodFactsSearch.merge(
            catalog = listOf(generic("Milk, whole", MatchTier.Head), generic("Chocolate milk", MatchTier.FullText)),
            openFoodFacts = listOf(off("Milk", MatchTier.Exact)),
            limit = 10,
        )
        assertEquals(listOf("Milk, whole", "Chocolate milk", "Milk"), names(merged))
    }

    @Test
    fun itFollowsBrandedRowsOfTheSameMatchAndPassesWorseOnes() {
        val merged = OpenFoodFactsSearch.merge(
            catalog = listOf(
                branded("Pain de mie [Branded]", MatchTier.Prefix),
                branded("Bread, pain de mie style", MatchTier.FullText),
            ),
            openFoodFacts = listOf(off("Pain de mie", MatchTier.Exact), off("Pain de mie complet", MatchTier.Prefix)),
            limit = 10,
        )
        assertEquals(
            listOf("Pain de mie", "Pain de mie [Branded]", "Pain de mie complet", "Bread, pain de mie style"),
            names(merged),
        )
    }

    @Test
    fun typoMatchesGoLastAndOrderIsKept() {
        val merged = OpenFoodFactsSearch.merge(
            catalog = listOf(generic("Banana, raw", MatchTier.Head), generic("Bananna split", MatchTier.Similar)),
            openFoodFacts = listOf(off("Banane", MatchTier.Similar), off("Bananes", MatchTier.Similar)),
            limit = 10,
        )
        assertEquals(listOf("Banana, raw", "Bananna split", "Banane", "Bananes"), names(merged))
    }

    @Test
    fun aProductTheCatalogHasIsNotRepeated() {
        val merged = OpenFoodFactsSearch.merge(
            catalog = listOf(branded("Nutella", MatchTier.Exact, barcode = "03017620422003")),
            openFoodFacts = listOf(off("Nutella", MatchTier.Exact, barcode = "03017620422003"), off("Nutella B-ready", MatchTier.Prefix)),
            limit = 10,
        )
        assertEquals(listOf(CatalogSource.UsdaBranded, CatalogSource.OpenFoodFacts), merged.map { it.food.source })
        assertEquals("Nutella B-ready", merged.last().food.name)
    }

    @Test
    fun theLimitHolds() {
        val merged = OpenFoodFactsSearch.merge(
            catalog = List(3) { generic("Rice $it", MatchTier.Head) },
            openFoodFacts = List(3) { off("Riz $it", MatchTier.Prefix) },
            limit = 4,
        )
        assertEquals(listOf("Rice 0", "Rice 1", "Rice 2", "Riz 0"), names(merged))
    }

    @Test
    fun countryTagsUseOpenFoodFactsNames() {
        assertEquals("en:united-states", OpenFoodFactsSearch.countryTag("US"))
        assertEquals("en:france", OpenFoodFactsSearch.countryTag("fr"))
        assertEquals("en:united-kingdom", OpenFoodFactsSearch.countryTag("GB"))
        assertEquals("en:czech-republic", OpenFoodFactsSearch.countryTag("CZ"))
        assertNull(OpenFoodFactsSearch.countryTag(null))
        assertNull(OpenFoodFactsSearch.countryTag("USA"))
        assertNull(OpenFoodFactsSearch.countryTag("1!"))
    }
}
