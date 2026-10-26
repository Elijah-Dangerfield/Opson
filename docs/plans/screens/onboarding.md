# Onboarding plan

Four skippable questions and a recommended mode, folded into the existing
`:features:onboarding` guest-account flow. No pricing screen in 0.1. Read
`shared.md` first.

## Source
- Handoff: `README.md` section 4.11; `PROTOTYPE_NOTES.md` "Pricing".
- Prototype markup: `Phone.dc.html` lines 39 to 86, the `is.onb` block.
- Logic: `phone-logic.js` `Q`, `pickMode`, `onb`, `finish`.
- Existing code: `features/onboarding/impl/.../OnboardingViewModel.kt` (Welcome, PickIdentity, deferred guest creation through `GuestAccountCreator`, `ResolveEntry`, OAuth new vs returning through `AuthOutcomeClassifier`), `OnboardingFeatureEntryPoint.kt`, `OnboardingScreen.kt`.
- System design section 13 and decision 2026-10-25 "no pricing in 0.1".

## What changes in the existing flow

| Today | After |
|---|---|
| Welcome: "Welcome", guest, Google, Apple, email sign-in, create account | Welcome: the prototype's intro copy, **Set it up** (the guest path, into the questions), **Skip, start logging**, then the existing sign-in buttons unchanged |
| PickIdentity: display name, then create the guest | Removed. Opson has no social surface, so a name screen is friction for nothing. The guest is created with `DisplayNameSuggester.next()` silently. |
| New OAuth account goes to PickIdentity | Goes to Question 1 with `identityClaimed = true` |
| `ResolveEntry` re-entry (authed, not onboarded) to PickIdentity | To Question 1 with `identityClaimed = true` |
| Returning account goes Home | Goes to Today (`router.enterTab(TodayRoute())`). Its settings arrive by sync; `VisibilityPolicySource` stays Blind until they do (see `shared.md`), so a new phone never shows Full numbers first. |
| `NavigateToHome` everywhere (onboarding, sign in, sign up, verify email) | `NavigateToToday`, routed with `router.enterTab(TodayRoute())` |

Pricing (step 0 in the prototype) is not built: nothing is for sale in 0.1, and
a price with no purchase flow is an App Review rejection.

## Route
`OnboardingRoute` unchanged (`:features:onboarding`, `screen<>`). Steps are
state inside it, as now.

## State

```kotlin
// com.opson.features.onboarding.impl
sealed interface OnboardingStep {
    data object Welcome : OnboardingStep
    data class Question(val number: Int) : OnboardingStep      // 1..4
    data object Recommendation : OnboardingStep
}

data class OnboardingState(
    val step: OnboardingStep = OnboardingStep.Welcome,
    val answers: SetupAnswers = SetupAnswers(),
    val recommended: PresetId = PresetId.Delayed,               // ModeRecommender.recommend(answers)
    val picked: PresetId? = null,                               // an alternative the user tapped
    val alternatives: List<PresetId> = emptyList(),
    // kept from today's state:
    val oauthInFlight: OAuthProvider? = null,
    val authError: OnboardingAuthError? = null,
    val identityClaimed: Boolean = false,
    val creationStarted: Boolean = false,
    val isFinishing: Boolean = false,
    val creationFailed: Boolean = false,
    val appleEnabled: Boolean = false,
) {
    val chosen: PresetId get() = picked ?: recommended
    val question: SetupQuestion?                // the current question, null off the question steps
    val options: List<SetupChoice>              // the current question's options with `selected`, derived from answers
    val canGoBack: Boolean                      // drives the on-screen back button and the system back handler
}

data class SetupAnswers(
    val why: Set<Why> = emptySet(),
    val feel: NumbersFeel? = null,
    val notice: Set<Notice> = emptySet(),
    val hard: Boolean = false,
) { fun toSettings(): OnboardingAnswers }      // option keys, for Settings.onboarding

enum class SetupQuestion(val number: Int) { Why(1), Feel(2), Notice(3), Hard(4) }
sealed interface SetupOption                    // Why, NumbersFeel and Notice entries, plus HardBefore
data class SetupChoice(val option: SetupOption, val selected: Boolean)
```

Option keys (the strings `Settings.onboarding` stores, never sent to telemetry):

| Enum | Entries and keys |
|---|---|
| `Why` | Curious `curious`, Performance `performance`, EatingEnough `eating_enough`, EatingRegularly `eating_regularly`, MedicalNeed `medical_need`, ChangingWeight `changing_weight`, WorkingWithProvider `working_with_provider` |
| `NumbersFeel` | LoveThem `love_them`, SomeAreFine `some_are_fine`, RatherNot `rather_not`, NotSure `not_sure` |
| `Notice` | Amounts `amounts`, Nutrients `nutrients`, Patterns `patterns`, HowEatingFeels `how_eating_feels` |
| `HardBefore` | stored as `OnboardingAnswers.hard` |

