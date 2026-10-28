package com.opson.util

import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget

internal fun Project.configureKotlinMultiplatform(
    configureIosTarget: KotlinNativeTarget.() -> Unit = {}
) {
    extensions.configure<KotlinMultiplatformExtension> {
        androidTarget()

        listOf(
            iosArm64(),
            iosSimulatorArm64()
        ).forEach { iosTarget ->
            iosTarget.configureIosTarget()
        }

        sourceSets.apply {
            androidMain.dependencies {
                // TODO(review): plain Kotlin libraries such as :libraries:core don't need this.
                // Move it to the Compose plugins once each module is checked for an implicit use
                // (docs/review/findings/build-config.md, finding 9).
                implementation(libs.androidx.activity.compose)
            }
            commonMain.dependencies {
                implementation(libs.kotlinx.coroutines.core)
            }
            commonTest.dependencies {
                implementation(libs.kotlin.test)
            }
        }
    }
}