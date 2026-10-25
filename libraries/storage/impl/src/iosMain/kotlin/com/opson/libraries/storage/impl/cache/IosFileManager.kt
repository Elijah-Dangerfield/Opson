package com.opson.libraries.storage.impl.cache

import com.opson.libraries.core.logging.KLog
import com.opson.libraries.core.Catching
import com.opson.libraries.core.logOnFailure
import com.opson.libraries.storage.FileManager
import kotlinx.cinterop.ExperimentalForeignApi
import me.tatarka.inject.annotations.Inject
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosFileManager @Inject constructor() : FileManager {

    override fun createFile(name: String): Path {
        val directories = NSSearchPathForDirectoriesInDomains(
            NSDocumentDirectory,
            NSUserDomainMask,
            true
        )
        val documentsDirectory = directories.firstOrNull() as? String
            ?: error("Unable to determine documents directory for DataStore")
        return ("$documentsDirectory/$name").toPath()
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun deleteFile(name: String) {
        Catching {
            val fileManager = NSFileManager.defaultManager
            val directories = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory,
                NSUserDomainMask,
                true
            )

            val documentsDirectory = directories.firstOrNull() as? String
                ?: error("Unable to determine documents directory for DataStore")

            fileManager.removeItemAtPath("$documentsDirectory/$name", error = null)
        }
            .logOnFailure("Failed to delete file $name")
    }

    @OptIn(ExperimentalForeignApi::class)
    override fun deleteAll() {
        Catching {
            val fileManager = NSFileManager.defaultManager
            val directories = NSSearchPathForDirectoriesInDomains(
                NSDocumentDirectory,
                NSUserDomainMask,
                true
            )
            val documentsDirectory = directories.firstOrNull() as? String
                ?: error("Unable to determine documents directory")

            val contents = fileManager.contentsOfDirectoryAtPath(documentsDirectory, error = null) as? List<*>

            contents?.forEach { filename ->
                val filePath = "$documentsDirectory/$filename"
                fileManager.removeItemAtPath(filePath, error = null)
            }
        }
            .onSuccess { KLog.i("Deleted all files") }
            .logOnFailure()
    }
}