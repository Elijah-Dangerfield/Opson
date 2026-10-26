plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.settings.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.settings)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)

            // Compose dependencies (navigation and lifecycle provided by opson.feature plugin)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }
    }
}
