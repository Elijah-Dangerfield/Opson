plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.entitlements"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
        }
    }
}
