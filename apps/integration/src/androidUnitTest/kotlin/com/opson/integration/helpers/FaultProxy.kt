package com.opson.integration.helpers

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.netty.NettyApplicationEngine
import io.ktor.server.request.httpMethod
import io.ktor.server.request.path
import io.ktor.server.request.receiveChannel
import io.ktor.server.request.uri
import io.ktor.server.response.header
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.io.readByteArray
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Sits between a client and the real server so a test can make the network
 * misbehave the way a phone's network does, without touching either side:
 *
 *  - [goDown] / [comeBack]: the connection itself fails. New connections are
 *    closed the moment they open and open ones are cut, so the client sees an
 *    I/O failure with no HTTP status, exactly like a dead network.
 *  - [failNext]: the next requests to a path answer with a [Fault] instead of
 *    (or after) reaching the server: a 5xx, a hang past the client's timeout, a
 *    200 whose body is not the contract, or a 502 *after* the server applied the
 *    request, which is what a response lost on the way back looks like.
 *
 * Two layers: a raw TCP gate (for [goDown]) in front of an HTTP forwarder (for
 * [failNext]). Point a client's base URL at [baseUrl].
 */
class FaultProxy(private val upstream: String) : AutoCloseable {

    sealed interface Fault {
        /** Answer [status], with [headers], without forwarding. */
        data class Status(
            val status: HttpStatusCode,
            val body: String = """{"code":"injected","message":"fault"}""",
            val headers: Map<String, String> = emptyMap(),
        ) : Fault

        /** Never answer; the client's request timeout ends it. Not forwarded. */
        data object Hang : Fault

        /** 200 with a body that is not the endpoint's JSON. Not forwarded. */
        data object Malformed : Fault

        /** Forward, let the server apply it, then answer 502 as if the response was lost. */
        data object LoseResponse : Fault
    }

    private class Rule(val pathPrefix: String, val fault: Fault, val remaining: AtomicInteger)

    private val rules = CopyOnWriteArrayList<Rule>()

    /** Paths of every request that reached the HTTP layer, in order, faulted or not. */
    val requests: MutableList<String> = CopyOnWriteArrayList()

    private val forwarder = HttpClient(OkHttp) { expectSuccess = false }

    private val http: EmbeddedServer<NettyApplicationEngine, NettyApplicationEngine.Configuration> =
        embeddedServer(Netty, port = 0, host = "127.0.0.1") {
            routing {
                route("{...}") {
                    handle { relay(call) }
                }
            }
        }.start(wait = false)

    private val httpPort: Int = runBlocking { http.engine.resolvedConnectors().first().port }

    private val gate = TcpGate(targetPort = httpPort)

    val baseUrl: String get() = "http://127.0.0.1:${gate.port}"

    /** The next [times] requests whose path starts with [pathPrefix] get [fault]. */
    fun failNext(pathPrefix: String, fault: Fault, times: Int = 1) {
        rules += Rule(pathPrefix, fault, AtomicInteger(times))
    }

    /** Every request to [pathPrefix] gets [fault] until [heal]. */
    fun failAll(pathPrefix: String, fault: Fault) = failNext(pathPrefix, fault, times = Int.MAX_VALUE)

    /** Drop every injected fault. */
    fun heal() = rules.clear()

    fun goDown() = gate.goDown()

    fun comeBack() = gate.comeBack()

    override fun close() {
        gate.close()
        http.stop(0, 0)
        forwarder.close()
    }

    private fun takeFault(path: String): Fault? {
        for (rule in rules) {
            if (!path.startsWith(rule.pathPrefix)) continue
            if (rule.remaining.getAndDecrement() > 0) return rule.fault
        }
        return null
    }

    private suspend fun relay(call: ApplicationCall) {
        val path = call.request.path()
        requests += path
        val body = call.receiveChannel().readRemaining().readByteArray()
        when (val fault = takeFault(path)) {
            is Fault.Status -> {
                fault.headers.forEach { (name, value) -> call.response.header(name, value) }
                call.respondBytes(fault.body.toByteArray(), ContentType.Application.Json, fault.status)
            }
            Fault.Hang -> awaitCancellation()
            Fault.Malformed -> call.respondBytes("""{"applied": "this is not a list", """.toByteArray(), ContentType.Application.Json, HttpStatusCode.OK)
            Fault.LoseResponse -> {
                forward(call, body)
                call.respondBytes("""{"code":"bad_gateway"}""".toByteArray(), ContentType.Application.Json, HttpStatusCode.BadGateway)
            }
            null -> {
                val response = forward(call, body)
                call.respondBytes(
                    response.readRawBytes(),
                    response.headers[HttpHeaders.ContentType]?.let(ContentType::parse),
                    response.status,
                )
            }
        }
    }

    private suspend fun forward(call: ApplicationCall, body: ByteArray): HttpResponse =
        forwarder.request("$upstream${call.request.uri}") {
            method = HttpMethod.parse(call.request.httpMethod.value)
            call.request.headers.forEach { name, values ->
                if (name !in HOP_HEADERS) values.forEach { headers.append(name, it) }
            }
            if (body.isNotEmpty()) setBody(body)
        }

    private companion object {
        val HOP_HEADERS = setOf(HttpHeaders.Host, HttpHeaders.ContentLength, HttpHeaders.TransferEncoding, HttpHeaders.Connection)
    }
}

/**
 * Pipes TCP connections to [targetPort] until [goDown], after which it closes
 * every open connection and refuses new ones by accepting and closing them.
 */
private class TcpGate(private val targetPort: Int) : AutoCloseable {

    private val server = ServerSocket(0, 50, InetAddress.getLoopbackAddress())
    private val open = CopyOnWriteArrayList<Socket>()

    @Volatile
    private var down = false

    @Volatile
    private var closed = false

    val port: Int = server.localPort

    init {
        thread(isDaemon = true, name = "fault-proxy-accept") {
            while (!closed) {
                val client = try {
                    server.accept()
                } catch (_: SocketException) {
                    break
                }
                if (down) {
                    client.close()
                    continue
                }
                val target = try {
                    Socket(InetAddress.getLoopbackAddress(), targetPort)
                } catch (_: SocketException) {
                    client.close()
                    continue
                }
                open += client
                open += target
                pipe(client.getInputStream(), target.getOutputStream(), client, target)
                pipe(target.getInputStream(), client.getOutputStream(), client, target)
            }
        }
    }

    fun goDown() {
        down = true
        open.forEach { runCatching { it.close() } }
        open.clear()
    }

    fun comeBack() {
        down = false
    }

    override fun close() {
        closed = true
        goDown()
        server.close()
    }

    private fun pipe(from: InputStream, to: OutputStream, vararg ends: Socket) {
        thread(isDaemon = true, name = "fault-proxy-pipe") {
            try {
                from.copyTo(to)
            } catch (_: Exception) {
                // A cut connection; the ends are closed below.
            } finally {
                ends.forEach { runCatching { it.close() } }
                open.removeAll(ends.toSet())
            }
        }
    }
}
