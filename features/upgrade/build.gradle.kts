plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.upgrade"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The upgrade.* values and AppGuardState.from read an AppConfigMap.
            api(projects.libraries.config)
            // BuildInfo, for the store link.
            implementation(projects.libraries.core)
        }
        commonTest.dependencies {
            // Main's implementation deps are not on the test classpath; Platform is named again.
            implementation(projects.libraries.core)
        }
    }
}
