package com.opson.features.settings.impl.support

import kotlinx.coroutines.test.runTest
import opson.libraries.resources.generated.resources.Res
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The committed `files/licenses.json` parses and carries what the screen promises. On iOS
 * because Compose resources need an Android context under a plain JVM unit test.
 */
class ShippedCreditsTest {

    @Test
    fun theShippedListParses_andCarriesTheLicenceTexts() = runTest {
        val shipped = CreditsStateBuilder.build(
            Res.readBytes(LICENSES_FILE).decodeToString(),
            Res.readBytes(FONT_LICENSE_FILE).decodeToString(),
        )

        assertTrue(shipped.libraries.size > 100, "only ${shipped.libraries.size} libraries")
        assertTrue(shipped.libraries.any { it.id == "io.sentry:sentry-cocoa" }, "Sentry Cocoa comes from apps/compose/aboutlibraries")
        assertTrue(shipped.libraries.any { it.id == "io.ktor:ktor-client-darwin" }, "the iOS-only libraries are in")
        assertTrue(shipped.licenses.first { it.id == "Apache-2.0" }.text.orEmpty().contains("Apache License"))
        assertTrue(shipped.licenses.first().text.orEmpty().contains("SIL OPEN FONT LICENSE"))
    }
}
