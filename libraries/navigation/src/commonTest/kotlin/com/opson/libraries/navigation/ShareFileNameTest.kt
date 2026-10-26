package com.opson.libraries.navigation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Both platform launchers write `cacheDir/shared/<name>`, so the name must stay a file name. */
class ShareFileNameTest {

    @Test
    fun keepsAPlainName() {
        assertEquals("opson-export.csv", shareFileName("opson-export.csv"))
    }

    @Test
    fun dropsAnyPath() {
        assertEquals("export.csv", shareFileName("../../databases/export.csv"))
        assertEquals("export.csv", shareFileName("..\\export.csv"))
        assertEquals("export.csv", shareFileName(" /tmp/export.csv "))
    }

    @Test
    fun refusesNamesThatAreNotFiles() {
        listOf("", "  ", "..", "a/", "/").forEach { name ->
            assertFailsWith<IllegalArgumentException>(name) { shareFileName(name) }
        }
    }
}
