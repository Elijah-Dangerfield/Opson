package com.opson.features.settings.impl.qa

/**
 * Whether this binary offers the QA menu: its More row and its route. Bound per platform, since
 * only iOS can tell at run time where the binary came from.
 */
fun interface QaAccess {
    fun allowed(): Boolean
}

/**
 * The rule behind [QaAccess], pure so it can be tested.
 *
 * Debug builds always get it, and `store` builds never do. A `beta` or `dev` release build gets
 * it unless the platform can see it came from the App Store: promoting a TestFlight build to the
 * App Store ships the same binary, still stamped `beta` (AGENTS.md, Known landmines), so the
 * channel alone would put the menu in front of customers. iOS answers that from the receipt
 * ([sandboxReceipt] false means a production receipt). Android cannot tell a Play internal-track
 * install from a production one, so there a promoted `beta` build keeps the menu.
 */
object QaGate {
    const val STORE_CHANNEL = "store"

    fun allowed(isDebug: Boolean, releaseChannel: String, sandboxReceipt: Boolean?): Boolean = when {
        isDebug -> true
        releaseChannel == STORE_CHANNEL -> false
        sandboxReceipt == false -> false
        else -> true
    }
}
