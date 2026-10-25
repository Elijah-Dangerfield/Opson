package com.opson.detekt

import com.intellij.openapi.util.Disposer
import dev.detekt.api.Finding
import dev.detekt.api.Rule
import org.jetbrains.kotlin.K1Deprecation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.LanguageVersionSettingsImpl
import org.jetbrains.kotlin.psi.KtPsiFactory

/**
 * Runs a rule over a Kotlin snippet and hands back what it reported.
 *
 * Stands in for `detekt-test`'s `lint()`, which cannot be resolved from Maven
 * Central for any published 2.0.0 alpha; see the note in `build.gradle.kts`.
 * For a rule that needs no type resolution this is the whole of what that helper
 * does: parse the source into a [org.jetbrains.kotlin.psi.KtFile] and call
 * [Rule.visitFile].
 *
 * The snippet is parsed, not compiled, so it does not need imports to resolve or
 * the composables it names to exist.
 */
fun Rule.findingsOn(source: String): List<Finding> = visitFile(
    Environment.psiFactory.createPhysicalFile("Snippet.kt", source.trimIndent()),
    LanguageVersionSettingsImpl.DEFAULT,
)

/**
 * One compiler environment for the whole test run. Building it is the expensive
 * part, and it holds no per-file state.
 */
private object Environment {
    @OptIn(K1Deprecation::class, CompilerConfiguration.Internals::class)
    val psiFactory: KtPsiFactory by lazy {
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MODULE_NAME, "detekt-rules-test")
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
        }
        val environment = KotlinCoreEnvironment.createForProduction(
            Disposer.newDisposable("detekt-rules-test"),
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )
        KtPsiFactory(environment.project)
    }
}
