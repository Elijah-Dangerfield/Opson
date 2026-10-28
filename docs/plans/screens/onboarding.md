# Onboarding plan

**Superseded for the account flow by 908798dd:** there is no guest, no Skip all
and no account step. Sign-in is required and comes straight after Welcome; see
`docs/plans/onboarding-v2.md` for the flow as built. Guest creation
(`GuestAccountCreator`) and `DisplayNameSuggester` below describe 0.1.

Welcome with the plan, three skippable questions, an optional statement, a
starting mode that lists what you'll see and what stays out of view, and an
optional account step last. Built to design v3. Read `shared.md` first.

**Superseded in part by `docs/plans/onboarding-v2.md`** (2026-10-27): "What do
you want to notice?" is cut, the starting point says why it was picked, a new
Apple account runs setup, and the goal, resting energy and meal times steps
follow the mode behind `release.onboardingSetup`. Where the two disagree, v2
wins.

## Source
- Design v3: `docs/design/v3/CHANGES.md` (global rules, the Onboarding row,
  "Pricing on Welcome"), `manifest.json` notes for `onboarding-welcome`,
  `onboarding-why`, `onboarding-numbers`, `onboarding-statement`,
  `onboarding-recommendation`, `onboarding-account`, and their images. v3 wins
  over the handoff wherever they disagree.
- Handoff, for what v3 does not redraw: `README.md` 4.11, `phone-logic.js`
  `pickMode`.
- Existing code: `features/onboarding/impl/.../OnboardingViewModel.kt`,
  `OnboardingScreen.kt`, `ModeRecommender.kt`, `SetupAnswers.kt`, the auth
  screens and their view models (unchanged).

## What changes from the previous build

| Before | v3 |
|---|---|
| Welcome: intro, Set it up, Skip start logging, then Google, Apple, email sign-in and create account | Welcome: intro, **the plan card** (Free and what it includes; Plus "Coming later", no price), **Get started**, **Skip and start logging**, and one "Have an account? **Sign in**" link to the existing `SignInRoute` (which has Google, Apple and email). No OAuth buttons on Welcome. |
| Skip all saved Delayed reveal | Skip all saves **Full numbers** and lands on Today, with no account step |
| Four questions, the fourth a "hard" toggle | Three questions, then the **statement** step ("4 of 4 · Optional"): a quote the person can agree with, two radio rows, and what happens with the answer. Never a question about a diagnosis. |
| "2 of 4" text, round back button, Skip | A top bar: back arrow (48 dp), a **segmented progress bar** (four segments), Skip. "n of 4" stays as a small label above the title. |
| Option rows with a trailing check | **Checkbox rows** (leading box) for multi-select, **radio rows** for single choice; selected rows are tinted |
| Question 3 multi-select | Question 3 is single choice ("uses this same layout" as question 2) |
| Recommendation: one sentence | "Your starting point", the mode, one line, then a **You'll see** card and a **Kept out of view** card, then three alternatives and **All modes** |
| Start finished onboarding | Start saves the mode, creates the guest, then shows the **account step**. Claimed identities and a guest that could not be created go straight to Today. |
| New OAuth or Apple account from Welcome ran the questions | Gone with the Welcome buttons. A brand-new email account confirmed through `VerifyEmail` still lands on question 1 with `identityClaimed`. |

## Design sync 2

The owner's export (`docs/plans/design-sync-2.md`, ids
`onboarding-screen__onboarding-*`) is the spec now. Most of it was already
built to v3. What changes:

| Screen | Design sync 2 | Build |
|---|---|---|
| Statement (1.4) | "Talk to someone" link (support icon, accent, bold) above See my starting point, shown whichever answer is picked, opens Find support (10.3) | New action `TalkToSomeoneTapped`, event `NavigateToSupport`. The entry point routes it through `:features:settings`. `FindSupportRoute` is being built by the Settings agent in parallel; until it lands here the link opens `SupportRoute` (`TODO(design-sync-2)`). |
| Recommendation (1.5) | Outlined eye and eye-off icons | `AppIcons.VisibilityOutline`, `AppIcons.VisibilityOffOutline` added to `:libraries:ui` |
| Questions (1.2, 1.3) | Segmented progress bar and back arrow | Already built |
| Welcome, Account | No layout change | Unchanged |

