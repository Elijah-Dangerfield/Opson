# Findings: onboarding-foods (`features/onboarding`, `features/foods`)

All 52 files in the ledger were read, and the unslop-code scanner was run over
both features (its hits were framework names and `example.com` in previews and
tests, nothing to change). Checks: `./gradlew detekt`,
`:features:onboarding:impl:testDebugUnitTest`,
`:features:foods:impl:testDebugUnitTest`, `:apps:leaktest:testDebugUnitTest`
and `:apps:compose:compileKotlinIosSimulatorArm64` are green.

Google and email sign-in being off for 0.1 (`SignInMethods`) is deliberate and
not a finding; finding 1 is what that leaves behind on Android.

## 1. Android offered a sign-in and an account step with nothing to tap (fixed)

- **Where:** `OnboardingScreen.kt:214` (the Welcome "Sign in" link) and
  `OnboardingViewModel.kt:253` (`claimable`), before the fix.
- **Rule:** review checklist, screens: no screen that is empty of what it
  promises; §3 "Actions are intents" (the decision belongs in the ViewModel).
- **Failure:** with Google and email off, Android has no sign-in method at all.
  "Have an account? Sign in" opened `SignInScreen` with a title, a subtitle and
  a Back button. Saving the mode opened "Save your setup: back up your logs and
  settings" whose only button was "Keep it on this phone". Android ships to Play
  (`docs/release-automation.md`).
- **Fix:** `fix(onboarding): a phone with no way to sign in skips Sign in and
  the account step`. `OnboardingState.canSignIn` (Apple on iOS, or Google or
  email once on) gates both; the guest is still created so logs sync.
  `OnboardingViewModelTest.signIn_onAPhoneWithNoWayToSignIn_goesNowhere` and
  `start_onAPhoneWithNoWayToClaim_goesToToday_pastTheAccountStep`. The VM takes
  `appleSignIn: Boolean = BuildInfo.isiOS()` so tests run either phone.

## 2. A guest's email claim reached Verify as a brand-new sign-up (fixed)

- **Where:** `SignUpViewModel.kt:66` and `OnboardingFeatureEntryPoint.kt:104`,
  before the fix.
- **Rule:** §1 "Code that reads true" (a documented flag nothing sets).
- **Failure:** `VerifyEmailRoute.guestLink` decides how a confirmed email is
  routed (`AuthOutcomeClassifier.classify(wasLink)`) and whether an expired
  session sends the person to sign-in. Nothing ever set it: sign-up navigated
  with the email alone, so a guest's claim was classified by the profile's
  one-shot new-account latch, and an expired guest session showed "still
  pending" forever instead of sign-in. Latent in 0.1 (email is off).
