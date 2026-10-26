plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.settings"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // NumbersChange travels through PauseBeforeMoreRoute.
            api(projects.libraries.nutrition)
        }
        commonTest.dependencies {
            implementation(projects.libraries.navigation)
        }
    }
}
