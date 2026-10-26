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
            // The meal note sheet behind "Add a note".
            implementation(projects.features.today)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.diary.ui)
            implementation(projects.libraries.foods)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.nutrition.ui)
            implementation(projects.libraries.config)
            implementation(projects.libraries.scanner)

            // Compose dependencies (navigation and lifecycle provided by opson.feature plugin)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.navigation)
            implementation(projects.features.logger)
            implementation(projects.libraries.core)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.diary.ui)
            implementation(projects.libraries.foods)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.config)
            implementation(projects.libraries.scanner)
        }
    }
}
