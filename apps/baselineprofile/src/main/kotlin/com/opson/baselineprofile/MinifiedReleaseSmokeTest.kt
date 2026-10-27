package com.opson.baselineprofile

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.opson.baselineprofile.BenchmarkJourney.launchIntent
import com.opson.baselineprofile.BenchmarkJourney.openAndCloseLogger
import com.opson.baselineprofile.BenchmarkJourney.reachToday
import com.opson.baselineprofile.BenchmarkJourney.visitTabs
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Drives the **minified** app through a real journey. This is the R8 smoke test.
 *
 * ## Why it is not a BaselineProfileRule test
 *
 * `BaselineProfileRule` refuses to run against a minified variant — it needs
 * unobfuscated output to write a profile against — so pointing it at
 * `benchmarkRelease` reports SKIPPED, which reads like a pass. A plain
 * instrumented test runs where the generators cannot. When checking this passed,
 * confirm the result XML says the test RAN; a skip is not a pass.
 *
 * ## Why it exists
 *
 * A release APK building cleanly says nothing about whether it works. R8 breaks
 * what is resolved *by name at runtime*, and a KMP app of this shape has three
 * of those: the `@Serializable` models (serializers are generated and reached
 * only through a `Companion`), the `@Serializable` navigation routes (renaming
 * one breaks type-safe nav with an argument error, not a missing-class error),
 * and the generated DI graph.
 *
 * Walking launch, onboarding, every tab and the logger sheet exercises all three:
 * the graph on launch, routes on every navigation, models on every persisted
 * read and server call. This is also the reason the profile journey should keep
 * hitting a real backend — stub it and this test stops testing what it was
 * written for.
 *
 * ```
 * ./gradlew :apps:baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.benchmarkEmail=... \
 *     -Pandroid.testInstrumentationRunnerArguments.benchmarkPassword=...
 * ```
 *
 * The account is the benchmark one described in [BenchmarkJourney].
 */
@RunWith(AndroidJUnit4::class)
class MinifiedReleaseSmokeTest {

    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun theMinifiedAppReachesTheTabsAndNavigates() {
        device.pressHome()
        InstrumentationRegistry.getInstrumentation().context.startActivity(
            launchIntent().addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )

        // Reaching Today means the DI graph built, sign-in went through,
        // persisted app state deserialized through its generated serializer, and
        // the start destination resolved: the first things R8 could have broken.
        // The same walk as the generators, sign-in included.
        device.reachToday()

        // Each tab switch and the logger resolve a @Serializable route by type.
        // If R8 renamed one, this fails with an argument error rather than a
        // missing class, which is the failure mode hardest to attribute in the wild.
        device.visitTabs()
        device.openAndCloseLogger()
    }
}
