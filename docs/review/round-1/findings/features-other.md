# Findings: features-other (`features/home/`)

Feedback, bug reports and the template's leftover Home view model. Every file was read and the
unslop-code scanner run over it. Each finding names the place, the rule from
[PRACTICES.md](../PRACTICES.md), what breaks, and its fate.

## 1. A failed feedback or bug report was thanked, counted and closed: fixed

- **Where:** `feedback/FeedbackViewModel.kt:37`, `bugreport/BugReportViewModel.kt` (before:
  `repository.submitFeedback(...).eitherWay { … }`).
- **Rule:** §2 "Errors at the edges: expected failures are values … handled at the ViewModel".
- **Failure:** `eitherWay` runs its block on success and failure alike, so a send that threw
  still bumped `feedbacksGiven` / `bugsReported`, showed "Got it. Thank you!" and closed the
  page, throwing away what the person typed. A second tap while sending also sent twice.
- **Fix:** only a sent message counts, thanks and closes; a failure keeps the text and shows
  "That didn't send. Try again in a moment." A send in flight ignores another tap. The copy that
  was hard-coded in both view models moved to `strings.xml` (the UI review flagged the same
  lines), navigation became events the entry point handles, and the repository uses `Catching`
  in place of `runCatching`. `fix(home): a feedback or bug report that fails to send says so`,
  with `FeedbackViewModelTest` (five cases; neither view model had a test).

## 2. Red on the feedback pages: fixed

- **Where:** `FeedbackScreen.kt`, `BugReportScreen.kt` (`AppTheme.colors.danger`, Material's
  `#B3261E`).
- **Rule:** design sync 2 global note "No red anywhere"; checklist "Colours … come from the
  theme tokens" and match the rest of the app.
- **Failure:** the character counter turned red at its limit, and the error line and the
  captured error message were red.
- **Fix:** ink. `fix(home): feedback and bug report pages drop the red`.

## 3. The route module declared UI dependencies it never used: fixed

- **Where:** `features/home/build.gradle.kts`.
- **Rule:** §1 "No dead code".
- **Failure:** the module holds one route class but declared core, ui and five Compose
  artifacts, with mixed indentation.
- **Fix:** `build(home): the route module depends only on navigation`.

## 4. Feedback "sent" with Sentry off is dropped and thanked anyway: deferred (cross-slice)

- **Where:** `libraries/opson/impl/.../AppTelemetry.kt:185-191` (`captureUserFeedback` returns
  `Unit` after logging "Sentry disabled, feedback dropped"), reached from
  `feedback/FeedbackRepository.kt`.
- **Rule:** §2 "Errors at the edges".
- **Failure:** with Sentry disabled for the environment, or a blank payload, the message goes
  nowhere and the page now (correctly, by its own lights) thanks the person, because the
  repository only sees an exception as failure.
- **Exact change:** make `Telemetry.captureUserFeedback` return `Boolean` (sent or not) and have
  `FeedbackRepositoryImpl` return `Result.failure` when it is false; finding 1's failure line then
  covers it.

## Not a problem

- **`HomeViewModel` has no screen.** Its KDoc says so and why: it is the worked example for the
  scenario harness here and for `TestClient` in `:apps:integration`, both of which use it.
- **"No visible counter" (UI review note).** Both pages draw `n/200` (`n/180` for bug reports)
  under the field at all times, and the field stops at the cap. The counter now changes from
  muted to ink at the limit instead of to red.
- **Two near-identical screens.** Feedback and Bug report share layout line for line. A shared
  composable would belong in `:libraries:ui`, and two template pages that rarely change don't
  justify it.
- **Scanner hits.** All ten are `collectAsStateWithLifecycle`, the factory name and test names.

## Product notes

- **Feedback and bug reports go to Sentry only.** With Sentry off (dev, or a future opt-out)
  nothing is sent and nothing says so (finding 4). If feedback matters for 0.2, a server
  endpoint would make it independent of crash reporting. Fairly sure it matters.
- **Feedback is capped at 200 characters.** Short for a bug someone cares about; 1,000 would
  cost nothing. Not sure what the cap was protecting.
