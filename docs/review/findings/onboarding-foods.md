# Findings: onboarding-foods, round two (`features/onboarding`, `features/foods`)

All 61 files in the ledger were read (`DisplayNameSuggester.kt` was read and then
deleted, finding 3), and the unslop-code scanner was run over both features. Its
hits were framework names (`collectAsStateWithLifecycle`), `example.com` in
previews and tests, and one emoji in a KDoc (fixed with finding 6). Checks:
`./gradlew detekt`, `:features:onboarding:impl:testDebugUnitTest`,
`:features:foods:impl:testDebugUnitTest`, `:apps:leaktest:testDebugUnitTest` and
`:apps:compose:compileKotlinIosSimulatorArm64` are green. The Foods previews were
recorded before and verified after finding 2 with `verifyRoborazziDebug`: no pixel
moved.

Round one's fixes still hold: the double-save guards (`saved` in Add a food,
Create a meal and Edit a copy), the live regions on auth errors, and "Signing in…"
on Apple. Its finding 9 (Recent showing fewer than six) is now fixed upstream:
`DiaryRepository.recentFoods` counts only entries logged from a food.

## 1. Picking lb and in relabelled the resting energy fields without converting them (fixed)

- **Where:** `OnboardingViewModel.kt:155` (`UnitPicked` copied the unit alone) and
  `SetupSteps.kt` `BodyAnswer.heightCmOrNull`/`weightKgOrNull`, which return null
  with nothing on screen. Routed from the journeys audit.
- **Rule:** §10 "the error state tells the person what they can do".
- **Failure:** type 175 and 70 in kg and cm, then pick lb and in: 175 cm became
  175 in (444 cm, so no resting energy) and 70 kg became 70 lb (31.8 kg, still in
  range, so it was logged as the first weigh-in). A US user typing 5.10 for 5 ft 10
  got 13 cm. In every invalid case the "About X a day at rest" line just never
  appeared, and a deficit or surplus started from no burn with nothing to say why.
- **Fix:** `fix(onboarding): switching to lb and in converts what is typed, and a
  field that can't be used says why`. `BodyAnswer.withUnit` converts height and
  weight to one decimal (175 cm is 68.9 in, and back to 175). `BodyView` carries
  `birthYearInvalid`, `heightInvalid` and `weightInvalid`, and the step draws a
  muted line under each ("Height in inches, like 67 for 5 ft 7"), in a polite
  live region. Tests: `OnboardingSetupStepsTest.switchingUnits_convertsWhatIsTyped_soTheWeighInIsStillTheirs`,
  `feetAndInchesTypedAsADecimal_saysHeightIsInInches`, and `aTypoInTheDetails_isNotSaved`
  now checks each field flags itself. Separate ft and in fields are left as a
  product note: Settings' Resting energy takes inches too, and the two should
  change together.

## 2. Each Foods tab section was one lazy item holding every row (fixed)

- **Where:** `FoodsScreen.kt:102` (`item(key = "yours") { RowsCard(...) }`), and
  the same for Saved meals and Recent. Routed from the compose-perf audit.
- **Rule:** §4 "Lazy lists have stable keys and contentType".
- **Failure:** `RowsCard` looped over `customFoods.observeAll()`, which has no
  limit. Someone with 60 foods of their own composed all 60 rows when the tab
  opened, and every emission (a sync, a new food) was a new list instance that
  recomposed the whole section.
- **Fix:** `perf(foods): each Foods tab row is its own lazy item`. A section is a
  label item, one `itemsIndexed` row per food keyed `"<section>:<row.key>"` (the
  section prefix matters: a food of your own can be in Your foods and Recent at
  once) with a `contentType`, and the empty note as its own item. Each is a slice
  of the card: the label carries its top corners, the last row its bottom corners
  and the gap below. `FoodsRowUi.key` was already a stable id. No unit test (no
  behaviour changed); the leak test renders the tab in every mode, and the
  screenshot verify showed the previews unchanged.

## 3. DisplayNameSuggester had no caller (fixed; doc deferred, cross-slice)

- **Where:** `DisplayNameSuggester.kt:10`. Routed from the truth audit.
- **Rule:** §1 "No dead code"; "Stale comments are bugs".
- **Failure:** guest creation went in 908798dd and took the only caller with it.
  The KDoc still said the guest account is created with one of these names.
- **Fix:** `refactor(onboarding): remove DisplayNameSuggester, which nothing calls
  since guests went`.
- **Deferred (cross-slice):** `docs/plans/screens/onboarding.md:168-193` (the
  Actions table and the `finish` steps) still describe the guest and account-step
  flow, `DisplayNameSuggester` at :191 included. No slice owns `docs/plans`. The
  privacy policy's promise about these names belongs to the website slice's
  privacy fix, as the audit says.

## 4. Edit a copy opened a weighed food's numbers as already changed, and rounded them on save (fixed)

- **Where:** `EditCopyState.kt:92` (`text.toDoubleOrNull() != before`) and `:111`
  (`draft` saving the typed text).
