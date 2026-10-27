# Code review practices

The rubric for the whole-codebase review, and for every change after it. It
extends [`docs/practices/review-checklist.md`](../practices/review-checklist.md)
(screens, endpoints, visibility, safety rules), which still applies line by
line. Add to this list when a review finds a new class of problem. Each item
says what to do and what it prevents.

A finding needs evidence: a file and line, and the input or sequence that
breaks. "Could be cleaner" without a failure or a concrete cost is not a
finding. Equally, a real maybe (a race that needs two taps in 50 ms) is still
worth fixing if the fix is small.

## 1. Code that reads true

- **Names do the documenting.** A function, class or parameter name says what
  it does or holds. Prefer renaming over a comment that explains a bad name.
- **Comments say why, never what.** Keep comments that record a reason, a
  constraint, a bug it prevents, or a link to a decision. Delete comments that
  narrate the next line.
- **Stale comments are bugs.** A comment, KDoc, plan reference or README line
  that no longer matches the code misleads the next reader. Fix the comment or
  the code; never leave them disagreeing. Check every comment near changed code.
- **No dead code.** Unused functions, parameters, flags, previews, resources,
  feature-flag branches that can't be reached, commented-out code. Delete.
- **No speculative generality.** One implementation behind an interface is
  fine only when a test fake or a platform split needs the interface.
- **Match the neighbours.** Same error handling, naming, layering and test
  style as the surrounding module (unslop-code: fit the codebase).
- **TODOs carry an owner tag** (`TODO(clinician)`, `TODO(launch)`,
  `TODO(design-sync-2)`) and a reason. A bare `TODO` is a finding.

## 2. Kotlin and coroutines

- **Structured concurrency only.** Launch in `viewModelScope`, the injected
  `AppCoroutineScope`, or a scope a caller owns. No `GlobalScope`, no ad-hoc
  `CoroutineScope(...)` that nothing cancels.
- **Cancellation is cooperative and never swallowed.** `Catching {}` and
  `runCatching` must not eat `CancellationException`; check that the project's
  `Catching` rethrows it and that no `catch (e: Exception)` sits around a
  suspend call without rethrowing cancellation.
- **Parallelise independent work** with `coroutineScope { async {} }` or
  `combine`, not sequential awaits. Don't parallelise writes that must order.
- **Right dispatcher.** Disk, database and parsing off main; UI state and
  platform UI objects (UIKit, UserNotifications, AVAudio session) on main or a
  dedicated queue, as the platform requires. Never block main.
- **Flows:** `stateIn`/`shareIn` with an explicit `SharingStarted` and a scope
  that dies with the owner; `distinctUntilChanged` before expensive work;
  `debounce` user typing (search ~250 ms) and cancel stale work with
  `flatMapLatest`/`collectLatest`.
- **No infinite loops in a scope tests can't control.** Tickers and pollers
  take an injectable clock/ticker and run in a scope a test can cancel
  (`backgroundScope`). One hung CI job already came from this.
- **Immutability:** state is `val` data classes and read-only collections;
  mutate through `copy`.
- **Errors at the edges:** expected failures are values (`Catching`, sealed
  results) handled at the ViewModel or repository boundary; programmer errors
  fail fast. No `!!` on data from the network, disk or the user.

## 3. Mobile architecture (SEA)

- **Actions are intents or arrivals** (`SearchTyped`, `MealPicked`,
  `DayLoaded`), never `UpdateState`/`SetX`.
- **One state, built by a pure builder.** Business rules live in the
  `…StateBuilder` or domain, tested without Compose.
- **Events for one-offs**, collected once, never a boolean in state that
  someone must reset.
- **Repositories own data; ViewModels never talk to Ktor or Room directly.**
- **Nothing runs at app start that the user hasn't turned on** (release flags,
  `AutoInit`, `init {}`), and every launch-time path is cheap.
- **Offline first:** writes go to Room and the outbox; the UI never waits on
  the network to show what the user just did.

## 4. Compose performance

- **Stable parameters.** Screen and component parameters are immutable data
  classes, `ImmutableList`/`List` from `@Immutable` state, primitives or
  lambdas remembered at the call site. An unstable parameter makes the whole
  subtree recompose on every state change.
- **Read state as late as possible.** Pass lambdas (`{ scrollOffset }`) or use
  `Modifier.offset { }`, `graphicsLayer { }`, `drawBehind { }` for values that
  change every frame (scroll, animation), so only layout or draw runs.
- **`derivedStateOf`** for values computed from rapidly changing state
  (first visible item > 0), `remember(keys)` for expensive calculations.
- **Lazy lists** have stable `key`s and `contentType`; no `LazyColumn` inside a
  vertically scrolling `Column`.
- **No work in composition:** no formatting, sorting, filtering, date maths or
  allocation of big objects in a composable body; that belongs in the
  builder.
- **No backwards writes** (writing state that was read earlier in the same
  composition) and no `LaunchedEffect` keyed on objects that change every
  recomposition.
- **Previews and screenshots don't spin:** infinite animations are still in
  inspection mode.

## 5. Logging, telemetry and privacy

- **Log ids, counts, outcomes and durations.** Never nutrition values, food
  names, notes, weights, emails, search text, transcripts, photos, health
  data, tokens or IP addresses. App events follow
  [`app-events.md`](../practices/app-events.md).
