plugins {
    id("opson.compose.multiplatform")
}

android {
    namespace = "com.opson.libraries.speech"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            implementation(compose.runtime)
        }
    }
}
