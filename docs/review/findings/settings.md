# Findings: settings, round two (`features/settings/`, `features/qa/`)

Every file under "## settings" in [LEDGER.md](../LEDGER.md) was read, and the unslop-code
scanner run over both modules (0 findings). Each finding names the place, the rule from
[PRACTICES.md](../PRACTICES.md), what breaks, and its fate. Round one's fixes
([round-1/findings/settings.md](../round-1/findings/settings.md)) were checked and still hold:
the shared `TimePickerDialog`, More's flag-gated `ScreenResumed`, the export's `flatMap`, the
Fasting and Integrations spinners, Targets' remembered text measure, and the `TODO(review)` for
the double pause sheet, still deferred to the router.

## 1. Logging out offline deleted the changes still in the outbox: fixed (routed, journeys audit)

- **Where:** `more/MoreViewModel.kt:115` (before: `logOut` called `auth.signOut()` straight
  away), meeting `UserScopedDaoCleaner` and `OutboxDao` as a `ClearableDao`
  (`libraries/diary/.../db/DiaryDaos.kt:206`).
- **Rule:** §3 "Offline first"; §10 "the error state tells the person what they can do".
- **Failure:** log three meals on the subway, so they sit in the outbox, then More › Log out.
  The user change wipes every `ClearableDao`, the outbox with it, while the dialog had just
  said "Your log stays in your account". After signing back in the meals are gone for good.
- **Fix:** `fix(settings): log out sends queued changes first and asks before wiping any it couldn't`.
  A signed-in log out now runs `DiarySync.syncNow()` first, capped at ten seconds (the sync
  carries on in the app scope). If it doesn't reach the account the dialog becomes
  `LogOutStep.Unsynced`: "Couldn't reach your account", with Stay and Log out anyway, and
  nothing is signed out or wiped until the second yes. Guests skip the sync, since nothing
  can reach their log again. `MoreTest` covers the sync before sign-out, the unreachable
  account, Stay, a sync that hangs, and the guest.
- **Deferred (cross-slice), two parts of the audit's fix:**
  - **A pending count on `DiarySync`.** A sync that succeeds can still leave rows queued (days
    more than `SERVER_MAX_DAYS_AHEAD` ahead, or a stalled page), and those are wiped too. In
    `libraries/diary/src/commonMain/kotlin/com/opson/libraries/diary/Repositories.kt`, add to
    `DiarySync`: `/** Changes still queued on this phone. */ suspend fun pendingChanges(): Int`;
    implement it in `libraries/diary/impl/.../DiarySyncer.kt` as `tables.outbox.count()`, and
    add it to any other `DiarySync` fakes. Then `MoreViewModel.reachedAccount()` (marked
    `TODO(review)`) becomes "synced and `pendingChanges() == 0`", and the Unsynced copy can say
    "N changes haven't reached your account yet".
  - **The journey test.** In `apps/integration`, beside the sync journeys: sign in, take the
    server away (or point the client at a closed port), log a meal so it sits in the outbox,
    drive `MoreViewModel` with the real `DiarySyncer` and `AuthRepository`, confirm log out,
    and assert the state is `LogOutStep.Unsynced`, the outbox still holds the row and the
    user is still signed in; then bring the server back, confirm again, and assert the meal
    is on the server before the sign-out.

## 2. Close the day's switch sat under "In the list": fixed (routed, truth audit)

- **Where:** `todayscreen/TodayScreenSettingsScreen.kt:128` (before), `SettingsRoutes.kt:34`,
  `todayscreen/TodayScreenSettingsState.kt:16`.
- **Rule:** §1 "Names do the documenting", "Stale comments are bugs".
- **Failure:** since `92795cb8` Close the day is a button in Today's header, but More › Today
  screen put its switch under the section label "In the list", where no such row exists. The
  route's and the state's KDoc said the same.
- **Fix:** `fix(settings): Close the day's switch sits under In the header, where the button is`.
  A new "In the header" section (`settings_today_in_header`) holds it; the KDoc says where it
  shows. UI and copy only, so no unit test: the switch writes the same setting
  (`TodayScreenSettingsTest.eachSwitch_writesItsSetting`).
