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
            // Routes the + sheet opens in its place: the meal note (Note), Weight (Weigh-in) and Fasting.
            implementation(projects.features.today)
            implementation(projects.features.trends)
            implementation(projects.features.settings)
            // Edit a copy, from the food detail's ⋮.
            implementation(projects.features.foods)
            // BuildersEnabled and BuilderProgramRoute: Builder mode, Check against the pack, the scanner's label read.
            implementation(projects.features.builder)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.diary.ui)
            implementation(projects.libraries.foods)
            // Photo estimate offers itself only with the photo_estimate entitlement.
            implementation(projects.libraries.entitlements)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.nutrition.ui)
            implementation(projects.libraries.config)
            implementation(projects.libraries.scanner)
            implementation(projects.libraries.speech)
            // release.exercise, and whether a health app is connected, for the exercise sheet.
            implementation(projects.libraries.health)
            // PromptEvents: a food report that went through is a moment a prompt can follow.
            implementation(projects.libraries.prompts)

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
            implementation(projects.libraries.entitlements)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.config)
            implementation(projects.libraries.scanner)
            implementation(projects.libraries.speech)
            implementation(projects.libraries.health)
            implementation(projects.libraries.prompts)
            implementation(projects.features.builder)
        }
    }
}
