package com.opson.libraries.networking

/**
 * Per-project network configuration. Bind a project-specific implementation in
 * your app's `impl` module to set the API base URL and default request timeout.
 *
 * The shipped default, `DefaultNetworkConfig`, leaves [baseUrl] blank.
 */
interface NetworkConfig {
    /**
     * Base URL applied to every request via Ktor's `defaultRequest` plugin.
     *
     * Blank is valid and means "this app has no API of its own" — it still
     * reaches Supabase and third parties by absolute URL. What it does not mean
     * is "send relative paths anyway": those are rejected before they leave the
     * device, because Ktor would otherwise resolve them against
     * `http://localhost` and the refused connection would read as the device
     * being offline. See `RejectUnresolvedRelativeRequests`.
     */
    val baseUrl: String

    /**
     * Whole-request budget, and the longest the socket may sit silent. A call
     * that waits on slow server work (a photo estimate) raises both per request.
     */
    val requestTimeoutMillis: Long get() = 30_000L

    /**
     * How long to wait for a connection to open. Shorter than the request
     * budget because a connection that has not opened in 10 s is not going to:
     * the network is dead even if the OS still reports a path, and the caller
     * should hear so instead of sitting out the full budget. Only OkHttp
     * (Android) applies it; NSURLSession has no separate connect timeout.
     */
    val connectTimeoutMillis: Long get() = 10_000L
}
