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
            // Progress photos: the repository, the phone's lock, the flag, and the in-app camera.
            implementation(projects.libraries.progressphotos)
            implementation(projects.libraries.scanner)
            // AppLifecycle: the photos lock again when the app goes to the background.
            implementation(projects.libraries.opson)
            // The Expended card: energy from the watch, behind release.health.
            implementation(projects.libraries.health)

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
            // A chart point's reading carries its metric's test tag.
            implementation(projects.libraries.nutrition.ui)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.progressphotos)
            implementation(projects.libraries.scanner)
            implementation(projects.libraries.config)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.health)
        }
    }
}
