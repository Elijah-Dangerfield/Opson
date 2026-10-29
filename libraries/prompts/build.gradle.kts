plugins {
    id("opson.compose.multiplatform")
}

android {
    namespace = "com.opson.libraries.prompts"
}

// What a module needs to offer something on Today (docs/practices/prompts.md): the candidate
// contract, the events that can call one up, and the host the app draws at its root.
kotlin {
    sourceSets {
        commonMain.dependencies {
            // A candidate names its copy, its icon and where accepting it goes.
            api(projects.libraries.navigation)
            api(projects.libraries.ui)
            api(compose.components.resources)
            api(libs.kotlinx.coroutines.core)
            implementation(compose.runtime)
        }
    }
}
