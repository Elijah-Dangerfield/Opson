package com.opson.util

import org.gradle.api.GradleException
import org.gradle.testfixtures.ProjectBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** `-Prelease.signing=true` either finds the upload key or fails; it never signs with the debug key. */
class ReleaseSigningTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Before
    fun noUploadKeyInTheEnvironment() {
        // The env wins over local.properties, so a machine that exports the upload key can't run these.
        assumeTrue(System.getenv("ANDROID_KEYSTORE_PATH").isNullOrBlank() && System.getenv("ANDROID_KEYSTORE_BASE64").isNullOrBlank())
    }

    @Test
    fun notAskedFor_isNull() {
        assertNull(project().releaseCredentials())
    }

    @Test
    fun askedForWithNoKey_failsTheBuild() {
        val project = project().also { it.extensions.extraProperties["release.signing"] = "true" }

        assertThrows(GradleException::class.java) { project.releaseCredentials() }
    }

    @Test
    fun askedForWithAKeyInLocalProperties_usesIt() {
        val root = folder.root
        val keystore = folder.newFile("upload.keystore")
        root.resolve("local.properties").writeText(
            """
            android.keystore.path=${keystore.absolutePath}
            android.keystore.password=secret
            android.key.alias=upload
            """.trimIndent(),
        )
        val project = project().also { it.extensions.extraProperties["release.signing"] = "true" }

        val credentials = project.releaseCredentials()

        assertEquals(keystore, credentials?.keystore)
        assertEquals("upload", credentials?.keyAlias)
    }

    private fun project() = ProjectBuilder.builder().withProjectDir(folder.root).build()
}
