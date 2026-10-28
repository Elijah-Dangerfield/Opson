package com.opson.libraries.networking.impl

import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.http.URLBuilder
import io.ktor.http.Url

/**
 * Thrown instead of sending a relative-path request that has no base URL to
 * resolve against.
 */
class UnresolvedRelativeRequestException internal constructor(path: String) : IllegalStateException(
    "No API base URL is configured, so `$path` has nothing to resolve against.\n" +
        "Bind a NetworkConfig with `replaces = [DefaultNetworkConfig::class]` and set baseUrl " +
        "(see SETUP.md, Networking).\n" +
        "If you meant to call http://localhost:80, set that as your baseUrl explicitly."
)

/**
 * Blocks requests that Ktor would otherwise quietly send to `http://localhost`.
 *
 * A blank base URL is a legitimate configuration, not an error: an app with no
 * backend of its own still signs in against Supabase and calls third-party APIs
 * by absolute URL, and demanding a base URL it has no use for would be wrong.
 *
 * What is not legitimate is a *relative* path with nothing to resolve it
 * against. Ktor's `URLBuilder` defaults to `http://localhost`, so the request
 * is sent, the connection is refused, and — because nothing answered — the
 * witnessed-reachability validator in [applyCommonConfig] correctly concludes
 * the round trip failed and reports the device unreachable. The app raises its
 * offline banner. So a project that has simply not set its base URL yet
 * presents as **offline**, on a device with four bars, and the first place
 * anyone looks is the connectivity code.
 *
 * That is the same failure shape as a blank Sentry DSN: a supported value that
 * silently means "off", where off is indistinguishable from working. Here it is
 * worse than silent, because it actively accuses something else.
 *
 * The check is narrow on purpose. It fires only on Ktor's exact untouched
 * default — http, host `localhost`, the protocol's default port — which is what
 * an unresolved relative path produces and is not what anyone configures for a
 * local dev server (those carry a port, or `10.0.2.2` on the Android emulator).
 * Someone who genuinely wants port 80 on localhost sets it as their base URL,
 * and the message says so.
 */
internal val RejectUnresolvedRelativeRequests = createClientPlugin("RejectUnresolvedRelativeRequests") {
    onRequest { request, _ ->
        if (request.url.isKtorDefaultOrigin()) {
            throw UnresolvedRelativeRequestException(request.url.pathSegments.joinToString("/"))
        }
    }
}

private fun URLBuilder.isKtorDefaultOrigin(): Boolean =
    host == DEFAULT_HOST && protocol.name == "http" && port == protocol.defaultPort

/** What `URLBuilder()` produces when nothing sets a host. */
private const val DEFAULT_HOST = "localhost"

/** Test seam: the same predicate against an already-built [Url]. */
internal fun Url.isKtorDefaultOrigin(): Boolean =
    host == DEFAULT_HOST && protocol.name == "http" && port == protocol.defaultPort
