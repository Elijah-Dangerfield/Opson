plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.reminders"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.libraries.diary)
            implementation(projects.libraries.core)
            // RemindersEnabled is a FlagConfigValue: callers resolve it through config's API.
            api(projects.libraries.config)
            // The notification copy is StringResources, resolved by the coordinator.
            api(projects.libraries.resources)
            api(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(projects.libraries.diary)
            implementation(libs.kotlinx.datetime)
        }
    }
}
