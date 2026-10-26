plugins {
    id("opson.compose.multiplatform")
}

android {
    namespace = "com.opson.libraries.scanner"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.ui)
        }
    }
}
