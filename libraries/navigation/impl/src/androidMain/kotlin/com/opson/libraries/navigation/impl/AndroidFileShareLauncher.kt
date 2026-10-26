package com.opson.libraries.navigation.impl

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.opson.libraries.core.Catching
import com.opson.libraries.navigation.FileShareLauncher
import com.opson.libraries.navigation.shareFileName
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn
import java.io.File

/**
 * Writes the file to `cache/shared/` and opens the system chooser over `ACTION_SEND` with a
 * `content://` URI from [ShareFileProvider]. The receiving app gets a read grant for that one URI
 * and nothing else; no storage permission is involved.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class)
class AndroidFileShareLauncher @Inject constructor(
    private val context: Context,
) : FileShareLauncher {

    override fun share(name: String, mimeType: String, bytes: ByteArray): Catching<Unit> = Catching {
        val fileName = shareFileName(name)
        val directory = File(context.cacheDir, ShareFileProvider.DIRECTORY).apply { mkdirs() }
        val file = File(directory, fileName).apply { writeBytes(bytes) }
        val uri = FileProvider.getUriForFile(context, ShareFileProvider.authority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            // The grant only travels through the chooser to the picked app when the URI is also
            // in the clip data; EXTRA_STREAM alone is not enough on current Android.
            clipData = ClipData.newRawUri(fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(chooser)
    }
}

/**
 * The app's own `FileProvider` for shared exports. A subclass rather than `FileProvider` itself so
 * it cannot collide with a dependency that declares one under the same class name in the merged
 * manifest. Paths are in `res/xml/file_share_paths.xml`.
 */
class ShareFileProvider : FileProvider() {
    internal companion object {
        /** Under the cache dir, matching the `cache-path` in `file_share_paths.xml`. */
        const val DIRECTORY = "shared"

        /** Matches `${applicationId}.fileshare` in this module's manifest. */
        fun authority(context: Context): String = "${context.packageName}.fileshare"
    }
}
