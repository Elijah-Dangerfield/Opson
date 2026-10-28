plugins {
    id("opson.kotlin.multiplatform")
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

android {
    namespace = "com.opson.libraries.config.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.config)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.networking)
            implementation(projects.libraries.storage)
            // AppEvents: config refreshes on app foreground.
            implementation(projects.libraries.opson)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
            implementation(compose.components.resources)
        }
        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.config)
            implementation(projects.libraries.core)
            implementation(projects.libraries.networking)
            implementation(projects.libraries.storage)
            implementation(projects.libraries.opson)
            implementation(libs.ktor.client.mock)
        }
    }
}

compose.resources {
    publicResClass = false
}