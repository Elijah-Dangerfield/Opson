package com.opson.libraries.progressphotos.impl

import android.content.Context
import com.opson.libraries.progressphotos.ProgressPhotoFileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.io.File

/**
 * A folder in `noBackupFilesDir`: private to the app (no other app, and not the
 * photo gallery, can read it) and left out of Auto Backup and device-to-device
 * transfer by the platform itself, so no backup rules are needed.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidProgressPhotoFileStore @Inject constructor(
    private val context: Context,
) : ProgressPhotoFileStore {

    private val folder: File get() = File(context.noBackupFilesDir, FOLDER)

    override suspend fun read(name: String): ByteArray? = withContext(Dispatchers.IO) {
        File(folder, name).takeIf { it.isFile }?.readBytes()
    }

    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        folder.mkdirs()
        val target = File(folder, name)
        val partial = File(folder, "$name.partial")
        partial.writeBytes(bytes)
        if (!partial.renameTo(target)) {
            partial.delete()
            error("Couldn't move a progress photo file into place")
        }
    }

    override suspend fun delete(name: String) {
        withContext(Dispatchers.IO) { File(folder, name).delete() }
    }

    override suspend fun deleteAll() {
        withContext(Dispatchers.IO) { folder.deleteRecursively() }
    }

    private companion object {
        const val FOLDER = "progress_photos"
    }
}
