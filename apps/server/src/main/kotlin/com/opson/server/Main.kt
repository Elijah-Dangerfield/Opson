package com.opson.server

import com.opson.server.config.ServerConfig
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/**
 * Entry point. Parses [ServerConfig] from the environment (OS vars first, then
 * apps/server/.env) once, then hands it to [module].
 */
fun main() {
    val config = ServerConfig.fromEnv()
    embeddedServer(
        factory = Netty,
        host = config.http.host,
        port = config.http.port,
        module = { module(config) },
    ).start(wait = true)
}
