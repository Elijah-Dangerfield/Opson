plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.pdf.impl"

    // The smoke test draws a real PDF under Robolectric's native graphics.
    testOptions.unitTests.isIncludeAndroidResources = true
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(projects.libraries.pdf)
        }

        commonTest.dependencies {
            implementation(projects.libraries.pdf)
        }

        androidUnitTest.dependencies {
            implementation(libs.robolectric)
            implementation(libs.kotlin.testJunit)
        }
    }
}

// Same reasoning as libraries/navigation/build.gradle.kts: resolve Robolectric's framework jar
// through Gradle and run offline against the staged copy, rather than letting Robolectric fetch
// ~200MB into ~/.m2 at test time.
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
    systemProperty("robolectric.offline", "true")
    systemProperty(
        "robolectric.dependency.dir",
        layout.buildDirectory.dir("robolectric-android-all").get().asFile.absolutePath,
    )
}
