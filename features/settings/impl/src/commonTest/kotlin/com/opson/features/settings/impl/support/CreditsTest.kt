package com.opson.features.settings.impl.support

import com.opson.features.settings.impl.eventsOf
import com.opson.libraries.flowroutines.testing.CoroutineTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Credits: the generated licence list becomes rows and licence texts, and a licence opens in
 * place when it has a text and in the browser when it only has a page.
 */
class CreditsTest : CoroutineTest() {

    // The shape `exportLibraryDefinitions` writes, trimmed.
    private val json = """
        {
          "libraries": [
            {"uniqueId": "io.ktor:ktor-client-core", "artifactVersion": "3.3.1", "name": "ktor-client-core", "licenses": ["Apache-2.0"]},
            {"uniqueId": "com.google.mlkit:barcode-scanning", "name": "Barcode Scanning", "licenses": ["9962f4"]},
            {"uniqueId": "io.sentry:sentry-cocoa", "artifactVersion": "8.57.3", "name": "Sentry Cocoa", "licenses": ["MIT"], "website": "https://github.com/getsentry/sentry-cocoa"}
          ],
          "licenses": {
            "Apache-2.0": {"name": "Apache License 2.0", "url": "https://spdx.org/licenses/Apache-2.0.html", "content": "Apache License\nVersion 2.0\n"},
            "MIT": {"name": "MIT License", "content": "Permission is hereby granted"},
            "9962f4": {"name": "ML Kit Terms of Service", "url": "https://developers.google.com/ml-kit/terms", "hash": "9962f4"}
          }
        }
    """.trimIndent()

    private val credits = CreditsStateBuilder.build(json, fontLicense = "Copyright 2020-2024\n")

    @Test
    fun librariesAreSortedByName_withTheirLicencesNamed() {
        assertEquals(
            listOf(
                CreditedLibrary("com.google.mlkit:barcode-scanning", "Barcode Scanning", null, listOf("ML Kit Terms of Service")),
                CreditedLibrary("io.ktor:ktor-client-core", "ktor-client-core", "3.3.1", listOf("Apache License 2.0")),
                CreditedLibrary("io.sentry:sentry-cocoa", "Sentry Cocoa", "8.57.3", listOf("MIT License")),
            ),
            credits.libraries,
        )
    }

    @Test
    fun theTypefaceLicenceComesFirst_thenEachLicenceOnce() {
        assertEquals(
            listOf("OFL-1.1", "Apache-2.0", "MIT", "9962f4"),
            credits.licenses.map { it.id },
        )
        assertEquals("Copyright 2020-2024", credits.licenses.first().text)
        assertNull(credits.licenses.last().text)
    }

    @Test
    fun aLicenceWithATextOpensInPlace_andClosesAgain() = runUnitTest {
        val vm = CreditsViewModel()
        vm.takeAction(CreditsAction.Loaded(credits))

        vm.takeAction(CreditsAction.LicenseTapped("Apache-2.0"))
        assertEquals("Apache-2.0", vm.stateFlow.value.openLicense)

        vm.takeAction(CreditsAction.LicenseTapped("MIT"))
        assertEquals("MIT", vm.stateFlow.value.openLicense)

        vm.takeAction(CreditsAction.LicenseTapped("MIT"))
        assertNull(vm.stateFlow.value.openLicense)
    }

    @Test
    fun aLicenceWithOnlyAPageOpensThePage() = runUnitTest {
        val vm = CreditsViewModel()
        vm.takeAction(CreditsAction.Loaded(credits))
        val events = eventsOf(vm)

        vm.takeAction(CreditsAction.LicenseTapped("9962f4"))

        assertEquals(listOf<CreditsEvent>(CreditsEvent.OpenLink("https://developers.google.com/ml-kit/terms")), events)
        assertNull(vm.stateFlow.value.openLicense)
    }
}
