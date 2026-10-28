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
    fun oneProductListedUnderManyBarcodesShowsOnce() {
        val label = Nutrients(kcal = 539.0, protein = 6.3, carbs = 57.5, fat = 30.9)
        fun nutella(name: String, barcode: String, per100g: Nutrients = label) = FoodMatch(
            Food(UUID.randomUUID(), CatalogSource.OpenFoodFacts, barcode, name, "Ferrero", barcode, per100g, emptyMap(), emptyList()),
            MatchTier.Exact,
            1.0,
        )
        val mostScanned = nutella("Nutella", "3017620422003")

        val merged = OpenFoodFactsSearch.merge(
            catalog = emptyList(),
            openFoodFacts = listOf(
                mostScanned,
                nutella("Nutella", "3017620425035"),
                nutella("NUTELLA ", "8000500310427"),
                nutella("Nutella", "3017620429484", label.copy(fat = 31.6)),
            ),
            limit = 10,
        )

        assertEquals(listOf("3017620422003", "3017620429484"), merged.map { it.food.barcode })
        assertEquals(mostScanned, merged.first())
    }

    @Test
    fun aNamedBrandsBrandedRowsDoNotPushTheProductOffTheFirstScreen() {
        // "nutella" names a brand, so the catalog lists that brand's Branded rows
        // (matched through the brand) above any generic food.
        val brandRows = List(25) { branded("Hazelnut Spread with Cocoa $it", MatchTier.FullText) }
        val merged = OpenFoodFactsSearch.merge(
            catalog = brandRows + generic("Chocolate hazelnut spread", MatchTier.FullText),
            openFoodFacts = listOf(off("Nutella", MatchTier.Exact), off("Nutella biscuits", MatchTier.FullText)),
            limit = 25,
        )
        assertEquals("Nutella", merged.first().food.name)
        assertEquals(25, merged.size)
        assertEquals(brandRows.take(24), merged.drop(1))
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

    @Test
    fun aHeaderWithNonAsciiLettersIsNoCountry() {
        // X-Country-Code is read as Latin-1; Locale.Builder throws on a region that isn't A to Z.
        assertNull(OpenFoodFactsSearch.countryTag("éé"))
        assertNull(OpenFoodFactsSearch.countryTag("ßx"))
    }
}
