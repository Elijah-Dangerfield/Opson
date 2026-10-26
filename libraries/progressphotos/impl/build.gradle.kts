plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.progressphotos.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.progressphotos)
            implementation(projects.libraries.core)
            // UserScopedClearer, so "Delete everything" and a sign-out delete the photos.
            implementation(projects.libraries.opson)
            // The prompt's title.
            implementation(projects.libraries.resources)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.progressphotos)
            implementation(projects.libraries.core)
            implementation(projects.libraries.opson)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            // ComponentActivity's result registry, for the confirm-credential screen below Android 9.
            implementation(libs.androidx.activity.compose)
        }
    }
}
