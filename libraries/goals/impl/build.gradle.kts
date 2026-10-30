plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.goals.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.goals)
            // Settings, the log, weigh-ins and the local calendar.
            implementation(projects.libraries.diary)
            // The health app's readings and each day's Burned.
            implementation(projects.libraries.health)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.goals)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.health)
            implementation(libs.kotlinx.datetime)
        }
    }
}
