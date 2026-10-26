package com.opson.features.settings.impl.support

import java.util.Locale
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

/** The default locale's region, which follows the region the person set on the phone. */
@Inject
@ContributesBinding(AppScope::class)
class AndroidDeviceRegion : DeviceRegion {
    override fun countryCode(): String? = Locale.getDefault().country.takeIf { it.isNotBlank() }
}
