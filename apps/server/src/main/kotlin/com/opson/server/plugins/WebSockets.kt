package com.opson.server.plugins

import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.websocket.WebSocketDeflateExtension
import kotlin.time.Duration.Companion.seconds

/**
 * Installs the Ktor WebSocket plugin with production-proven defaults.
 * Lives in its own file so changing the heartbeat strategy is a
 * single-file edit + the routes don't need to know.
 *
 * Ping cadence (15s) + timeout (30s) means a peer that's gone silent
 * for half a minute gets its session torn down, which fires your route's
 * onClose handler. Any "they might reconnect" grace period belongs in
 * the route's own state, not here.
 *
 * Permessage-deflate compresses anything > 4KB — most event payloads fit
 * under that; the option's here for larger state snapshots.
 *
 * Wire-envelope lesson (learned in production): model socket events as a
 * sealed @Serializable hierarchy with explicit @SerialName discriminators,
 * and decode with `ignoreUnknownKeys = true` on the client. That pairing is
 * what lets old clients survive new event types (unknown discriminators are
 * skippable) and new clients survive old servers (missing fields default).
 * Ad-hoc per-message JSON shapes cannot evolve safely.
 */
fun Application.installWebSockets() {
    install(WebSockets) {
        pingPeriod = 15.seconds
        timeout = 30.seconds
        maxFrameSize = Long.MAX_VALUE
        masking = false
        extensions {
            install(WebSocketDeflateExtension)
        }
    }
}
