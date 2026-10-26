plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.today.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.today)
            // Routes Today opens: the logger and Log screen, Nutrients and Weight,
            // the pause sheet and Water settings, and Create a meal.
            implementation(projects.features.logger)
            implementation(projects.features.trends)
            implementation(projects.features.settings)
            implementation(projects.features.foods)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.diary.ui)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.nutrition.ui)
            // Expended so far, behind release.health.
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
            implementation(projects.features.today)
            implementation(projects.features.logger)
            implementation(projects.features.settings)
            implementation(projects.libraries.core)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.diary.ui)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.health)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.ui)
        }
    }
}
