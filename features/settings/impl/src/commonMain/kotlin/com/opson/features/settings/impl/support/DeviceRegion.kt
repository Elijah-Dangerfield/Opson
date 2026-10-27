package com.opson.features.settings.impl.support

/**
 * The phone's region ("US", "GB"), from the system locale, or null when it has none. Find
 * support starts from it. Read on the device only: it is never sent or stored.
 */
fun interface DeviceRegion {
    fun countryCode(): String?
}
