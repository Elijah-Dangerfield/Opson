package com.opson.server.plugins

import com.opson.server.config.ObservabilityConfig
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.opentelemetry.sdk.testing.exporter.InMemoryLogRecordExporter
import io.opentelemetry.sdk.testing.exporter.InMemoryMetricReader
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter
import io.ktor.client.request.get
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Before
import org.junit.BeforeClass
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Pins the OTel wiring contract: the SDK builds for both exporter modes,
 * [withSpan] emits properly-attributed spans, and the correlation baggage
 * lands on every span in a trace tree.
 *
 * `GlobalOpenTelemetry.set(...)` is set-once per classloader; this is
 * the only test class in the suite that touches the global slot, so a
 * single companion-init install is safe. Other tests run alongside
 * with an installed SDK that pipes their spans into the same in-memory
 * exporter — `@Before` clears it so each test asserts on its own slice.
 */
class TelemetryTest {

    @Before
    fun resetExporter() {
        exporter.reset()
        logExporter.reset()
    }

    @Test
    fun buildOpenTelemetrySdk_withNoEndpoint_returnsUsableSdk() {
        val sdk = buildOpenTelemetrySdk(
            ObservabilityConfig(
                otlpEndpoint = null,
                otlpHeaders = null,
                serviceName = "test-server",
                environment = "test",
                release = null,
            ),
        )

        sdk.getTracer("smoke").spanBuilder("noop").startSpan().end()
        sdk.close()
    }

    @Test
    fun buildOpenTelemetrySdk_resourceCarriesServiceAndEnvAttrs() {
        val exporter = InMemorySpanExporter.create()
        val sdk = buildOpenTelemetrySdk(
            ObservabilityConfig(
                otlpEndpoint = null,
                otlpHeaders = null,
                serviceName = "opson-server",
                environment = "test",
                release = "abc123",
            ),
            spanExporter = exporter,
        )
        sdk.getTracer("attrs").spanBuilder("noop").startSpan().end()
        sdk.sdkTracerProvider.forceFlush().join(1, TimeUnit.SECONDS)

        val span = exporter.finishedSpanItems.single()
        val resourceAttrs = span.resource.attributes
        assertEquals("opson-server", resourceAttrs.get(AttributeKey.stringKey("service.name")))
        assertEquals("test", resourceAttrs.get(AttributeKey.stringKey("deployment.environment")))
        assertEquals("abc123", resourceAttrs.get(AttributeKey.stringKey("service.version")))
        sdk.close()
    }

    @Test
    fun withSpan_emitsSpanWithAttributes() = runTest {
        withSpan(
            name = "test_span",
            configure = {
                setAttribute(SpanAttrs.UserId, "user-1")
            },
        ) {
            // no-op body
        }
        flushSpans()

        val span = exporter.finishedSpanItems.single { it.name == "test_span" }
        assertEquals("user-1", span.attributes.get(SpanAttrs.UserId))
    }

    @Test
    fun withSpan_propagatesContextToChild() = runTest {
        withSpan(name = "outer") {
            withSpan(name = "inner") { }
        }
        flushSpans()

        val outer = exporter.finishedSpanItems.single { it.name == "outer" }
        val inner = exporter.finishedSpanItems.single { it.name == "inner" }
        assertEquals(
            outer.spanContext.spanId,
            inner.parentSpanContext.spanId,
            "inner span should be parented to outer via the coroutine context element",
        )
    }

    @Test
    fun baggageCorrelationIds_areCopiedOntoSpans() = runTest {
        // Mirrors what installHttpServerTracing does per request: put the
        // correlation ids in OTel Baggage, then create spans inside that
        // context. The BaggageAttributeSpanProcessor must copy them onto the
        // span — and onto a nested child span — so `{ .session_id = X }` in
        // Tempo matches the whole trace tree, not just the HTTP root.
        val context = Baggage.current().toBuilder()
            .put("session_id", "sess-xyz")
            .put("install_id", "inst-abc")
            .build()
            .storeInContext(Context.current())

        withContext(context.asContextElement()) {
            withSpan("baggage_outer") {
                withSpan("baggage_inner") { }
            }
        }
        flushSpans()

        listOf("baggage_outer", "baggage_inner").forEach { name ->
            val span = exporter.finishedSpanItems.single { it.name == name }
            assertEquals("sess-xyz", span.attributes.get(AttributeKey.stringKey("session_id")), "$name session_id")
            assertEquals("inst-abc", span.attributes.get(AttributeKey.stringKey("install_id")), "$name install_id")
        }
    }

    @Test
    fun spansWithoutBaggage_haveNoCorrelationAttributes() = runTest {
        withSpan("no_baggage") { }
        flushSpans()

        val span = exporter.finishedSpanItems.single { it.name == "no_baggage" }
        assertEquals(null, span.attributes.get(AttributeKey.stringKey("session_id")))
    }

