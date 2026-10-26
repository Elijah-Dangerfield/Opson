package com.opson.server.plugins

import com.opson.server.config.ObservabilityConfig
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationStopPreparing
import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.context.Context
import io.opentelemetry.exporter.logging.LoggingMetricExporter
import io.opentelemetry.exporter.logging.LoggingSpanExporter
import io.opentelemetry.exporter.logging.SystemOutLogRecordExporter
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter
import io.opentelemetry.exporter.otlp.http.metrics.OtlpHttpMetricExporter
import io.opentelemetry.exporter.otlp.http.trace.OtlpHttpSpanExporter
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.logs.export.LogRecordExporter
import io.opentelemetry.sdk.logs.export.SimpleLogRecordProcessor
import io.opentelemetry.sdk.metrics.SdkMeterProvider
import io.opentelemetry.sdk.metrics.export.MetricExporter
import io.opentelemetry.sdk.metrics.export.MetricReader
import io.opentelemetry.sdk.metrics.export.PeriodicMetricReader
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.ReadWriteSpan
import io.opentelemetry.sdk.trace.ReadableSpan
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.SpanProcessor
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor
import io.opentelemetry.sdk.trace.export.SpanExporter
import org.slf4j.LoggerFactory
import java.time.Duration as JavaDuration

/**
 * Initialises the OpenTelemetry SDK and registers it as [GlobalOpenTelemetry],
 * so feature code reads the tracer/meter via the global without threading the
 * SDK through constructors. Returns the live SDK.
 *
 * **Env-gated by a single switch.** `OTEL_EXPORTER_OTLP_ENDPOINT` flips traces,
 * logs, and metrics together:
 *  - unset → stdout exporters (spans/logs/metrics appear in your normal log
 *    output, so dev + unconfigured deploys stay debuggable, no external sink).
 *  - set   → OTLP/HTTP for all three, with headers from
 *    `OTEL_EXPORTER_OTLP_HEADERS` (`Key=Value,Key=Value`, percent-decoded for
 *    `Authorization=Basic%20…`-style vendor headers).
 *
 * The global slot is set-once. We do **not** probe with `GlobalOpenTelemetry.get()`
 * first — the probe locks the slot to noop and makes our `set()` throw. Try-set-
 * catch is the only safe order.
 */
fun Application.installOpenTelemetry(config: ObservabilityConfig): OpenTelemetry {
    val log = LoggerFactory.getLogger("OpenTelemetry")

    val sdk = buildOpenTelemetrySdk(config)
    val installed: OpenTelemetry = try {
        GlobalOpenTelemetry.set(sdk)
        sdk
    } catch (e: IllegalStateException) {
        log.warn("OpenTelemetry global already set; reusing the live SDK", e)
        GlobalOpenTelemetry.get()
    }
    OpenTelemetryAppender.install(installed)
    ServerMetrics.register()

    monitor.subscribe(ApplicationStopPreparing) {
        try {
            sdk.close()
        } catch (e: Throwable) {
            log.warn("OpenTelemetry SDK shutdown failed", e)
        }
    }

    log.info(
        "OpenTelemetry initialised (service={}, env={}, exporter={})",
        config.serviceName,
        config.environment,
        if (config.otlpEndpoint.isNullOrBlank()) "stdout" else "otlp-http",
    )
    return installed
}

/**
 * Builds (but does not register) an [OpenTelemetrySdk] for [config].
 * Test-friendly seam: tests that swap in
 * [io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter] (or its
 * log counterpart) build their SDK directly and inject it where needed
 * without touching the global slot.
 */
fun buildOpenTelemetrySdk(
    config: ObservabilityConfig,
    spanExporter: SpanExporter? = null,
    logRecordExporter: LogRecordExporter? = null,
    metricReader: MetricReader? = null,
): OpenTelemetrySdk {
    val resource = Resource.getDefault().merge(
        Resource.create(
            Attributes.builder()
                .put(AttributeKey.stringKey("service.name"), config.serviceName)
                .put(AttributeKey.stringKey("deployment.environment"), config.environment)
                .apply { config.release?.let { put(AttributeKey.stringKey("service.version"), it) } }
                .build(),
        ),
    )
    val otlp = !config.otlpEndpoint.isNullOrBlank()

    val resolvedSpanExporter = spanExporter ?: defaultSpanExporter(config)
    val tracerProvider = SdkTracerProvider.builder()
        .setResource(resource)
        // Runs first: copy correlation baggage onto every span (root + children)
        // as it starts, before the export processor sees it.
        .addSpanProcessor(BaggageAttributeSpanProcessor(CORRELATION_BAGGAGE_KEYS))
        .addSpanProcessor(UrlRedactingSpanProcessor)
        .addSpanProcessor(
            if (spanExporter == null && otlp) BatchSpanProcessor.builder(resolvedSpanExporter).build()
            else SimpleSpanProcessor.create(resolvedSpanExporter),
        )
        .build()

    val resolvedLogExporter = logRecordExporter ?: defaultLogRecordExporter(config)
    val loggerProvider = SdkLoggerProvider.builder()
        .setResource(resource)
        .addLogRecordProcessor(
            if (logRecordExporter == null && otlp) BatchLogRecordProcessor.builder(resolvedLogExporter).build()
            else SimpleLogRecordProcessor.create(resolvedLogExporter),
        )
        .build()

    val meterProvider = SdkMeterProvider.builder()
        .setResource(resource)
        .registerMetricReader(metricReader ?: defaultMetricReader(config))
        .build()

    return OpenTelemetrySdk.builder()
        .setTracerProvider(tracerProvider)
        .setLoggerProvider(loggerProvider)
        .setMeterProvider(meterProvider)
        .build()
}