Kept on purpose, with the reason:
- **Welcome**: Free only. No Plus row, no price and no "Coming later": 0.1
  sells nothing, and a price with no way to buy is an App Review rejection.
  The name stays "Opson" (the design's "Friendly Tracking" is a placeholder).
  Free reads "Logging, saved meals and every mode": barcode scan is behind
  `release.barcode`, so the design's "barcode scan, recipes" would advertise
  what isn't on.
- **Statement body** keeps "the answer is used for nothing else". The design's
  "stays on this phone" isn't true: settings sync to the person's own row.
- **Account**: Apple (iOS) and "Keep it on this phone" only. Google and email
  stay hidden by `SignInMethods` for 0.1. The card keeps "Private to you. No ad
  tracking." (the server can read rows, so "Encrypted. Only you can read it."
  can't be promised) and drops the coach line (coaching is v2). The footer keeps
  "Everything works without an account.": More has no account entry yet, so
  "You can add an account later in More." would point nowhere.

## Route
`OnboardingRoute` unchanged. Steps are state inside it.

## State

```kotlin
sealed interface OnboardingStep {
    val eventName: String
    data object Welcome          // "welcome"
    data class Question(val question: SetupQuestion)   // question.eventName
    data object Recommendation   // "recommendation"
    data object Account          // "account"
}

enum class SetupQuestion(val number: Int, val multiSelect: Boolean, val eventName: String) {
    Reason(1, true, "question_1"), Feeling(2, false, "question_2"),
    Focus(3, false, "question_3"), Statement(4, false, "statement"),
}

data class SetupAnswers(
    val why: Set<Why> = emptySet(),
    val feel: NumbersFeel? = null,
    val notice: Notice? = null,
    val statement: StatementAnswer? = null,
) {
    val hard: Boolean            // statement == Agree
    val isEmpty: Boolean         // no answer that says anything (Not really counts as none)
    fun toSettings(): OnboardingAnswers   // notice as a one-item list, hard from the statement
}

enum class StatementAnswer : SetupOption { Agree("agree"), NotReally("not_really") }

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val answers: SetupAnswers = SetupAnswers(),
    val recommended: PresetId,                 // ModeRecommender.recommend(answers)
    val picked: PresetId? = null,
    val alternatives: List<PresetId>,          // three, or every offered mode after All modes
    val showAllModes: Boolean = false,
    val accountInFlight: AccountMethod? = null,  // Google or Apple link running
    val accountError: OnboardingAccountError? = null,
    val identityClaimed: Boolean = false,
    val creationStarted: Boolean = false,
    val isFinishing: Boolean = false,          // Start or Skip all running
    val creationFailed: Boolean = false,
    val appleEnabled: Boolean = false,
) {
    val chosen: PresetId
    val question: SetupQuestion?
    val options: List<SetupChoice>
    val progress: Int                          // 1..4 on the question steps
    val preview: ModePreview                   // ModePreview.of(Presets[chosen])
    val canGoBack: Boolean
}
```

Removed: `oauthInFlight`, `authError` (the Welcome sign-in errors), the
`HardBefore` option.

`canGoBack`: false on Welcome, false while finishing or linking, false on
question 1 when `identityClaimed` or `creationStarted`; true on the
recommendation and on the account step (back goes to the recommendation).

### ModeRecommender

| Answers | Preset |
|---|---|
| nothing that says anything (`isEmpty`) | **Full** (the same as Skip all) |
| feel LoveThem | Delayed if hard, else Full |
| feel RatherNot | Rhythm if why has EatingRegularly, else Blind |
| feel SomeAreFine | Fuel if why has EatingEnough, else Nutrient |
| notice HowEatingFeels | Mindful |
| otherwise | Blind if hard, else Delayed |

`alternatives(answers, chosen, recommended, all)`: the recommended mode first
(so a picked alternative swaps places with it), then modes the answers point at
(EatingRegularly Rhythm, SomeAreFine or notice Nutrients Nutrient, EatingEnough
Fuel, HowEatingFeels Mindful, LoveThem Full then Delayed, RatherNot or hard
Blind), then Blind, Delayed, Nutrient, Full; without `chosen`, without Full and
Sprint when hard, distinct, the first three. With `all`, every offered mode in
`Presets.all` order except `chosen`. The design's Fuel case (eating enough,
eating regularly, some are fine) gives Rhythm, Nutrient focus, Blind.

