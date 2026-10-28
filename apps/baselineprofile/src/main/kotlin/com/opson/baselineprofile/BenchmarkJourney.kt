package com.opson.baselineprofile

import android.content.Intent
import android.widget.EditText
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.platform.app.InstrumentationRegistry
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
 * ## The journey
 *
 * Onboarding to the tab shell, then every tab and the logger sheet. Keep two
 * things as the screens change:
 *
 * 1. **The adaptive structure.** [reachToday] walks whatever is on screen rather
 *    than replaying a fixed tap sequence. Onboarding gains and loses steps, and
 *    a hardcoded sequence breaks silently on every one of those changes.
 * 2. **[describeScreen].** A failure that says only "the thing I wanted was not
 *    there" cannot separate an R8 breakage from an error dialog, a spinner, or a
 *    step nobody knew existed. Those need different fixes and guessing between
 *    them costs a full emulator run each time.
 *
 * ## Signing in
 *
 * Accounts are required, so a fresh install stops at sign-in. The journey signs
 * in with email as an account that has already finished onboarding, so sign-in
 * lands straight on Today. The account comes in as instrumentation arguments
 * (`-Pandroid.testInstrumentationRunnerArguments.benchmarkEmail=…` and
 * `…benchmarkPassword=…`) and is never in the repo.
 *
 * Generation runs a **release** variant, which as built signs in to prod. The
 * account belongs in the dev project, so the profile variants need to point at
 * dev and offer email sign-in; until they do, the run stops at the sign-in step
 * with no "Continue with email" on screen (docs/review/findings/build-config.md).
 * Don't gate any of that on `BuildConfig.DEBUG`: it is false exactly here.
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
     * Walks whatever onboarding is on screen until the tab shell appears.
     *
     * Adaptive rather than a fixed tap sequence, and that is a correctness
     * choice: the app may show onboarding, may skip it because the device is
     * already signed in, and may gain steps later. A hardcoded sequence breaks
     * on all three.
     */
    fun MacrobenchmarkScope.reachHome() = device.reachToday()

    /**
     * [reachHome] for a plain instrumented test, which has a device but no benchmark scope.
     * [todayTimeoutMs] is the last wait for Today; a cold minified start needs longer.
     */
    fun UiDevice.reachToday(todayTimeoutMs: Long = FIRST_SCREEN_TIMEOUT_MS) {
        val anyOnboardingCta = ciAnyOf(GET_STARTED, CONTINUE_WITH_EMAIL, SETTING_UP)
        repeat(ONBOARDING_MAX_STEPS) {
            if (hasObject(todayScreen())) return
            if (hasObject(textField())) signInWithEmail() else tapMatching(anyOnboardingCta, ONBOARDING_STEP_TIMEOUT_MS)
        }
        check(wait(Until.hasObject(todayScreen()), todayTimeoutMs) == true) {
            "Never reached Today. " + describeScreen()
        }
    }

    /** Fills the email form with the benchmark account and waits for Today. */
    private fun UiDevice.signInWithEmail() {
        val (email, password) = benchmarkAccount()
        val fields = findObjects(textField())
        check(fields.size >= 2) { "Expected an email and a password field. " + describeScreen() }
        fields[0].text = email
        fields[1].text = password
        tapRequired(SIGN_IN)
        check(wait(Until.hasObject(todayScreen()), SIGN_IN_TIMEOUT_MS) == true) {
            "Signed in, but Today never appeared. Has the benchmark account finished onboarding? " + describeScreen()
        }
    }

    private fun benchmarkAccount(): Pair<String, String> {
        val arguments = InstrumentationRegistry.getArguments()
        val email = arguments.getString(EMAIL_ARGUMENT)
        val password = arguments.getString(PASSWORD_ARGUMENT)
        check(!email.isNullOrBlank() && !password.isNullOrBlank()) {
            "The app is asking for an account and none was passed. Run with " +
                "-Pandroid.testInstrumentationRunnerArguments.$EMAIL_ARGUMENT=... and .$PASSWORD_ARGUMENT=... " +
                "(a dev-project account that has finished onboarding)."
        }
        return email to password
    }

    private fun textField(): BySelector = By.clazz(EditText::class.java)

    /**
     * Every tab, then the logger sheet and back.
     *
     * Deeper than the startup path on purpose: this covers the first run of
     * navigation, each tab's ViewModel and the sheet, which is where the app
     * spends its time after launch and which the startup profile must NOT carry.
     */
    fun MacrobenchmarkScope.visitDetailScreens() {
        device.visitTabs()
        device.openAndCloseLogger()
    }

    /** Switches through every tab, checking each one's own screen rendered. */
    fun UiDevice.visitTabs() {
        listOf(
            TRENDS to By.desc(TRENDS_SETTINGS),
            FOODS to ciText(FOODS_CREATE_MEAL),
            MORE to ciText(MORE_MODES),
            TODAY to todayScreen(),
        ).forEach { (tab, screen) ->
            tapRequired(tab)
            check(wait(Until.hasObject(screen), SCREEN_TIMEOUT_MS) == true) {
                "Tapped the $tab tab but its screen never appeared. " + describeScreen()
            }
        }
    }

    /** Opens the logger from the center + and backs out of it to Today. */
    fun UiDevice.openAndCloseLogger() {
        val add = wait(Until.findObject(By.desc(LOG_FOOD)), SCREEN_TIMEOUT_MS)
        checkNotNull(add) { "No \"$LOG_FOOD\" button in the bottom bar. " + describeScreen() }
        add.click()
        check(wait(Until.hasObject(ciText(LOGGER_SEARCH)), SCREEN_TIMEOUT_MS) == true) {
            "Tapped + but the logger never appeared. " + describeScreen()
        }
        pressBack()
        check(wait(Until.gone(ciText(LOGGER_SEARCH)), SCREEN_TIMEOUT_MS) == true) {
            "Back did not close the logger. " + describeScreen()
        }
        check(hasObject(todayScreen())) {
            "Closing the logger did not leave Today showing. " + describeScreen()
        }
    }

    // Verbatim from the screens they appear on. The root build's
    // verifyBenchmarkJourneyCopy fails `check` when one of these leaves the
    // app's strings, so a copy change surfaces on the PR rather than in next
    // month's profile run.
    const val GET_STARTED = "Get started"
    const val CONTINUE_WITH_EMAIL = "Continue with email"
    const val SIGN_IN = "Sign in"
    const val SETTING_UP = "Setting things up…"
    const val TODAY = "Today"
    const val TRENDS = "Trends"
    const val FOODS = "Foods"
    const val MORE = "More"
    const val LOG_FOOD = "Log food"

    // One thing each screen always shows, in every mode. Today's day label reads "Today" like
    // its tab, so Today and Trends are found by an icon button's spoken label instead.
    const val TODAY_DAY_MENU = "More for this day"
    const val TRENDS_SETTINGS = "Trends settings"
    const val FOODS_CREATE_MEAL = "Create a meal"
    const val MORE_MODES = "Modes and numbers"
    const val LOGGER_SEARCH = "Search foods, dishes, restaurants"

    /** Today's day menu button, present on every day in every mode. */
    fun todayScreen(): BySelector = By.desc(TODAY_DAY_MENU)

    const val FIRST_SCREEN_TIMEOUT_MS = 20_000L

    // Sign-in is a network round trip to Supabase and then the server, on a cold
    // emulator, before Today can draw.
    const val SIGN_IN_TIMEOUT_MS = 30_000L
    const val SCREEN_TIMEOUT_MS = 15_000L
    const val ONBOARDING_STEP_TIMEOUT_MS = 15_000L
    const val ONBOARDING_MAX_STEPS = 8

    const val EMAIL_ARGUMENT = "benchmarkEmail"
    const val PASSWORD_ARGUMENT = "benchmarkPassword"

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