    @Test
    fun logger_inSpanContext_attachesTraceIdToLogRecord() = runTest {
        withSpan("log_test") {
            LoggerFactory.getLogger(TelemetryTest::class.java)
                .info("hello from inside a span")
        }
        flushLogs()
        flushSpans()

        val span = exporter.finishedSpanItems.single { it.name == "log_test" }
        val record = logExporter.finishedLogRecordItems
            .firstOrNull { it.bodyValue?.asString() == "hello from inside a span" }
        assertNotNull(record, "expected the log record to flow through the OTel appender")
        assertEquals(
            span.spanContext.traceId,
            record.spanContext.traceId,
            "log record should carry the active span's trace_id for trace↔log correlation",
        )
    }

    @Test
    fun httpSpans_neverCarryWhatSomeoneSearchedScannedOrOpened() = testApplication {
        application {
            installHttpServerTracing(sdk)
            routing {
                get("/v1/foods/search") { call.respondText("ok") }
                get("/v1/foods/barcode/{code}") { call.respondText("ok") }
                get("/v1/foods/{id}") { call.respondText("ok") }
            }
        }

        client.get("/v1/foods/search?q=chocolate+cake&limit=5")
        client.get("/v1/foods/barcode/0012345678905")
        client.get("/v1/foods/6f1c2d3e-4b5a-4c6d-8e7f-9a0b1c2d3e4f")
        flushSpans()

        val spans = exporter.finishedSpanItems.filter { it.kind == io.opentelemetry.api.trace.SpanKind.SERVER }
        assertEquals(3, spans.size)
        for (span in spans) {
            span.attributes.forEach { key, value ->
                val text = value.toString()
                assertFalse("chocolate" in text || "12345678905" in text || "6f1c2d3e" in text, "${key.key} = $value")
            }
        }
        assertEquals(
            setOf("/v1/foods/search", "/v1/foods/barcode/{code}", "/v1/foods/{id}"),
            spans.map { it.attributes.get(AttributeKey.stringKey("url.path")) }.toSet(),
        )
    }

    @Test
    fun redactedPath_masksIdsAndCodesAndKeepsTheShape() {
        assertEquals("/v1/foods/{id}", redactedPath("/v1/foods/6F1C2D3E-4B5A-4C6D-8E7F-9A0B1C2D3E4F"))
        assertEquals("/v1/foods/barcode/{code}", redactedPath("/v1/foods/barcode/0012345678905"))
        assertEquals("/v1/foods/search", redactedPath("/v1/foods/search"))
        assertEquals("/v1/export.csv", redactedPath("/v1/export.csv"))
    }

    @Test
    fun parseOtlpHeaders_percentDecodesEachValue() {
        // Grafana Cloud hands the auth header as `Basic%20<base64>`. The
        // SDK consumer needs the literal-space form, so the parser must
        // URL-decode values. Skipping that decode means every request
        // ships with header `Basic%20…` and the gateway 401s.
        val parsed = parseOtlpHeaders("Authorization=Basic%20abc%3D%3D,X-Other=hello%20world")

        assertEquals(
            listOf(
                "Authorization" to "Basic abc==",
                "X-Other" to "hello world",
            ),
            parsed,
        )
    }

    @Test
    fun parseOtlpHeaders_handlesEmptyAndMalformedInputs() {
        assertEquals(emptyList(), parseOtlpHeaders(null))
        assertEquals(emptyList(), parseOtlpHeaders(""))
        assertEquals(emptyList(), parseOtlpHeaders("   "))
        // No `=` → dropped, not crashed.
        assertEquals(emptyList(), parseOtlpHeaders("nothingHere"))
    }

    private fun flushSpans() {
        sdk.sdkTracerProvider.forceFlush().join(1, TimeUnit.SECONDS)
    }

    private fun flushLogs() {
        sdk.sdkLoggerProvider.forceFlush().join(1, TimeUnit.SECONDS)
    }

    companion object {
        private val exporter = InMemorySpanExporter.create()
        private val logExporter = InMemoryLogRecordExporter.create()
        private val metricReader = InMemoryMetricReader.create()
        private val sdk = buildOpenTelemetrySdk(
            ObservabilityConfig(
                otlpEndpoint = null,
                otlpHeaders = null,
                serviceName = "opson-server-test",
                environment = "test",
                release = null,
            ),
            spanExporter = exporter,
            logRecordExporter = logExporter,
            metricReader = metricReader,
        )

        @BeforeClass
        @JvmStatic
        fun installGlobalOpenTelemetry() {
            // `GlobalOpenTelemetry.set` is one-shot: it can only be
            // called once before any `get` call. Other tests in the
            // suite may implicitly hit `GlobalOpenTelemetry.getTracer(...)`
            // (via withSpan), which initialises the global to noop and
            // locks `set` out. The SDK ships a package-private
            // `resetForTest` for exactly this scenario; reflection
            // bypasses the access check.
            resetGlobalOpenTelemetry()
            GlobalOpenTelemetry.set(sdk)
            // Logback already has the `OpenTelemetryAppender` registered
            // (logback.xml on the test classpath), but it buffers
            // records until install() points it at a live SDK. Wire it
            // here so log records emitted during tests flow into
            // `logExporter`.
            OpenTelemetryAppender.install(sdk)
        }

        private fun resetGlobalOpenTelemetry() {
            val method = GlobalOpenTelemetry::class.java.getDeclaredMethod("resetForTest")
            method.isAccessible = true
            method.invoke(null)
        }
    }
}
