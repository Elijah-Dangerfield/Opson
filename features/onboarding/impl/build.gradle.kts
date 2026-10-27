plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.onboarding.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.onboarding)
            implementation(projects.features.today)
            // Find support, from "Talk to someone" on the statement.
            implementation(projects.features.settings)
            implementation(projects.libraries.navigation)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.identity)
            implementation(projects.libraries.diary)
            // Meal slot names, for the meal times step.
            implementation(projects.libraries.diary.ui)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.nutrition.ui)
            // OnboardingSetupEnabled, the setup steps' release flag.
            implementation(projects.libraries.config)

            // Compose dependencies (navigation and lifecycle provided by opson.feature plugin)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.features.onboarding)
            implementation(projects.libraries.core)
            implementation(projects.libraries.identity)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.config)
            implementation(projects.features.settings)
            implementation(libs.turbine)
        }
    }
}