- **Deferred (cross-slice):**
  - `features/today/impl/src/commonMain/kotlin/com/opson/features/today/impl/TodayStateBuilder.kt:128`
    reads the setting through `listRow(inputs) { it.todayCloseDay }`. Add a `headerButton`
    twin (or rename the helper to something like `shownOnToday`) so the name says where it
    shows.
  - `docs/plans/screens/today.md:198-199`: "and In the list (Water, Weight on days you weigh
    in, Close the day). Outside Full numbers only the list's rows can be changed." becomes "In
    the list (Water, Weight on days you weigh in) and In the header (Close the day). Outside
    Full numbers only those can be changed."

## 3. Energy today, Resting energy and Today screen drew a bare header while loading: fixed

- **Where:** `energy/EnergyTodayScreen.kt:100`, `energy/RestingEnergyScreen.kt:100`,
  `todayscreen/TodayScreenSettingsScreen.kt:91` (before: `if (!state.loaded) return@SettingsPage`).
- **Rule:** brief "a blank screen while something loads is a bug"; §10 loading state.
- **Failure:** round one fixed this on Fasting and Watch and health apps and missed these
  three. Energy today waits on Burned and Resting energy on the resting choices, both read
  from the health store, so the page sat empty under its header.
- **Fix:** `fix(settings): Energy today, Resting energy and Today screen show a spinner while loading`.
  The same `DelayedLoadingIndicator`. UI only; the `loaded` flags were already covered by the
  builder tests.

## 4. Targets showed "Targets work in the background" while it loaded: fixed

- **Where:** `targets/TargetsState.kt` (`TargetsState.visible` defaulted to false),
  `targets/TargetsScreen.kt:205`.
- **Rule:** §10 loading state; brief "missing loading and error states".
- **Failure:** the view model starts from `TargetsState()`, and the screen reads
  `visible = false` as "calories are hidden", so someone in All numbers saw the background
  card, the Blind mode page, until the settings, the policy and Burned (a health store read
  with `release.goalsAndEnergy`) arrived.
- **Fix:** `fix(settings): Targets shows a spinner, not the background card, while it loads`.
  A `loaded` flag the builder sets; `TargetsTest.untilBurnedArrives_thePageIsLoading_notTheBackgroundCard`.

## 5. Support's Credits button said it opened the browser: fixed

- **Where:** `support/SupportScreen.kt:247` (before: `trailing = opens`).
- **Rule:** §8 accessibility (content descriptions); §1 "Names do the documenting".
- **Failure:** Credits opens a page in the app (`CreditsRoute`), but the button carried the
  open-in-new mark described as "opens in your browser", so a screen reader announced the
  wrong thing. More's Credits row already uses a chevron.
- **Fix:** `fix(settings): Support's Credits button no longer says it opens the browser`.
  `CreditsScreen`'s KDoc now names More as well. UI only.

## 6. Fasting's `loading` flag was never read: fixed

- **Where:** `fasting/FastingState.kt:15`.
- **Rule:** §1 "No dead code".
- **Failure:** the screen shows its spinner while `page` is null; `loading` was written and
  never read, so two fields said the same thing and could disagree.
- **Fix:** `refactor(settings): drop Fasting's loading flag, which nothing read`.

## 7. The pause route's round-trip test skipped the weekly reveal: fixed

- **Where:** `features/settings/src/commonTest/.../PauseBeforeMoreRouteTest.kt:22`.
- **Rule:** §7 "Tests assert behaviour". The KDoc promises every `NumbersChange` survives the
  trip; `SetRevealWeekly` was the one it left out.
- **Fix:** `test(settings): the pause route round-trips the weekly reveal change`, plus an
  unknown `revealOnKey` reading back as null.

## 8. A stale reason on the Exercise adjustment row's file: fixed

- **Where:** `targets/TargetsExerciseRow.kt:15`.
- **Rule:** §1 "Stale comments are bugs".
- **Failure:** "Kept apart from TargetsScreen so the 0.2 work merges cleanly": that merge is
  done. The real reason to leave it is that a preview's file name is part of its design-sync
  id.
- **Fix:** `docs(settings): say why the Exercise adjustment row keeps its own file`.

