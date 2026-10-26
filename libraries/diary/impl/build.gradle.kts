plugins {
    id("opson.kotlin.multiplatform")
}

moduleConfig {
    optIn("kotlinx.coroutines.ExperimentalCoroutinesApi")
}

android {
    namespace = "com.opson.libraries.diary.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.diary)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.networking)
            implementation(projects.libraries.identity)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.opson.storage)
            implementation(projects.libraries.storage)

            implementation(libs.kotlinx.datetime)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(projects.libraries.diary)
            implementation(projects.libraries.nutrition)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.networking)
            implementation(projects.libraries.identity)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.opson.storage)
            implementation(projects.libraries.storage)
        }
    }
}
