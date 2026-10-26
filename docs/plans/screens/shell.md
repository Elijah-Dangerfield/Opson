# Shell plan

The bottom navigation, tab switching, the center + that opens the Logger, and
the two things provided once at the root: `LocalVisibilityPolicy` and the
theme. Read `shared.md` first.

## Source
- Handoff: `docs/design/handoff/README.md` section 3 (IA), section 5 (tokens, center + shadow), section 6 (settings).
- Prototype markup: `Phone.dc.html`, the `showNav` block (bottom nav, `nav` list).
- Logic: `phone-logic.js` `navItems`, `navActive`, `nav`.
- System design sections 10 and 11. Cards: `libraries/navigation/.../Route.kt` (`TabRoute`), `Router.kt` (`switchTab`, `tabReselects`, `enterTab`), `impl/DelegatingRouter.kt` (`executeSwitchTab`), `apps/compose/.../App.kt` (`AppChromeBottomBar`, `isSwitchingTabs`).

## Modules and classes

| Class | Module / package |
|---|---|
| `TabRoute`, `Router.switchTab`, `enterTab`, `tabReselects`, `notifyTabReselected`, `OnTabReselected` | `:libraries:navigation`, `com.opson.libraries.navigation` (port from Cards verbatim, including the `@Deprecated(level = ERROR)` `navigate(TabRoute)` guard) |
| `DelegatingRouter.executeSwitchTab` | `:libraries:navigation:impl` |
| `TodayRoute` | `:features:today`, `com.opson.features.today` |
| `TrendsRoute` | `:features:trends`, `com.opson.features.trends` |
| `FoodsRoute` | `:features:foods`, `com.opson.features.foods` |
| `MoreRoute` | `:features:settings`, `com.opson.features.settings` |
| `LoggerRoute` | `:features:logger`, `com.opson.features.logger` (see `logger.md`) |
| `ShellViewModel`, `ShellState`, `AppChromeBottomBar`, `AppShellProviders` | `:apps:compose`, `com.opson.shell` |
| `BottomNavBar`, `CenterAddButton`, `AppThemeProvider(palette, dark, largeText)` | `:libraries:ui` (parallel build) |

## Routes

```kotlin
@Serializable class TodayRoute : TabRoute()
@Serializable class TrendsRoute : TabRoute()
@Serializable class FoodsRoute : TabRoute()
@Serializable class MoreRoute : TabRoute()
```

- Tab roots take no arguments (system design section 10). Today's selected day lives in `SelectedDay`, not the route.
- `AppViewModel` start destination: `TodayRoute()` when `hasUserOnboarded`, else `OnboardingRoute()`. Onboarding finishes with `router.enterTab(TodayRoute())`, which clears the pre-tab stack.
- Delete `HomeRoute`, `HomeScreen` and `HomeViewModel`. Delete the template's unused `SettingsRoute` (its name collides with the new settings feature). Keep `FeedbackRoute` and `BugReportRoute`; Feedback loses its only entry (the old Home button), so Support gains a "Send feedback" row (`settings.md`).

## State

```kotlin
// com.opson.shell
data class ShellState(
    val policy: VisibilityPolicy? = null,      // null until VisibilityPolicySource's first value
    val palette: Palette = Palette.Pine,
    val theme: ThemeMode = ThemeMode.System,
    val textSize: TextSize = TextSize.Normal,
)
```

No `Display` here: the shell renders no numbers.

## Actions

| Action | Does |
|---|---|
| `PolicyArrived(policy)` | Stores the policy the root provides. |
| `AppearanceArrived(palette, theme, textSize)` | From `SettingsRepository.observe()`, distinct on the three fields. |

Tab taps and the center + are not ViewModel actions: they are routing, done in
`AppChromeBottomBar` exactly as Cards does it, because they only read the
`NavController`.

## Events
None from the ViewModel. `AppChromeBottomBar` calls:

| Tap | Call |
|---|---|
| A tab that is not selected | `router.switchTab(route)` |
| The selected tab | `router.notifyTabReselected(route)` |
| Center + | `router.navigate(LoggerRoute())` and log `logger.opened` with `source = "nav"` |

