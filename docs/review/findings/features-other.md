# Findings: features-other (`features/home/`), round two

Feedback, bug reports and the template's leftover Home view model. All 15 files were read and
the unslop-code scanner run over the slice. Each finding names the place, the rule from
[PRACTICES.md](../PRACTICES.md), what breaks, and its fate.

## 1. A double tap on Send sent twice and closed two screens: fixed

- **Where:** `feedback/FeedbackViewModel.kt:30`, `bugreport/BugReportViewModel.kt:39` (before:
  `if (current.isSubmitting) return`).
- **Rule:** §2 "Errors at the edges"; the rubric's opening note that a race needing two taps in 50 ms is
  worth fixing when the fix is small.
- **Failure:** `SEAViewModel` runs actions one at a time, so `isSubmitting` is always false again
  by the time a queued second `Submit` is handled. Two taps inside one frame (or the keyboard's
  Send plus the button) both reach the view model before the button redraws as disabled. The
  second sends the report again, bumps `feedbacksGiven` / `bugsReported` again, shows the thanks
  twice, and its `Sent` event calls `router.goBack()` a second time, which pops Support (or the
  screen under the bug report) as well. Round one's "a send in flight ignores another tap" did
  not hold.
- **Fix:** a sent page records `isSent` and ignores further `Submit`s; `canSubmit` moved into the
  state so the screen and the view model agree. `fix(home): a double tap on Send sends feedback
  once and closes one screen`, with `aDoubleTapOnSend_sendsOnceAndClosesOnce` and its bug report
  twin (both failed before the fix), plus a test for the blank-message line.
- **Left as is:** if the person backgrounds the app mid-send for over five seconds, the `Sent`
  event expires and the page stays open. Its button reads Sent (second review, item 1), and Back
  still works.

## 2. "Captured details" showed context the report never carried: fixed

- **Where:** `bugreport/BugReportViewModel.kt:45`, `feedback/FeedbackRepository.kt` (before: only
  `logId` and `errorCode` were passed on).
- **Rule:** §1 "Code that reads true"; §10 error states tell the truth.
- **Failure:** the bug report page lists the error the person was looking at (the error screen's
  subtitle, or "Triggered via shake") under "Captured details", but only the error code and log
  id reached Sentry. Triage couldn't tell which screen failed or that a report came from a shake.
- **Fix:** the repository takes `contextMessage` and leads the report body with
  `Context: …`. `fix(home): a bug report sends the context its page says it captured`, with
  `FeedbackRepositoryImplTest.aBugReport_carriesTheContextThePageShowed` and the view model test
  now asserting the context is passed.

## 3. Feedback was captured on the main thread: fixed

- **Where:** `feedback/FeedbackRepository.kt:45`, reached from the view models on
  `viewModelScope` (Main).
- **Rule:** §2 "Right dispatcher … Never block main".
- **Failure:** `Telemetry.captureUserFeedback` is synchronous. It snapshots the session log ring
  buffer (up to 500 lines of up to 1,000 characters), encodes it into an attachment and builds
  the Sentry carrier event and user feedback, all on the frame the person tapped Send.
- **Fix:** the call runs in `withContext(dispatchers.io)`, with `Catching` inside so cancellation
  still propagates. `perf(home): feedback is captured off the main thread`, with
  `theReportIsCapturedOnTheIoDispatcher` (fails when the call moves to another dispatcher).

## 4. A failed send wasn't announced to screen readers: fixed

- **Where:** `feedback/FeedbackScreen.kt:134`, `bugreport/BugReportScreen.kt:167`.
- **Rule:** §8 "live regions for status changes".
- **Failure:** "That didn't send. Try again in a moment." appears under the field after Send has
  cleared focus. TalkBack and VoiceOver said nothing, so a blind user heard the button come back
  and had no idea why.
- **Fix:** the line is a polite live region, as the error lines in onboarding, weight and voice
  logging are. `fix(home): a screen reader announces when feedback didn't send`. No test: the
  change is one semantics modifier, and this module has no Compose UI test setup (Robolectric
  plus the offline android-all jar, as `:libraries:ui` has) to hang one on.

## 5. An unused Material 3 dependency: fixed

- **Where:** `features/home/impl/build.gradle.kts:25`.
- **Rule:** §1 "No dead code".
- **Failure:** nothing in the module imports `androidx.compose.material3`.
- **Fix:** `build(home): drop the unused Material 3 dependency`.

## 6. "Triggered via shake" is hard-coded English on a user-facing card: deferred (cross-slice)

- **Where:** `apps/compose/src/commonMain/kotlin/com/opson/ShakeDialogEntryPoint.kt:48`.
- **Rule:** checklist "All strings are resources, not literals"; §1 "Code that reads true".
- **Failure:** a shake report shows "Triggered via shake" under "Captured details", a developer's
  note in the person's face, and it can't be translated.
- **Exact change:** add `<string name="bug_report_context_shake">You shook your phone.</string>`
  to `libraries/resources/src/commonMain/composeResources/values/strings.xml`; in
  `ShakeDialogEntryPoint`, inside the `dialog<ShakeDialogRoute>` content, read
  `val shakeContext = stringResource(Res.string.bug_report_context_shake)` and navigate with
  `BugReportRoute(contextMessage = shakeContext)`. Finding 2 then sends that line with the report.

