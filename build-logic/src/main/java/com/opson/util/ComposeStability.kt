package com.opson.util

import org.gradle.api.Project
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * Points the Compose compiler at the repo's `compose-stability.conf`. Without it, a parameter
 * typed `List`, `LocalDate` or a diary domain class reads as unstable, and a composable that
 * takes one recomposes on every state change even when the value is equal.
 */
internal fun Project.configureComposeStability() {
    extensions.findByType(ComposeCompilerGradlePluginExtension::class.java)
        ?.stabilityConfigurationFiles
        ?.add(rootProject.layout.projectDirectory.file("compose-stability.conf"))
}
