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
            // FoodRef keys, for Add to favorites on a food's sheet.
            implementation(projects.libraries.foods)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.nutrition.ui)
            implementation(projects.libraries.reminders)
            // Expended so far, behind release.health.
            implementation(projects.libraries.health)
            // The design sync 2 release flags (release.todaySummary, todaySelect, checkIn).
            implementation(projects.libraries.config)
            // The summary card's page and collapse, kept on the device (TodayCardViewStore).
            implementation(projects.libraries.storage)
            // UserScopedClearer, so a user change wipes the card's view with the rest.
            implementation(projects.libraries.opson)

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
            implementation(projects.libraries.foods)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.health)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.reminders)
            implementation(projects.libraries.config)
            implementation(projects.libraries.storage)
            implementation(projects.libraries.opson)
        }
    }
}
