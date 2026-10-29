plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.builder.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.builder)
            // MoreRowContribution, for More › Builders.
            implementation(projects.features.settings)
            // SubmitFoodRoute, for "Add a food from its pack".
            implementation(projects.features.logger)
            // The join prompt on Today.
            implementation(projects.libraries.prompts)

            implementation(projects.libraries.foods)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.config)
            // LocalCalendar, for the date Pro runs to.
            implementation(projects.libraries.diary)
            implementation(libs.kotlinx.datetime)

            // Compose dependencies (navigation and lifecycle provided by opson.feature plugin)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            // Main's implementation deps are not on the test classpath here, so the types the
            // fakes implement are named again.
            implementation(projects.features.builder)
            implementation(projects.features.logger)
            implementation(projects.libraries.foods)
            implementation(projects.libraries.prompts)
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.core)
            implementation(projects.libraries.config)
            implementation(projects.libraries.diary)
            implementation(libs.kotlinx.datetime)
        }
    }
}
