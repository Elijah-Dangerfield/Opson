# Findings: ui-library (`libraries/ui`, `libraries/resources`)

All 181 files in the ledger were read. Dead code was found with a whole-repo
search (Kotlin and Swift, comments stripped) and every deletion was grepped
again by name before it went. Checks: `./gradlew detekt`,
`:libraries:ui:allTests`, `:apps:compose:compileKotlinIosSimulatorArm64` and
`:apps:compose:compileDebugKotlinAndroid` are green.

Policy on the catalogs: the design system gallery (`DesignSystemGallery.kt`)
is a design-sync screen, so a component it shows counts as used. The older
template catalog pages (Forms, Buttons, Radii...) do not; a component only
they showed was deleted with its catalog section.

## 1. Every `Text` parsed its string as HTML, crashing on "</b>" (fixed)

- **Where:** `components/text/Text.kt:51` (before), via
  `components/text/HtmlTextExt.kt:26`.
- **Rule:** §4 "No work in composition"; §2 "No `!!` on data from ... the user".
- **Failure:** no string in the app uses markup, but notes, food names and
  search text are the user's own. `Regex("<[^>]*>")` deleted anything between
  angle brackets ("had <2 slices> of pizza" became "had  of pizza"), and a
  stray "</b>" called `AnnotatedString.Builder.pop()` on an empty stack, which
  throws: the screen showing that note crashed. It also ran a regex and an
  O(n²) string recursion on every recomposition of every `Text`.
- **Fix:** `fix(ui): Text draws people's text as typed instead of parsing it as
  HTML`. `TextTest` renders "Pizza <2 slices> and a stray </b>".

## 2. A text field kept text its ViewModel refused (fixed)

- **Where:** `components/text/TypedText.kt` (`rememberTypedText`), used by
  `InputBox`, `BasicTextField`, `SearchField` and `SheetMenuNumberRow`.
- **Rule:** §3 "One state"; review checklist: what is on screen is what is
  stored.
- **Failure:** TypedText (the fix for late echoes) shows what was typed and
  waits for the ViewModel to echo it. When an edit is refused the state does
  not change and no echo comes, so the field kept the refused text forever.
  Feedback and Bug report cap the message at 200 and 180 characters in their
  `onValueChange`: type past the cap and the extra text stays on screen but is
  never sent. The same happens with the photo note cap, the weight sanitizer,
  the barcode digit filter, and NumberField's own sanitizer (a second decimal
  point stayed visible).
- **Fix:** `fix(ui): a text field never keeps text its ViewModel refused`. Once
  typing pauses 300 ms with field and ViewModel disagreeing, the field settles
  on the ViewModel's value. NumberField's filter runs before TypedText, so a
  dropped key never shows. `TextFieldSemanticsTest` has both cases;
  `TypedTextTest` covers `settle` and the no-change keystroke. All three new
  tests were run against the old code and failed.

## 3. Theme colors made from `withAlpha` never compared equal (fixed)

- **Where:** `system/color/ColorResource.kt:108` (`FromColor`, before).
- **Rule:** §4 "Stable parameters".
- **Failure:** every theme token and every `withAlpha(...)` is a `FromColor`,
  a plain class with identity equality. A composable handed
  `onHero.withAlpha(0.7f)` got a "new" argument on every recomposition and
  never skipped; `Screen` provided a new `LocalContentColor` each time, which
  invalidated every reader beneath it.
- **Fix:** `perf(ui): let composables skip on theme tokens, and decode
  thumbnails off main`. Equal by color and name; `ColorResourceTest`.

## 4. Type variants built a new `TextStyle` on every read (fixed)

- **Where:** `system/typography/TypographyResource.kt` (`Bold`, `ExtraBold`,
  `Tabular`... were `get() = copy(...)`).
- **Rule:** §4 "No ... allocation of big objects in a composable body".
- **Failure:** `AppTheme.type.bodyBold.ExtraBold` in a composable body built a
  `TypographyResource` and its `TextStyle` on every recomposition.
