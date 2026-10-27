plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.foods.impl"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.foods)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.networking)
            implementation(projects.libraries.opson.storage)

            implementation(libs.kotlinx.serialization.json)
            implementation(libs.ktor.client.core)
        }
        commonTest.dependencies {
            implementation(projects.libraries.foods)
            implementation(projects.libraries.diary)
            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines.testing)
            implementation(projects.libraries.networking)
            implementation(projects.libraries.opson.storage)
            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.mock)
            implementation(libs.ktor.client.contentNegotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
    }
}
