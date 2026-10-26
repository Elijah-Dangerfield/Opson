plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.today"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // MealSlot is a route argument.
            api(projects.libraries.diary)
        }
    }
}
