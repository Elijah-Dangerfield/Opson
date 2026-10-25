plugins {
    id("opson.kotlin.multiplatform")
}

moduleConfig {
    di()
    optIn("io.opentelemetry.kotlin.ExperimentalApi")
}

android {
    namespace = "com.opson.libraries.telemetry.impl"
}

// The OTel exporters depend on `ktor-client-cio`, and CIO on Kotlin/Native has
// NO TLS: any https request that resolves to it dies with "TLS sessions are not
// supported on Native platform". We never ask for CIO — every HttpClient in this
// project is built with `platformHttpEngineFactory` (OkHttp on Android, Darwin on
// iOS), which is the real defence and the reason this has not bitten us. But a
// `HttpClient { }` written without that factory resolves an engine off the
// classpath, and with two engines present which one wins is a race. An app whose
// backend works is not evidence of absence; it is evidence that Darwin won.
//
// Excluded here rather than globally: `:apps:server` uses CIO deliberately, on
// the JVM, where it has TLS.
//
// It also drags in a NEWER ktor than the catalog pins (3.5.1 against 3.3.3),
// so removing it takes a version skew out of the graph as well.
//
// Verify with:
//   ./gradlew :apps:compose:dependencies \
//     --configuration iosSimulatorArm64CompileKlibraries | grep ktor-client
configurations.configureEach {
    exclude(group = "io.ktor", module = "ktor-client-cio")
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            implementation(projects.libraries.config)
            implementation(projects.libraries.networking)
            // AppEventListener — app.launched must wait for the cold-boot
            // dispatch so it carries the settled session_id.
            implementation(projects.libraries.opson)
            // FileManager — the on-device buffer directory for the durable
            // export pipeline.
            implementation(projects.libraries.storage)
            implementation(projects.libraries.flowroutines)
            implementation(libs.okio)
            implementation(libs.otel.kotlin.api)
            implementation(libs.otel.kotlin.sdk.api)
            implementation(libs.otel.kotlin.implementation)
            implementation(libs.otel.kotlin.exporters.core)
            implementation(libs.otel.kotlin.exporters.persistence)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.client.encoding)
        }

        commonTest.dependencies {
            implementation(projects.libraries.core)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.flowroutines.testing)
            implementation(libs.okio)
            implementation(libs.otel.kotlin.api)
            implementation(libs.otel.kotlin.sdk.api)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.mock)
        }

        androidMain.dependencies {
            implementation(libs.ktor.client.okhttp)
            implementation(libs.androidx.metrics.performance)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }
    }
}