### ModePreview (pure, onboarding impl)

Built from the preset's own levels, so the list can never say a number is out
of view when the policy shows it. Water and vitamins are left out (every gentle
mode softens water; vitamins are off in every preset).

| Subject | Show | Soften | Delay | Hide |
|---|---|---|---|---|
| Calories | seen: Calorie amounts | seen: A word for energy; kept out: Calorie amounts and watch energy | seen: Calories once the day is closed (once a week for Coast) | kept out: Calorie amounts and watch energy |
| Macros, grouped by level | seen: "… amounts" | seen: "… as fill bars" | seen: "… once the day is closed" | kept out: the group |
| Weight | seen: Your weight | kept out: Weight shows as a trend only | kept out: (same as Soften) | kept out: Weight |
| Meal count (`mealCount`) | seen: How many meals you've had | | | |
| Layout | Meals: What you ate, meal by meal; Rhythm: A checklist of your meals; Explorer: The new foods you try; Journal: How each meal felt | | | |

Seen is ordered shown, then softened, then later; each line carries its mark
(eye, soft, later, eye-off) for the icon. Fuel reads exactly as the design.

## Actions

| Action | Does |
|---|---|
| `ResolveEntry` (existing) | Onboarded: Today. Claimed, not onboarded: question 1 with `identityClaimed`. Stranded guest: adopt, Today. Else log `welcome`. |
| `GetStartedTapped` | Question 1. No account yet. |
| `SkipAllTapped` | Welcome only. `finish(Full, empty answers, skippedAll)`, then Today. No account step. |
| `SignInTapped` | Event `NavigateToSignIn`. |
| `OptionTapped(option)` | Multi toggles, single replaces. Clears `picked` and `showAllModes`, recomputes. |
| `ContinueTapped` | Next question, statement, then the recommendation. |
| `SkipQuestionTapped` | Same move, answer left as it is. Nothing asks again later. |
| `AlternativeTapped(preset)` | `picked = preset`, alternatives recomputed against it. |
| `AllModesTapped` | `showAllModes = true`. |
| `StartTapped` | `finish(chosen, answers)`, then the account step for a fresh guest, Today otherwise. Allowed again after Back from the account step: it saves the new choice and skips creating a guest that exists. |
| `SaveWithAppleTapped` | iOS. Apple credential, then `linkAppleIdentity` onto the guest. Success: Today. Dismissed: quiet. `AlreadyOnAnotherAccount`: `AccountInUse`, never a session switch (that would wipe the settings just saved). |
| `SaveWithGoogleTapped` | `linkOAuthIdentity(Google)`. Same outcomes. |
| `SaveWithEmailTapped` | Event `NavigateToSignUp`; `SignUpViewModel` links the email to the anonymous guest, `VerifyEmail` lands on Today. |
| `KeepOnPhoneTapped` | Today. |
| `TalkToSomeoneTapped` | Statement step. Event `NavigateToSupport`; the step and answers stay as they are. Logs nothing. |
| `Back` | Account to recommendation, recommendation to the statement, question n to n − 1, question 1 to Welcome unless claimed or started. |

