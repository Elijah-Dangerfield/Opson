plugins {
    id("opson.compose.multiplatform")
}

android {
    namespace = "com.opson.libraries.speech.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.speech)
            implementation(projects.libraries.core)
            // ActivityProvider, for the Android permission rationale check.
            implementation(projects.libraries.opson)
            implementation(compose.runtime)
        }

        androidMain.dependencies {
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }
    }
}
