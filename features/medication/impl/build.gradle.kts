plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.medication.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.medication)
            // MoreRowContribution, for More › Medication.
            implementation(projects.features.settings)
            // TodayCardContribution, for the Today card.
            implementation(projects.features.today)

            implementation(projects.libraries.medication)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.config)
            // LocalCalendar, and TodayCard for the card's shape.
            implementation(projects.libraries.diary)
            implementation(projects.libraries.diary.ui)
            // RemindersEnabled and NotificationPermissions, for "Remind me on dose day".
            implementation(projects.libraries.reminders)
            implementation(libs.kotlinx.datetime)

            // Compose dependencies (navigation and lifecycle provided by opson.feature plugin)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(projects.features.medication)
            implementation(projects.libraries.medication)
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.core)
            implementation(projects.libraries.config)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.reminders)
            implementation(projects.libraries.resources)
            implementation(libs.kotlinx.datetime)
        }
    }
}
