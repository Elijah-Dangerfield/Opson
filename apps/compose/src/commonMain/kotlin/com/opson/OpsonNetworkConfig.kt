package com.opson

import com.opson.libraries.core.BuildInfo
import com.opson.libraries.networking.NetworkConfig
import com.opson.libraries.networking.impl.DefaultNetworkConfig
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/** The Fly apps in `apps/server/fly.toml` (dev) and `fly.prod.toml` (prod). */
internal const val DEV_API_BASE_URL = "https://opson-server-dev.fly.dev"
internal const val PROD_API_BASE_URL = "https://opson-server-prod.fly.dev"

/**
 * Debug builds talk to the dev server, release builds to prod. Keyed on
 * [BuildInfo.isDebug] rather than the release channel, because a TestFlight
 * build promoted to the App Store keeps saying `beta` forever (AGENTS.md,
 * Known landmines) and must still reach prod.
 */
internal fun apiBaseUrl(isDebug: Boolean): String = if (isDebug) DEV_API_BASE_URL else PROD_API_BASE_URL

/**
 * The app's [NetworkConfig]. Tests that need another server construct their
 * own `NetworkConfig` (the integration harness does) rather than overriding
 * this one.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [DefaultNetworkConfig::class])
@Inject
class OpsonNetworkConfig : NetworkConfig {
    override val baseUrl: String = apiBaseUrl(BuildInfo.isDebug)
}
