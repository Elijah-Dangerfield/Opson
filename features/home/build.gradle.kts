plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.home"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // FeedbackRoute is a Route.
            implementation(projects.libraries.navigation)
        }
    }
}
