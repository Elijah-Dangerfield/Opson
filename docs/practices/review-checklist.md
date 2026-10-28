# Review checklist

Every screen and every endpoint is reviewed against this list before it merges.
It is the rubric, not a style guide: each item exists because breaking it
produces a specific bug, and the bug is named next to it. Read
[`docs/system-design.md`](../system-design.md) first for the shape of the app.

## Screens

### State and actions
- [ ] **One `SEAViewModel` per screen or sheet.** State is a single immutable
  `data class`. No `MutableState` in the ViewModel, no second source of truth.
- [ ] **Actions name what the user did or what happened**, never what the state
  should become. `AddGlassTapped`, `MealHeaderPlusTapped(meal)`,
  `DayClosed`, `ModePicked(preset)`. Not `UpdateState`, `SetLoading`,
  `SetWater(ml)`. An action that is a setter in disguise means the logic moved
  into the composable, which is where it cannot be tested.
- [ ] **Data loads arrive as actions too** (`DayLoaded(day)`), so every state
  change goes through `handleAction` and a test can replay it.
- [ ] **One-off effects are `Event`s** (navigate, show toast, open sheet).
  Never a `showToast: Boolean` in state that something has to reset.
- [ ] **Derived values are computed in the ViewModel or the domain**, not in
  the composable. A composable that does arithmetic on nutrition numbers is a
  bug, see the visibility rules below.

### Composables
- [ ] **Stateful wrapper, stateless screen.** The entry point collects state
  and passes `state` and `onAction` to `XScreen(state, onAction)`. The screen
  and everything under it takes plain parameters and is previewable.
- [ ] **A screen preview renders one theme at full phone width.** Use a
  separate `…Dark` preview, never `PreviewLightDark`, for whole screens and
  sheets: every preview is also a design-sync screenshot at 390 dp, and two
  themes side by side squeeze each to half width. Components may pair them.
- [ ] **A `@Preview` for each meaningful state**: empty, populated, and at
  least one mode that hides amounts (Macros only or No numbers). Uses
  `org.jetbrains.compose.ui.tooling.preview.Preview`, never the androidx one
  (fails the iOS link).
- [ ] **Components come from `:libraries:ui`.** No raw Material components in
  feature code. Missing one means adding it to `:libraries:ui` first.
- [ ] **Colours, type, radii and spacing come from the theme tokens.** No hex
  literals or `dp` magic numbers outside `:libraries:ui`.
- [ ] **Touch targets are at least 44 pt**, except where the design says 40.
- [ ] **No animated value read during composition** (detekt
  `AnimatedStateReadInComposition` enforces it).
- [ ] **Bottom sheets pass `scrollableContent = true`** instead of wrapping
  content in their own scroll, and a sheet with a `LazyColumn` passes
  `heightFraction` (detekt `ScrollInsideBottomSheet`).

### Visibility (the rule this app exists for)
- [ ] **No nutrition number is formatted outside `VisibilityPolicy`.** Every
  amount reaches the screen as a `Display` (or `RingValue` / `MacroPart`) and
  renders through `NutrientValue` / `NutrientText` / `MacroLine`. Grep the diff for `kcal`, `" g"` and `toString()` on a
  nutrient. Building one of those by hand outside `:libraries:nutrition` fails
  detekt `HardcodedDisplay`.
- [ ] **`HIDE` removes the label too**, not just the digits.
- [ ] **Every rendered number carries `Modifier.testTag("metric:<metric>")`**,
  which `NutrientValue` does for you.
- [ ] **The screen's surfaces are in its feature's `XLeakSurfaces.all`**
  (`TodayLeakSurfaces`, `FoodsLeakSurfaces`, ...) and registered in
  `apps/leaktest/.../Surfaces.kt` (`SurfacesCoverageTest` fails otherwise),
  and the leak test passes.
- [ ] **No screen checks which mode is active.** Layout choices come from
  `ModePreset.today` and per-metric levels, never `if (preset == PresetId.NoNumbers)`.

### Copy
- [ ] Copy matches the handoff word for word where the handoff has it.
- [ ] New copy follows the voice: warm, direct, short. Never "cheat", "bad",
  "guilt", "burn it off", "earn", "deficit". "On target", never "0 left".
- [ ] All strings are resources, not literals, so tone (Plain / Warm) can swap
  them.

### Tests
- [ ] ViewModel test for every action: given state, take action, assert state
  and events. Hand-rolled fakes only (see `testing.md`).
- [ ] Domain logic the screen relies on has a pure unit test in its library.

## Endpoints

- [ ] **Route, domain, data split** as in `apps/server/README.md`. Routes map
  DTOs and status codes, nothing else.
- [ ] **The caller's id comes from the JWT** (`call.userId()`), never a body
  field or path parameter.
- [ ] **Every write is idempotent.** Client-generated UUIDs, upsert by id.
  Retrying a request after a timeout must not double-log a meal.
- [ ] **Conflicts resolve by `client_updated_at`, last write wins**, with the
  server's `server_seq` only used as the pull cursor.
- [ ] **Errors use the `ProblemResponse` envelope** with a stable `code`.
- [ ] **Schema changes are a new Flyway migration**, mirrored in `Tables.kt`
  and `DatabaseSchemaTest`. Never edit an applied migration.
- [ ] **Every user-owned table has `user_id` with `ON DELETE CASCADE`** to
  `auth.users`, so account deletion actually deletes.
- [ ] **Row level security is on for every table**, even though the server
  connects as the owner. It is what stops the anon key reading the table if
  someone ever points a client at it directly.
- [ ] Route test with a fake, repository test against Testcontainers Postgres.
- [ ] **No nutrition values in logs or span attributes.** Log ids and counts.
  This is health data.

## Safety rules (from the handoff, non-negotiable)

- [ ] The energy target can be raised freely and never lowered below the
  starting target while `GOAL_FLOOR_KCAL` is null.
- [ ] No evening "calories left" notification, and no setting to enable one.
- [ ] A big day never lowers tomorrow's target.
- [ ] Fasting is off by default, offered only in Full and Sprint, capped at
  16:8, with no streaks or records. Logging food ends a fast.
- [ ] No manual exercise logging.
- [ ] Settings change only when the user changes them. The mode records when
  it was changed.
