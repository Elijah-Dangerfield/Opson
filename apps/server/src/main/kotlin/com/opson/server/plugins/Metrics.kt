package com.opson.server.plugins

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.metrics.Meter

/** One meter for the whole server, resolved through the global SDK. */
internal val serverMeter: Meter
    get() = GlobalOpenTelemetry.getMeter(SERVER_TRACER_NAME)

/**
 * Custom server metrics. Register gauges/counters here; [register] is called
 * once after the OpenTelemetry SDK is installed (see [installOpenTelemetry]).
 *
 * The example gauge just proves the metrics pipeline is wired (it shows up in
 * stdout or your OTLP backend). Replace it with real metrics — e.g. queue
 * depths, cache sizes, domain counts.
 */
object ServerMetrics {
    fun register() {
        serverMeter.gaugeBuilder("app.example.up")
            .ofLongs()
            .setDescription("Always 1 — proves the metrics pipeline is exporting.")
            .buildWithCallback { measurement -> measurement.record(1) }
    }
}