- **Fix:** same perf commit; each variant is built once per style.

## 5. `AppTheme` rebuilt Material's guard theme on every recomposition (fixed)

- **Where:** `system/AppTheme.kt:106` (`MaterialWrapper`).
- **Rule:** §4 "No work in composition".
- **Failure:** a `Typography` and a 29-colour `ColorScheme` were allocated each
  time; `ColorScheme` has no value equality, so Material's locals changed too.
- **Fix:** same perf commit; they are constants.

## 6. `PhotoThumbnail` decoded a JPEG on the main thread in composition (fixed)

- **Where:** `components/PhotoThumbnail.kt:40`.
- **Rule:** §2 "Never block main"; §4 "No work in composition".
- **Failure:** decoding the captured photo (up to the scanner's max edge) ran
  synchronously in `remember`, a dropped frame or two as the estimate sheet
  opens.
- **Fix:** same perf commit; `produceState` on `Dispatchers.Default`.

## 7. Clickable rows, cards and chips had no role (fixed)

- **Where:** `system/BounceClick.kt:53`; reaches `ListRow`, `EdgeCard`,
  `MenuItem`, `CenterAddButton`, `PagerChevron` and most of the app's buttons.
- **Rule:** §8 Accessibility.
- **Failure:** a screen reader announced them as plain text, not buttons.
- **Fix:** `fix(ui): buttons say they are buttons, and small controls get a
  48 dp tap`. `bounceClick` defaults to `Role.Button`; `BounceClickTest`.

## 8. Touch targets under 48 dp (fixed)

- **Where:** `components/icon/IconTile.kt:95` (`RoundIconButton`,
  `touchTarget = size`), `components/SheetHeader.kt:101` (the close button,
  44 dp, whose comment promised 48), `components/SheetMenu.kt:137` (the water
  sheet's number-row action, 40 dp), `components/chip/PillChip.kt:118,186`
  (`ActionChip`, `ChoiceChip`: 48 tall but as narrow as the label, 32 dp for
  "g").
- **Rule:** §8 "44 pt targets" (design v3 asks 48).
- **Fix:** same accessibility commit. `RoundIconButton` lays out at 48 by
  default (only the sheet close button relied on the old default; it grows
  4 dp); the others take `minTouchTarget()`.

## 9. Labels cut off in large text (fixed)

- **Where:** `components/button/BasicButton.kt:182-211` (`maxLines = 1`),
  `components/SegmentedControl.kt:73`.
- **Rule:** §8 "dynamic type that doesn't truncate".
- **Failure:** "Just the name" in a three-way control, or a half-width button
  label, lost its end with large text on.
- **Fix:** same accessibility commit; two lines, centred.

## 10. A disabled Stepper button was an unlabelled icon to screen readers (fixed)

- **Where:** `components/Stepper.kt:79`.
- **Rule:** §8.
- **Failure:** disabling dropped the clickable, so its semantics (role,
  disabled state) went with it.
- **Fix:** same accessibility commit.

## 11. Hard-coded English in spoken labels (fixed)

- **Where:** `components/header/Header.kt:68` ("Navigate back"),
  `components/Snackbar.kt:124` ("Close"), `components/dialog/Dialog.kt:227`
  (`stateDescription = "Dialog"`, also redundant with `dialog()`).
- **Rule:** review checklist: copy lives in `:libraries:resources`.
- **Fix:** same accessibility commit (the snackbar one in the snackbar commit).

## 12. Boot captions were Kotlin literals and looped forever in previews (fixed)

- **Where:** `components/CyclingLoadingMessage.kt:49,82`.
- **Rule:** §4 "Previews and screenshots don't spin"; copy in resources.
- **Failure:** "Getting things ready" and friends were out of reach of
  translation and VerifyStrings. In a preview the `while (true)` delay loop
  ran for as long as the composition lived.
- **Fix:** `fix(ui): boot loading captions come from resources and hold still
  in previews`.

## 13. Snackbar callbacks lived in an unguarded, leaking map (fixed)

- **Where:** `snackbar/SnackBarPresenter.kt:75,85,119` (before).
- **Rule:** §2 structured concurrency and thread safety; no leaks.
- **Failure:** `show()` runs on whatever thread calls it (`Router.toastOnError`
  is called from background coroutines) and wrote a plain `HashMap` while the
  host removed entries on main. A message emitted with no host collecting, or
  dropped by `DROP_OLDEST`, left its entry, and its captured lambdas, forever.
- **Fix:** `fix(ui): snackbar callbacks travel with their message`; the host
  calls the message's own callbacks. `SnackbarMessageTest`.

## 14. The snackbar failure log printed the message text (fixed)

- **Where:** `snackbar/PresenterSnackbarHost.kt:62`.
- **Rule:** §5 "Never ... food names, notes ...". Toast copy carries food and
  meal names.
- **Fix:** snackbar commit; logs the id.

## 15. A restored bottom sheet lost its skip flags (fixed)

- **Where:** `components/dialog/bottomsheet/BottomSheetState.kt:93` (`Saver`).
- **Rule:** §8 Android "process death survives".
- **Failure:** restore used the default `skipPartiallyExpanded = true`; a sheet
  restored while PartiallyExpanded would hand Material a combination it
  rejects with an exception. Latent: no sheet rests half open today.
- **Fix:** `fix(ui): a restored bottom sheet keeps the skip flags it was made
  with`; `BottomSheetStateSaverTest`.

## 16. `expandOnDragUp` used `composed` (fixed)

- **Where:** `components/dialog/bottomsheet/ExpandOnDragUp.kt:29`.
- **Rule:** §4 (the reason `bounceClick` already gives in its KDoc).
- **Fix:** same commit as 15; a `@Composable` modifier factory.

## 17. `BasicDialog` applied the caller's modifier twice (fixed)

- **Where:** `components/dialog/BasicDialog.kt:43`.
- **Failure:** passed to `Dialog` and again to the `ModalContent` inside it;
  `fillMaxWidth(0.9f)` came out at 0.81 (ShakeDialog is the caller).
- **Fix:** `fix(ui): BasicDialog applies the caller's modifier once`.

## 18. `PageHeader` was written twice (fixed)

- **Where:** `components/PageHeaderSubtitle.kt` (the whole file).
- **Rule:** §1 "Match the neighbours"; duplication that had drifted.
- **Failure:** the subtitle overload copied the back button, label logic and
  title, and could not show Close or actions.
- **Fix:** `refactor(ui): one PageHeader, with an optional subtitle`. The
  preview stays in `PageHeaderSubtitle.kt` so its design-sync id holds.

## 19. Components, helpers and resources nothing used (fixed)

- **Where:** 67 files and parts of 20 more; the commit body lists them.
  Template-era components never adopted or dropped in design sync 2
  (BarChart, the dropdowns, ListItem, ProgressRow, the old bottom bar and
  badge, the underline and outline input fields, Checkbox, CircleCheckbox,
  Slider, RadioButton and RadioGroup, the typewriter effect, ScrollBar,
  ImageUtils, the UIColor bridge), helpers (Point, whose `equals` threw on a
  non-Point; Util, Toggle, pulsate, fadingEdge, FieldState with a KDoc cut off
  mid-word), unused public functions in live files, unused icons, the
  `Icons` enum's 35 unused entries, and a placeholder test asserting 1 + 2 = 3.
- **Rule:** §1 "No dead code".
- **Fix:** `refactor(ui): delete the components, helpers, fonts and strings
  nothing uses`.

## 20. 1.6 MB of fonts and five libraries shipped unused (fixed)

- **Where:** `libraries/ui/src/commonMain/composeResources/font/` (Roboto x5,
  Poppins x5, DM Serif x2), `libraries/ui/build.gradle.kts:43-47` (compottie,
  compottie-network...), `compose.material3AdaptiveNavigationSuite` in both
  build files.
- **Rule:** §1; app size.
- **Failure:** nothing referenced them. Removing compottie also showed that
  `RecompositionCounter` had been using Ktor's clock, which only reached
  `:libraries:ui` through compottie-network; it uses `TimeSource.Monotonic`
  now (and starts its cooldown so the first rapid burst is still logged).
- **Fix:** same refactor commit.

## 21. Ten strings nothing reads (fixed)

- **Where:** `trends_placeholder`, `barcode_close_cd`, `photos_back`,
  `today_meal_yesterday`, `today_meal_copy`, `today_meal_saved`,
  `today_feel_title`, `today_feel_subtitle`, `today_feel_mood`,
  `today_weight_at`. (`hold/0.2-widgets-native` mentions some of them, but only
  in files it inherits unchanged from an older main; its own two commits touch
  none of them.)
- **Fix:** same refactor commit.

## 22. Placeholder and bare TODOs (fixed)

- **Where:** `libraries/resources/.../Resources.kt` ("TODO: Add your library
  implementation here", an object nothing used, in a `com/opsone` directory
  that did not match its package); `libraries/ui/build.gradle.kts:24` (a bare
  TODO over an unused `libraries.opson` dependency); a `/*TODO*/` in a
  `BasicDialog` preview.
- **Rule:** §1 "TODOs carry an owner tag".
- **Fix:** refactor commit and the BasicDialog commit.

## 23. Comments that no longer matched the code (fixed)

- **Where:** `HeroBlock.kt:48` (called itself Today's block; Today dropped it),
  `Progress.kt:36` ("every bar and ring" reads `barFill`; RangeBar and the
  nutrient bars do not), `IntakeBarChart.kt:47` (pointed at the deleted
  BarChart), `FlatButton.kt:62` ("full width by default"; it wraps), catalog
  copy (serif and sans faces, a 25% button radius, pages "reused by
  DesignSystemPreview"), `Dots.kt` and `FormCatalog.kt` pointing at deleted
  components, an orphaned KDoc in `Dialog.kt`.
- **Rule:** §1 "Stale comments are bugs".
- **Fix:** `docs(ui): comments that no longer matched the code`, and the
  commits that touched those files.

## 24. Two `MinTouchTarget` constants (deferred, cross-slice)

- **Where:** `components/HeroParts.kt:84` and `system/TouchTarget.kt:9`, plus
  `AppSpacing.touchTarget`; all 48 dp.
- **Why deferred:** Today imports the components one. The components constant
  is now an alias of the system one with a `TODO(review)`. Exact change: in
  `features/today/impl/.../TodayLeakSurfaces.kt:7`, `TodaySelectViews.kt:31`
  and `TodayScreen.kt:72`, import `com.opson.libraries.ui.system.MinTouchTarget`
  instead of `com.opson.libraries.ui.components.MinTouchTarget`; then delete
  `HeroParts.kt:83-85`.

## 25. Components only the gallery shows (deferred, design decision)

- **Where:** `HeroBlock`, `PagerDots` (`HeroBlock.kt`), `FillBar`,
  `ProgressRing`, `RingSegment(s)` (`Progress.kt`), shown only by the gallery's
  hero section.
- **Why deferred:** the gallery counts as a use, and dropping the hero section
  changes a design-sync screen. Comments now say Today no longer uses them.

## 26. Button colours animate through composition (deferred)

- **Where:** `components/button/Button.kt:81-96`.
- **Failure:** the background and content colour animations are read with
  `.value` and passed to `BasicButton`, so a button recomposes on every frame
  of the 300 ms colour change when it is enabled or disabled.
- **Why deferred:** small and rare; the fix is colour lambdas through
  `BasicButton` and `Surface`, a wider change than the cost.

## 27. Names in other slices that point at deleted code (deferred, cross-slice)

- `detekt-rules/.../ScrollInsideBottomSheet.kt:13,36-38`: drop the
  `BasicBottomSheet` mentions (the component is gone).
- `detekt-rules/.../VerifyStrings.kt:61-65`: `TEXT_CALLEES` can lose
  `OutlinedText`, `ClickableText` and `BoldPrefixedText`, and the AsteriskText
  comment can go.
- `docs/practices/design-sync.md:83`: the preview-id example uses `BarChart.kt`;
  use `IntakeBarChart.kt` / `IntakeBarChartPreview` instead.
- `gradle/libs.versions.toml:24,263-267`: the `compottie` version and five
  library entries are unused now.

## 28. Hard-coded copy and an unlabelled button in callers (deferred, cross-slice)

- `features/home/impl/.../BugReportViewModel.kt:57` ("Thanks for reporting
  this!") and `FeedbackViewModel.kt:46` ("Got it. Thank you!") pass English
  literals to `showSnackBar`; `libraries/navigation/.../Router.kt`
  `toastOnError` defaults to "Oops something went wrong". Move to resources.
- `features/onboarding/impl/.../AuthScreens.kt:119`: `Icons.ChevronLeft(null)`
  on an icon button gives it no spoken label; use
  `stringResource(SharedRes.string.ds_back)`.

## 29. Material's guard scheme uses a deprecated constructor (deferred)

- **Where:** `system/AppTheme.kt:127`.
- **Failure:** the old `ColorScheme` constructor leaves the newer
  `surfaceContainer*` roles at Material defaults instead of the red that makes
  a leaked Material colour obvious. Nothing users see; noted for whoever next
  touches the theme.

## Looked at, not a problem

- **TypedText stale echoes.** When a StateFlow conflates, `indexOf` can leave
  older sends in `pending`. They are capped at 64, cleared by the next value
  the field did not send, and now by `settle`. `typed.send = onValueChange`
  during composition writes a plain field, the same as `rememberUpdatedState`.
- **SearchField.** The clear button is 48 dp with a role; both callers pass
  `clearDescription`. Focus-on-open is skipped in inspection mode.
- **DelayedLoadingIndicator.** Visible at once in previews, so screenshots are
  still. The description is set during the empty first 500 ms, so a screen
  reader hears "Loading" straight away, which is the right behaviour.
- **Charts without semantics** (`IntakeBarChart`, `RangeBar`, `TimelineTrack`).
  Giving them range info would let VoiceOver read a percentage in Blind; the
  screens describe them in text.
- **`Catching` around `showSnackbar`** rethrows cancellation
  (`shouldNotBeCaught`).
- **`rememberLoopingFloat`** has no callers but is the sanctioned way to loop
  (AGENTS.md "Infinite animations", the `AnimatedStateReadInComposition` rule
  names it). Kept.
- **Lazy lists** in the slice without keys are previews only.

## Product notes

- **Times are always 12-hour.** `DateCopy.time` prints "9:41 PM" and both time
  pickers pass `is24Hour = false`, whatever the phone's setting. Anyone outside
  the US will notice on the first meal time. Sure.
- **Feedback and Bug report cap the message without saying so.** 200 and 180
  characters, no counter; the field now stops at the cap, but a person writing
  a real bug report hits a wall with no explanation. A visible count, or a
  higher cap, would help. Fairly sure.
- **The design gallery shows a hero the app no longer has** (rings, pager dots,
  the coloured top block). Designers syncing against it see parts that are not
  in the product. Decide whether the gallery should drop them. Sure it
  confuses; unsure which way design wants it.
- **Page dots are 20 dp wide targets** on Today's summary pager. Swiping works,
  but tapping a dot is fiddly. Low confidence it matters.
