plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.foods.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.foods)
            // LoggerRoute: Foods rows open it with a food picked, and Create a meal picks through it.
            implementation(projects.features.logger)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.diary.ui)
            implementation(projects.libraries.foods)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.nutrition.ui)

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
            implementation(projects.features.foods)
            implementation(projects.features.logger)
            implementation(projects.libraries.core)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.diary.ui)
            implementation(projects.libraries.foods)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.resources)
            implementation(libs.kotlinx.datetime)
            implementation(compose.components.resources)
        }
    }
}
