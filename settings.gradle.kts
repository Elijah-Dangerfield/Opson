enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "Opson"

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        google()
        mavenCentral()
    }
}

// `-DserverOnly=true` (set by apps/server/Dockerfile) trims the build graph to
// just :apps:server so a server image build needs no Android SDK or Kotlin/Native
// toolchain. The property name is intentionally project-agnostic so the rename
// tooling can't break the Dockerfile↔settings contract. The server is a plain JVM
// module with no client-library deps, so nothing else has to be included. If
// apps/server ever depends on a :libraries:* module, add an always-included
// `include(...)` for it here (outside the `if`) and a matching COPY in the Dockerfile.
val serverOnly = System.getProperty("serverOnly") == "true"

// Apps (always included)
include(":apps")
include(":apps:server")

if (!serverOnly) {
    include(":apps:compose")
    // Note: iOS app is not a Gradle module - it's an Xcode project in apps/ios/

    // End-to-end integration harness: the real client stack (real view models)
    // driven against a real in-process Ktor server over a Testcontainers
    // Postgres. Depends on client impl modules + :apps:server, so it's gated
    // out of the server-only build.
    include(":apps:integration")

    // Baseline Profile generation + the minified-release smoke test. A
    // com.android.test module that ships nothing and exists only at build time,
    // so it is client-side and gated out of the server-only graph.
    include(":apps:baselineprofile")

    // Compose Multiplatform (web) admin console for remote config. The server
    // serves the prebuilt bundle at /admin; CI builds it — the server build
    // itself stays JS-toolchain-free, so it's gated out of the server-only
    // graph like the rest of the client. The first (and only) JS target.
    include(":apps:admin")

    // Features
    include(":features:home")
    include(":features:home:impl")
    include(":features:onboarding")
    include(":features:onboarding:impl")

    // Libraries
    include(":libraries:config")
    include(":libraries:config:impl")
    include(":libraries:core")
    include(":libraries:flowroutines")
    include(":libraries:flowroutines:testing")
    include(":libraries:navigation")
    include(":libraries:navigation:impl")
    include(":libraries:networking")
    include(":libraries:networking:impl")
    include(":libraries:nutrition")
    include(":libraries:identity")
    include(":libraries:identity:impl")
    include(":libraries:resources")
    include(":libraries:review")
    include(":libraries:review:impl")
    include(":libraries:storage")
    include(":libraries:storage:impl")
    // No api sibling on purpose: the public surface is the `logEvent`
    // extension in :libraries:core; this impl only hosts the experimental
    // opentelemetry-kotlin dependency + the GrafanaLogTree wiring.
    include(":libraries:telemetry:impl")
    include(":libraries:opson")
    include(":libraries:opson:impl")
    include(":libraries:opson:storage")
    include(":libraries:ui")

    // Custom detekt rules — a standalone JVM jar detekt loads via
    // `detektPlugins`. Dev/CI tooling only, never shipped; gated out of the
    // server-only Docker build like every other client module.
    include(":detekt-rules")
}