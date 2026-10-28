plugins {
    id("opson.compose.multiplatform")
}

android {
    namespace = "com.opson.libraries.ui"

    // The composition tests launch a real ComponentActivity, which Robolectric only finds in the
    // merged manifest this flag hands it. See libraries/navigation/build.gradle.kts.
    testOptions.unitTests.isIncludeAndroidResources = true
}

kotlin {
    sourceSets {

        androidMain.dependencies {
            api(compose.preview)
            api(compose.uiTooling)
        }

        commonMain.dependencies {
            implementation(projects.libraries.core)
            // The copy, fonts and brand mark the components draw themselves (spoken labels,
            // the shake dialog, the wordmark and the bowl), so `VerifyStrings` has a resource
            // to point at.
            implementation(projects.libraries.resources)
            // `RelativeDay` and `MonthGrid` take `LocalDate`, so it is part of this module's API.
            api(libs.kotlinx.datetime)

            api(compose.ui)
            api(compose.uiUtil)
            api(compose.runtime)
            api(compose.foundation)
            api(compose.material3)
            api(compose.components.resources)
            api(compose.components.uiToolingPreview)
            api(compose.materialIconsExtended)
            api(libs.compose.backhandler)
        }

        // Composition tests, the recipe in docs/practices/testing.md: what a component puts in
        // the semantics tree is a claim about composition, which only a real one can check.
        androidUnitTest.dependencies {
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
