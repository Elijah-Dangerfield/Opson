package com.opson.baselineprofile

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.opson.baselineprofile.BenchmarkJourney.CONTINUE
import com.opson.baselineprofile.BenchmarkJourney.CONTINUE_AS_GUEST
import com.opson.baselineprofile.BenchmarkJourney.MESSAGE
import com.opson.baselineprofile.BenchmarkJourney.REPORT_A_BUG
import com.opson.baselineprofile.BenchmarkJourney.SEND_FEEDBACK
import com.opson.baselineprofile.BenchmarkJourney.ciAnyOf
import com.opson.baselineprofile.BenchmarkJourney.ciText
import com.opson.baselineprofile.BenchmarkJourney.describeScreen
import com.opson.baselineprofile.BenchmarkJourney.launchIntent
import com.opson.baselineprofile.BenchmarkJourney.tapMatching
import com.opson.baselineprofile.BenchmarkJourney.tapRequired
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
 * Walking launch, onboarding, Home and two pushed routes exercises all three:
 * the graph on launch, routes on every navigation, models on every persisted
 * read and server call. This is also the reason the profile journey should keep
 * hitting a real backend — stub it and this test stops testing what it was
 * written for.
 *
 * ```
 * ./gradlew :apps:baselineprofile:pixel6Api34BenchmarkReleaseAndroidTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class MinifiedReleaseSmokeTest {

    private val device: UiDevice =
        UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    @Test
    fun theMinifiedAppReachesHomeAndNavigates() {
        device.pressHome()
        InstrumentationRegistry.getInstrumentation().context.startActivity(
            launchIntent().addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK,
            ),
        )

        // Walk onboarding if it is showing. Adaptive for the same reason as the
        // generators: a device that is already onboarted lands straight on Home.
        val anyOnboardingCta = ciAnyOf(CONTINUE_AS_GUEST, CONTINUE)
        repeat(BenchmarkJourney.ONBOARDING_MAX_STEPS) {
            if (device.hasObject(ciText(SEND_FEEDBACK))) return@repeat
            device.tapMatching(anyOnboardingCta, ONBOARDING_STEP_TIMEOUT_MS)
        }

        // Reaching Home means the DI graph built, persisted app state
        // deserialized through its generated serializer, and the start
        // destination resolved — the first things R8 could have broken.
        check(device.wait(Until.hasObject(ciText(SEND_FEEDBACK)), LAUNCH_TIMEOUT_MS) == true) {
            "Never reached Home. " + device.describeScreen()
        }

        // Each push resolves a @Serializable route by type. If R8 renamed one,
        // this fails with an argument error rather than a missing class, which
        // is the failure mode hardest to attribute in the wild.
        device.tapRequired(SEND_FEEDBACK)
        check(device.wait(Until.hasObject(ciText(MESSAGE)), SCREEN_TIMEOUT_MS) == true) {
            "Navigated to feedback but the form never rendered. " + device.describeScreen()
        }
        device.pressBack()

        device.tapRequired(REPORT_A_BUG)
        check(device.wait(Until.hasObject(ciText(MESSAGE)), SCREEN_TIMEOUT_MS) == true) {
            "Navigated to bug report but the form never rendered. " + device.describeScreen()
        }
        device.pressBack()

        check(device.wait(Until.hasObject(ciText(SEND_FEEDBACK)), SCREEN_TIMEOUT_MS) == true) {
            "Popping back to Home failed. " + device.describeScreen()
        }
    }

    private companion object {
        const val LAUNCH_TIMEOUT_MS = 30_000L
        const val SCREEN_TIMEOUT_MS = 15_000L
        const val ONBOARDING_STEP_TIMEOUT_MS = 15_000L
    }
}
