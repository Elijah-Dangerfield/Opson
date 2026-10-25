package com.opson.libraries.core

import com.opson.buildinfo.OpsonBuildConfig
import com.opson.libraries.core.BuildConfig as AndroidBuildConfig

actual object BuildInfo {
    actual val isDebug: Boolean
        get() = AndroidBuildConfig.DEBUG

    actual val platform: Platform
        get() = Platform.Android

    actual val applicationId: String
        get() = OpsonBuildConfig.APPLICATION_ID

    actual val versionName: String
        get() = OpsonBuildConfig.VERSION_NAME

    actual val versionCode: Int
        get() = OpsonBuildConfig.VERSION_CODE

    actual val releaseChannel: String
        get() = OpsonBuildConfig.RELEASE_CHANNEL

    actual val buildNumber: Int
        get() = OpsonBuildConfig.BUILD_NUMBER

    actual val commitSha: String
        get() = OpsonBuildConfig.COMMIT_SHA

    actual val commitBranch: String
        get() = OpsonBuildConfig.COMMIT_BRANCH
}