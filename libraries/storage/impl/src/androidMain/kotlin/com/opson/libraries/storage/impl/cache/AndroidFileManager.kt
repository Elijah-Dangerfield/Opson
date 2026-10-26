package com.opson.libraries.storage.impl.cache

import android.content.Context
import com.opson.libraries.storage.FileManager
import me.tatarka.inject.annotations.Inject
import okio.Path
import okio.Path.Companion.toPath
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidFileManager @Inject constructor(
    private val context: Context
) : FileManager {
    override fun createFile(name: String): Path {
        return context.filesDir.resolve(name).absolutePath.toPath()
    }
}
