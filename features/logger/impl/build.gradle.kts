plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.logger.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.logger)
            // Routes the logger opens: Weight (Weigh-in chip) and the meal note sheet (Add a note).
            implementation(projects.features.trends)
            implementation(projects.features.today)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.diary.ui)
            implementation(projects.libraries.foods)
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
            implementation(projects.features.logger)
            implementation(projects.libraries.core)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.diary.ui)
            implementation(projects.libraries.foods)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.ui)
        }
    }
}
