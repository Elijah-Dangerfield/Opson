plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.review"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
    }
}
