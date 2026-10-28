plugins {
    // Glance widgets are composables, so the Android half needs the Compose compiler.
    id("opson.compose.multiplatform")
}

moduleConfig {
    optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
}

android {
    namespace = "com.opson.libraries.widgets.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.widgets)
            // Burned and release.goalsAndEnergy, for a set deficit or surplus's target.
            implementation(projects.libraries.health)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.config)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            // Signed out, the widget is cleared rather than drawn from the default settings.
            implementation(projects.libraries.identity)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.resources)
            implementation(compose.runtime)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.datetime)
        }
        commonTest.dependencies {
            implementation(projects.libraries.health)
            implementation(projects.libraries.widgets)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.config)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.identity)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.resources)
            implementation(compose.components.resources)
            implementation(libs.kotlinx.datetime)
        }
        androidMain.dependencies {
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.glance.appwidget)
            // Glance's own @Preview for the widget layouts. Previews only; nothing at runtime.
            implementation(libs.androidx.glance.preview)
            implementation(libs.androidx.glance.appwidget.preview)
        }
    }
}
