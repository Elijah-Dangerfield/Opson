// ComposablePreviewScanner deprecates its `common` scanner in favour of androidx previews, which
// this repo cannot use (see the version catalog note), so the warning is known and accepted.
@file:Suppress("DEPRECATION")

package com.opson.apps.screenshots

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureScreenRoboImage
import com.opson.libraries.ui.NoScreenshot
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import sergio.sastre.composable.preview.scanner.common.CommonComposablePreviewScanner
import sergio.sastre.composable.preview.scanner.common.CommonPreviewInfo
import sergio.sastre.composable.preview.scanner.core.preview.ComposablePreview
import java.io.File

/**
 * Renders every `@Preview` in the project to `<screen id>.png`.
 *
 * Driven by Roborazzi's Gradle tasks, not by a plain unit-test run (the build file skips it
 * otherwise): `recordRoborazziDebug` writes the PNGs, `verifyRoborazziDebug` fails on any pixel
 * change against the last recording, `compareRoborazziDebug` writes the diffs without failing.
 *
 * Fixed device: 390x844dp at xxhdpi (a 1170x2532 PNG), SDK 35, native graphics, and the clock held
 * still and stepped a fixed amount. That is what makes two runs of unchanged code produce the same
 * bytes, which both the diff and the design comparison depend on.
 *
 * A preview that throws is recorded as a failure in its result file and the run moves on: one
 * broken preview should not cost the designer every other screen. The exception is Roborazzi's own
 * verify failure, which is the point of `verifyRoborazziDebug` and is rethrown.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w390dp-h844dp-xxhdpi")
class PreviewScreenshotTest(private val screen: Screen) {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun render() {
        val png = File(outputDir, "${screen.id}.png")
        try {
            // Held clock: an infinite animation that ignores LocalInspectionMode would otherwise
            // keep the test waiting for an idle that never comes. Stepping a fixed amount lets
            // entry animations finish the same way on every run.
            compose.mainClock.autoAdvance = false
            compose.setContent {
                // What Android Studio's preview pane provides, and what the codebase already keys
                // preview behaviour off (dialogs inline, sheets expanded, loops frozen).
                CompositionLocalProvider(LocalInspectionMode provides true) {
                    screen.preview()
                }
            }
            compose.mainClock.advanceTimeBy(SETTLE_MILLIS)
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            writeResult(failure = t)
            return
        }

        try {
            // The whole window set, so a preview that opens a dialog shows the dialog too.
            captureScreenRoboImage(png)
            writeResult(failure = null)
        } catch (changed: AssertionError) {
            writeResult(failure = null, changed = changed.message)
            throw changed
        } catch (@Suppress("TooGenericExceptionCaught") t: Throwable) {
            writeResult(failure = t)
        }
    }

    private fun writeResult(failure: Throwable?, changed: String? = null) {
        val result = ScreenResult(
            id = screen.id,
            file = if (failure == null) "${screen.id}.png" else null,
            module = screen.module,
            preview = screen.qualifiedName,
            previewName = screen.previewName,
            failure = failure?.let { "${it::class.java.simpleName}: ${it.message.orEmpty()}".trim() },
            changed = changed,
        )
        resultsDir.mkdirs()
        File(resultsDir, "${screen.id}.json").writeText(result.toJson())
    }

    /** One preview to render. [toString] is the test name Gradle reports, so it is the id. */
    class Screen(
        val id: String,
        val module: String,
        val qualifiedName: String,
        val previewName: String?,
        val preview: ComposablePreview<CommonPreviewInfo>,
    ) {
        override fun toString() = id
    }

    companion object {
        /** Long enough for enter transitions and short delays; a fixed value so runs agree. */
        private const val SETTLE_MILLIS = 1_000L

        private val outputDir = File(requireProperty("screenshots.outputDir"))
        private val resultsDir = File(requireProperty("screenshots.resultsDir"))
        private val repoRoot = File(requireProperty("screenshots.repoRoot"))

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun screens(): List<Screen> {
            val previews = CommonComposablePreviewScanner()
                // The project's root package, which init_project rewrites with everything else.
                .scanPackageTrees("com.opson")
                .includePrivatePreviews()
                .excludeIfAnnotatedWithAnyOf(NoScreenshot::class.java)
                .getPreviews()
                .sortedWith(compareBy({ it.declaringClass }, { it.methodName }, { it.previewIndex ?: -1 }))
            // Zero is never a real answer for this project, and it is what a wrong package root
            // (say, a rename that missed the line above) looks like: a green run with nothing in it.
            check(previews.isNotEmpty()) {
                "No @Preview functions found under the scanned package. Is the package root above still this project's?"
            }

            val ids = ScreenIds()
            return previews.map { preview ->
                Screen(
                    id = ids.next(preview),
                    module = moduleOf(preview.declaringClass),
                    qualifiedName = "${preview.declaringClass}.${preview.methodName}",
                    previewName = preview.previewInfo.name.ifBlank { null },
                    preview = preview,
                )
            }
        }

        /**
         * The Gradle path of the module a class was compiled in, read off where its .class file
         * sits on the test classpath (`<repo>/features/home/impl/build/...` is
         * `:features:home:impl`). "unknown" rather than a guess when it is not under the repo.
         */
        private fun moduleOf(className: String): String {
            val resource = className.replace('.', '/') + ".class"
            val url = PreviewScreenshotTest::class.java.classLoader?.getResource(resource)
                ?: return "unknown"
            val path = url.path.removePrefix("file:").substringBefore("!")
            val root = repoRoot.absolutePath.trimEnd('/') + "/"
            if (!path.startsWith(root) || "/build/" !in path) return "unknown"
            val moduleDir = path.removePrefix(root).substringBefore("/build/")
            return ":" + moduleDir.replace('/', ':')
        }

        private fun requireProperty(name: String): String = requireNotNull(System.getProperty(name)) {
            "System property $name is not set. Run this through ./gradlew :apps:screenshots:recordRoborazziDebug."
        }
    }
}
