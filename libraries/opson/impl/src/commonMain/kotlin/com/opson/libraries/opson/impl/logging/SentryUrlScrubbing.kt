package com.opson.libraries.opson.impl.logging

import io.sentry.kotlin.multiplatform.SentryEvent
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb

/*
 * What someone types reaches the server in a URL: a food search is
 * `GET /v1/foods/search?q=<the words>`, and a typed barcode is a path segment. Ktor
 * puts the request URL in its exception messages ("Request timeout has expired
 * [url=…]", "Server error(GET …: 500 …)"), and the iOS SDK records the URL of
 * every request as a breadcrumb, so without this a failed search sent the search
 * words to Sentry and the privacy policy had to say so.
 *
 * Everything bound for Sentry goes through [scrubUrls]: breadcrumbs (ours and the
 * SDK's), event messages, tags and extras, exception messages, and the local log
 * buffer that rides on a feedback report.
 */

/**
 * A URL, absolute or a bare path, followed by a query or a fragment. Group 1 is the
 * URL without them. The query ends at whitespace, a quote or a bracket, which is
 * where Ktor's messages put the next word.
 */
private val URL_WITH_QUERY = Regex("""((?:[A-Za-z][A-Za-z0-9+.\-]*://|/)[^\s?#"'<>\[\]()]*)[?#][^\s"'<>\[\]()]*""")

/** The barcode in `/v1/foods/barcode/{code}`: someone may have typed it, and it is what they eat. */
private val BARCODE_SEGMENT = Regex("""(/foods/barcode/)[^/\s?#"'<>\[\]()]+""")

/** Breadcrumb data the iOS network tracker files the query and fragment under, apart from the URL. */
private val QUERY_DATA_KEYS = setOf("http.query", "http.fragment")

internal const val REDACTED_BARCODE = "{code}"

/** [text] with every URL's query and fragment removed and any barcode path segment replaced. */
internal fun scrubUrls(text: String): String =
    text.replace(URL_WITH_QUERY) { it.groupValues[1] }
        .replace(BARCODE_SEGMENT) { it.groupValues[1] + REDACTED_BARCODE }

internal fun Breadcrumb.scrubbed(): Breadcrumb = apply {
    message = message?.let(::scrubUrls)
    val data = getData() ?: return@apply
    val clean: MutableMap<String, Any> = data
        .filterKeys { it !in QUERY_DATA_KEYS }
        .mapValues { (_, value) -> if (value is String) scrubUrls(value) else value }
        .toMutableMap()
    setData(clean)
}

/**
 * The parts of an event the KMP SDK writes back to the native event after
 * `beforeSend`: the message, the breadcrumbs and the tags. Exceptions are not among
 * them, so [SentryLogTree] scrubs a throwable before it is captured, with
 * [scrubbedForSentry].
 */
internal fun SentryEvent.scrubbed(): SentryEvent = apply {
    message?.let {
        it.message = it.message?.let(::scrubUrls)
        it.formatted = it.formatted?.let(::scrubUrls)
        it.params = it.params?.map(::scrubUrls)
    }
    breadcrumbs = breadcrumbs.map { it.scrubbed() }.toMutableList()
    tags = tags.mapValues { (_, value) -> scrubUrls(value) }.toMutableMap()
}

/**
 * [this], or a stand-in carrying the same messages with their URLs scrubbed when any
 * message in the cause chain had something to scrub.
 *
 * Kotlin cannot rewrite a throwable's message, so the stand-in is a new throwable:
 * it names the original class in its message and keeps the chain of causes, scrubbed
 * the same way, but its stack trace is where it was logged. That costs the network
 * stack frames, which are Ktor's internals, and nothing that would find the bug.
 */
internal fun Throwable.scrubbedForSentry(): Throwable {
    if (generateSequence(this) { it.cause }.none { it.message.needsScrubbing() }) return this
    return UrlScrubbedException(this)
}

private fun String?.needsScrubbing(): Boolean = this != null && scrubUrls(this) != this

internal class UrlScrubbedException(original: Throwable) : RuntimeException(
    "${original::class.simpleName ?: "Throwable"}: ${original.message?.let(::scrubUrls)}",
    original.cause?.let { UrlScrubbedException(it) },
)
