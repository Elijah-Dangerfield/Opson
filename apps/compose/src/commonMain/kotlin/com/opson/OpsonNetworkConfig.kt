package com.opson

import com.opson.libraries.core.BackendEnvironment
import com.opson.libraries.networking.NetworkConfig
import com.opson.libraries.networking.impl.DefaultNetworkConfig
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The app's [NetworkConfig]: the API server [BackendEnvironment] picks for
 * this build. Tests that need another server construct their own
 * `NetworkConfig` (the integration harness does) rather than overriding this
 * one.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [DefaultNetworkConfig::class])
@Inject
class OpsonNetworkConfig : NetworkConfig {
    override val baseUrl: String = BackendEnvironment.forBuild().apiBaseUrl
}
