plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.review.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.review)
            implementation(projects.libraries.core)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.storage)
        }
        commonTest.dependencies {
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.review)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.storage)
        }

        androidMain.dependencies {
            implementation(libs.google.play.review)
            implementation(libs.google.play.review.ktx)
        }
    }
}
