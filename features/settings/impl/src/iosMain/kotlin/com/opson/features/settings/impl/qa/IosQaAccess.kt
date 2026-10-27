package com.opson.features.settings.impl.qa

import com.opson.libraries.core.BuildInfo
import me.tatarka.inject.annotations.Inject
import platform.Foundation.NSBundle
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/**
 * TestFlight and Xcode installs carry a sandbox receipt, App Store installs a production one. The
 * receipt's URL is there before StoreKit fetches the file, so reading its name costs nothing.
 */
@Inject
@ContributesBinding(AppScope::class)
class IosQaAccess : QaAccess {
    private val allowed: Boolean by lazy {
        val sandbox = NSBundle.mainBundle.appStoreReceiptURL?.lastPathComponent == "sandboxReceipt"
        QaGate.allowed(BuildInfo.isDebug, BuildInfo.releaseChannel, sandboxReceipt = sandbox)
    }

    override fun allowed(): Boolean = allowed
}