- **Rule:** §1 "Code that reads true"; the review checklist's visibility rule that
  a number is what was logged.
- **Failure:** each field opens at one decimal ("7.5"), but was compared with the
  entry's own number (7.53). A food weighed from the catalog rarely has round
  numbers, so on open most fields were tinted with "Was 8 g" under them before
  anything was typed, and saving wrote 7.5, not 7.53. The fixtures are all whole
  numbers, which is why the tests never saw it.
- **Fix:** `fix(foods): Edit a copy opens a weighed food's numbers unchanged, and
  keeps them exact`. "Changed" compares with the number as the field opened it,
  and a field left as it opened saves the entry's own number.
  `EditCopyTest.aNumberWithMoreThanOneDecimal_opensUnchanged_andIsSavedExactly`
  fails without the fix.

## 5. A new Google account from the email sign-in screen skipped setup (fixed; KDoc deferred, cross-slice)

- **Where:** `SignInViewModel.kt:86` (`sessionEstablished = false` for OAuth).
- **Rule:** §9 "The server and the client agree on the contract" (here, the client
  and its own repository); §1 stale comments.
- **Failure:** `SignInViewModel` took `AuthRepository.signInWithOAuth`'s KDoc at
  its word ("Success means browser opened") and never asked whether a Google
  account was new, so it marked every Google sign-in onboarded and sent a brand
  new account to Today in All numbers with no questions. The implementation
  (`SupabaseAuthRepositoryImpl.kt:669`) waits for the redirect, and
  `OnboardingViewModel` already relies on that. Latent while
  `SignInMethods.EMAIL` is off, since this screen is only reached from the email
  button.
- **Fix:** `fix(onboarding): a new Google account from the email sign-in screen
  still runs setup`. The old test that pinned the wrong behaviour is now
  `SignInViewModelTest.signInWithOAuth_forANewGoogleAccount_goesToSetup_notToday`.
- **Deferred (cross-slice):** `libraries/identity/src/commonMain/kotlin/com/opson/libraries/identity/auth/AuthRepository.kt:145-149`
  should say the call returns once the `opson://login-callback` redirect has been
  handled, so `Success` means signed in.

## 6. Comments that described flows that are gone (fixed)

- **Where:** `ModeRecommender.kt:9` and `:90` ("Skip and start logging", removed
  with Skip all); `OnboardingFixtures.kt:26` (recommends "Nutrients" with "Later",
  neither a mode any more; it is Macros only with No numbers and Macros with
  grams); `OnboardingScreen.kt:169` ("three questions", "shows no nutrition
  numbers", though the goal and resting energy steps do); `OnboardingSetupEnabled.kt:15`
  ("goes to the account step, as 0.1 does"); `AuthRoutes.kt:8` (every sign-in goes
  to Today); `VerifyEmailViewModel.kt:155` (a template line about floating a
  confirmation surface); an emoji in `EditCopyScreen.kt:62`.
- **Rule:** §1 "Stale comments are bugs".
- **Fix:** `docs(onboarding): comments that still described Skip all, the account
  step and three questions`.

## 7. The onboarding routes module declared dependencies it never used (fixed)

- **Where:** `features/onboarding/build.gradle.kts`.
- **Rule:** §1 "No dead code".
- **Failure:** five route classes, with `libraries:core`, `compose.runtime`,
  `foundation`, `material3`, `ui`, resources and tooling declared; `ui` and
  `navigation` already come from `opson.feature`. Every module that depends on the
  routes waited on those.
- **Fix:** `build(onboarding): the routes module drops dependencies it never used`.

## Not problems

- **A failed local write on Save.** Add a food, Create a meal and Edit a copy call
  the repository without catching, so a Room insert that throws would crash the
  action loop. Every other screen that writes to Room does the same, and a local
  insert failing is a disk-full case, not a network one. Not worth a pattern of
  its own here.
- **Google sign-in off the action loop.** `OnboardingViewModel.googleAttempt`
  updates state from its own coroutine. It is documented, cancelled on every
  other tap and on leaving the step, and it is how closing the browser stays
  recoverable.
- **Round one's raw dp values (its finding 8).** Still there, still wants one
  sweep across features with design-sync screenshots. Not re-reported.

## Product notes

- **Height in inches only is awkward for anyone in the US.** People think in feet
  and inches; the hint now explains, but two fields (ft, in) on both onboarding and
  Settings' Resting energy would remove the mistake. Fairly sure.
- **The resting energy hints show while typing.** "17" on the way to 175 shows
  "Height in centimetres, like 170" for a keystroke. It is muted and reads as
  guidance, but showing it only once the field loses focus would be calmer.
  Moderately sure it matters.
- **A deficit chosen with half the details falls back to no deficit quietly.**
  With the step skipped or incomplete, `useDeficit(null)` starts at 0 kcal under.
  A line on the goal step or Targets saying "add your details to work this out"
  would explain it. Moderately sure.
