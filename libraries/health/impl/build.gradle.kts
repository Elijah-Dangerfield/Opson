plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.health.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.health)
            // Settings, weigh-ins and the local calendar, for RealBurnedRepository.
            implementation(projects.libraries.diary)
            implementation(projects.libraries.core)
            // AppData carries the one thing persisted: that the person connected.
            implementation(projects.libraries.opson)
            implementation(projects.libraries.storage)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.health)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.core)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.storage)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.androidx.health.connect)
            // ComponentActivity's result registry, for the permission request.
            implementation(libs.androidx.activity.compose)
        }
    }
}
