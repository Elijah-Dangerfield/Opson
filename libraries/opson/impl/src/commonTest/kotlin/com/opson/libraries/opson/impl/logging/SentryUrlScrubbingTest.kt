package com.opson.libraries.opson.impl.logging

import com.opson.libraries.core.logging.LogLevel
import com.opson.libraries.opson.impl.SentryRuntimeConfig
import io.sentry.kotlin.multiplatform.SentryEvent
import io.sentry.kotlin.multiplatform.SentryOptions
import io.sentry.kotlin.multiplatform.protocol.Breadcrumb
import io.sentry.kotlin.multiplatform.protocol.Message
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

/**
 * A failed food search must not send the words searched to Sentry. They travel in the query
 * string of `GET /v1/foods/search`, and Ktor's exception messages and the iOS SDK's network
 * breadcrumbs both carry the whole URL.
 */
class SentryUrlScrubbingTest {

    private val searchUrl = "https://opson-server-prod.fly.dev/v1/foods/search?q=chicken%20soup&limit=20"

    @Test
    fun aSearchUrlLosesEverythingAfterTheQuestionMark() {
        assertEquals("https://opson-server-prod.fly.dev/v1/foods/search", scrubUrls(searchUrl))
    }

    /**
     * The query runs to the next space, so the punctuation Ktor puts straight after a URL goes
     * with it. Stopping at `:` or `,` instead would keep the rest of a query that contains one.
     */
    @Test
    fun ktorsOwnMessagesKeepTheirShape() {
        val timeout = "Request timeout has expired [url=$searchUrl, request_timeout=30000 ms]"
        assertEquals(
            "Request timeout has expired [url=https://opson-server-prod.fly.dev/v1/foods/search request_timeout=30000 ms]",
            scrubUrls(timeout),
        )
        val server = "Server error(GET $searchUrl: 500 Internal Server Error. Text: \"oops\")"
        assertEquals(
            "Server error(GET https://opson-server-prod.fly.dev/v1/foods/search 500 Internal Server Error. Text: \"oops\")",
            scrubUrls(server),
        )
    }

    @Test
    fun aBarePathAFragmentAndABarcodeAreScrubbedToo() {
        assertEquals("GET /v1/foods/search failed", scrubUrls("GET /v1/foods/search?q=oat+milk failed"))
        assertEquals("opson://auth/confirmed", scrubUrls("opson://auth/confirmed#access_token=abc"))
        assertEquals(
            "https://host/v1/foods/barcode/{code}",
            scrubUrls("https://host/v1/foods/barcode/0123456789012"),
        )
    }

    @Test
    fun ordinaryTextIsLeftAlone() {
        listOf("foods.search failed (timeout)", "Did it work?", "Config refresh failed; keeping the current config")
            .forEach { assertEquals(it, scrubUrls(it)) }
    }

    @Test
    fun beforeSend_leavesNothingAfterTheQuestionMarkInTheEvent() {
        val options = SentryOptions().also(config()::applyTo)
        val event = SentryEvent().apply {
            message = Message(message = "Search failed: $searchUrl", formatted = "Search failed: $searchUrl")
            breadcrumbs = mutableListOf(
                Breadcrumb.http(url = searchUrl, method = "GET", code = 500),
                Breadcrumb.error("foods.search failed: $searchUrl"),
            )
            setTag("last_url", searchUrl)
        }

        val sent = options.beforeSend!!(event)!!

        val everything = buildList {
            add(sent.message?.message)
            add(sent.message?.formatted)
            sent.breadcrumbs.forEach { crumb ->
                add(crumb.message)
                crumb.getData()?.values?.forEach { add(it.toString()) }
            }
            addAll(sent.tags.values)
        }.filterNotNull()
        assertTrue(everything.isNotEmpty())
        everything.forEach { text ->
            assertFalse("chicken" in text || "q=" in text || "?" in text, "the search reached the event: $text")
        }
        assertTrue(everything.any { it == "https://opson-server-prod.fly.dev/v1/foods/search" }, "the URL itself stays")
    }

    @Test
    fun beforeBreadcrumb_dropsTheIosQueryFields() {
        val options = SentryOptions().also(config()::applyTo)
        val crumb = Breadcrumb.http(url = "https://host/v1/foods/search", method = "GET").apply {
            setData("http.query", "q=chicken%20soup")
            setData("http.fragment", "x")
        }

        val kept = options.beforeBreadcrumb!!(crumb)!!.getData().orEmpty()

        assertEquals(setOf("url", "method"), kept.keys)
    }

    @Test
    fun theIosFailedRequestEvent_isOff() {
        val options = SentryOptions().also(config()::applyTo)
        assertFalse(options.enableCaptureFailedRequests)
    }

    @Test
    fun aThrowableIsReplacedOnlyWhenItsMessagesCarryAQuery() {
        val plain = IllegalStateException("no URL here")
        assertSame(plain, plain.scrubbedForSentry())

        val cause = IllegalStateException("Request timeout has expired [url=$searchUrl]")
        val scrubbed = RuntimeException("search failed", cause).scrubbedForSentry()

        val messages = generateSequence(scrubbed) { it.cause }.mapNotNull { it.message }.toList()
        assertEquals(
            listOf(
                "RuntimeException: search failed",
                "IllegalStateException: Request timeout has expired [url=https://opson-server-prod.fly.dev/v1/foods/search]",
            ),
            messages,
        )
    }

    private fun config() = SentryRuntimeConfig(
        dsn = "https://key@o1.ingest.sentry.io/1",
        environment = "store-ios-release",
        release = "opson@0.2.0+2",
        sendDefaultPii = false,
        attachStacktrace = true,
        tracesSampleRate = 0.15,
        platformTag = "ios",
        buildTypeTag = "release",
        logPolicy = SentryRuntimeConfig.LogPolicy(minBreadcrumbLevel = LogLevel.Info, minEventLevel = LogLevel.Error),
        enableAutoSessionTracking = true,
    )
}