private fun defaultSpanExporter(config: ObservabilityConfig) =
    config.otlpEndpoint?.takeUnless { it.isBlank() }?.let { endpoint ->
        OtlpHttpSpanExporter.builder()
            .setEndpoint(endpoint.trimEnd('/') + "/v1/traces")
            .apply { parseOtlpHeaders(config.otlpHeaders).forEach { (n, v) -> addHeader(n, v) } }
            .build()
    } ?: LoggingSpanExporter.create()

private fun defaultLogRecordExporter(config: ObservabilityConfig) =
    config.otlpEndpoint?.takeUnless { it.isBlank() }?.let { endpoint ->
        OtlpHttpLogRecordExporter.builder()
            .setEndpoint(endpoint.trimEnd('/') + "/v1/logs")
            .apply { parseOtlpHeaders(config.otlpHeaders).forEach { (n, v) -> addHeader(n, v) } }
            .build()
    } ?: SystemOutLogRecordExporter.create()

private fun defaultMetricExporter(config: ObservabilityConfig): MetricExporter =
    config.otlpEndpoint?.takeUnless { it.isBlank() }?.let { endpoint ->
        OtlpHttpMetricExporter.builder()
            .setEndpoint(endpoint.trimEnd('/') + "/v1/metrics")
            .apply { parseOtlpHeaders(config.otlpHeaders).forEach { (n, v) -> addHeader(n, v) } }
            .build()
    } ?: LoggingMetricExporter.create()

private fun defaultMetricReader(config: ObservabilityConfig): MetricReader =
    PeriodicMetricReader.builder(defaultMetricExporter(config))
        .setInterval(JavaDuration.ofSeconds(60))
        .build()

/**
 * Parses an `OTEL_EXPORTER_OTLP_HEADERS`-shaped string (`Key=Value,Key=Value`).
 * Values are percent-decoded per the OTel spec — vendors hand you headers like
 * `Authorization=Basic%20<base64>` with the space encoded.
 */
internal fun parseOtlpHeaders(raw: String?): List<Pair<String, String>> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(',').mapNotNull { pair ->
        val eq = pair.indexOf('=')
        if (eq <= 0) return@mapNotNull null
        pair.substring(0, eq).trim() to java.net.URLDecoder.decode(pair.substring(eq + 1).trim(), Charsets.UTF_8)
    }
}

/**
 * Correlation ids carried in OTel Baggage for the duration of a request (set
 * in [Application.installHttpServerTracing]) and copied onto every span by
 * [BaggageAttributeSpanProcessor]. Same keys as the Sentry tags and Loki log
 * fields, so one query string works across all three systems.
 */
internal val CORRELATION_BAGGAGE_KEYS: List<String> = listOf("session_id", "install_id")

/**
 * Copies the given baggage entries onto every span as it starts — including
 * child spans that manual [withSpan] chains create, which the per-request
 * HTTP-span attribute extractor can't reach (span attributes don't inherit;
 * baggage propagates through the OTel context, so a processor is the canonical
 * way to land a per-request value on the whole trace tree).
 *
 * onStart only; cheap string copies. Pairs with the baggage population in
 * [Application.installHttpServerTracing].
 */
internal class BaggageAttributeSpanProcessor(
    private val keys: List<String>,
) : SpanProcessor {
    override fun isStartRequired(): Boolean = true

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {
        val baggage = Baggage.fromContext(parentContext)
        keys.forEach { key ->
            baggage.getEntryValue(key)?.let { span.setAttribute(key, it) }
        }
    }

    override fun isEndRequired(): Boolean = false

    override fun onEnd(span: ReadableSpan) = Unit
}

/**
 * Takes what someone eats out of the URL the Ktor instrumentation records on
 * every HTTP server span, which carries the session and install ids: the
 * query string (`/v1/foods/search?q=`) is replaced, and the path keeps its
 * shape with food ids and barcodes masked ([redactedPath]). The span name is
 * already the route template. A started span's attribute can't be dropped,
 * so values are overwritten.
 */
internal object UrlRedactingSpanProcessor : SpanProcessor {
    private val URL_QUERY: AttributeKey<String> = AttributeKey.stringKey("url.query")
    private val URL_PATH: AttributeKey<String> = AttributeKey.stringKey("url.path")

    override fun isStartRequired(): Boolean = true

    override fun onStart(parentContext: Context, span: ReadWriteSpan) {
        if (span.getAttribute(URL_QUERY) != null) span.setAttribute(URL_QUERY, "redacted")
        span.getAttribute(URL_PATH)?.let { span.setAttribute(URL_PATH, redactedPath(it)) }
    }

    override fun isEndRequired(): Boolean = false

    override fun onEnd(span: ReadableSpan) = Unit
}
