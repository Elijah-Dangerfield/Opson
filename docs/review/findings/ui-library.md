# Findings: ui-library, round two (`libraries/ui`, `libraries/resources`)

All 136 files in the ledger were read, plus the string files in
`libraries/resources` (the routed findings live there). Dead code was found
with a whole-repo token count (Kotlin and Swift, comments stripped) and every
deletion was grepped again by name before it went. The unslop-code scanner
reported 51 low "verbose identifier" hits, all sentence-named tests and Compose
test APIs, which is this repo's test style. Round one's fixes were rechecked and
still hold; its two cross-slice deferrals (one `MinTouchTarget`, names of
deleted code in detekt rules and docs) were done by their owners.

## 1. The QA menu told testers about a guest session they cannot have (fixed)

- **Where:** `libraries/resources/.../values/strings_qa.xml:49`
  (`qa_reset_onboarding_sub`). Routed from the truth audit.
- **Rule:** §1 "Stale comments are bugs" (tester-facing copy).
- **Failure:** it said a live guest session skips back to Today. A tester now
  always has a claimed account, and `OnboardingViewModel.handleResolveEntry`
  (`OnboardingViewModel.kt:179`) sends a claimed account that has not onboarded
  to the questions, not to Today and not to Welcome.
- **Fix:** `fix(resources): the QA reset onboarding line says where a signed-in
  tester lands`. It now reads "Shows on next launch. Signed in, it opens at the
  questions; log out to start from Welcome."

## 2. Eight strings nothing reads (fixed, one doc line deferred cross-slice)

- **Where:** `strings_modes.xml:59-62` (`modes_hunger_not_really`, `_a_little`,
  `_hungry`, `_very`), `strings_onboarding.xml:24-26` (`onboarding_plan_plus`,
  `_plus_later`, `_plus_includes`) and `:150` (`onboarding_account_coach`), and
  the Plus sentence in the plan comment above them. Routed from the truth audit.
  The same search found `strings_reminders.xml` introducing a Rhythm card, with
  an empty "Today, Rhythm" section, that left with Rhythm mode.
- **Rule:** §1 "No dead code" (resources).
- **Failure:** translators and copy reviews still see the lines. The hunger
  words went when hunger became one five-step scale (e8c0eec0).
- **Fix:** `refactor(resources): delete eight strings nothing reads, and the
  comments about them`. A repo-wide search now finds no string without a reader
  except the one in finding 11.
- **Deferred (cross-slice):** `docs/plans/screens/onboarding.md:249`, delete the
  table row for `onboarding_plan_plus` / `_plus_later` / `_plus_includes`.

## 3. Ten icons nothing draws, and notes naming gone features (fixed)

- **Where:** `components/icon/AppIcons.kt`: Mood, Dialpad, Bedtime, SelectAll,
  CalendarMonth, Straighten, TrendingDown, TrendingUp, Adjust, Route;
  EventAvailable's KDoc (Coast); `PromptCard.kt:31-35` (Explorer's food bridge,
  "an icon tile"). Routed from the truth audit.
- **Rule:** §1 "No dead code", "Stale comments are bugs".
- **Failure:** the nine lost their last callers (6e6b4555, 0cb315c1, ad56ad73,
  8e9e0b05, 342b02e1) and survived only in the gallery list. Route was drawn
  only by PromptCard's preview, for a feature that is gone.
- **Fix:** `refactor(ui): delete ten icons nothing draws, and fix the notes that
  named gone features`. The audit asked to keep Route and fix its note; with its
  only use a preview of a gone feature, the preview now shows the card Today
  draws (the untracked day, BeachAccess) and Route went too. A duplicate
  `ContentCopy` import went with it.

## 4. A disabled button sounded like a working one; a deep button not like a button (fixed)

