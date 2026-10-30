package com.opson.admin

import kotlinx.browser.document
import org.w3c.dom.HTMLAnchorElement
import org.w3c.dom.url.URL
import org.w3c.files.Blob
import org.w3c.files.BlobPropertyBag

/** Hands [text] to the browser as a file download. Nothing leaves the page but the file. */
internal fun downloadText(fileName: String, text: String, mimeType: String) {
    val blob = Blob(arrayOf(text), BlobPropertyBag(type = mimeType))
    val url = URL.createObjectURL(blob)
    val link = document.createElement("a") as HTMLAnchorElement
    link.href = url
    link.download = fileName
    link.click()
    URL.revokeObjectURL(url)
}
