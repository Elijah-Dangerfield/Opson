plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.upgrade.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.upgrade)
            implementation(projects.libraries.config)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            // AppEvents: a stopped app re-checks the config each time it comes back.
            implementation(projects.libraries.opson)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            // Main's implementation deps are not on the test classpath here, so the types the
            // fakes implement are named again.
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.features.upgrade)
            implementation(projects.libraries.config)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.core)
        }
    }
}
