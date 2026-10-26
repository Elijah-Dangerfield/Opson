plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.trends.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.trends)
            implementation(projects.features.settings)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.diary.ui)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.nutrition.ui)

            // Compose dependencies (navigation and lifecycle provided by opson.feature plugin)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            // Main's implementation deps are not on the test classpath here (see home's
            // identity line), so the types the fakes implement are named again.
            implementation(projects.features.trends)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.resources)
        }
    }
}
