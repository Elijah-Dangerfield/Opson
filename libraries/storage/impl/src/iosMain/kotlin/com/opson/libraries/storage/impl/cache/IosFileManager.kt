package com.opson.libraries.storage.impl.cache

import com.opson.libraries.storage.FileManager
import com.opson.libraries.storage.impl.NoBackupDirectory
import me.tatarka.inject.annotations.Inject
import okio.Path
import okio.Path.Companion.toPath
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class IosFileManager @Inject constructor() : FileManager {

    private val directory by lazy { NoBackupDirectory() }

    override fun createFile(name: String): Path =
        requireNotNull(directory.adopt(name).path) { "No path for $name" }.toPath()
}