`finish(preset, answers)`:
1. `SettingsRepository.update(stampNumbersChange = false) { startingWith(preset, answers, today) }` (Sprint sets `sprintStartedOn`).
2. No session yet: `guestAccountCreator.start(PendingIdentity(DisplayNameSuggester.next()))`, bounded 5 s wait; a timeout or failure sets `creationFailed`.
3. `hasUserOnboarded = true`, `onboarding.completed` (first time only).
4. Skip all, a claimed identity, or `creationFailed`: `NavigateToToday`. Otherwise `step = Account`.

## Events
`NavigateToToday`, `NavigateToSignIn`, `NavigateToSignUp`, `NavigateToSupport`
(Find support, `SupportRoute` until `FindSupportRoute` exists).

## Data
`SettingsRepository`, `AppCache`, `GuestAccountCreator`, `AuthRepository`
(`current`, `linkOAuthIdentity`, `linkAppleIdentity`), `AppleSignInCoordinator`,
`AuthOutcomeClassifier` is no longer needed, `Clock`. The answers are saved with
the preset in `Settings.onboarding`, which syncs inside the user's own settings
row like every other setting. They never reach telemetry.

## Telemetry
- `onboarding.step_viewed` `step`: `welcome`, `question_1`, `question_2`,
  `statement` (was `question_4`), `recommendation`, `goal`, `resting_energy`,
  `meal_times`, `account`. `question_3` went in onboarding v2.
- `onboarding.auth_selected` (`guest`, false) for Get started and Skip all;
  (`guest`, true) for an adopted stranded guest. The Welcome OAuth and Apple
  entries are gone.
- `onboarding.completed` unchanged (`duration_sec`, `account_ready`, `preset`,
  `took_recommendation`, `skipped_all`), once, when the mode is first saved.
- New `onboarding.account_choice` `choice`: `phone`, `google`, `apple`,
  `email`, when the person leaves the account step that way (Google and Apple
  on a successful link only).
- `onboarding.abandoned` `step`: never after `completed`.
- **No answer in any event.** Update `docs/practices/app-events.md` and the
  `onboarding-funnel` dashboard's step list.

## Components
`:libraries:ui`: `ChoiceRow` restyled to v3 (leading checkbox for
`Role.Checkbox`, leading `RadioDot` for `Role.RadioButton`, tinted fill and
accent border when selected, 56 dp, bold label), `SegmentedBar` for progress,
`ButtonPrimary`/`ButtonSecondary`/`ButtonGhost`, `EdgeCard`, `PillChip`,
`AppIcons` (adds `Visibility`, `BlurOn` for softened, `Mail`, `Group`). The top
bar is onboarding's own (arrow, progress, Skip), drawn like `PageHeader`'s
Material top app bar; `PageHeader` itself needs a title, which these steps do
not have. The primary button is pinned at the bottom on the question,
statement and recommendation steps. Welcome and account scroll as one column
with their actions at the bottom. 48 dp touch targets throughout.

## Visibility surfaces
None: onboarding renders no numbers. `OnboardingLeakSurfaces`:
`onboarding-welcome`, `onboarding-recommendation` (plus `onboarding-statement`
and `onboarding-account`), all `showsCaloriesInFull = false`, built from
`OnboardingFixtures`, which runs the real `ModeRecommender`.

## Copy
Verbatim from the v3 images except where noted. `strings_onboarding.xml`.

