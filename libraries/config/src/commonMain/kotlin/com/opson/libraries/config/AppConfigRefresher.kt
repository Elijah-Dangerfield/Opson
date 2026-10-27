package com.opson.libraries.config

/**
 * Fetches the remote config now, past the foreground throttle. For a screen that is waiting on
 * the server to change its mind, the maintenance screen's "Try again" and the guard's own
 * re-check on foreground. Everything else should leave the timing to the repository.
 *
 * A failure keeps the current config, as every refresh does; the caller learns the outcome from
 * the config stream, not from here.
 */
fun interface AppConfigRefresher {
    suspend fun refresh()
}
