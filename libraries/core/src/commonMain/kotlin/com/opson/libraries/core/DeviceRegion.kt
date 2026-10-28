package com.opson.libraries.core

/**
 * The phone's region ("US", "GB"), from the system locale, or null when it has none. Find
 * support starts from it, and onboarding picks height and weight units from it. Read on the
 * device only: it is never sent or stored. Bound per platform in `:features:settings:impl`.
 */
fun interface DeviceRegion {
    fun countryCode(): String?
}
