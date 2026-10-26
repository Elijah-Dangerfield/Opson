plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.storage"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.androidx.room.runtime)
            api(libs.androidx.sqlite.bundled)
            api(libs.androidx.datastore.core)
            api(libs.androidx.datastore.core.okio)
            api(libs.androidx.datastore.preferences)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}