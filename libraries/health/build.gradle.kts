plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.health"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // HealthReleaseFlag is a FlagConfigValue.
            api(projects.libraries.config)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
            // Burned and resting energy, which BurnedRepository answers with.
            api(projects.libraries.nutrition)
        }
    }
}
