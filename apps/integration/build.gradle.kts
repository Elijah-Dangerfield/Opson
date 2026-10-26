plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.apps.integration"
}

// End-to-end integration harness. The tests run as Android unit tests on the
// host JVM (`testDebugUnitTest`) — the same path the feature view models already
// compile through — so they can drive the REAL client stack (and the real
// HomeViewModel) against a REAL in-process Ktor server over a REAL Postgres
// (Testcontainers). Everything lives in the `androidUnitTest` source set;
// commonMain stays empty (nothing ships here, and the iOS target must not try
// to link the JVM-only server).
//
// No `jvm{}` targets are added to the client libraries — we reuse their
// existing Android variants on the host JVM. The one unusual edge this module
// proves out is consuming the JVM-only `:apps:server` from an Android
// unit-test classpath.
kotlin {
    sourceSets {
        androidUnitTest.dependencies {
            // Real server: installApp, ServerComponent, Database.connect, the
            // JwtVerification.Static seam.
            implementation(projects.apps.server)

            // Real client view models + the stack beneath them.
            implementation(projects.features.home)
            implementation(projects.features.home.impl)
            implementation(projects.libraries.identity)
            implementation(projects.libraries.identity.impl)
            implementation(projects.libraries.networking)
            implementation(projects.libraries.networking.impl)
            implementation(projects.libraries.storage)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.core)

            // ConfigManifestRegistryDriftTest instantiates the real
            // `ConfiguredValue` classes. They are spread across two impl
            // modules that cannot see each other — `:libraries:config:impl`
            // owns `config.refreshThrottleMs`, `:libraries:telemetry:impl` owns
            // the `telemetry.*` three — so an `:apps:*` module is the only
            // place a test can hold the whole declared key set at once.
            implementation(projects.libraries.config)
            implementation(projects.libraries.config.impl)
            implementation(projects.libraries.telemetry.impl)
            // `release.barcode` lives next to the logger it gates.
            implementation(projects.features.logger.impl)
            implementation(libs.kotlinx.serialization.json)

            // Boot a real server on an ephemeral port + verify HS256 test JWTs.
            // Declared here because :apps:server's dependencies are
            // `implementation`-scoped and don't leak to consumers' compile
            // classpaths.
            implementation(libs.ktor.serverCore)
            implementation(libs.ktor.serverNetty)
            implementation(libs.ktor.serverAuthJwt)
            implementation(libs.auth0.jwt)
            // The client's HttpClient {} resolves its engine per platform;
            // supply the Android/JVM one explicitly so engine discovery is
            // deterministic on the host JVM.
            implementation(libs.ktor.client.okhttp)

            // Real Postgres for the server side (same recipe as the server's
            // own DatabaseTest — shared container per JVM, Flyway migrations
            // through the production Database.connect path).
            implementation(libs.testcontainers.postgres)

            implementation(libs.kotlin.test)
            implementation(libs.kotlin.testJunit)
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

// ── Files these tests read at runtime, declared so Gradle can see them ────────
//
// ConfigManifestRegistryDriftTest reads a committed JSON file, and
// DashboardQueryContractTest reads a directory of them plus the Kotlin tree. An
// Android unit test's working directory is not worth guessing at, so the paths
// are handed in as system properties — a wrong guess reads nothing and passes.
//
// The `inputs.file`/`inputs.files` declarations are the load-bearing half, and
// the non-obvious one. **A file read at test *runtime* is invisible to Gradle's
// up-to-date check.** Without these declarations,
// editing the registry alone leaves `testDebugUnitTest` UP-TO-DATE and the
// drift ships: downstream, the first mutation run reported BUILD SUCCESSFUL
// against a registry with a key deleted and three values wrong. The rule
// generalises to any test whose subject is a file rather than a class.
tasks.withType<Test>().configureEach {
    val repoRoot = rootProject.layout.projectDirectory
    systemProperty("repoRoot", repoRoot.asFile.absolutePath)

    val registry = rootProject.file("apps/admin/config-manifest-registry.json")
    inputs.file(registry).withPropertyName("configManifestRegistry")
    systemProperty("configManifestRegistry", registry.absolutePath)

    val dashboards = rootProject.file("ops/grafana")
    inputs.dir(dashboards).withPropertyName("grafanaDashboards")
    systemProperty("grafanaDashboards", dashboards.absolutePath)

    // DashboardQueryContractTest checks every analytics view a board reads
    // against the views these migrations create.
    inputs.dir(rootProject.file("apps/server/src/main/resources/db/migration")).withPropertyName("serverMigrations")

    // The same hole one level along: the tests also grep the Kotlin tree, for
    // `ConfiguredValue` declarations and for `logEvent(...)` call sites, and
    // Gradle cannot see either read. A
    // filtered tree rather than `inputs.dir` — declaring the directories
    // wholesale sweeps in `*/build/**`, which is another task's output, and
    // Gradle rejects the undeclared dependency. `.claude/` holds agent
    // worktrees, which are full checkouts of this repo, so without excluding it
    // the inputs change every time an agent edits anything.
    inputs.files(
        rootProject.fileTree(repoRoot) {
            include("libraries/**/*.kt", "features/**/*.kt", "apps/**/*.kt")
            exclude("**/build/**", ".claude/**")
        },
    ).withPropertyName("sourceTreeScan")
}
