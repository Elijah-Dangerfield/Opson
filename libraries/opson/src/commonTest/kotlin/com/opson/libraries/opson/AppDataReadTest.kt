package com.opson.libraries.opson

import com.opson.libraries.storage.versionedJsonSerializer
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A field dropped from [AppData] must not make an older file unreadable: an unreadable
 * `app_data` resets to defaults, which sends an onboarded person back through onboarding.
 */
class AppDataReadTest {

    @Test
    fun aFileWithFieldsSinceDropped_stillReads() = runTest {
        val written = """
            {"v":1,"d":{"hasUserOnboarded":true,"installId":"abc","screenVisits":{"feedbackScreenOpens":2},"feedbacksGiven":1}}
        """.trimIndent()

        val read = versionedJsonSerializer(defaultValue = { AppData() }).read(written.encodeToByteArray())

        assertTrue(read.hasUserOnboarded)
        assertEquals("abc", read.installId)
    }
}
