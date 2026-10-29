plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.apps.integration"

    // OkHttp sees android.jar on this classpath, decides it is on Android and
    // asks android.util.Log whether to log, which throws on the stub jar. It
    // only ever got as far as that when the server built an OkHttp client
    // (the Anthropic SDK's) before the app's own; let the stubs answer
    // defaults instead, so nothing depends on which client starts first.
    testOptions.unitTests.isReturnDefaultValues = true
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
            // `ConfiguredValue` classes. They are spread across modules that
            // cannot see each other, several of them impls (`:libraries:config:impl`
            // owns `config.refreshThrottleMs`, `:libraries:telemetry:impl` the
            // `telemetry.*` three, the logger, Today and onboarding impls their
            // release flags), so an `:apps:*` module is the only place a test
            // can hold the whole declared key set at once.
            implementation(projects.libraries.config)
            implementation(projects.libraries.config.impl)
            // FastingReleaseFlag and CheckInEnabled, for the config manifest drift test.
            implementation(projects.libraries.diary)
            implementation(projects.libraries.telemetry.impl)
            // RemindersEnabled, for ConfigManifestRegistryDriftTest.
            implementation(projects.libraries.reminders)
            // HealthReleaseFlag, GoalsAndEnergyEnabled and ExerciseReleaseFlag, for ConfigManifestRegistryDriftTest.
            implementation(projects.libraries.health)
            // release.barcode, .photo, .voice and .searchOptions live next to the logger they gate.
            implementation(projects.features.logger.impl)
            // release.todaySummary, release.todaySelect and release.foodActions live next to Today.
            implementation(projects.features.today.impl)
            // BuildersEnabled (release.builders), for ConfigManifestRegistryDriftTest.
            implementation(projects.features.builder)
            // release.onboardingSetup lives in onboarding's API, as Targets reads it too.
            implementation(projects.features.onboarding)
            implementation(projects.features.onboarding.impl)
            // WidgetsEnabled (release.widgets), for ConfigManifestRegistryDriftTest.
            implementation(projects.libraries.widgets)
            // FindSupportEnabled (release.findSupport) and ProviderSummaryEnabled (release.providerSummary),
            // for ConfigManifestRegistryDriftTest.
            implementation(projects.features.settings)
            // MoreViewModel, for the log out journey.
            implementation(projects.features.settings.impl)
            // ProgressPhotosEnabled (release.progressPhotos), for ConfigManifestRegistryDriftTest.
            implementation(projects.libraries.progressphotos)
            // MedicationEnabled (release.medication), for ConfigManifestRegistryDriftTest, and the
            // medication tables RoomMigrationTest opens.
            implementation(projects.libraries.medication)
            // The upgrade.* values, and the guard the remote-config journeys drive.
            implementation(projects.features.upgrade)
            implementation(projects.features.upgrade.impl)
            implementation(libs.kotlinx.serialization.json)

            // The device side of the journeys: the diary, its sync engine and
            // the Room database it writes, the food catalog client and the
            // user-change reset, wired by hand in TestClient the way the app's
            // DI graph wires them.
            implementation(projects.libraries.diary.impl)
            implementation(projects.libraries.foods)
            implementation(projects.libraries.foods.impl)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.opson.impl)
            implementation(projects.libraries.opson.storage)
            implementation(projects.libraries.storage.impl)
            implementation(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.kotlinx.datetime)
            implementation(libs.ktor.client.core)

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

    // RoomMigrationTest builds each old database from the exported Room schemas.
    inputs.dir(rootProject.file("libraries/storage/impl/schemas")).withPropertyName("roomSchemas")

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

// ── Real Room on the host JVM ─────────────────────────────────────────────────
//
// The journeys run the app's own AppDatabase, in memory, under the bundled
// SQLite driver production uses. On this classpath that driver is its Android
// variant, which ships its native library for Android ABIs only and loads it
// with System.loadLibrary("sqliteJni"). The JVM variant of the same artifact
// carries that library, built from the same sources, for desktop platforms, so
// it is unpacked here and put on java.library.path. None of the JVM jar's
// classes reach the classpath.
val sqliteHostNatives: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    sqliteHostNatives(libs.androidx.sqlite.bundled.jvm)
}

interface ArchiveAccess {
    @get:Inject
    val archives: ArchiveOperations
}

val stageSqliteNatives = tasks.register<Sync>("stageSqliteNatives") {
    val archives = objects.newInstance<ArchiveAccess>().archives
    from(sqliteHostNatives.elements.map { jars -> jars.map { archives.zipTree(it.asFile) } })
    include("natives/**")
    into(layout.buildDirectory.dir("sqlite-natives"))
}

tasks.withType<Test>().configureEach {
    dependsOn(stageSqliteNatives)
    val os = System.getProperty("os.name").lowercase()
    val arch = if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64" else "x64"
    val platform = when {
        "mac" in os -> "osx_$arch"
        "win" in os -> "windows_$arch"
        else -> "linux_$arch"
    }
    systemProperty(
        "java.library.path",
        layout.buildDirectory.dir("sqlite-natives/natives/$platform").get().asFile.absolutePath,
    )
}
