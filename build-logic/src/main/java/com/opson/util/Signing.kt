package com.opson.util

import com.android.build.api.dsl.ApkSigningConfig
import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.GradleException
import org.gradle.api.Project
import java.io.File
import java.util.Properties

private const val ENABLE_FLAG = "release.signing"
private const val CONFIG_NAME = "release"

private const val ENV_KEYSTORE_PATH = "ANDROID_KEYSTORE_PATH"
private const val ENV_KEYSTORE_BASE64 = "ANDROID_KEYSTORE_BASE64"
private const val ENV_KEYSTORE_PASSWORD = "ANDROID_KEYSTORE_PASSWORD"
private const val ENV_KEY_ALIAS = "ANDROID_KEY_ALIAS"
private const val ENV_KEY_PASSWORD = "ANDROID_KEY_PASSWORD"

private const val LOCAL_PROP_KEYSTORE_PATH = "android.keystore.path"
private const val LOCAL_PROP_KEYSTORE_PASSWORD = "android.keystore.password"
private const val LOCAL_PROP_KEY_ALIAS = "android.key.alias"
private const val LOCAL_PROP_KEY_PASSWORD = "android.key.password"

fun Project.configureReleaseSigning(extension: ApplicationExtension): ApkSigningConfig? {
    val credentials = releaseCredentials() ?: return null
    return extension.signingConfigs.maybeCreate(CONFIG_NAME).apply {
        storeFile = credentials.keystore
        storePassword = credentials.storePassword
        keyAlias = credentials.keyAlias
        keyPassword = credentials.keyPassword
        enableV1Signing = true
        enableV2Signing = true
    }
}

/**
 * The upload key, or null when release signing wasn't asked for (a local release build signs
 * with the debug key, which is fine for an emulator). Asked for and missing fails the build:
 * falling back to the debug key let a release run go all the way to the Play upload before
 * anything noticed, and attached a debug-signed APK to the GitHub release.
 */
internal fun Project.releaseCredentials(env: (String) -> String? = System::getenv): ReleaseCredentials? {
    val enabled = (findProperty(ENABLE_FLAG) as? String)?.toBoolean() == true
    if (!enabled) return null
    return readReleaseCredentials(this, env) ?: throw GradleException(
        "-P$ENABLE_FLAG=true but no upload key was found. Set $ENV_KEYSTORE_PATH or " +
            "$ENV_KEYSTORE_BASE64, $ENV_KEYSTORE_PASSWORD and $ENV_KEY_ALIAS (or the android.keystore.* " +
            "and android.key.* keys in local.properties), or drop -P$ENABLE_FLAG for a debug-signed build."
    )
}

internal data class ReleaseCredentials(
    val keystore: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
)

private fun readReleaseCredentials(project: Project, env: (String) -> String?): ReleaseCredentials? {
    val localProps = loadLocalProperties(project)
    fun value(envKey: String, propKey: String): String? =
        env(envKey)?.takeIf { it.isNotBlank() }
            ?: localProps.getProperty(propKey)?.takeIf { it.isNotBlank() }

    val keystore = resolveKeystoreFile(project, localProps, env) ?: return null
    val storePassword = value(ENV_KEYSTORE_PASSWORD, LOCAL_PROP_KEYSTORE_PASSWORD) ?: return null
    val keyAlias = value(ENV_KEY_ALIAS, LOCAL_PROP_KEY_ALIAS) ?: return null
    val keyPassword = value(ENV_KEY_PASSWORD, LOCAL_PROP_KEY_PASSWORD) ?: storePassword

    return ReleaseCredentials(keystore, storePassword, keyAlias, keyPassword)
}

private fun resolveKeystoreFile(project: Project, localProps: Properties, env: (String) -> String?): File? {
    val path = env(ENV_KEYSTORE_PATH)?.takeIf { it.isNotBlank() }
        ?: localProps.getProperty(LOCAL_PROP_KEYSTORE_PATH)?.takeIf { it.isNotBlank() }
    if (path != null) {
        val file = File(path)
        return if (file.exists()) file else {
            project.logger.warn("[release-signing] keystore path does not exist: $path")
            null
        }
    }

    val base64 = env(ENV_KEYSTORE_BASE64)?.takeIf { it.isNotBlank() } ?: return null
    val target = project.layout.buildDirectory.file("keystore/release.keystore").get().asFile
    target.parentFile.mkdirs()
    target.writeBytes(java.util.Base64.getDecoder().decode(base64.replace("\\s+".toRegex(), "")))
    return target
}

private fun loadLocalProperties(project: Project): Properties {
    val file = project.rootProject.file("local.properties")
    val props = Properties()
    if (file.exists()) {
        file.inputStream().use(props::load)
    }
    return props
}
