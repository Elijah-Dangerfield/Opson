package com.opson.baselineprofile

import android.content.Intent
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.BySelector
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern

/**
 * The journey the profile generators and the R8 smoke test all drive.
 *
 * Shared because the alternative is three copies of the same UiAutomator taps
 * drifting apart, and the first symptom of that drift is a profile that quietly
 * covers less while every assertion still passes.
 *
 * ## Replace this with your app's journey
 *
 * What is here walks the template's own shortest meaningful path: onboarding to
 * Home, then Home into a detail screen and back. Swap the constants and the two
 * journey functions for whatever your app's equivalent is. Keep two things:
 *
 * 1. **The adaptive structure.** [reachHome] walks whatever is on screen rather
 *    than replaying a fixed tap sequence. Onboarding gains and loses steps, and
 *    a hardcoded sequence breaks silently on every one of those changes.
 * 2. **[describeScreen].** A failure that says only "the thing I wanted was not
 *    there" cannot separate an R8 breakage from an error dialog, a spinner, or a
 *    step nobody knew existed. Those need different fixes and guessing between
 *    them costs a full emulator run each time.
 *
 * ## If you add a hook to skip onboarding
 *
 * Generation runs a **release** variant, so a benchmark hook gated on
 * `BuildConfig.DEBUG` is dead exactly where it is needed. Gate on the backend
 * environment instead — which is also what stops a generation run creating real
 * accounts in production. There is no such hook here yet, because this journey
 * walks onboarding for real and the template ships no backend to protect.
 *
 * Nothing on the launch path may be network-bound. An earlier downstream version
 * awaited sign-in inside `onCreate`, which put a network round trip on the main
 * thread: the activity never finished launching and the benchmark failed with a
 * blank screen. Split the local cache write (blocking, short timeout) from
 * anything remote (fire-and-forget).
 */
object BenchmarkJourney {

    const val PACKAGE = "com.opson"

    /** Launch intent that starts the app cold on its real first screen. */
    fun launchIntent(): Intent = Intent().apply {
        // By class name, not MAIN/LAUNCHER: once benchmark flags and extras are
        // attached, category-based intent resolution fails outright.
        setClassName(PACKAGE, "$PACKAGE.MainActivity")
    }

    /**
     * Walks whatever onboarding is on screen until Home appears.
     *
     * Adaptive rather than a fixed tap sequence, and that is a correctness
     * choice: the app may show onboarding, may skip it because the device is
     * already onboarded, and may gain steps later. A hardcoded sequence breaks
     * on all three.
     */
    fun MacrobenchmarkScope.reachHome() {
        val anyOnboardingCta = ciAnyOf(CONTINUE_AS_GUEST, CONTINUE, SETTING_UP)
        repeat(ONBOARDING_MAX_STEPS) {
            if (device.hasObject(ciText(SEND_FEEDBACK))) return
            device.tapMatching(anyOnboardingCta, ONBOARDING_STEP_TIMEOUT_MS)
        }
        check(device.wait(Until.hasObject(ciText(SEND_FEEDBACK)), FIRST_SCREEN_TIMEOUT_MS) == true) {
            "Never reached Home. " + device.describeScreen()
        }
    }

    /**
     * Home into each detail screen and back.
     *
     * Deeper than the startup path on purpose: this covers the first run of
     * navigation, the ViewModel and the form UI, which is where the app spends
     * its time after launch and which the startup profile must NOT carry.
     */
    fun MacrobenchmarkScope.visitDetailScreens() {
        device.tapRequired(SEND_FEEDBACK)
        check(device.wait(Until.hasObject(ciText(MESSAGE)), SCREEN_TIMEOUT_MS) == true) {
            "Tapped $SEND_FEEDBACK but the form never appeared. " + device.describeScreen()
        }
        device.pressBack()

        device.tapRequired(REPORT_A_BUG)
        check(device.wait(Until.hasObject(ciText(MESSAGE)), SCREEN_TIMEOUT_MS) == true) {
            "Tapped $REPORT_A_BUG but the form never appeared. " + device.describeScreen()
        }
        device.pressBack()

        check(device.wait(Until.hasObject(ciText(SEND_FEEDBACK)), SCREEN_TIMEOUT_MS) == true) {
            "Never got back to Home. " + device.describeScreen()
        }
    }

    // Verbatim from the screens they appear on. If one changes, these tests are
    // where it surfaces, which is the point.
    const val CONTINUE_AS_GUEST = "Continue as guest"
    const val CONTINUE = "Continue"
    const val SETTING_UP = "Setting things up…"
    const val SEND_FEEDBACK = "Send Feedback"
    const val REPORT_A_BUG = "Report a Bug"
    const val MESSAGE = "Message"

