@file:OptIn(ExperimentalObjCName::class)

package com.opson.libraries.navigation

import kotlin.experimental.ExperimentalObjCName
import kotlin.native.ObjCName

/**
 * The Swift half of [FileShareLauncher] on iOS: presents a `UIActivityViewController` for a file
 * Kotlin has already written. Implemented by `IOSFileSharePresenter` in the iOS app and passed into
 * the DI graph through `IosAppComponent`'s `create(...)`, like `ReviewLauncher`.
 *
 * Kotlin keeps the bytes and writes the file, and Swift only gets a path, because a Kotlin
 * `ByteArray` crosses into Swift as `KotlinByteArray`, which can only be copied out a byte at a
 * time. The presenter must hop to the main thread itself; it may be called from any thread.
 */
@ObjCName("FileSharePresenter", exact = true)
interface FileSharePresenter {
    /** [filePath] is an absolute path in the app's temporary directory. */
    fun present(filePath: String)
}
