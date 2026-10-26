package com.opson.server.data

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SearchTermsTest {

    @Test
    fun buildsPrefixAndWholeWordQueriesWithPluralVariants() {
        val terms = SearchTerms.of("boiled eggs")!!
        assertEquals("(boiled:* | boileds:*) & (eggs:* | egg:*)", terms.prefixTsQuery)
        assertEquals("(boiled | boileds) & (eggs | egg)", terms.wholeWordTsQuery)
        assertEquals(listOf("boiled eggs", "boiled egg"), terms.phraseVariants)
        assertEquals("^(boiled eggs|boiled egg)($|[^[:alnum:]])", terms.wordPrefixRegex)
    }

    @Test
    fun singularizesCommonPlurals() {
        assertEquals(listOf("berries", "berry"), SearchTerms.variantsOf("berries"))
        assertEquals(listOf("tomatoes", "tomato"), SearchTerms.variantsOf("tomatoes"))
        assertEquals(listOf("glass"), SearchTerms.variantsOf("glass"))
        assertEquals(listOf("2"), SearchTerms.variantsOf("2"))
    }

    @Test
    fun querySyntaxNeverReachesTsqueryOrRegex() {
        val terms = SearchTerms.of("milk' | !(x) & .* \\ 100%_")!!
        assertEquals("(milk:* | milks:*) & (x:*) & (100:*)", terms.prefixTsQuery)
        assertEquals("^(milk x 100)($|[^[:alnum:]])", terms.wordPrefixRegex)
        assertEquals("milk' | !(x) & .* \\\\ 100\\%\\_%", terms.likePrefix)
    }

    @Test
    fun nothingSearchableIsNull() {
        assertNull(SearchTerms.of("%%% ,,,"))
    }
}