## Not a problem

- **Energy today and Resting energy map `health.status` without calling `refresh()`.** The
  status starts at NotConnected, but Burned's read goes through `HealthRepository.energy`,
  which refreshes first, so the activity source corrects itself on the first read.
- **Modes reads `TimeZone.currentSystemDefault()` instead of `LocalCalendar`.** Only for the
  "Last changed" timestamp, which should be the phone's zone anyway; the builder tests pass
  the zone in.
- **Leave out a day: two fast taps on the same day can both write "left out".** Each tap
  toggles from the last arrival, so the second writes the same value again. Idempotent; the
  next arrival shows the truth.
- **Credits shows only the food data and font cards if `licenses.json` fails to load.** The
  file ships in the binary, so that is a packaging bug, logged at error, and `ShippedCreditsTest`
  proves the committed file parses.
- **The QA menu.** `QaGate` is pure and tested for the promoted-beta case; the route is
  registered only where `QaAccess` allows; the tools run through `Catching`, which rethrows
  cancellation; nothing in it calls the server apart from the config refetch. The preview's
  server URL and Supabase ref are public values, not secrets.
- **`SettingsRow`'s and `QaRow`'s `onClick!!`.** Inside `thenIf(onClick != null)`, so it can't
  be null; not data from outside.
- **The log out dialog says "Your log stays in your account" while the sync runs.** True of
  everything that has synced; the Unsynced step covers the rest.

## Second review

A second reviewer read finding 1's fix and flagged two regressions in it. Both were real.

### S1. A back press during the sync wait hid the "Couldn't reach your account" warning: fixed

- **Where:** `more/MoreViewModel.kt` `logOut`, which waited up to ten seconds for the sync
  inside the action queue, and `more/MoreScreen.kt` `LogOutDialog`, whose `onDismissRequest`
  sent `LogOutCancelled` even while working.
- **Rule:** §2 (work that blocks the action queue), §10 "the error state tells the person what
  they can do".
- **Failure:** offline, confirm log out, then press back or tap outside the dialog during the
  wait. The cancel queued behind `logOut`, and by its turn the step was already `Unsynced`, so
  the `Working` guard didn't apply and it reset to `Idle`. The warning never showed and nothing
  said why the person was still signed in. Nothing was wiped.
- **Fix:** `fix(settings): a back press while log out syncs no longer hides the warning`. The
  wait runs beside the queue and reports back as `LogOutSyncFinished`, so a dismiss meanwhile
  meets `Working` and is ignored, and the dialog no longer sends one while working, like
  Support's delete. `MoreTest.logOut_backPressedWhileItSends_stillShowsTheWarning` failed
  before (ended `Idle`).

### S2. After "Log out anyway" the dialog said "Your log stays in your account": fixed

- **Where:** `more/MoreScreen.kt`, which drew `LogOutStep.Working` with the signed-in copy, and
  `more/MoreViewModel.kt`, which set `Working(guest = false)` from the Unsynced step.
- **Rule:** §1 "code that reads true", applied to the words on screen.
- **Failure:** the person accepts that unsynced changes will be lost, and for as long as
  `auth.signOut()` takes (it goes to the network) the dialog tells them the opposite.
- **Fix:** `fix(settings): Log out anyway keeps saying what is lost while it signs out`.
  `Working` carries `unsynced`, and `LogOutCopy.of(step)` keeps the Unsynced copy for it.
  `MoreTest.logOut_anyway_keepsSayingWhatIsLost_whileItSignsOut` failed before (got
  `SignedIn`).

## Product notes

- **Log out could show what hasn't synced before asking.** Once `DiarySync` can count pending
  changes (finding 1), the first dialog itself can say "3 changes haven't reached your account
  yet" instead of finding out only after a failed sync. Fairly sure people would rather know
  before they tap.
- **Energy today shows nothing at all in a mode that hides calories, energy out and
  Remaining.** Every section drops out and the page is a header with no words. Today's summary
  card is the only link here and probably isn't drawn in those modes, so it may never be
  reached; if it can be, a line saying the mode keeps these numbers out of sight would beat
  an empty page. Not sure it's reachable.
