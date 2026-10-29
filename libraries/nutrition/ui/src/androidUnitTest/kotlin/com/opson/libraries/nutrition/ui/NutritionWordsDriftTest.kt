package com.opson.libraries.nutrition.ui

import com.opson.libraries.nutrition.AmountWords
import com.opson.libraries.nutrition.NutritionWords
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [NutritionWords]' and [AmountWords]' defaults are what every test and preview spells with, and
 * `strings_nutrition.xml` is what the app does. This reads the XML from disk, so a wording change
 * in one that misses the other fails here instead of drifting quietly.
 */
class NutritionWordsDriftTest {

    @Test
    fun `the English defaults are strings_nutrition_xml`() {
        val xml = ResourceXml.read("strings_nutrition.xml")

        val fromXml = runBlocking { loadNutritionWords { resource -> xml.getValue(resource.key) } }

        assertEquals(NutritionWords(), fromXml)
    }

    @Test
    fun `the amount units are strings_nutrition_xml`() {
        val xml = ResourceXml.read("strings_nutrition.xml")

        val fromXml = runBlocking { loadAmountWords { resource -> xml.getValue(resource.key) } }

        assertEquals(AmountWords(), fromXml)
    }
}

/** A composeResources strings file as name to text, entities decoded. */
internal object ResourceXml {
    private val STRING = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)

    fun read(file: String): Map<String, String> {
        // Unit tests run in the module's directory: libraries/nutrition/ui.
        val text = File("../../resources/src/commonMain/composeResources/values/$file").readText()
        return STRING.findAll(text).associate { match ->
            match.groupValues[1] to match.groupValues[2]
                .replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
                .replace("&amp;", "&")
        }
    }
}
