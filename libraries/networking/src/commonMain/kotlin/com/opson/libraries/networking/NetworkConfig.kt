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

    /** Per-request timeout in milliseconds. Default 30s. */
    val requestTimeoutMillis: Long get() = 30_000L
}
