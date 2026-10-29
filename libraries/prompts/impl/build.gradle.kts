plugins {
    id("opson.compose.multiplatform")
}

android {
    namespace = "com.opson.libraries.prompts.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.prompts)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.storage)
            // AppCache (onboarded, Health connected) and SessionTracker (one prompt a session).
            implementation(projects.libraries.opson)
            implementation(projects.libraries.identity)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.config)

            // What the prompts shipped here read: settings and the log, the modes, Health, reminders.
            implementation(projects.libraries.diary)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.health)
            implementation(projects.libraries.reminders)
            // Where they lead (IntegrationsRoute, RemindersRoute, CheckInRoute), and Today's card slot.
            implementation(projects.features.settings)
            implementation(projects.features.today)

            api(libs.jetbrains.navigation.compose)
            // Used directly by commonMain (collectAsStateWithLifecycle, viewModel(),
            // LocalLifecycleOwner, repeatOnLifecycle); see libraries/navigation/impl.
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.kotlinx.datetime)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }

        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.prompts)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.diary)
            implementation(libs.kotlinx.datetime)
            implementation(compose.components.resources)
        }
    }
}