    const val FIRST_SCREEN_TIMEOUT_MS = 20_000L
    const val SCREEN_TIMEOUT_MS = 15_000L
    const val ONBOARDING_STEP_TIMEOUT_MS = 15_000L
    const val ONBOARDING_MAX_STEPS = 8

    private const val TAP_ATTEMPTS = 3
    private const val RETRY_FIND_MS = 500L
    private const val MAX_REPORTED_LINES = 25

    /**
     * A selector for [text] that ignores case.
     *
     * Not a nicety. The design system's button typography renders its label in
     * upper case, and UiAutomator reads the *rendered* text — so `By.text("Send
     * Feedback")` matches nothing while the button plainly says SEND FEEDBACK.
     * The whole journey failed on this, and the only reason it took one run
     * rather than several is that [describeScreen] printed what was actually on
     * screen. Match case-insensitively so a typography change cannot silently
     * break the profile.
     */
    fun ciText(text: String): BySelector =
        By.text(Pattern.compile(Pattern.quote(text), Pattern.CASE_INSENSITIVE))

    /** Selector matching any one of [options], ignoring case. */
    fun ciAnyOf(vararg options: String): BySelector = By.text(
        Pattern.compile(
            options.joinToString("|") { Pattern.quote(it) },
            Pattern.CASE_INSENSITIVE,
        ),
    )

    /** Taps a text element, reporting whether it was there. */
    fun UiDevice.tap(text: String, timeoutMs: Long): Boolean =
        tapMatching(ciText(text), timeoutMs)

    /**
     * Finds and taps, re-finding if the node goes stale in between.
     *
     * Every transition in this app is animated, and UiAutomator hands back a
     * handle to a node Compose may replace before the click lands — which throws
     * rather than missing. Only the first look pays the caller's timeout; a
     * retry means the node was there a frame ago, and re-paying the full wait on
     * every retry is what once turned a three-minute run into twenty-five.
     */
    fun UiDevice.tapMatching(selector: BySelector, timeoutMs: Long): Boolean {
        repeat(TAP_ATTEMPTS) { attempt ->
            waitForIdle()
            val budget = if (attempt == 0) timeoutMs else RETRY_FIND_MS
            val found = wait(Until.findObject(selector), budget) ?: return false
            try {
                found.click()
                waitForIdle()
                return true
            } catch (_: StaleObjectException) {
                // Recomposed between find and click. Go round again.
            }
        }
        return false
    }

    /** Taps something that must be there, failing the run loudly if it is not. */
    fun UiDevice.tapRequired(text: String, timeoutMs: Long = SCREEN_TIMEOUT_MS) {
        check(tap(text, timeoutMs)) {
            "Journey stalled: no \"$text\" after ${timeoutMs}ms. " + describeScreen()
        }
    }

    /**
     * Everything readable on screen when an assertion failed.
     *
     * Without it a failure says only "the thing I wanted was not there", which
     * cannot separate an R8 breakage from an error dialog, a spinner or a step
     * nobody knew existed. Those need different fixes, and guessing between them
     * costs a full emulator run each time.
     */
    fun UiDevice.describeScreen(): String {
        // `By.textContains("")` matches nothing, which is why this reported an
        // empty screen on every failure and told us less than no diagnostic at
        // all — it looked like evidence of a blank window. `.+` is the selector
        // that actually matches any non-empty text.
        val visible = findObjects(By.text(Pattern.compile(".+")))
            .mapNotNull { runCatchingStale { it.text?.trim() } }
            .filter { !it.isNullOrEmpty() }
            .distinct()
            .take(MAX_REPORTED_LINES)

        // Which app is actually in front. Distinguishes "our screen is wrong"
        // from "we are not even in the app any more" — a crash, a system
        // dialog, or a launch that never landed all look identical otherwise.
        val foreground = runCatchingStale { currentPackageName } ?: "unknown"

        // Deliberately ONE line. Gradle's console prints only the first line of
        // an assertion message, so a pretty multi-line dump is invisible exactly
        // when it is needed.
        val screen = if (visible.isEmpty()) {
            "<no readable text — blank, loading, or a native-canvas-only screen>"
        } else {
            visible.joinToString(" | ")
        }
        return "[foreground=$foreground] screen: $screen"
    }

    /** UiAutomator throws if a node vanishes mid-read; a diagnostic must not. */
    private fun <T> runCatchingStale(block: () -> T): T? =
        try {
            block()
        } catch (_: StaleObjectException) {
            null
        }
}
