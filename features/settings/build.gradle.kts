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
            // FindSupportEnabled, the release flag other features check before linking to the page.
            api(projects.libraries.config)
        }
        commonTest.dependencies {
            implementation(projects.libraries.navigation)
        }
    }
}
