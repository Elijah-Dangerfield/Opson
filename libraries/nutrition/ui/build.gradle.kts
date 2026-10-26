plugins {
    id("opson.compose.multiplatform")
}

android {
    namespace = "com.opson.libraries.nutrition.ui"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.libraries.nutrition)
            implementation(projects.libraries.ui)
        }
    }
}
