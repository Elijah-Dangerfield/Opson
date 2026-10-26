plugins {
    id("opson.kotlin.multiplatform")
}

android {
    namespace = "com.opson.libraries.nutrition"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.datetime)
            // `NumbersChange` and `SummaryRingMode` are persisted and passed through the pause
            // sheet, so their serializers are part of this module's API.
            api(libs.kotlinx.serialization.json)
        }
    }
}
