plugins {
    id("opson.compose.multiplatform")
}

android {
    namespace = "com.opson.libraries.goals.ui"
}

// Goal views more than one feature draws (docs/plans/goals-v2.md, "The goal UI"): the projection
// chart and its reveal, and the pieces a goal is set with. Settings' goal screens and onboarding's
// goal question both build from these, so a goal looks and reads the same wherever it is set.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.libraries.goals)
            api(projects.libraries.nutrition.ui)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.resources)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }
        commonTest.dependencies {
            implementation(compose.components.resources)
        }
    }
}
