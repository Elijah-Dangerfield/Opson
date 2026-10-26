package com.opson.libraries.navigation

import com.opson.libraries.core.Catching

/**
 * Hands a file to the platform share sheet: Android's chooser over `ACTION_SEND`, iOS's
 * `UIActivityViewController`. Feature code calls [Router.shareFile], not this.
 *
 * [name] is the file name the receiving app sees (`opson-export.csv`), [mimeType] what it is
 * (`text/csv`). The bytes are written to app-private cache first; nothing lands anywhere the user
 * did not pick in the sheet.
 */
fun interface FileShareLauncher {
    fun share(name: String, mimeType: String, bytes: ByteArray): Catching<Unit>
}

/**
 * The file name to write: the last path segment of [name], so a name can never point outside the
 * share directory. Blank or dot-only names are refused rather than guessed at.
 */
fun shareFileName(name: String): String {
    val last = name.substringAfterLast('/').substringAfterLast('\\').trim()
    require(last.isNotEmpty() && last.any { it != '.' }) { "Not a file name: \"$name\"" }
    return last
}