Reselect behaviour, owned by each tab through `OnTabReselected`: Today scrolls to
the top and selects today; Trends, Foods and More scroll to the top.

## Root providers

`App.kt` order, outermost first:

1. `AppThemeProvider(palette, dark = theme.resolve(isSystemInDarkTheme()), largeText = textSize == Large)`.
2. `CompositionLocalProvider(LocalVisibilityPolicy provides state.policy)` around `AppNavigation`, above the `NavHost`, so every `screen<>`, `bottomSheet<>` and `dialog<>` destination inherits it.
3. Boot gate: `AppViewModel.isBootComplete` also waits for the first `VisibilityPolicySource` value, so no frame renders under the unprovided default and then flips. (The unprovided default is Blind on purpose; it is the fail-closed backstop, not the loading state.)

## Bottom bar

- Visible on every in-app destination, as the prototype (`showNav` is false only on onboarding). Hidden on onboarding, sign-in, sign-up, verify email, blocking errors.
- **Selected tab is the tab root at the bottom of the current back stack**, found by walking `navController.currentBackStack` for the last `TabRoute` entry. No per-route mapping table, so Nutrients pushed from Trends highlights Trends and pushed from Today highlights Today, which is what the prototype's `navActive` does by hand.
- Items: Today (`calendar_today`), Trends (`insights`), center + (`add`, 60 dp raised circle, accent fill, shadow `0 8 20 rgba(20,20,30,.28)`), Foods (`restaurant`), More (`more_horiz`). Active item: accent icon with FILL 1, label weight 800; inactive muted, FILL 0, weight 600.
- Tab switches use no transition (`isSwitchingTabs()` from Cards); `TabRoute` itself carries fade enter/exit.
- The bar's reactive reads stay inside `AppChromeBottomBar` so emissions recompose only the bar (the Cards rule that stopped the `NavHost` rebuilding).

## Data

| Source | Flow |
|---|---|
| `VisibilityPolicySource.policy` | `PolicyArrived` |
| `SettingsRepository.observe()` | `AppearanceArrived` |

Offline: both are local, nothing changes.

## Components
`BottomNavBar`, `CenterAddButton`, `AppThemeProvider` from `:libraries:ui`.
`LocalVisibilityPolicy` from `:libraries:nutrition:ui`. Nothing new here.

## Visibility surfaces
The shell renders no nutrition number. It is responsible for the policy every
other surface reads, so the one shell rule is: **the provider wraps every
destination, sheets included.** A test proves it (below).

## Copy

| Key | String |
|---|---|
| `shell_tab_today` | Today |
| `shell_tab_trends` | Trends |
| `shell_tab_add` | Add (content description for the center +) |
| `shell_tab_foods` | Foods |
| `shell_tab_more` | More |

## Edge cases
- **Logger opened from the center + while Today shows another day**: `LoggerRoute(dayIso = null)` resolves to `SelectedDay`, and the Logger header says which day (see `logger.md`).
- **Midnight while open**: `SelectedDay` moves to the new date if it was on today; `VisibilityPolicySource` re-emits.
- **Large text**: labels stay single line, ellipsize; the bar grows in height, the center + stays 60 dp.
- **iOS**: tab routes are `class`, no enum args, so no typeMap. `LoggerRoute` takes `MealSlot?`, registered with the typeMap in `logger.md`.
- **Tab state**: `switchTab` saves and restores each tab's stack. Today's `SelectedDay` survives a tab switch because it is not in the route.

## Tests
- `ShellViewModelTest`: `PolicyArrived` stores it; `AppearanceArrived` updates palette, theme, text size; settings emissions that do not touch appearance do not change state.
- `DelegatingRouterTest` (`:libraries:navigation:impl`, port Cards'): `switchTab` saves and restores state and is single top; `enterTab` clears the stack.
- `PolicyReachesSheetsTest` in `:apps:leaktest` (composition): provide a Blind policy at the root, open a `bottomSheet<>` destination whose content reads `currentVisibilityPolicy()`, assert it is the provided one, not the preview default.
- Previews: `BottomNavBar` with each tab selected, light and dark, large text.
- No `SURFACES` entry: the shell has no number. The leak test covers what it hosts.
