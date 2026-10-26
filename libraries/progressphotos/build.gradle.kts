plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.progressphotos"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // ProgressPhotosEnabled is a FlagConfigValue.
            api(projects.libraries.config)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