- **Fix:** `fix(onboarding): an email linked onto the guest reaches Verify as a
  guest link`. `SignUpViewModelTest` asserts the flag on both paths. The same
  commit drops a template comment ("A generated project can float a
  confirmation surface here").

## 3. Auth errors weren't announced to screen readers (fixed)

- **Where:** `AuthScreens.kt` `ErrorText` and `VerifyEmailBanner`;
  `OnboardingScreen.kt` `AccountErrorText`.
- **Rule:** §8 "live regions for status changes".
- **Failure:** each error appears after a tap, away from the focused button,
  and was silent with TalkBack and VoiceOver.
- **Fix:** polite live regions, in `fix(onboarding): auth errors are announced,
  and Apple sign-in says it's working` and the commit for finding 1. No test:
  the feature module has no Compose UI test harness; semantics only.

## 4. Apple sign-in only greyed out while it worked (fixed)

- **Where:** `AuthScreens.kt:167`.
- **Rule:** brief, "missing loading and error states".
- **Failure:** after the Apple sheet closes, `signInWithApple` is a network
  call. The button greyed out with its label unchanged, which reads as broken.
- **Fix:** it reads "Signing in…" (`auth_signing_in`), same commit as 3.

## 5. Dead code in the plan card (fixed)

- **Where:** `OnboardingScreen.kt:241` (`PlanRow`'s `note`, always null) and
  four unused imports (`onboarding_plan_plus*`, `onboarding_account_coach`).
- **Rule:** §1 "No dead code".
- **Fix:** removed in the commit for finding 1. The four strings stay in
  `strings_onboarding.xml`: they are the Plus row and coach line that return
  with purchases and sharing, listed in `docs/plans/screens/onboarding.md`.

## 6. Apple sign-in failures were logged twice (fixed)

- **Where:** `SignInViewModel.kt:96`.
- **Rule:** §5 "Right level, right volume".
- **Failure:** `logOnFailure` logged the throwable at error, then `logger.w`
  logged it again.
- **Fix:** the second line is gone, in `refactor(ios): remove the
  PermissionManager nothing asks` (it rode along with that commit).

## 7. A second Save tap wrote a duplicate meal or food (fixed)

- **Where:** `CreateMealViewModel.kt:94`, `AddFoodViewModel.kt:54`.
- **Rule:** §6 "Idempotent writes"; brief, "a race that needs two taps in 50 ms
  is still worth fixing if the fix is small".
- **Failure:** actions run in order. A second tap queued before `Close`
  navigated away ran `save()` again with the same draft: two "Usual breakfast"
  saved meals, or two custom foods.
- **Fix:** `fix(foods): a second Save tap adds no second meal or food`.
  `CreateMealTest.aSecondSaveTapBeforeTheScreenClosesSavesNothing`,
  `AddFoodTest.aSecondSaveTapBeforeTheScreenClosesAddsNothing`.

## 8. Raw dp values in the Foods screens (deferred)

- **Where:** `FoodsScreen.kt`, `FoodsCommon.kt`, `CreateMealScreen.kt`,
  `AddFoodScreen.kt` (28.dp, 14.dp, 52.dp and so on).
- **Rule:** review checklist, "no `dp` magic numbers outside `:libraries:ui`".
- **Why deferred:** the same pattern is in every feature (about 30 files), most
  values have exact `Dimension` tokens but some (52, 60) don't. It wants one
  sweep with design-sync screenshots, not a per-slice edit.

## 9. Recent can show fewer than six foods (deferred, cross-slice)

- **Where:** `FoodsViewModel.kt:32` with `FoodsStateBuilder.build`'s `recent`.
- **Rule:** §1 "Code that reads true".
- **Failure:** `diary.recentFoods(limit = 6)` counts quick numbers and
  name-only lines, which the tab then drops because there is nothing to pick
  again. A day of quick adds leaves Recent with one or two rows, or none.
- **Why deferred:** the fix belongs in `DiaryRepository.recentFoods`
  (`libraries/diary`, another slice): give it a "foods only" option, or have
  it skip items with no `foodRef`, so the limit counts what the tab shows.

## Not problems

- **Support from the statement respects `FindSupportEnabled`.**
  `OnboardingFeatureEntryPoint.kt:52` opens Find support when the flag is on
  and Support (the helplines) when it is off.
- **The guest path end to end.** Get started, the questions, Start, the guest
  created under a 5 s cap with a degraded path, and "Setting things up…" on the
  disabled buttons while it runs; Skip all goes straight to Today. Covered by
  `OnboardingViewModelTest`.
- **The account step's back button reads "Back".** The coordinator asked about
  `AuthScreens.kt:122` passing `Icons.ChevronLeft(null)`. That button's content
  is `Text(stringResource(Res.string.common_back))`, so it is announced as
  "Back"; describing the chevron too would make it "Back, Back".
- **`state.preview` builds a `ModePreview` in composition.** A handful of
  lines, rebuilt only when the recommendation step recomposes.
- **The Foods tab while Room loads.** The title, search and tiles render at
  once; the cards follow a local read. Not a blank screen.
- **A Create a meal prefill that lands after typing.** It reads one local day
  before the first frame the person could type into.

## Product notes

- **Signing in with Apple on a fresh install skips setup.** `SignInViewModel`
  marks the user onboarded on any success, so an Apple ID with no account yet
  lands on Today in Full numbers without the questions. Fairly sure this will
  surprise people who tap "Sign in" by mistake.
- **Closing Create a meal throws away the foods picked.** The X has no "discard
  this meal?" step. Moderately sure it matters once people build bigger meals.
- **Android has no way to back up in 0.1.** With Apple only, an Android user's
  data lives in an anonymous account they can't recover on a new phone. Sure.
