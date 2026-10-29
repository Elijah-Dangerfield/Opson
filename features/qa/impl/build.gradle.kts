plugins {
    id("opson.feature")
}

android {
    namespace = "com.opson.features.qa.impl"
}

// The QA menu's screen, its More row and its route registration. Left out of store builds by
// :apps:compose (see :features:qa).
kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.features.qa)
            // MoreRowContribution, for the Testing card on More.
            implementation(projects.features.settings)
            // DayCloseRoute: the menu opens today's check-in.
            implementation(projects.features.today)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.config)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.identity)
            implementation(projects.libraries.reminders)
            // AppCache (reset onboarding), NetworkConfig (the server), the food cache.
            implementation(projects.libraries.opson)
            implementation(projects.libraries.networking)
            implementation(projects.libraries.foods)
            // PromptDebug, for the Prompts page.
            implementation(projects.libraries.prompts)

            implementation(libs.kotlinx.datetime)

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
            implementation(projects.libraries.config)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.identity)
            implementation(projects.libraries.reminders)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.networking)
            implementation(projects.libraries.foods)
            implementation(projects.libraries.prompts)
            implementation(projects.libraries.resources)
            implementation(libs.kotlinx.datetime)
            implementation(compose.components.resources)
        }
    }
}
