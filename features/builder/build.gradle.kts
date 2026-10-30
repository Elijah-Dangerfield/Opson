plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.builder"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // BuildersEnabled is a release flag over an AppConfigMap.
            api(projects.libraries.config)
            // VerifyFoodAvailable asks BuilderProgram whether this account is an active Builder.
            implementation(projects.libraries.foods)
        }
    }
}
