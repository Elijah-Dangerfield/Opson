package com.opson.libraries.storage.impl

import com.opson.libraries.core.logging.KLog
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUserDomainMask

/**
 * `Application Support/no_backup`, where the database and the settings files live on iOS.
 *
 * The folder, not each file, carries `NSURLIsExcludedFromBackupKey`, because the flag is an
 * attribute of one file on disk and both stores replace their files: DataStore writes a temp file
 * and renames it over the old one, and SQLite deletes and recreates its `-wal` and `-shm`. A flag
 * on the folder covers whatever is inside it. The log is on the server, so leaving it out of
 * iCloud and computer backups loses nothing, and keeps health data out of iCloud (App Store
 * guideline 5.1.3(ii)).
 *
 * Builds before this kept the same files in `Documents`, which is backed up. [adopt] moves them
 * across the first time each is opened.
 */
@OptIn(ExperimentalForeignApi::class)
internal class NoBackupDirectory(
    private val legacy: NSURL = directory(NSDocumentDirectory),
    parent: NSURL = directory(NSApplicationSupportDirectory),
) {

    private val manager get() = NSFileManager.defaultManager

    val url: NSURL = requireNotNull(parent.URLByAppendingPathComponent(FOLDER, isDirectory = true))

    /**
     * Where [name] lives: inside the folder, after creating it, flagging it (every time, as a
     * restore or an older build may have cleared the flag) and moving [name] in from `Documents`
     * if an older build left it there. [companions] are files that must sit beside [name], like
     * SQLite's `-wal`, and follow it.
     *
     * If the move fails the old URL comes back, so the app keeps its data in a backed-up folder
     * rather than starting empty and stranding writes the server hasn't seen yet.
     */
    fun adopt(name: String, companions: List<String> = emptyList()): NSURL {
        manager.createDirectoryAtURL(url, withIntermediateDirectories = true, attributes = null, error = null)
        val flagged = url.setResourceValue(NSNumber(bool = true), forKey = NSURLIsExcludedFromBackupKey, error = null)
        if (!flagged) KLog.e("Couldn't exclude the local data folder from backups")
        val target = moveIn(name) ?: return requireNotNull(legacy.URLByAppendingPathComponent(name))
        companions.forEach(::moveIn)
        return target
    }

    /** The file's URL in the folder, or null if it is still in `Documents` because the move failed. */
    private fun moveIn(name: String): NSURL? {
        val target = requireNotNull(url.URLByAppendingPathComponent(name))
        val old = legacy.URLByAppendingPathComponent(name)
        if (old == null || !old.exists() || target.exists()) return target
        if (manager.moveItemAtURL(old, toURL = target, error = null)) return target
        KLog.e("Couldn't move $name out of Documents, so it stays there and in backups")
        return null
    }

    private fun NSURL.exists(): Boolean = path?.let { manager.fileExistsAtPath(it) } == true

    private companion object {
        const val FOLDER = "no_backup"

        fun directory(which: NSSearchPathDirectory): NSURL = requireNotNull(
            NSFileManager.defaultManager.URLForDirectory(
                directory = which,
                inDomain = NSUserDomainMask,
                appropriateForURL = null,
                create = true,
                error = null,
            ),
        )
    }
}
