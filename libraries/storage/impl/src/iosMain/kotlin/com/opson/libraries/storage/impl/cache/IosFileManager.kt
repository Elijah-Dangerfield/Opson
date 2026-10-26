package com.opson.libraries.storage.impl.cache

import com.opson.libraries.storage.FileManager
import me.tatarka.inject.annotations.Inject
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSDocumentDirectory
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
}
