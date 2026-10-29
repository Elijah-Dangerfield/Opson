package com.opson.libraries.core

import android.os.Build
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

@Inject
@ContributesBinding(AppScope::class)
class AndroidDeviceDescription : DeviceDescription {
    override fun summary(): String {
        // "Pixel 8" needs its maker in front; a model that already names it ("Nokia G21") does not.
        val model = Build.MODEL.orEmpty()
        val maker = Build.MANUFACTURER.orEmpty().replaceFirstChar { it.uppercase() }
        val device = if (model.startsWith(maker, ignoreCase = true)) model else "$maker $model".trim()
        return "$device, Android ${Build.VERSION.RELEASE}"
    }
}
