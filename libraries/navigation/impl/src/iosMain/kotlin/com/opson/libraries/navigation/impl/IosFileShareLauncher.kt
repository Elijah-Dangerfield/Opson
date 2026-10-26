package com.opson.libraries.navigation.impl

import com.opson.libraries.core.Catching
import com.opson.libraries.navigation.FileShareLauncher
import com.opson.libraries.navigation.FileSharePresenter
import com.opson.libraries.navigation.shareFileName
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import me.tatarka.inject.annotations.Inject
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.create
import platform.Foundation.writeToFile
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * Writes the file to `tmp/shared/` and asks the Swift [FileSharePresenter] to show the share
 * sheet for it. iOS clears the temporary directory on its own schedule, which is the lifetime an
 * export wants.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosFileShareLauncher @Inject constructor(
    private val presenter: FileSharePresenter,
) : FileShareLauncher {

    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    override fun share(name: String, mimeType: String, bytes: ByteArray): Catching<Unit> = Catching {
        val directory = NSTemporaryDirectory().trimEnd('/') + "/shared"
        NSFileManager.defaultManager.createDirectoryAtPath(
            directory,
            withIntermediateDirectories = true,
            attributes = null,
            error = null,
        )
        val path = "$directory/${shareFileName(name)}"
        val data = if (bytes.isEmpty()) {
            NSData()
        } else {
            bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        }
        check(data.writeToFile(path, atomically = true)) { "Could not write the file to share" }
        presenter.present(path)
    }
}
