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
        }
    }
}
