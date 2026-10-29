package com.opson.libraries.foods

/**
 * What a contribution request (a food submission, a report) carries about the device, so the
 * server can keep a banned person from contributing again after a reinstall. Only those requests
 * carry it: logging food never does, and is never blocked by it.
 *
 * Two headers, each only when there is something to send:
 *
 *  - [HEADER_DEVICE_KEY]: a random install key. On iOS a Keychain item that usually survives the
 *    app being deleted (Apple doesn't promise it); on Android the install id the app already
 *    sends. The server keeps only a keyed hash of it.
 *  - [HEADER_INTEGRITY_TOKEN]: a DeviceCheck token (iOS) or a Play Integrity token bound to the
 *    request (Android), once remote config turns them on (`integrity.deviceCheck`,
 *    `integrity.playCloudProjectNumber`). The server reads and sets its bit with Apple or Google
 *    and drops the token.
 */
interface ContributionAttestation {
    /** Headers for one request, bound to [requestId] where the platform can. Never throws; empty when nothing applies. */
    suspend fun headers(requestId: String): Map<String, String>

    /** Sends nothing: tests, and any platform without an implementation. */
    object None : ContributionAttestation {
        override suspend fun headers(requestId: String): Map<String, String> = emptyMap()
    }

    companion object {
        const val HEADER_DEVICE_KEY = "X-Device-Key"
        const val HEADER_INTEGRITY_TOKEN = "X-Integrity-Token"
    }
}
