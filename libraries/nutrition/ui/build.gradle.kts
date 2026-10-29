plugins {
    id("opson.compose.multiplatform")
}

android {
    namespace = "com.opson.libraries.nutrition.ui"
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            api(projects.libraries.nutrition)
            implementation(projects.libraries.ui)
            // strings_nutrition.xml, read into NutritionWords.
            implementation(projects.libraries.resources)
        }
        androidUnitTest.dependencies {
            implementation(projects.libraries.resources)
            implementation(libs.kotlin.testJunit)
        }
    }
}
