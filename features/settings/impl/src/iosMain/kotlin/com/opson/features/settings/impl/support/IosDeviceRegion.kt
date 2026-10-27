package com.opson.features.settings.impl.support

import me.tatarka.inject.annotations.Inject
import platform.Foundation.NSLocale
import platform.Foundation.countryCode
import platform.Foundation.currentLocale
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** The current locale's region, which follows Settings > General > Language & Region. */
@Inject
@ContributesBinding(AppScope::class)
class IosDeviceRegion : DeviceRegion {
    override fun countryCode(): String? = NSLocale.currentLocale.countryCode
}
