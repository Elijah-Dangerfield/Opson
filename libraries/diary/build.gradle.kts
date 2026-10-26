plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.diary"
}

// Room entities and DAOs live here so `:libraries:storage:impl` can register
// them in AppDatabase without depending on an impl module.
moduleConfig.storage()

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.libraries.nutrition)
            api(projects.libraries.opson.storage)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
            // api: SettingsJson is part of the surface.
            api(libs.kotlinx.serialization.json)
        }
    }
}
