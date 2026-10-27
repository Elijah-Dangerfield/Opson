plugins {
    id("opson.compose.multiplatform")
}

android {
    namespace = "com.opson.libraries.diary.ui"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.libraries.diary)
            api(projects.libraries.nutrition.ui)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.resources)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }
        commonTest.dependencies {
            // ExerciseLabelsTest compares StringResources.
            implementation(compose.components.resources)
        }
    }
}
