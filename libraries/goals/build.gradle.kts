plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.goals"
}

// Goals (docs/plans/goals-v2.md): the repository every screen reads a goal's status, days, week
// and projection from. The pure engine is in :libraries:nutrition, the stored goals in
// :libraries:diary's Settings.
kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.libraries.diary)
            api(projects.libraries.nutrition)
            api(libs.kotlinx.datetime)
            api(libs.kotlinx.coroutines.core)
        }
    }
}
