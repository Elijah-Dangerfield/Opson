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
            // `:features:home` api carries `HomeRoute`, where a guest's
            // "start fresh" lands once a new session is minted.
            implementation(projects.features.home)
            api(libs.jetbrains.navigation.compose)
            implementation(libs.kotlinx.serialization.json)
        }
    }
}