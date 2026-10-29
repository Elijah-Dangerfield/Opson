plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.medication.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.medication)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.config)
            // LocalCalendar, the phone's day.
            implementation(projects.libraries.diary)
            // DoseReminders, which this module answers for the reminder coordinator.
            implementation(projects.libraries.reminders)
            // The plan lives in a device cache, wiped on a user change (UserScopedClearer).
            implementation(projects.libraries.storage)
            implementation(projects.libraries.opson)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(projects.libraries.medication)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.reminders)
            implementation(projects.libraries.storage)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.config)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines.testing)
        }
    }
}
