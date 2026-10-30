plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.entitlements.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.entitlements)
            // BetaReleaseFlag, release.betaNotice.
            implementation(projects.libraries.config)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.networking)
            implementation(projects.libraries.storage)
            // UserScopedSyncer and UserScopedClearer.
            implementation(projects.libraries.opson)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(projects.libraries.entitlements)
            implementation(projects.libraries.config)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.networking)
            implementation(projects.libraries.storage)
            implementation(projects.libraries.opson)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}
