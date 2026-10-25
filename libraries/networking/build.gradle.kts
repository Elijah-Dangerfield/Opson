plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.networking"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.ktor.client.core)
            api(libs.kotlinx.serialization.json)

            implementation(projects.libraries.core)
        }

        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.core)
        }

        // Engines are `api` so the platform-specific offline-error
        // classification (OfflineErrors.android/ios) can see the concrete
        // engine exception types.
        androidMain.dependencies {
            api(libs.ktor.client.okhttp)
        }
        iosMain.dependencies {
            api(libs.ktor.client.darwin)
        }
    }
}

tasks.matching { it.name.contains("kspCommonMainKotlinMetadata", ignoreCase = true) }
    .configureEach { enabled = false }
