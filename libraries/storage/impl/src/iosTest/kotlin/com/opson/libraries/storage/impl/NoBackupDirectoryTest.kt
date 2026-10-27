package com.opson.libraries.storage.impl

import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSNumber
import platform.Foundation.NSString
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLIsExcludedFromBackupKey
import platform.Foundation.NSUTF8StringEncoding
import platform.Foundation.NSUUID
import platform.Foundation.create
import platform.Foundation.dataUsingEncoding
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class NoBackupDirectoryTest {

    private val manager = NSFileManager.defaultManager
    private val root = NSURL.fileURLWithPath(NSTemporaryDirectory() + "no-backup-" + NSUUID().UUIDString, isDirectory = true)
    private val documents = folder("Documents")
    private val support = folder("Application Support")
    private val directory = NoBackupDirectory(legacy = documents, parent = support)

    @AfterTest
    fun cleanUp() {
        manager.removeItemAtURL(root, error = null)
    }

    @Test
    fun theFolderIsExcludedFromBackups() {
        directory.adopt("app_data.json")

        assertTrue(isExcludedFromBackup(directory.url))
    }

    @Test
    fun theFlagIsPutBackOnAFolderThatLostIt() {
        directory.adopt("app_data.json")
        directory.url.setResourceValue(NSNumber(bool = false), forKey = NSURLIsExcludedFromBackupKey, error = null)
        assertFalse(isExcludedFromBackup(directory.url))

        directory.adopt("app_data.json")

        assertTrue(isExcludedFromBackup(directory.url))
    }

    @Test
    fun aFileAnOlderBuildLeftInDocumentsMovesIn() {
        write(documents, "app_data.json", "old settings")

        val url = directory.adopt("app_data.json")

        assertEquals(child(directory.url, "app_data.json").path, url.path)
        assertEquals("old settings", read(url))
        assertFalse(exists(child(documents, "app_data.json")))
    }

    @Test
    fun theDatabaseBringsItsWriteAheadLogWithIt() {
        write(documents, "opson.db", "pages")
        write(documents, "opson.db-wal", "newest entries")

        directory.adopt("opson.db", companions = listOf("opson.db-wal", "opson.db-shm"))

        assertEquals("pages", read(child(directory.url, "opson.db")))
        assertEquals("newest entries", read(child(directory.url, "opson.db-wal")))
        assertFalse(exists(child(documents, "opson.db-wal")))
    }

    @Test
    fun aFileAlreadyInTheFolderIsNotOverwrittenByAStaleCopy() {
        write(documents, "app_data.json", "stale")
        directory.adopt("app_data.json")
        write(documents, "app_data.json", "restored by an old backup")

        val url = directory.adopt("app_data.json")

        assertEquals("stale", read(url))
    }

    private fun folder(name: String): NSURL {
        val url = child(root, name)
        manager.createDirectoryAtURL(url, withIntermediateDirectories = true, attributes = null, error = null)
        return url
    }

    private fun child(parent: NSURL, name: String): NSURL = requireNotNull(parent.URLByAppendingPathComponent(name))

    private fun write(parent: NSURL, name: String, text: String) {
        manager.createFileAtPath(requireNotNull(child(parent, name).path), contents = text.toNSData(), attributes = null)
    }

    private fun read(url: NSURL): String? =
        manager.contentsAtPath(requireNotNull(url.path))?.let { NSString.create(data = it, encoding = NSUTF8StringEncoding)?.toString() }

    private fun exists(url: NSURL): Boolean = manager.fileExistsAtPath(requireNotNull(url.path))

    // A fresh NSURL, because NSURL caches resource values it has already read.
    private fun isExcludedFromBackup(url: NSURL): Boolean =
        (NSURL.fileURLWithPath(requireNotNull(url.path)).resourceValuesForKeys(listOf(NSURLIsExcludedFromBackupKey), error = null)
            ?.get(NSURLIsExcludedFromBackupKey) as? NSNumber)?.boolValue == true

    private fun String.toNSData(): NSData =
        requireNotNull(NSString.create(string = this).dataUsingEncoding(NSUTF8StringEncoding))
}
