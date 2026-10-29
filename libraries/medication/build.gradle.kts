plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.medication"
}

// Room entities and DAOs live here so `:libraries:storage:impl` can register
// them in AppDatabase without depending on an impl module.
moduleConfig.storage()

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            api(projects.libraries.opson.storage)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            // MedicationEnabled, the release.medication flag.
            api(projects.libraries.config)
            // Labels for sites, symptoms and medicines are StringResources.
            api(projects.libraries.resources)
        }
        commonTest.dependencies {
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
