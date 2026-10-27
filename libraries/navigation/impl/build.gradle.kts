plugins {
    id("opson.compose.multiplatform")
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.opson.libraries.navigation.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.core)
            implementation(projects.libraries.navigation)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.resources)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.opson)
            // Session-expired recovery signs the dead session out.
            implementation(projects.libraries.identity)
            // `:features:onboarding` api carries `OnboardingRoute`, whose
            // sign-in step is where the session-expired screen lands.
            implementation(projects.features.onboarding)
            api(libs.jetbrains.navigation.compose)
            // Used directly by commonMain (collectAsStateWithLifecycle,
            // viewModel(), LocalLifecycleOwner). The platform compilations get
            // them transitively, the common metadata compilation does not, so
            // leaving them implicit fails `./gradlew build` while CI passes.
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.kotlinx.serialization.json)
        }

        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.navigation)
            implementation(libs.kotlinx.datetime)
            implementation(projects.libraries.identity)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.storage)
        }

        androidMain.dependencies {
            // FileProvider, for Router.shareFile.
            implementation(libs.androidx.core.ktx)
        }
    }
}