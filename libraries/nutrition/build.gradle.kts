plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.nutrition"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            // `NumbersChange` is passed through the pause sheet's route, so its serializer is
            // part of this module's API.
            api(libs.kotlinx.serialization.json)
        }
    }
}
