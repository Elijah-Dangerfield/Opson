package com.opson.libraries.core

import com.opson.buildinfo.OpsonBuildConfig
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.Platform as NativePlatform

@OptIn(ExperimentalNativeApi::class)
actual object BuildInfo {
    actual val isDebug: Boolean
        get() = NativePlatform.isDebugBinary

    actual val platform: Platform = Platform.iOS

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