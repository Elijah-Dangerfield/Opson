plugins {
    id("opson.kotlin.multiplatform")
}

moduleConfig {
    optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
}

android {
    namespace = "com.opson.libraries.reminders.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.reminders)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.config)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.resources)
            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(projects.libraries.reminders)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.config)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.resources)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            // ComponentActivity's result registry, for the POST_NOTIFICATIONS prompt.
            implementation(libs.androidx.activity.compose)
        }
    }
}