| Key | String |
|---|---|
| `onboarding_welcome_title` | Opson (the design's "Friendly Tracking" is the placeholder name) |
| `onboarding_welcome_subtitle` | Write down what you eat. See as many numbers as you want, or none. |
| `onboarding_welcome_body` | You can show, soften, save for later or hide every number, and change it whenever you like. |
| `onboarding_plan_free` / `_free_includes` | Free / Logging, saved meals and every mode (design: "barcode scan, recipes"; barcode is hidden in 0.1) |
| `onboarding_plan_plus` / `_plus_later` / `_plus_includes` | Plus / Coming later / Photo estimates and watch quick-log (no price, see CHANGES.md) |
| `onboarding_get_started` | Get started |
| `onboarding_skip_all` | Skip and start logging |
| `onboarding_have_account` / `onboarding_sign_in` | Have an account? / Sign in |
| `onboarding_progress` | %1$d of 4 |
| `onboarding_progress_optional` | %1$d of 4 · Optional |
| `onboarding_skip`, `onboarding_continue` | Skip, Continue |
| `onboarding_q_why`, `onboarding_pick_any` | Why are you tracking? / Pick any that fit. |
| why options | Curious what I eat / Training or performance / Eating enough / Eating regularly / A medical need / Changing my weight / Working with a provider |
| `onboarding_q_feel`, `onboarding_feel_intro`, `onboarding_feel_footnote` | How do numbers sit with you? / Not everyone does well seeing calories, or thinking about them much. / This only picks where you start. You can change any number later. |
| feel options | I like seeing them / Some are fine, just not calories / I'd rather not see them / I'm not sure yet |
| `onboarding_q_notice` | What do you want to notice? (options unchanged) |
| `onboarding_q_statement` | Does this sound like you? |
| `onboarding_statement_quote` | "Numbers or tracking have been hard for me before." |
| `onboarding_statement_body` | If you say yes, you get gentler starting points. We don't ask why, and the answer is used for nothing else. (`TODO(clinician)`; the design says "stays on this phone", which is not true: settings sync) |
| `onboarding_statement_yes` / `_no` | Yes, that's me / Not really |
| `onboarding_talk_to_someone` | Talk to someone |
| `onboarding_see_starting_point` | See my starting point |
| `onboarding_reco_kicker` | Your starting point |
| `onboarding_reco_see` / `onboarding_reco_kept_out` | You'll see / Kept out of view |
| `onboarding_preview_*` | the ModePreview lines above |
| `onboarding_reco_or`, `onboarding_all_modes` | Or start with / All modes |
| `onboarding_start_with`, `onboarding_reco_change` | Start with %1$s / Change it any time under More, then Modes and numbers. |
| `onboarding_preset_fuel_line` (changed) | Eating enough comes first. Other lines unchanged except Full, which drops the watch (0.2). |
| `onboarding_account_title` / `_body` | Save your setup / Back up your logs and settings, and pick up on a new phone. |
| `onboarding_account_google` / `_apple` / `_email` | Continue with Google / Continue with Apple / Continue with email |
| `onboarding_account_private` | Private to you. No ad tracking. (design: "Encrypted. Only you can read it.", which the server cannot promise) |
| `onboarding_account_export` / `_coach` | Export or delete it any time / Needed later to share with a coach |
| `onboarding_account_keep` / `_later` | Keep it on this phone / You can add an account later in More. (More has no account entry yet; flagged) |
| `onboarding_account_in_use` | That account is already in use. Try another way, or keep it on this phone. |

## Edge cases
- **Skip all is Full numbers** (v3). An empty set of answers through the
  questions recommends Full too, so the two paths agree.
- **Statement yes hides Full and Sprint** from the recommendation and the
  alternatives, as before.
- **Back from the account step** returns to the recommendation; Start again
  re-saves the new choice and does not create a second guest or a second
  `onboarding.completed`.
- **Guest creation failed or timed out**: no account step (nothing to claim);
  Today, as before.
- **A claimed identity** (verified email) skips the account step.
- **Large text**: rows grow, the primary button stays pinned.

## Tests
- `ModeRecommenderTest`: every row, empty is Full, statement yes alone is
  Blind, the design's Fuel alternatives, Full and Sprint never offered when
  hard, All modes lists every offered mode.
- `ModePreviewTest`: Fuel matches the design; for every preset nothing hidden
  is in "seen", every shown subject is in "seen"; every preset sees something.
- `SetupAnswersTest`: toggles, replaces, `isEmpty`, `toSettings`.
- `OnboardingViewModelTest`: each action above (Talk to someone navigates and
  leaves the step and answers alone, with either answer picked), the account step outcomes, Skip
  all saves Full, back from the account step, no answer in any event.
- Previews named for the design ids: welcome, why, numbers, notice, statement,
  recommendation (Fuel), recommendation hard, all modes, account (Android and
  iOS), plus dark and large text.
