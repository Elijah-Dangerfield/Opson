@file:OptIn(ExperimentalObjCName::class)

package com.opson.libraries.foods

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The two iOS calls [ContributionAttestation] needs, implemented in Swift (`IOSDeviceSignals`)
 * and passed into the app's component from `iOSApp.swift`: a random id kept in the Keychain
 * (`kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly`, so it never moves to another phone in a
 * backup), and a DeviceCheck token.
 */
@ObjCName("DeviceSignals", exact = true)
interface IosDeviceSignals {
    /** The Keychain id, made on first use. Null when the Keychain refused. */
    fun keychainInstallId(): String?

    /** A DeviceCheck token, base64, or null where DeviceCheck isn't supported (the simulator) or failed. */
    fun deviceCheckToken(completion: (String?) -> Unit)
}
