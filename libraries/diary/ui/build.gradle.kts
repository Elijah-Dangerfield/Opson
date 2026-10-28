plugins {
    id("opson.compose.multiplatform")
}

android {
    namespace = "com.opson.libraries.diary.ui"

    // The composition tests launch a real ComponentActivity, which Robolectric only finds in the
    // merged manifest this flag hands it. See libraries/navigation/build.gradle.kts.
    testOptions.unitTests.isIncludeAndroidResources = true
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.libraries.diary)
            api(projects.libraries.nutrition.ui)
            implementation(projects.libraries.ui)
            implementation(projects.libraries.resources)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
        }
        commonTest.dependencies {
            // ExerciseLabelsTest compares StringResources.
            implementation(compose.components.resources)
        }
        // Composition tests (docs/practices/testing.md): which row keeps which remembered state
        // is a claim about composition, which only a real one can check.
        androidUnitTest.dependencies {
            implementation(projects.libraries.ui)
            implementation(libs.androidx.compose.uiTest.junit4)
            implementation(libs.androidx.compose.uiTest.manifest)
            implementation(libs.robolectric)
            implementation(libs.kotlin.testJunit)
        }
    }
}

// Robolectric's framework jar resolved through Gradle and run offline, as in
// libraries/navigation/build.gradle.kts, which explains why.
val robolectricAndroidAll: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies {
    robolectricAndroidAll(libs.robolectric.androidAll)
}

val stageRobolectricJars = tasks.register<Sync>("stageRobolectricJars") {
    from(robolectricAndroidAll)
    into(layout.buildDirectory.dir("robolectric-android-all"))
}

tasks.withType<Test>().configureEach {
    dependsOn(stageRobolectricJars)
    maxHeapSize = "2g"
    systemProperty("robolectric.offline", "true")
    systemProperty(
        "robolectric.dependency.dir",
        layout.buildDirectory.dir("robolectric-android-all").get().asFile.absolutePath,
    )
}
