package com.opson.libraries.progressphotos.impl

import com.opson.libraries.progressphotos.ProgressPhotoFileStore
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSData
import platform.Foundation.NSDataWritingAtomic
import platform.Foundation.NSDataWritingFileProtectionComplete
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToURL
import platform.posix.memcpy
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * `Application Support/progress_photos`: inside the app's sandbox, so not the
 * photo library and not visible in Files. The folder is marked excluded from
 * iCloud and computer backups, and every file is written atomically with
 * complete protection, so it can't be read while the phone is locked.
 */
@OptIn(ExperimentalForeignApi::class)
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosProgressPhotoFileStore @Inject constructor() : ProgressPhotoFileStore {

    private val manager get() = NSFileManager.defaultManager

    private fun folder(create: Boolean): NSURL? {
        val support = manager.URLForDirectory(
            directory = NSApplicationSupportDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = create,
            error = null,
        ) ?: return null
        val folder = support.URLByAppendingPathComponent(FOLDER, isDirectory = true) ?: return null
        if (create) {
            manager.createDirectoryAtURL(folder, withIntermediateDirectories = true, attributes = null, error = null)
            // Set every time, not only on creation: a restore or an older build could have dropped it.
            folder.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
        }
        return folder
    }

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) {
        val url = folder(create = false)?.URLByAppendingPathComponent(name) ?: return@withContext null
        NSData.dataWithContentsOfURL(url)?.toBytes()
    }

    override suspend fun write(name: String, bytes: ByteArray) {
        withContext(Dispatchers.IO) {
            val url = folder(create = true)?.URLByAppendingPathComponent(name) ?: error("No Application Support folder")
            val written = bytes.toNSData().writeToURL(url, options = NSDataWritingAtomic or NSDataWritingFileProtectionComplete, error = null)
            check(written) { "Couldn't write a progress photo file" }
        }
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) {
            folder(create = false)?.URLByAppendingPathComponent(name)?.let { manager.removeItemAtURL(it, error = null) }
        }
    }

    override suspend fun deleteAll() {
        withContext(Dispatchers.IO) {
            folder(create = false)?.let { manager.removeItemAtURL(it, error = null) }
        }
    }

    private companion object {
        const val FOLDER = "progress_photos"
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toBytes(): ByteArray {
    val size = length.toInt()
    val bytes = ByteArray(size)
    if (size > 0) bytes.usePinned { memcpy(it.addressOf(0), this.bytes, length) }
    return bytes
}

@OptIn(ExperimentalForeignApi::class)
private fun ByteArray.toNSData(): NSData {
    if (isEmpty()) return NSData()
    return usePinned { NSData.create(bytes = it.addressOf(0), length = size.toULong()) }
}
