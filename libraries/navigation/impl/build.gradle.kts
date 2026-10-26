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
            // Session-expired recovery reads AuthState + mints guest sessions.
            implementation(projects.libraries.identity)
            // `:features:onboarding` api carries `SignInRoute`, where the
            // session-expired screen's "sign in again" lands.
            implementation(projects.features.onboarding)
            // `:features:today` api carries `TodayRoute`, where a guest's
            // "start fresh" lands once a new session is minted.
            implementation(projects.features.today)
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
        }

        androidMain.dependencies {
            // FileProvider, for Router.shareFile.
            implementation(libs.androidx.core.ktx)
        }
    }
}