- **Right level, right volume.** `debug` for traces that help a developer;
  `info` for lifecycle; `warn` for recoverable surprises; `error` only for what
  someone should look at. No logging in tight loops or per frame.
- **Errors carry context, not payloads**: the operation and ids, not the
  request body.
- **Secrets never in code, logs, URLs or commits.**

## 6. Backend (Ktor, Exposed, Postgres)

- **Every input is validated at the route**, sizes are capped (body limits,
  list lengths, string lengths), and errors use the problem envelope with a
  stable code.
- **AuthZ on every row.** User id from the JWT; every query filters by it; a
  test proves user A can't read or overwrite user B's rows.
- **Queries are parameterised** (Exposed or bound params), use an index for
  every hot path (check `EXPLAIN` on search, sync pull and anything per
  request), and page with a cursor, not `OFFSET`, for user-growing tables.
- **Transactions are short** and never span a network call. No N+1 loops.
- **Idempotent writes and retries.** Client ids, upserts, and an explicit
  last-write-wins rule; external calls have timeouts, bounded retries and a
  circuit for outages.
- **Rate limits** on anything unauthenticated or expensive (search, photo,
  waitlist, barcode misses).
- **Migrations are additive and idempotent while old clients exist.** Never
  edit an applied migration. Large backfills run as scripts in batches, not in
  a migration.
- **Configuration from env with safe defaults**; prod fails closed (CORS,
  admin token, feature flags off).
- **Observability:** each route has a span and a metric; dashboards query only
  `analytics` views; alerts exist for error rate and latency.

## 7. Tests

- **Every bug fixed gets the test that would have caught it.**
- **ViewModel per action, builder per visibility branch, repository against
  real Postgres (Testcontainers), route with fakes.**
- **End-to-end integration tests** in `:apps:integration`: real client code
  (repositories, sync, network layer) talking to the real in-process server and
  database, no mocks in between. Every user journey that crosses the wire gets
  one: onboard and sync, log and pull on a second device, settings merge across
  an old and new client, search and barcode, delete everything, fasting sync,
  waitlist, photo with a fake provider.
- **Tests assert behaviour, not implementation**, and fail when the behaviour
  breaks (prove it once by breaking the code).
- **No sleeps;** use virtual time, `awaitUntil` with a timeout, or fakes.
- **The Blind leak test covers every surface,** including notifications,
  widgets and anything new.

## 8. Platform specifics

- **iOS:** Kotlin/Native calls into UIKit and AV frameworks on the right
  thread; Info.plist usage strings for every permission; no entitlement on
  main before its release (see `docs/plans/0.2/README.md`).
- **Android:** permissions requested in context, `exported` set explicitly,
  no work on the main thread, process death survives (state restoration).
- **Accessibility:** content descriptions, 44 pt targets, live regions for
  status changes, dynamic type that doesn't truncate (see the Targets fix).

## 9. Environments and the release path

Added 2026-10-27, after 0.1.0 and the first 0.2 TestFlight build shipped
signing in to the dev Supabase project while calling the prod server. Every
request came back 401. A file-by-file review can't see this: each file was
fine, and the bug was in how values from three places met in one binary.

- **One switch per build picks the environment, and every environment value
  follows it.** API base URL, Supabase project and key, Sentry environment,
  Grafana endpoint, ad unit ids, OAuth client ids, deep link scheme, bundle
  and App Group ids. List them all and check each one for debug, release,
  local and CI builds on iOS and Android.
- **Release values come from committed config or CI secrets, never from a
  developer's `local.properties`.** A release build must not depend on whose
  machine built it.
- **A wrong environment fails loudly at build time,** not as a 401 on a
  phone. Where a value can be checked (the Supabase host matches the server's
  `SUPABASE_URL`, no placeholder values in a release build), a build-logic
  test or a release-build check does it.
- **The server and the client agree on the contract.** Route paths, auth
  header, DTO fields and their nullability, status codes the client handles,
  and what 0.1 clients still send. A change on one side gets a test that runs
  both sides (`:apps:integration`).
- **Before a build is called ready, its release configuration has been
  exercised end to end:** sign in, search, barcode, log, sync, settings,
  delete, against the environment it will talk to. "It compiles and uploads"
  is not ready.
- **Server env vars:** every one the server reads is documented, set on dev
  and prod (or deliberately absent with a logged reason), and the server
  logs which optional features are off at boot.
- **Secrets and repo settings the pipelines need exist** (GitHub secrets and
  variables, Fly secrets), and a step that skips for a missing secret says so
  as a warning someone will see, not a silent pass.

## 10. Journeys, not just files

- **Fresh install** reaches sign-in and onboarding, every time, on both
  platforms, including after a reinstall when the iOS Keychain still holds
  an old session.
- **Every user-visible flow has a non-empty state, a loading state, an empty
  state and an error state,** and the error state tells the person what they
  can do.
- **Permissions:** asked in context, denied handled, the usage string present
  for every API the binary references (App Store Connect rejects the upload
  otherwise: ITMS-90683 for `NSHealthUpdateUsageDescription`, 2026-10-27).
- **Lifecycle:** backgrounding, process death, offline, a slow network, a
  token that expires mid-session, two devices editing the same day.