## Not a problem

- **Round one's fixes hold.** Failures keep the text and say so (finding 1 there); the pages are
  ink, not red; the route module declares only navigation. Round one's deferred finding 4 has
  landed: `Telemetry.captureUserFeedback` returns `Boolean`, a dropped report becomes
  `FeedbackNotSent`, and `FeedbackRepositoryImplTest` covers both.
- **`HomeViewModel` has no screen.** Its KDoc is still true: `TestClient.homeVm()` and
  `HarnessSmokeTest` in `:apps:integration` and the harness here use it.
- **Typed text is lost on process death.** `SEAViewModel.onCleared` documents the codebase's
  policy (restoration only for Bundle-able state), and two 200-character pages don't justify an
  exception.
- **No `:apps:integration` journey.** Feedback goes to Sentry, not to `:apps:server`, so nothing
  here crosses our wire.
- **Leak test.** Neither page shows a number.
- **`commonTest` re-declares `commonMain` dependencies.** It looks redundant but isn't: with them
  removed, `compileDebugUnitTestKotlinAndroid` can't resolve `Profile` (tried).
- **Per-file `@OptIn(ExperimentalTime::class)` in the test harness.** The convention plugin opts
  in module-wide, so these are redundant, but about a hundred files across the repo do the same.
- **Scanner hits.** All ten are low: `collectAsStateWithLifecycle`, the factory name, test names.

## Second review

A second reviewer read every commit on this branch for regressions it introduced, reverting each
fix locally to check its test, and flagged six things. Fates:

1. **A sent page could be left with a dead Send button (8463bfc2): real, fixed.** The double-tap
   guard greys Send out once a report is sent. If the `Sent` event expires while the app is in the
   background (the five seconds of `SEAViewModel`), the page stays open showing the person's text
   and a disabled Send with no reason, which reads as a failure and invites retyping the report.
   Before the branch the button came back and a second tap sent a duplicate, so this is a trade
   rather than a straight loss, but the dead button was new. Fixed in "fix(home): a sent page says
   Sent instead of a dead Send button": both states expose `sendLabel` (Sent, Sending, Send) and the
   screens show it. `aSentPage_saysSentOnItsDisabledButton` and
   `aSentBugReport_saysSentOnItsDisabledButton` fail when a sent page falls back to Send. The
   commit's own tests hold: with `|| current.isSent` removed, both double-tap tests fail.
2. **The context line in the report body (c2044adc): not a problem.** With the context dropped,
   `aBugReport_carriesTheContextThePageShowed` and the view model's context assertion fail. The
   context is the error screen's subtitle or the shake line, never typed data, and the `Context:`
   label only reaches Sentry, so it needs no translation. `AppTelemetry` now sees context plus
   message as its payload, which changes its `payload_length` log extra and its blank check, but
   nothing reads that extra and both view models refuse a blank message first.
3. **Capturing feedback on `dispatchers.io` (3545ff83): not a problem.** Without `withContext`,
   `theReportIsCapturedOnTheIoDispatcher` fails. The log snapshot is taken under
   `LogRingBuffer`'s lock, the Sentry SDK is thread-safe, cancellation still propagates from
   `withContext`, and `DefaultDispatcherProvider` is bound in `AppScope`. Nothing else constructs
   `FeedbackRepositoryImpl`.
4. **The failure line's live region (c3ed0333): not a regression, untested.** The modifier matches
   the error lines in `AuthScreens`, `WeightScreen` and `DescribeVoiceViews`. Nobody checked on a
   device that TalkBack and VoiceOver announce a line that appears rather than one whose text
   changes, so if they don't, those screens share the problem. There is still no test, for the
   reason finding 4 gives.
5. **Dropping Material 3 (de8a1333): not a problem.** Nothing under `features/home` imports it, and
   the module compiles and its tests pass without it.
6. **The docs commit (f3da4327): not a problem.** `LEDGER.md` changes only the features-other
   section, and the findings' claims hold against the code (the 500-line, 1,000-character buffer,
   the neighbouring live regions, the five-second event expiry). Finding 1's "Left as is" note is
   updated for item 1. No commit edits a file outside the slice except the new `common_sent`
   string, which the brief allows.

## Product notes

- **With crash reporting off, feedback always fails.** The page now says "That didn't send. Try
  again in a moment.", and trying again never helps in a build without a Sentry DSN. Either hide
  the Support row's "Send feedback" when Sentry is off, or say feedback isn't available in this
  build. Fairly sure this will confuse whoever tests a dev build.
- **The bug report page could take a screenshot.** `Telemetry.captureUserFeedback` already
  accepts up to three, and a shake report about a broken screen is the case where a picture
  beats a sentence. Moderately sure it's worth it.
- **The two caps differ for no stated reason** (200 for feedback, 180 for bug reports). Pick one;
  round one's note that both are short still stands. Not sure what 180 was protecting.

## Deferred cross-slice items: fates (2026-10-28)

Applied on branch `worktree-agent-a89d6bb1fb957d17a` once every slice was on main.

- Finding 6: applied in b96767d4.
