plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ksp)
    application
}

application {
    mainClass.set("com.opson.server.MainKt")
}

tasks.named<JavaExec>("run") {
    // Resolve apps/server/.env relative to the repo root so
    // `./gradlew :apps:server:run` picks up local dev secrets.
    workingDir = rootDir
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.ktor.serverCore)
    implementation(libs.ktor.serverNetty)
    implementation(libs.ktor.serverContentNegotiation)
    implementation(libs.ktor.serverCors)
    implementation(libs.ktor.serverStatusPages)
    implementation(libs.ktor.serverCallLogging)
    implementation(libs.ktor.serverCallId)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.logback.classic)

    // Outbound HTTP (Supabase Admin API client)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.contentNegotiation)

    // Auth — verify Supabase-issued JWTs (JWKS / ES256).
    implementation(libs.ktor.serverAuth)
    implementation(libs.ktor.serverAuthJwt)
    implementation(libs.auth0.jwt)
    implementation(libs.auth0.jwksRsa)

    // Photo estimates: the official Anthropic SDK (Messages API, image input).
    implementation(libs.anthropic.java)

    // Rate limiting + observability. Sentry is a no-op until SENTRY_DSN is set;
    // OpenTelemetry exports to stdout until OTEL_EXPORTER_OTLP_ENDPOINT is set.
    implementation(libs.ktor.serverRateLimit)
    implementation(libs.sentry.jvm)
    implementation(libs.opentelemetry.api)
    implementation(libs.opentelemetry.sdk)
    implementation(libs.opentelemetry.sdk.logs)
    implementation(libs.opentelemetry.exporter.otlp)
    implementation(libs.opentelemetry.exporter.logging)
    implementation(libs.opentelemetry.extension.kotlin)
    implementation(libs.opentelemetry.logback.appender)
    implementation(libs.opentelemetry.ktor)

    // Persistence — Postgres + HikariCP pool + Exposed DSL + Flyway migrations.
    implementation(libs.postgres.jdbc)
    implementation(libs.hikaricp)
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.javaTime)
    implementation(libs.flyway.core)
    runtimeOnly(libs.flyway.postgres)

    // DI — the same kotlin-inject + anvil stack the client uses, so moving
    // between client and server DI is the same mental model.
    implementation(libs.kotlin.inject.runtime.kmp)
    implementation(libs.anvil.runtime)
    implementation(libs.anvil.runtime.optional)
    ksp(libs.kotlin.inject.compiler.ksp)
    ksp(libs.anvil.compiler)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlin.testJunit)
    testImplementation(libs.junit)
    testImplementation(libs.ktor.serverTestHost)
    testImplementation(libs.ktor.client.contentNegotiation)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.opentelemetry.sdk.testing)
    testImplementation(libs.testcontainers.postgres)
}

kotlin {
    jvmToolchain(17)
    compilerOptions {
        // Match the client convention plugins' opt-ins so KSP-generated code
        // that touches still-experimental stdlib types (Clock, Uuid) compiles.
        freeCompilerArgs.addAll(
            "-opt-in=kotlin.time.ExperimentalTime",
            "-opt-in=kotlin.uuid.ExperimentalUuidApi",
        )
    }
}
