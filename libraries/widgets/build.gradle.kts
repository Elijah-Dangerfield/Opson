plugins {
    id("opson.compose.multiplatform")
    // WidgetSnapshot is JSON on disk, read by Glance and by the Swift extension.
    alias(libs.plugins.kotlinSerialization)
}

android {
    namespace = "com.opson.libraries.widgets"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            // The builder reads today's Day and Settings and resolves through the policy.
            api(projects.libraries.diary)
            api(projects.libraries.nutrition)
            // WidgetsEnabled is a FlagConfigValue: callers resolve it through config's API.
            api(projects.libraries.config)
            // WidgetCopy resolves StringResources, so they are part of the surface.
            api(projects.libraries.resources)
            api(libs.kotlinx.serialization.json)
            implementation(projects.libraries.core)
            // Meal labels and tones, and the palette's colours for the snapshot.
            implementation(projects.libraries.diary.ui)
            implementation(projects.libraries.ui)

            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }
        commonTest.dependencies {
            implementation(projects.libraries.core)
            implementation(libs.kotlinx.datetime)
        }
    }
}
