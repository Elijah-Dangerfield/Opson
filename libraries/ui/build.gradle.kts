
plugins {
    id("opson.compose.multiplatform")
}

android {
    namespace = "com.opson.libraries.ui"
}

kotlin {
    sourceSets {

        androidMain.dependencies {
            api(compose.preview)
            api(compose.uiTooling)
        }

        commonMain.dependencies {
            implementation(projects.libraries.core)
            // TODO honestly the opson library should expose the component that require opson domain
            implementation(projects.libraries.opson)
            // User-facing copy in the few DS components that carry their own
            // (ShakeDialog), so `VerifyStrings` has a resource to point at.
            implementation(projects.libraries.resources)

            api(compose.ui)
            api(compose.uiUtil)
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.components.resources)
            api(compose.components.uiToolingPreview)
            api(compose.materialIconsExtended)
            api(compose.material3AdaptiveNavigationSuite)
            api(libs.compose.backhandler)

            api(libs.compottie)
            api(libs.compottie.resources)
            api(libs.compottie.dot)
            api(libs.compottie.lite)
            api(libs.compottie.network)
        }
    }
}