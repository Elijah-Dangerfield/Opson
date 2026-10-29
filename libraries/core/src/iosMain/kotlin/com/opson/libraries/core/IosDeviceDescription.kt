package com.opson.libraries.core

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.alloc
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toKString
import me.tatarka.inject.annotations.Inject
import platform.UIKit.UIDevice
import platform.posix.uname
import platform.posix.utsname
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding

@Inject
@ContributesBinding(AppScope::class)
class IosDeviceDescription : DeviceDescription {
    override fun summary(): String {
        val device = UIDevice.currentDevice
        return "${machine() ?: device.model}, ${device.systemName} ${device.systemVersion}"
    }

    /**
     * The hardware identifier ("iPhone15,4"). `UIDevice.model` only ever says "iPhone", which tells
     * triage nothing. The simulator reports its host's architecture here, which is true enough.
     */
    @OptIn(ExperimentalForeignApi::class)
    private fun machine(): String? = memScoped {
        val info = alloc<utsname>()
        if (uname(info.ptr) != 0) return@memScoped null
        info.machine.toKString().takeIf { it.isNotBlank() }
    }
}
