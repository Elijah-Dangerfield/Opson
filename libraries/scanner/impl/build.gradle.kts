plugins {
    id("opson.compose.multiplatform")
}

android {
    namespace = "com.opson.libraries.scanner.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.scanner)
            implementation(projects.libraries.core)
            // ActivityProvider, for the Android permission rationale check.
            implementation(projects.libraries.opson)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
        }

        androidMain.dependencies {
            // CameraX for the preview and frames, ML Kit's bundled model to read them.
            // See docs/plans/screens/barcode.md "Scanner choices".
            implementation(libs.androidx.camera.camera2)
            implementation(libs.androidx.camera.lifecycle)
            implementation(libs.androidx.camera.view)
            implementation(libs.mlkit.barcode.scanning)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.core.ktx)
        }
    }
}
