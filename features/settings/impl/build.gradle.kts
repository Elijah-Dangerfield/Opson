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
            // FeedbackRoute (Support's "Send feedback") and OnboardingRoute (after deleting everything).
            implementation(projects.features.home)
            implementation(projects.features.onboarding)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.nutrition.ui)
            implementation(projects.libraries.identity)

            implementation(libs.kotlinx.datetime)

            // Compose dependencies (navigation and lifecycle provided by opson.feature plugin)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.identity)
            implementation(libs.kotlinx.datetime)
            implementation(projects.libraries.resources)
            implementation(compose.components.resources)
        }
    }
}
