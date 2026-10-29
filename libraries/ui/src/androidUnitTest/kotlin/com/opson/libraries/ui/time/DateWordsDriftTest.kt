package com.opson.libraries.ui.time

import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * [DateWords]' defaults are what every test and preview reads, and `strings_ui.xml` is what the
 * app does. This reads the XML from disk, so a wording change in one that misses the other fails
 * here instead of drifting quietly.
 */
class DateWordsDriftTest {

    @Test
    fun `the English defaults are strings_ui_xml`() {
        // Unit tests run in the module's directory: libraries/ui.
        val text = File("../resources/src/commonMain/composeResources/values/strings_ui.xml").readText()
        val xml = Regex("""<string name="([^"]+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text).associate { it.groupValues[1] to it.groupValues[2] }

        val fromXml = runBlocking { loadDateWords { resource -> xml.getValue(resource.key) } }

        assertEquals(DateWords(), fromXml)
    }
}
