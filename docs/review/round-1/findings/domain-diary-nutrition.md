# Findings: domain-diary-nutrition

`libraries/diary/` (api, impl, ui) and `libraries/nutrition/` (api, ui). Each
finding names the place, the rule from [PRACTICES.md](../PRACTICES.md), what
breaks, and its fate. The unslop-code scanner found only long identifiers
(`clientUpdatedAtEpochMs` and the like), which match the wire names on purpose.

## 1. A restart before auth answered showed default settings as the user's own: fixed

- **Where:** `libraries/diary/impl/src/commonMain/kotlin/com/opson/libraries/diary/impl/SettingsStore.kt:181`
  (before the fix: `ActiveUser.Unresolved, ActiveUser.SignedOut -> true`).
- **Rule:** checklist "Visibility"; §3 offline first; the store's own rule that
  an unknown user is treated as Blind.
- **Failure:** sign in to an existing account with no connection, so the first
  pull never lands. The stored settings are the defaults, Full numbers, owned by
  nobody. Kill the app and open it: until auth resolved, `isKnown` was true, so
  `SettingsVisibilityPolicySource` served Full numbers and Today drew calories
  for someone who may have chosen Blind. Reminders and the widget read the same
  flag. Then auth answered, and it went Blind again.
- **Fix:** before auth answers, settings are known only when a pull ever landed
  in them (`ownerUserId != null`, `SettingsStore.kt:185`). Signed out still
  trusts what is stored. `SettingsStoreTest.beforeAuthAnswers_onlySettingsAPullLandedInAreKnown`
  and `nobodySignedIn_isKnownOnceAuthSaysSo`. Commit: `fix(diary): settings no
  pull reached stay unknown until auth answers`.

## 2. Goal styles never reached Today's range: fixed in nutrition, deferred (cross-slice) for Today

- **Where:** `libraries/nutrition/src/commonMain/kotlin/com/opson/libraries/nutrition/Targets.kt:213`
  (`Targets.today` started from the base `minKcal`/`maxKcal`), against
  `TargetStyle.kt:6`, which says every style answers through `rangeFor` "so
  Today and Trends never branch on it".
- **Rule:** §1 stale comments are bugs; correctness.
- **Failure:** someone who picks "Different by day" (Saturday 2,300 to 2,700) or
  "A set deficit" on Targets sees Today, Day close and the summary card draw the
  base range on every day. Only the logger's amount sheet called `rangeFor`.
- **Fix (mine):** `Targets.today` takes the day's goal (`goal`, defaulting to
  the base so callers can move one at a time). `TargetsTest.today_startsFromTheDaysOwnGoal`.
  The widget, which had the same bug, is fixed in platform-libraries #4.
  Commit: `fix(nutrition): a day's range can start from its own goal`.
- **Deferred (cross-slice, today):**
  - `features/today/impl/src/commonMain/kotlin/com/opson/features/today/impl/ExerciseRange.kt:18`:
    pass `goal = settings.targets.rangeFor(day.date, settings.numbers.preset)`
    to `settings.targets.today(...)`.
  - `features/today/impl/src/commonMain/kotlin/com/opson/features/today/impl/dayclose/DayCloseState.kt:163`:
    `settings.targets.tomorrow()` should be tomorrow's own goal,
    `settings.targets.rangeFor(day.date.plus(1, DateTimeUnit.DAY), settings.numbers.preset)`
    (it still takes nothing from today, so a big day still cannot move it).
  - Trends (`TrendsState.kt:220`, `NutrientsState.kt:125`) draws the base band
    too. A week chart may want per-day bands; that is a product call, see the
    notes below.

## 3. Copying food after the window closed lost the fast it ended: fixed

- **Where:** `libraries/diary/impl/src/commonMain/kotlin/com/opson/libraries/diary/impl/DiaryRepositoryImpl.kt:150`
  to `:166` (`copyItems`, `copyDay`, `copySlot` told the fast ender after writing).
