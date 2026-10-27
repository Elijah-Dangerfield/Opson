package com.opson.features.settings.impl.qa

import com.opson.libraries.core.BuildInfo
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** The channel alone: Play reports the same installer for the internal track and production. */
@Inject
@ContributesBinding(AppScope::class)
class AndroidQaAccess : QaAccess {
    override fun allowed(): Boolean = QaGate.allowed(BuildInfo.isDebug, BuildInfo.releaseChannel, sandboxReceipt = null)
}