- **Where:** `components/button/BasicButton.kt:70-133` (before).
- **Rule:** §8 Accessibility.
- **Failure:** a disabled Button (onboarding's Continue before an answer, the
  photo unlock, the upgrade retry) had no clickable, so the only semantics were
  a `Role.Button` on the face. TalkBack and VoiceOver said "Continue, button",
  never "dimmed", and a double tap did nothing. A `deep = true` button had a
  clickable with no role; a role on a child does not merge up (its merge policy
  keeps the parent's, which was empty), so it was announced as plain text.
- **Fix:** `fix(ui): a disabled button says it is dimmed, and a deep button says
  it is a button`. Both paths put one merging node with `Role.Button`, disabled
  when it is. `ButtonSemanticsTest` covers disabled, enabled and deep; the
  disabled and deep cases failed on the old code. `onDisabledTap` went with it:
  no caller passed it.

## 5. The shake dialog's Dismiss cut it off mid-frame (fixed)

- **Where:** `components/dialog/ShakeDialog.kt:100` (before).
- **Rule:** §1 "Match the neighbours" (a tap outside and Report a bug both go
  through `state.dismiss()`); the owner's call that menus animate away however
  they close (ed274227).
- **Failure:** Dismiss called `onDismiss` directly; the caller
  (`ShakeDialogEntryPoint.kt:44`) goes back, which removed the dialog in one
  frame, while a tap outside played the exit first.
- **Fix:** `fix(ui): the shake dialog's Dismiss animates away like a tap
  outside`. `ShakeDialogTest` fails on the old code (dismissed at once).

## 6. Components and tokens nothing uses (fixed)

- **Where:** `ChecklistRow.kt` (ChecklistRow, ChecklistAction) and `TintTile.kt`
  (TintTile, TintTileGrid), both whole files; `DayHeader.kt` CountChip;
  `TypeScale.figureHero`; `Colors.popoverScrim`; `Radii.Input`;
  `FlatButtonDefaults.CompactMinHeight`; `EdgeCardDefaults.SectionGap`;
  `IconSize.AppBar`, `Smallest`, `Largest` and `IconButton.Size.Smallest`,
  `Largest`; two unused imports in `ListRow.kt`.
- **Rule:** §1 "No dead code".
- **Failure:** the five components lost their callers today when Today's mode
  layouts were rebuilt (21a032b5); the gallery does not show them, so by round
  one's policy they are dead. The tokens had no reader at all.
- **Fix:** `refactor(ui): delete the components and tokens nothing uses`.

## 7. RangeBar defaulted to hero colours no caller used, and misstated its size (fixed)

- **Where:** `components/DayHeader.kt:189-215, 280-286` (before).
- **Rule:** §1 "No dead code", "Stale comments are bugs".
- **Failure:** `colors = RangeBarDefaults.onHero()` was overridden by all six
  callers (summary or onSurface), and the KDoc promised "14 tall track, 18 tall
  box"; at the default 22 dp the track is 13.2 and the box 20.
- **Fix:** same commit as 6. The default is `summary()`, `onHero` and its two
  alphas are gone, and the note says the track is 60% of the height.

## 8. Two snackbar duration conversions, one misnamed (fixed)

- **Where:** `components/Snackbar.kt:294-308` (before).
- **Rule:** §1 "Names do the documenting".
- **Failure:** `toMaterial()` and `fromMaterial()` had identical bodies, and
  `fromMaterial` converts to Material.
- **Fix:** `refactor(ui): one snackbar duration conversion, named for what it
  does`.

## 9. DefaultTypography asked for a serif and a sans face (fixed)

- **Where:** `system/typography/TypographyResource.kt:188-210` (before).
- **Rule:** §1 "Names do the documenting".
- **Failure:** `serifFontFamily` and `sansSerifFontFamily` were always handed the
  same Atkinson family; the names said Display is a serif.
- **Fix:** `refactor(ui): DefaultTypography takes one text face, not a serif and
  a sans`.

## 10. Comments and catalog copy that no longer matched the code (fixed)

- **Where:** `catalog/TypographyCatalog.kt:29-97` ("Display · serif", "Heading ·
  sans" and so on, and "the italic is the dialog title signature", which nothing
  uses); `TypographyResource.kt:668` (Brand preview: "Atkinson Hyperlegible Next
  ExtraBold"; it is Young Serif); `RadioRow.kt:84` (18 dp in the matrix; it is
  20); `PillChip.kt:162` (Explorer's safe foods); `HeroParts.kt:34-38` (cards
  "at the top of Today"); `Banner.kt:23` (quoted a banner Today no longer
  shows); `libraries/ui/build.gradle.kts:23` ("the few DS components ...
  (ShakeDialog)"; eleven files read resources); `SwipePages.kt:126` (see 13).
- **Rule:** §1 "Stale comments are bugs".
- **Fix:** `docs(ui): comments and catalog copy that no longer matched the
  code`. Round one's finding 23 covered the catalog subtitle but not these
  section labels.

## 11. A string read only by an unused import (deferred, cross-slice)

- **Where:** `strings_settings.xml:151` (`settings_targets_activity_none`),
  imported at `features/settings/impl/.../targets/TargetsScreen.kt:92` and never
  used since 91456b95.
- **Rule:** §1 "No dead code".
- **Why deferred:** deleting the string breaks that import, which is the
  settings slice's file. Exact change: delete `TargetsScreen.kt:92`, then delete
  `strings_settings.xml:151`.

## 12. Three CompositionLocals nobody reads, defaulting to a crash (deferred, cross-slice)

- **Where:** `system/Local.kt:28-38` (`LocalBuildInfo`, `LocalAppState`,
  `LocalClock`), provided in `Preview.kt:48-52, 83-87`, `apps/compose/.../App.kt:176-178`
  and `apps/leaktest/.../LeakScan.kt:57-59`.
- **Rule:** §1 "No dead code"; AGENTS.md "Default it to a noop, never
  `error("not provided")`".
- **Failure:** no composable reads any of them, so every provider is work for
  nothing, and the `error(...)` defaults are the pattern AGENTS.md forbids.
- **Why deferred:** the providers are in two other slices. Exact change: in
  `App.kt` delete the three `provides` lines at 176-178 and their imports at
  64-66 (`LocalAppState`, `LocalBuildInfo`, `LocalClock`); in `LeakScan.kt`
  delete lines 57-59 and the imports of `PreviewAppState`, `LocalAppState`,
  `LocalBuildInfo`, `LocalClock` (22-25) and of `Clock`/`fixed` if then unused.
  Then in this slice delete `Local.kt:28-38`, the three `provides` lines in both
  `Preview.kt` wrappers and `PreviewAppState`.

## 13. Page dots take an 18 by 28 dp tap (deferred, owner's call)

- **Where:** `components/SwipePages.kt:142,154-155` (`PageIndicator`), under
  Today's summary card and in the sticky bar.
- **Rule:** §8 "44 pt targets" (design v3 asks 48).
- **Failure:** each dot is a `Role.Tab` with an 18 by 28 dp target, and its
  KDoc said the tap area was not small. Swiping the card is the main way across
  and screen readers use the tab semantics, so nobody is locked out, but a
  tap on a dot is fiddly.
- **Why deferred:** 48 dp targets spread the dots or add 20 dp under the card;
  the owner set these sizes today (c9bdb676). The KDoc now says the size, with a
  `TODO(review)`.

## 14. Button colours still animate through composition (deferred, unchanged)

- **Where:** `components/button/Button.kt:81-96`. Round one's finding 26.
- **Why deferred:** still true and still rare (a button changing enabled
  state). The content colour reaches `Text` through a composition local, so a
  draw-phase fix needs more than lambdas through `BasicButton`.

## 15. Material's guard scheme uses a deprecated constructor (deferred, unchanged)

- **Where:** `system/AppTheme.kt:125`. Round one's finding 29; still true.

## Looked at, not a problem

- **Cancellation.** `Catching` in `PhotoThumbnail`, `SearchField` and
  `PresenterSnackbarHost` rethrows it (`Catching.kt:16`). `BottomSheetState.dismiss`
  uses `try/finally` with no catch.
- **`PopoverMenu` writes `targetState` in composition.** Written before it is
  read, the pattern Material's own dropdown uses; `PopoverMenuTest` covers both
  overloads.
- **`HostedDialog` upserts on every composition.** The entries are a
  `StateFlow` of data classes, so an unchanged entry emits nothing.
- **`AppSwitch` clicks on its 50 by 30 box.** Compose extends pointer hit tests
  to the 48 dp minimum, and `minTouchTarget` keeps that space clear in layout.
- **`libraries/resources` exports Material 3 and the icon set as `api`.** No
  failure, and five modules that depend on resources without `:libraries:ui`
  may lean on it transitively.
- **Launch work, numbers and logging.** Nothing in the slice runs at app start;
  components take formatted strings; the only log of user data, the snackbar
  failure, logs the id (round one's fix holds).

## Product notes

- **Page dots are small to tap.** See finding 13. Bigger targets mean wider
  dots or more space under the card. Fairly sure some people will miss them;
  unsure it is worth the space.
- **The time picker's digits are not in the app's face.** `TimePickerDialog`
  hands Material's picker a default `Typography()`, so the dial and the hour
  boxes draw in the system font beside Atkinson everywhere else. Sure it shows;
  unsure anyone minds.
- **A dimmed Continue does not say why.** With finding 4 a screen reader now
  hears "dimmed", but onboarding gives no spoken or visible hint of what to pick
  first. A line under the button, or a hint, would help. Fairly sure.
