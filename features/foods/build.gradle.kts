plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.foods"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // MealSlot is a route argument.
            api(projects.libraries.diary)
        }
    }
}
