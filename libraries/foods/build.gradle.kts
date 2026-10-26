plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.foods"
}

// The food_cache entity and DAO live here rather than in the impl so
// `:libraries:storage:impl` can register them without an impl dependency.
moduleConfig.storage()

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.libraries.diary)
            api(projects.libraries.opson.storage)
            api(projects.libraries.core)
            api(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}