- **Rule:** match the neighbours (`addItems` tells it first); correctness of
  `FastingRepositoryImpl.foodLogged`.
- **Failure:** the app is closed when the eating window shuts at 7 pm. At 8 pm
  the person copies yesterday's dinner. The copy was written first, so the fast
  due from 7 pm saw food logged since the close and was never written; the
  Fasting card said "Resting" rather than "Ended at 8:10 PM". A new log did it
  right.
- **Fix:** copies read their source, and when there is any, end the fast before
  writing. The reason is now in `foodLoggedOn`'s KDoc (`:220`).
  `FastingRepositoryImplTest.copyingFoodAfterTheCloseRecordsTheFastItEnded_too`.
  Commit: `fix(diary): copying food after the window closed records the fast it ended`.

## 4. Dead code: fixed

- **Where:** `SyncWire.kt` `SERVER_MAX_MUTATIONS` (no reader; the drain's KDoc
  already states the cap), `TodayCards.kt` `TRACK_ALPHA` (no reader),
  `Settings.kt` `fun Settings.numbers()` (returned the property of the same
  name; one caller, plus a test of the alias).
- **Rule:** §1 no dead code.
- **Fix:** deleted, after grepping the whole repo. Commit: `refactor(diary):
  drop an unused cap, alpha and numbers() alias`.

## 5. Diary sync: outbox, last-write-wins, rollback: not a problem

Read `DiarySyncer`, `DiaryStore`, `SettingsStore` and the DAOs for the
orderings that lose writes:

- A change made while its row is in flight replaces the waiting mutation under
  a new id, keeping the old base (`DiaryStore.kt:105`), so the ack for the old
  id cannot remove it, and a refusal of the old id finds nothing to undo
  (`DiarySyncer.kt:277`).
- `stamp` is strictly newer than the previous stamp, so two edits in one
  millisecond are not dropped by the server's strict comparison.
- The pull never overwrites a row with a change waiting; acks, rollbacks, the
  merge and the cursor commit in one transaction.
- `Catching` rethrows cancellation (`ThrowableExtensions.kt:8`), and the shared
  sync rethrows only when the caller itself is cancelled.

## 6. `turnOnPause` stamps `changedAt` itself: not a problem

`SettingsNumbersChanger.kt:67` sets `changedAt` although the store re-stamps
it. Every change the changer makes is stamped the same way (`NumbersChange.applyTo`),
so a repository that does not stamp still records when the user changed it;
`SettingsNumbersChangerTest` relies on it.

## 7. Delayed reveal and midnight loops: not a problem

`LivePolicy` and `SelectedDayImpl` loop forever, but in a scope a test can
cancel, with an injected clock, and in steps of at most a minute so a phone
that slept does not wake hours late.

## 8. Visibility in `:libraries:diary:ui` and `:libraries:nutrition:ui`: not a problem

Every number in the diary cards reaches the screen as a `Display`, `RingValue`
or `MacroPart` and renders through `NutrientText`, `NutrientFigure`,
`NutrientPhrase`, `NutrientBar` or `MacroLine`, each tagged `metric:<key>`.
Labels sit inside `NutrientVisible`. The eaten bar on the summary card is drawn
only when `eaten` is not hidden and its macro segments only under a fill (a
softened amount goes to `SoftSummaryCard` instead). `LocalVisibilityPolicy`
fails closed to Blind outside a preview.

## Product notes

- **Goal styles do almost nothing yet.** "Different by day" and "A set
  deficit" change the Targets screen, the logger's amount sheet and (now) the
  widget, but not Today, Day close or Trends (finding 2). Someone who sets a
  training-day range will think it is broken. Sure.
- **Logging yesterday's food ends today's fast.** `foodLoggedOn` ends a running
  fast for any day up to today, so back-filling last night's snack at 8 am ends
  the morning's fast. Back-filling is not eating now; only today's date should
  count, or only items logged for a time after the fast began. Fairly sure it
  will surprise people who fast.
