package com.opson.libraries.core

expect object BuildInfo {
    val isDebug: Boolean
    val platform: Platform
    val applicationId: String
    val versionName: String
    val versionCode: Int
    val releaseChannel: String
    val buildNumber: Int

    /** Short git SHA the build was produced from — `GITHUB_SHA` in CI,
     *  `git rev-parse` locally, `"unknown"` when neither is available. */
    val commitSha: String

    /** Branch the build was produced from (`GITHUB_REF_NAME` in CI). */
    val commitBranch: String
}

fun BuildInfo.isiOS() = BuildInfo.platform == Platform.iOS
val BuildInfo.buildType: String get() = if (BuildInfo.isDebug) "debug" else "release"
fun BuildInfo.versionString(): String = "$versionName ($buildNumber)"


enum class Platform {
    Android,
    iOS
}