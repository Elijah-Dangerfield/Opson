package com.opson.util

import org.gradle.api.Project
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

/**
 * Writes the Compose compiler's own stability and skippability reports when
 * `-Popson.composeMetrics=true` is passed.
 *
 * Off by default because it slows every compile and the output is only useful
 * while someone is reading it. It is the only way to answer "is this composable
 * skippable" without guessing — a restartable-but-not-skippable composable
 * recomposes whenever its parent does, however unchanged its arguments, and
 * nothing about the source says so.
 */
internal fun Project.configureComposeMetrics() {
    if (providers.gradleProperty("opson.composeMetrics").orNull != "true") return

    extensions.findByType(ComposeCompilerGradlePluginExtension::class.java)?.apply {
        reportsDestination.set(layout.buildDirectory.dir("compose-reports"))
        metricsDestination.set(layout.buildDirectory.dir("compose-metrics"))
    }
}
