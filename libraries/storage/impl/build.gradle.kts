plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.storage.impl"
}

moduleConfig.storage()

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.storage)

            implementation(projects.libraries.core)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.opson.storage)
            // Their Room entities and DAOs register in AppDatabase.
            implementation(projects.libraries.diary)
            implementation(projects.libraries.foods)
            implementation(libs.kotlinx.serialization.json)
        }
        commonTest.dependencies {
            implementation(projects.libraries.storage)
            implementation(projects.libraries.core)
            implementation(projects.libraries.opson)
            implementation(projects.libraries.opson.storage)
            implementation(projects.libraries.flowroutines)
            implementation(projects.libraries.flowroutines.testing)
        }
    }
}

tasks.matching { it.name.contains("kspCommonMainKotlinMetadata", ignoreCase = true) }
    .configureEach { enabled = false }