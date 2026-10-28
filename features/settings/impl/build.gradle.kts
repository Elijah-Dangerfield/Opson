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
            // NutrientsRoute, which Energy today links to.
            implementation(projects.features.trends)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.diary)
            // The fasting status card, shared with Today.
            implementation(projects.libraries.diary.ui)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.nutrition.ui)
            implementation(projects.libraries.identity)
            implementation(projects.libraries.reminders)
            implementation(projects.libraries.config)
            implementation(projects.libraries.health)
            // The Widgets page and its More row, and the widget's leak surface.
            implementation(projects.libraries.widgets)
            // ProgressPhotosEnabled: Modes' Progress photos row.
            implementation(projects.libraries.progressphotos)

            implementation(libs.kotlinx.datetime)

            // Compose dependencies (navigation and lifecycle provided by opson.feature plugin)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            // FindSupportEnabled, and WebLinkLauncher for the fake dialer.
            implementation(projects.features.settings)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.flowroutines.testing)
            // DeviceRegion, for Find support and the height unit.
            implementation(projects.libraries.core)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.identity)
            implementation(projects.libraries.reminders)
            implementation(projects.libraries.config)
            implementation(projects.libraries.health)
            implementation(projects.libraries.widgets)
            implementation(projects.libraries.progressphotos)
            implementation(libs.kotlinx.datetime)
            implementation(projects.libraries.resources)
            // AppIcons, for the fake contributed More row.
            implementation(projects.libraries.ui)
            implementation(compose.components.resources)
        }
    }
}
