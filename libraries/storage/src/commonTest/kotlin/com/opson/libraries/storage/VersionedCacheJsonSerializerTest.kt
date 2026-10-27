package com.opson.libraries.storage

import androidx.datastore.core.CorruptionException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class VersionedCacheJsonSerializerTest {

    @Serializable
    private data class ProfileV1(val email: String = "", val weightKg: Int = 0)

    @Serializable
    private data class ProfileV2(val email: String = "", val streak: Int = 0)

    @Test
    fun aFieldAddedOrRemovedBetweenReleases_stillReads() = runTest {
        val old = versionedJsonSerializer(defaultValue = { ProfileV1() })
        val new = versionedJsonSerializer(defaultValue = { ProfileV2() })

        val bytes = old.write(ProfileV1(email = "sam@example.com", weightKg = 81))

        assertEquals(ProfileV2(email = "sam@example.com"), new.read(bytes))
    }

    /**
     * The corruption handler logs this exception, with its causes, to Sentry.
     * kotlinx.serialization quotes the JSON around a failure ("JSON input:
     * ..."), and these files hold emails, names and body weights.
     */
    @Test
    fun anUnreadableFile_doesNotCarryItsContentsIntoTheException() = runTest {
        val serializer = versionedJsonSerializer(defaultValue = { ProfileV1() })
        val wrongType = """{"v":1,"d":{"email":"sam@example.com","weightKg":"eighty-one"}}"""
        val truncated = """{"v":1,"d":{"email":"sam@example.com","weightKg":8"""

        listOf(wrongType, truncated).forEach { file ->
            val failure = assertFailsWith<CorruptionException> { serializer.read(file.encodeToByteArray()) }

            val reported = generateSequence<Throwable>(failure) { it.cause }.joinToString { it.message.orEmpty() }
            assertFalse("sam@example.com" in reported, reported)
        }
    }
}
