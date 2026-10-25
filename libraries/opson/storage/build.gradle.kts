plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.opson.storage"
}

moduleConfig.storage()


kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.opson)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.storage)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}