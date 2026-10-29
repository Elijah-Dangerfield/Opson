plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.home.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.home)
            implementation(projects.libraries.navigation)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.identity)
            // The system photo picker, for a screenshot on a report.
            implementation(projects.libraries.scanner)
            // PromptEvents: a report that went through is a moment a prompt can follow.
            implementation(projects.libraries.prompts)

            // Compose dependencies (navigation and lifecycle provided by opson.feature plugin)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.identity)
            // AppState, DeviceDescription and ScreenCapture, faked for the report pages.
            implementation(projects.libraries.core)
            implementation(projects.libraries.ui)
            // AppCache for the feedback counters, and the copy the failure line points at.
            implementation(projects.libraries.opson)
            implementation(projects.libraries.storage)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.prompts)
            implementation(compose.components.resources)
        }
    }
}