Removed fields: `displayName`, `userEditedName`, `saveError`. No `Display`
values: onboarding shows no numbers. `canGoBack` is false on Welcome, false
everywhere while `isFinishing`, and false on Question 1 when `identityClaimed`
or `creationStarted`.

`ModeRecommender` (pure, onboarding impl), a port of `pickMode`:

| Answers | Preset |
|---|---|
| feel LoveThem | Delayed if hard, else Full |
| feel RatherNot | Rhythm if why has EatingRegularly, else Blind |
| feel SomeAreFine | Fuel if why has EatingEnough, else Nutrient |
| notice has HowEatingFeels | Mindful |
| otherwise | Blind if hard, else Delayed |

`alternatives(chosen, hard)`: the first four of `Presets.all` excluding
`chosen`, and excluding Full **and Sprint** when `hard` is true.

## Actions

| Action | Does |
|---|---|
| `ResolveEntry` (existing) | Re-entry now lands on `Question(1)`. |
| `ContinueAsGuest` (existing, the Set it up button) | `step = Question(1)`. No account yet. |
| `SkipAllTapped` | `finish(ModeRecommender.recommend(SetupAnswers()))`, which is Delayed. |
| `OptionTapped(option)` | Updates the current question's answer: single choice replaces, multi toggles, the hard option toggles. Clears `picked`. Recomputes `recommended` and `alternatives`. |
| `NextTapped` | Next question, or `Recommendation` after 4. |
| `SkipQuestionTapped` | Same as `NextTapped`, answer left as is. |
| `AlternativeTapped(preset)` | `picked = preset`; alternatives recomputed against the new `chosen`, so the recommended preset appears as an alternative and the picked one leaves the row (the prototype's behaviour). |
| `StartTapped` | `finish(chosen)`. |
| `Back` (existing) | Recommendation to Question 4, Question n to n − 1, Question 1 to Welcome unless `identityClaimed` or `creationStarted`. |
| `SignIn`, `SignUp`, `SignInWithOAuth`, `SignInWithApple` (existing) | Unchanged, except a new account continues to `Question(1)`. |

`finish(preset)`:
1. `SettingsRepository.update(stampNumbersChange = false) { it.copy(numbers = NumbersSettings(preset = preset), onboarding = answers.toSettings(), sprintStartedOn = …) }`, so `changedAt` stays null and Modes later reads "Set by you during setup." Sprint sets `sprintStartedOn = today` (from the injected `Clock` in the system time zone); any other preset leaves it as is. Safe before the account exists: the guest is the first user on the install, so no `UserScopedClearer` runs (it only clears a departing user), and the settings outbox row drains once the account is live.
2. Guest path (no session yet): `guestAccountCreator.start(PendingIdentity(DisplayNameSuggester.next()))` with the existing bounded wait. Claimed path: nothing; the server-generated profile name stands, so the VM no longer needs `ProfileRepository`.
3. `appCache.update { hasUserOnboarded = true }`, event `NavigateToToday`.

Removed actions: `DisplayNameChanged`, `RegenerateDisplayName`, `ContinueFromPickIdentity`.

## Events
`NavigateToToday` (replaces `NavigateToHome`), `NavigateToSignIn`, `NavigateToSignUp` (existing).

## Data
`SettingsRepository`, `AppCache`, `GuestAccountCreator`, `AuthRepository`,
`AuthOutcomeClassifier` (existing), `Clock` (for Sprint's start day). The
answers are saved with the preset in `Settings.onboarding` (owner's call: the
field exists so Modes can keep Full out of reach for someone who said numbers
have been hard). They travel only inside the user's own settings row, like every
other setting, and are never sent anywhere else. Skip all saves empty answers.
Offline: questions and the settings write work; account creation retries in the
background as today.

Telemetry: keep `onboarding.completed` (`duration_sec`, `account_ready`) and add
`preset` (key), `took_recommendation` (true when the started preset is the
recommended one; false on skip all, where nothing was recommended on screen) and
`skipped_all`. `onboarding.auth_selected` (`method` guest, `returning` false)
fires for Set it up and for Skip, start logging. Per-step view and abandon
events use the stable names `welcome`, `question_1` to `question_4`,
`recommendation` (`pick_identity` is gone with its step). **No answer values in
any event**: "numbers or tracking have been hard for me before" is close to a
clinical disclosure.

## Components
`:libraries:ui` buttons, a new `ChoiceRow` (the prototype's option button: surface,
1.5 dp line border that turns ink when selected, a check mark, 52 dp tall,
radius 14; `role` Checkbox for multi-select and the hard toggle, RadioButton for
the feel question), progress text, `PillChip` for alternatives (44 dp tall, as
the prototype). Back is an icon button (`AppIcons.ChevronLeft`, "Back") on the
question and recommendation steps plus a system `BackHandler`, both gated on
`canGoBack`. Existing auth buttons unchanged. The primary button is pinned at
the bottom on the question and recommendation steps; Welcome scrolls as one
column with its buttons at the bottom.

## Visibility surfaces
None: onboarding renders no numbers. It sets the preset every other surface
reads, and the Full numbers alternative disappears when `hard` is set.

## Copy

| Key | String |
|---|---|
| `onboarding_welcome_title` (changed from "Welcome") | Opson |
| `onboarding_welcome_subtitle` (changed) | Write down what you eat. See as many numbers as you want, or none. |
| `onboarding_welcome_questions` | Four short questions. You can skip all of them. |
| `onboarding_set_it_up` | Set it up |
| `onboarding_skip_all` | Skip, start logging |
| `onboarding_progress` | %1$d of 4 |
| `onboarding_skip` | Skip |
| `onboarding_q_why` / `_sub` | Why are you tracking? / Pick any. |
| options | Curious / Performance / Eating enough / Eating regularly / A medical need / Changing my weight / Working with a provider |
| `onboarding_q_feel` | How do you feel about seeing numbers? |
| options | Love them / Some are fine / Rather not / Not sure |
| `onboarding_q_notice` / `_sub` | What do you want to notice? / Pick any. |
| options | Amounts / Nutrients / Patterns / How eating feels |
| `onboarding_q_hard` / `_sub` | One optional question / This only unlocks gentler starting points. We never ask for a diagnosis. |
| option | Numbers or tracking have been hard for me before |
| `onboarding_next` | Next |
| `onboarding_reco_kicker` | A starting point, not a recommendation |
| `onboarding_reco_see` | What you'll see |
| `onboarding_preset_<key>`, `onboarding_preset_<key>_line` | the labels and lines from the Modes table in `settings.md`. Kept in the onboarding file because no shared preset-label resource exists yet; fold into one when Modes lands its own. |
| `onboarding_reco_or` | Or start with |
| `onboarding_reco_change` | Change it any time under More, then Modes and numbers. |
| `onboarding_start_with` | Start with %s |

The existing auth strings stay, moved with the rest of the `onboarding_`
strings from `strings.xml` to `strings_onboarding.xml`.
`onboarding_pick_display_name_*`, `onboarding_display_name_*`,
`onboarding_suggest_another`, `onboarding_continue` and
`onboarding_continue_as_guest` are deleted with the step.

## Edge cases and conflicts
- **Skip all defaults to Delayed reveal, not Full numbers.** Judgment call: the prototype's skip keeps the default mode (Full), but its own recommender gives Delayed for an empty set of answers. Someone who skips setup should not see a calorie total on day one without choosing to; Full is one tap away in Modes.
- **Hard answer hides Sprint too.** The handoff hides Full numbers; Sprint shows the same numbers, so hiding only Full would leave the same exposure under another name.
- **Recommendation is Full and the user ticks hard later**: going back and toggling hard recomputes, so Full can no longer be the pick or an alternative.
- **Back on Question 1 after an OAuth new account**: blocked (`identityClaimed`), as PickIdentity is today.
- **Large text**: options stack, the primary button stays pinned at the bottom.
- **Already onboarded** (`hasUserOnboarded`): `ResolveEntry` bounces to Today as now.

## Tests
- `ModeRecommenderTest`: every row of the table, the empty case is Delayed, alternatives exclude chosen, Full and Sprint excluded when hard, always four or fewer.
- `OnboardingViewModelTest` (update the existing one): Set it up goes to Question 1 without creating an account; options single vs multi vs hard toggle; Next and Skip through to Recommendation; Back at each step; Start writes `NumbersSettings(preset)` with null `changedAt` then creates the guest then marks onboarded then emits `NavigateToToday`; Sprint sets `sprintStartedOn`; Skip all writes Delayed; OAuth new account lands on Question 1; returning account emits `NavigateToToday` with no settings write; no event attribute contains an answer.
- Previews: Welcome, each question with and without selections, Recommendation (Delayed with four alternatives), Recommendation with hard set (no Full, no Sprint).
- `SURFACES`: `OnboardingLeakSurfaces.all` in impl `commonMain`, ids `onboarding-welcome`, `onboarding-recommendation` (port of the prototype's two onboarding surfaces, pricing replaced by welcome), both `showsCaloriesInFull = false`. State comes from `OnboardingFixtures`, which builds it through `ModeRecommender`, the same code the ViewModel runs.
