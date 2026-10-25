# AGENTS.md

Guidelines for AI agents working in the Opson repository.

## Overview

KMP (Kotlin Multiplatform) app with Compose Multiplatform. Modular architecture with Room database, navigation, and SEAViewModel pattern.

This is **Kotlin Multiplatform**: most code is shared, but some platform features (permissions, sensors, native APIs) require platform-specific implementations. When implementing something not inherently cross-platform, follow the patterns in `docs/swift-kotlin-communication-patterns.md`.

## Build Commands

```shell
./gradlew :apps:compose:assembleDebug          # Android
./gradlew :apps:compose:compileKotlinIosSimulatorArm64  # iOS Kotlin
xcodebuild -project apps/ios/iosApp.xcodeproj -scheme iosApp -sdk iphonesimulator  # iOS full
```

`iosApp` is the only scheme, and it is **shared**: it lives at
`apps/ios/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme` and is
tracked by git (`.gitignore` ignores `xcuserdata`, so a per-user scheme would
not survive a fresh clone). If you ever add a target, share its scheme too:
un-shared, `xcodebuild` autocreates one from the target, which works until the
day it doesn't and leaves nobody a file to review.

## Module Structure

```
apps/compose/          # KMP entry point (Android + iOS)
apps/ios/              # Swift wrapper
features/<name>/       # Routes, public API
features/<name>/impl/  # Screens, ViewModels
libraries/<name>/      # Interfaces
libraries/<name>/impl/ # Implementations
```

**Rules**, enforced at Gradle configuration by the convention plugins:

- Only `:apps:*` may depend on `*:impl`. Impls are DI wiring composed by the app, not consumed by other modules.
- Feature `impl` modules may depend on another feature's `api`. Feature `api` modules may **not** depend on other feature `api`s (api-to-api is a cycle risk, shared types go in a library).
- Sub-modules of the same feature (`:features:foo:storage` → `:features:foo`) are allowed.
- `:libraries:storage:impl` is the one shared impl: it owns the `AppDatabase`.

Shared code → libraries. Main modules expose interfaces only; impl modules contain implementations.

## Conventional Commits (required)

Every commit (and every PR title, PRs are squash-merged) must follow [Conventional Commits](https://www.conventionalcommits.org/). Release-please derives the next version bump from commit history.

| Type | When | Version bump |
| --- | --- | --- |
| `feat:` | User-visible new capability | minor |
| `fix:` | Bug fix | patch |
| `perf:` | Perf improvement, user-visible | patch |
| `feat!:` / `BREAKING CHANGE:` | Breaking change | major |
| `refactor:`, `style:`, `test:`, `docs:`, `ci:`, `build:`, `chore:`, `revert:` | No user impact | none |

A local `.githooks/commit-msg` hook enforces this on every commit. The Gradle build fails with an install-hooks message if the hook isn't wired, run `./scripts/install_hooks.sh`.

## Convention Plugins

| Plugin | Use |
|--------|-----|
| `opson.kotlin.multiplatform` | Pure Kotlin |
| `opson.compose.multiplatform` | Kotlin + Compose |
| `opson.feature` | Feature modules |
| `opson.application` | apps:compose only |

Use `/scripts/create_module` for new modules.

## DI (kotlin-inject-anvil)

```kotlin
@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
@Inject
class MyImpl : MyInterface

// Multibinding for FeatureEntryPoints
@ContributesBinding(AppScope::class, multibinding = true)
```

No expect/actual for platform impls, bind different implementations per platform. iOS impls written in Swift get passed into the DI graph via `IosAppComponentFactory.create(...)`.

### Boot-time construction: the `AutoInit` marker

Kotlin-inject singletons are constructed lazily on first injection, so a repo that nobody touches until a deep nav target stays cold. A hydrate-from-disk or listener-registering `init {}` doesn't run until something injects the class.

For singletons where the warm path matters (app-lifecycle dispatchers, disk-backed repositories, anything whose `init {}` is load-bearing), implement [`AutoInit`](libraries/core/src/commonMain/kotlin/com/opson/libraries/core/AutoInit.kt) and contribute a second binding via multibinding:

```kotlin
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = MyRepository::class)
@ContributesBinding(AppScope::class, boundType = AutoInit::class, multibinding = true)
@Inject
class MyRepositoryImpl(...) : MyRepository, AutoInit
```

The `Set<AutoInit>` is resolved at app start (`Application.onCreate` on Android, `iOSApp.init` on iOS, `App.kt` remember-block on first composition). Resolving the set forces every contributor to construct, which runs their `init {}`. That's where hydrate-from-disk and lifecycle-listener registration happen.

**Opt in** when there's first-touch latency the user notices, an `init {}` that registers a listener, or a cache that needs its observer running before the user can navigate. **Skip** for debug-only / QA-menu singletons and anything whose `init {}` is empty. Forgetting the marker is a perf regression, not a correctness one. The class still works lazily. So the bigger risk is overuse making boot slow.

## Auth model (`:libraries:identity`)

Anonymous-first Supabase auth. Sessions are never minted implicitly: onboarding drives guest creation (`GuestAccountCreator`), the sign-in flows mint claimed ones, and `GuestSessionHealer` recovers a stranded onboarded device.

- **`AuthState` is sealed with no in-flight sentinel**: `Authenticated(userId, isAnonymous, email)` or `Unauthenticated(cause, reason, wasAnonymous)`. `current()` suspends until the answer is real; `observe()` emits only resolved values. UI renders a spinner while awaiting its first emission, never off a `Loading` enum.
- **Per-operation sealed outcomes** (`SignInOutcome`, `SignUpOutcome`, …) instead of thrown exceptions. Screens render specific messages for invalid-credentials vs offline vs already-registered.
- **`Unauthenticated.reason` drives app routing**: `SessionExpired` pushes the blocking recovery screen (sign-in-again for claimed, start-fresh for guests); `SignedOut` marks a deliberate exit this run so self-heal never resurrects a signed-out user; `None` is the ordinary no-session state.
- **User-change choke point**: every transition flows through the auth orchestrator, which runs the `UserScopedDataReset` dump (Room tables via `ClearableDao` multibinding, profile caches, account-scoped `AppData` fields) *before* the new `AuthState` is emitted. A reactive loader can't race the wipe. `AppEvent.UserChanged(previous, current)` is the after-the-fact announcement for side effects that hold no user-scoped storage.
- **Tokens**: the network layer only sees `AuthTokenProvider` (`awaitReady()` then `accessToken()`); a server-confirmed 401-after-refresh routes through `SessionRejectionBus` (no 401 loops), a 403 ban envelope through `AccessDeniedBus`.
- **Session persistence is OS-encrypted** (Keychain on iOS via the Swift `IOSSecureSessionStorage`, `EncryptedSharedPreferences` on Android) with a file mirror for anonymous sessions so a TestFlight Keychain wipe can't strand a guest.
- The browser-OAuth redirect is `opson://login-callback` (the scheme renames with the project); `App.kt` hands it to `completeOAuthRedirect`, never the nav graph.

## Triggered sync (`UserScopedSyncer`)

Repositories that mirror server state don't invent their own refresh timing. Implement one idempotent `sync(): Result<Unit>`, contribute to the `UserScopedSyncer` multibinding (see `ExampleUserScopedSyncer` for the two-line registration recipe), and `UserScopedSyncCoordinator` runs it on every edge that matters: account became active (sign-in, cold-boot resolve, switch, claim), warm foreground, and connectivity regained. With exponential retry that parks as success while offline (re-armed by the reconnect edge). The level-keyed `runWhen` core means a subscriber can't miss an edge that fired before it attached. For offline *writes*, use the outbox pattern instead. `docs/practices/outbox.md`.

## Server (`:apps:server`)

A Ktor + Postgres backend with Supabase JWT auth. It reuses the client's conventions, kotlin-inject + anvil DI (`ServerScope` / `ServerComponent`), the `domain/` interface + `data/` impl split, one `fun Route.xRoutes(deps)` per resource, and degrades gracefully (boots with no DB / no Supabase). It's a plain JVM module, so it applies plugins directly rather than via a convention plugin.

The full reference, how to add a route, repository, migration, or config value, plus the auth, persistence, and testing patterns, lives in [`apps/server/README.md`](apps/server/README.md). Read it before touching the server.

## Telemetry goes to a shared Grafana stack

Every project generated from this template exports to the **same** Grafana
Cloud stack. Apps are separated by the `service_name` label
(`<project>-client` / `<project>-server`, per-project because the rename pass
rewrites it), never by having a stack of their own, with
`deployment_environment` splitting dev from prod inside that.

Two things follow that will bite an agent debugging from logs. **A Loki query
with no `service_name` matcher spans every app ever shipped from this
template**, so a result that looks broad probably is. And quota and retention
are shared, so the levers for a noisy app are `telemetry.appEventsSampleRate`
and the `telemetry.appEventsEnabled` kill switch, both remote config and both
per app, rather than anything stack-level.

Full detail and the case for splitting later in
[`docs/practices/observability.md`](docs/practices/observability.md); the
decision and what it costs in `docs/decisions.md`, 2026-09-21.

## Testing

Conventions (hand-rolled fakes only, dispatcher choice, which layer catches which bug) live in [`docs/practices/testing.md`](docs/practices/testing.md). Read it before adding tests. The end-to-end tier is `:apps:integration`: an Android-library module whose tests run on the host JVM (`./gradlew :apps:integration:testDebugUnitTest`, needs Docker) and drive the real client stack (real `HomeViewModel`, real repositories, real HTTP client) over real TCP against a real in-process Ktor server on a Testcontainers Postgres. `HarnessSmokeTest` is the worked example; `commonMain` stays empty so iOS never links the JVM-only server.

Composables can be tested too. `FloatingWindowHostTest` in `:libraries:navigation` is the worked example: a real composition under Robolectric in `androidUnitTest`, driven and asserted on the host JVM, running under the same plain `testDebugUnitTest` sweep. Reach for it only for claims that are *about* composition, an effect that must run or dispose, a recomposition, what is on screen. Anything a view-model or pure-function test can answer stays there, because this layer is slower and much easier to write so that it passes for the wrong reason. The recipe, and why it is not a `jvm()` target, are in [`docs/practices/testing.md`](docs/practices/testing.md).

## SEAViewModel Pattern

```kotlin
class MyViewModel : SEAViewModel<State, Event, Action>(initialStateArg = State()) {
    override suspend fun handleAction(action: Action) {
        when (action) {
            is Action.Load -> action.updateState { it.copy(loading = true) }
        }
    }
}
```

- **State**: Immutable data class for UI
- **Event**: One-shot side effects (navigation, toasts)
- **Action**: Only way to mutate state via `action.updateState { }`

## Navigation

Routes are `@Serializable` data classes extending `Route`. Register in `FeatureEntryPoint.buildNavGraph()`:

```kotlin
screen<MyRoute> { backStackEntry -> MyScreen(...) }
bottomSheet<SheetRoute> { backStackEntry, sheetState -> ... }
dialog<DialogRoute> { backStackEntry, dialogState -> ... }
navigation<MyGraph>(startDestination = MyRoute()) { screen<...>; bottomSheet<...> }
```

### iOS/Native landmines (production crashes, both)

1. **Routes must be `class` (or `data class`), never `data object`.** A
   `data object` route SIGSEGVs at navigate time on iOS, Native's
   serialization of object routes crashes inside androidx.navigation. An
   arg-less route is still a `data class MyRoute(...)` extending `Route`
   with default args.
2. **Every enum (or other non-primitive) route arg must be `@Serializable`
   AND registered in a typeMap.** Base-class args (`enter`/`exit`/`popExit`)
   come from `baseRouteTypeMap`, which every `screen<>`/`dialog<>`/
   `bottomSheet<>`/`routeDeepLink<>` builder merges in automatically. Args
   you add to your own route need `typeMap = mapOf(typeOf<MyEnum>() to
   serializableType<MyEnum>())` at the registration site. Miss one and
   graph-build throws `could not find any NavType for argument …`, often
   naming a *different* arg than the one you forgot. Use `routeDeepLink<T>`
   for deep links, never bare `navDeepLink`.

**Use `bottomSheet<>` for transient picker / overlay UIs** (a settings list, a "select an item" sheet) rather than pushing a full screen. The backstack stays one entry deep, the underlying screen is visible under a scrim, and `sheetState.dismiss()` is a clean exit. Reach for full `screen<>` only when the destination is its own context (settings page, detail view).

**Open external URLs via `Router.openWebLink(url)`**, don't roll your own platform `Intent.ACTION_VIEW` / `UIApplication.shared.open` plumbing. The implementation is in `libraries/navigation/impl/.../{Android,Ios,Jvm}WebLinkLauncher.kt` and is already wired into the DI graph and the `Router` interface.

## App-wide state

`AppData` (in `libraries/<projectid>/.../AppCache.kt`) is a `@Serializable` data class persisted via `CacheFactory.persistent`. Add fields here for things like:

- Onboarding flags (`hasUserOnboarded`)
- User-facing setting toggles
- Counters / lightweight telemetry (`feedbacksGiven`, `bugsReported`)

Don't roll a new persistent cache for a single boolean, extend `AppData`. Round-trip is automatic via `versionedJsonSerializer` (missing fields fall back to defaults, so adding a field is non-breaking). For an example wrapper that exposes `StateFlow<Boolean>` for Compose, see how a feature-level store reads `AppCache.updates` and writes via `appCache.update { it.copy(...) }`.

## Cross-cutting state in Compose

When something (a service, a setting, a theme value) is needed by every composable in a subtree but doesn't belong on the screen-level ViewModel, prefer a `staticCompositionLocalOf` over threading parameters. Provide it once at the subtree root:

```kotlin
val LocalMyService = staticCompositionLocalOf<MyService> { NoopMyService }

// At the screen root:
CompositionLocalProvider(LocalMyService provides realService) {
    HorizontalPager(...) { … }
}
```

Default it to a noop, never `error("not provided")`. This keeps `@Preview` and unit tests trivial. They get the noop automatically.

## Coding Guidelines

- Code like a staff engineer
- Use `Catching { }` from libraries/core instead of `runCatching`
- Custom UI components in libraries/ui, avoid Material directly
- Check `ComposeApp.h` for Swift names of Kotlin types before using in Swift

### Comments and docs earn their upkeep, or they lie

A comment that records **why** a non-obvious choice was made ages well: it is the thing that stops the next person deleting the choice. A comment that restates a file name, a line number, a count, or the state of the world is a hostage to the next change: it is correct on the day it is written and silently wrong afterwards. Prefer the first, and write it where the choice lives.

**Whatever describes a thing gets updated in the same commit that changes the thing.** Not the next commit, not the cleanup PR. Doc rot has no failing test and no compiler. It is invisible until somebody acts on it, and by then the cost is paid.

The reason this is a rule and not a platitude is that the failures are specific and they all look like something else:

- **AGENTS.md's own iOS build command named a scheme that has never existed** (`-scheme iOS`; the only scheme is `iosApp`). The error, `xcodebuild: error: The project does not contain a scheme named "iOS"`. Reads as a broken Xcode install or a bad checkout, so people debug their machine.
- **`docs/release-automation.md` told readers to create a TestFlight external group named `main`**, while `release.yml` and the Fastfile both looked for `External Testers`. Nothing is internally inconsistent. Code agrees with code. So no gate could catch it. The symptom is a green release that reaches nobody on external TestFlight, and the natural conclusion is "my App Store Connect setup is wrong."
- **Two `This template` placeholders shipped into every generated app's operator runbook** ("create a Google Cloud project called *This template CI*"). It reads as a copy-paste slip in a doc rather than a defect, so nobody files it and it lives as long as the app.
- **Two build files named a Gradle property with a project prefix it does not have.** The prefix was a comment restating a literal that had deliberately moved, and the restatement was in a file the rename pass rewrites, so each generated project got its own wrong name while the real flag stayed constant. The neighbouring comment on the property itself explains exactly why it carries no prefix; the two copies did not read it.
- **Docs cited source files by their package path**, and the generator rewrote the citation on one rule while moving the directory on another. Every such link pointed at nothing, in every generated project, and kept looking plausible while doing it.

These were all found by someone hitting the symptom, not by anyone reading the doc. `scripts/verify_template.sh` now resolves every repo-relative markdown link in a generated project, which turns the subset of this class that is a dead path into a red build. The rest is discipline.

## This template is fed by the apps built from it

Apps generated from here run into production before the template does. They hit the App Store review, the Play policy deadline, the R8 keep rule that only breaks at runtime, the Compose gotcha that only shows up at 60fps with real data. That knowledge is worth more than anything written speculatively in this repo, and it only arrives if someone carries it back.

**If you are working in a generated app, port it back.** Two things qualify, and both go to the same place: `docs/PORT-CANDIDATES.md` **in the template repo**, which is a queue of work for this repo, not a log of what has been done:

- **Something you built that a brand-new app would want before it has any features.** Say what it is, why a generic app wants it, and the path to copy from. Don't port speculatively; something that hasn't survived production downstream is not yet worth this repo's maintenance.
- **A bug in code you inherited from the template, or a fix that generalizes.** These matter more, because every generated app already has them. Say what broke, *how it looked from the outside*, and why it was hard to spot. The next person meets a symptom, not a cause. Worth writing even when the fix is one line: the diagnosis is the value, not the diff. If you can fix it in the template yourself, do that and skip the entry.

Note that file lives in the template only; a generated project doesn't carry a copy, so you are writing across repos on purpose.

**Write it in the template repo, not in yours. A local note is not a port.** This is the step that actually fails. Three separate apps diagnosed template bugs correctly, wrote them up well, and filed them in their own `docs/decisions.md` or `docs/todos.md` with a sentence saying the fix belongs upstream. Nobody upstream reads those files, so the template kept shipping the bug and the next generated app inherited it. One of them sat in `Cache.update` for months that way. Recording it at home feels like doing the work and is indistinguishable from not doing it.

So finish the trip. The template is a separate checkout, usually a sibling of this one. Find it by the queue itself, which no generated project carries:

```sh
ls -d ../*/docs/PORT-CANDIDATES.md
```

Then `cd` to that repo, add your entry, and commit and push from there. Deliberately a search rather than a name: the init script rewrites the template's own name to the project's everywhere it appears, so a hardcoded path in this file would point a generated app at itself.

You are allowed to leave this repo to do it. If the search finds nothing, or the edit cannot be pushed, say so in your reply to the user and ask. Do not fall back to writing the entry locally and moving on: a note in this repo saying "this belongs upstream" is the exact failure described above. Writing it in both places is fine; writing it only here is not.

**If you are working in this template**, `docs/PORT-CANDIDATES.md` is the queue. Take from it in priority order. Delete entries as you land them rather than ticking them off, so the file stays a queue and not a changelog.

**Generalize on the way in.** A port arrives shaped like the app it came from. Strip its domain, name it for what it does rather than what it did, and keep the *reason*. The comment explaining why a rule exists is usually the most valuable line in the diff, because it is what stops the next person deleting it.

## Known landmines

Each of these cost a downstream app real time. They are cheap to avoid and expensive to rediscover.

- **Compose Multiplatform's iOS klib only ships the JetBrains `Preview` annotation.** Migrating previews to `androidx.compose.ui.tooling.preview.Preview` compiles on Android and fails the iOS link. One app migrated 171 files before finding out, and only because it compiled the iOS target for an unrelated reason. Use `org.jetbrains.compose.ui.tooling.preview.Preview`.
- **Routes must be `class`, never `data object`.** A `data object FooRoute : Route()` SIGSEGVs the iOS navigator at navigate time. Also covered under Navigation.
- **Enum route arguments must be `@Serializable`** or the graph crashes at build time on iOS/Native. JVM tests will not catch it.
- **`UIApplication.canOpenURL` needs its scheme declared in `LSApplicationQueriesSchemes`.** Undeclared, it returns false for everything, and a launcher that checks it first silently opens nothing. Every outbound link in the app dies with no error.
- **Infinite animations hang preview and screenshot capture.** Anything looping forever must return a fixed value under `LocalInspectionMode`, or a screenshot test waits for an idle state that never arrives.
- **Reading an animated value during composition recomposes the whole subtree every frame.** `val x by animateFloatAsState(...)` read in a composable body is the single most common Compose performance bug; feeding text with it thrashes Skia's glyph cache and can wedge the RenderThread into an ANR. Read it in `graphicsLayer`/`drawBehind` instead. Enforced by the `AnimatedStateReadInComposition` detekt rule, which fails the build.
- **A bottom sheet whose height comes from its content snaps back mid-drag.** Material3 derives the Expanded anchor from the sheet's *measured* height and recomputes the anchors on every measure pass, so tall content re-measured mid-drag yanks the sheet back to Expanded: dragging down to close jumps, and near the top it bounces without ever closing. Short content measures stably, so it stays invisible until the first long sheet. Pass `scrollableContent = true` to `BottomSheet` instead of wrapping the content in your own `Column(verticalScroll(...))`. Enforced by the `ScrollInsideBottomSheet` detekt rule.
- **A clean detekt run does not prove a custom rule ran.** A silently-undispatched rule and a working rule that finds nothing are identical from the build output. Two causes seen downstream: detekt `2.0.0-alpha.5` failed to dispatch custom rules at all (fixed in `alpha.6`, which this repo pins), and **the Gradle daemon caches detekt's worker classloader**, so an edited rule keeps running its previous jar until `./gradlew --stop`. Neither is universal. The method is the lesson. Prove dispatch by making the rule report unconditionally, confirm the flood, then revert.
- **A string literal that names this project is a moving target in template code, and the de-branding grep cannot catch it.** `verify_template.sh` asserts that no generated project *contains* the template's name. So a literal that gets correctly renamed passes, even when renaming it is the bug. A status check in `scripts/setup.main.kts` compared a config value against the template's own name to ask "has this been changed from the default"; in a generated project that literal became the project's own name and the comparison inverted, reporting the opposite answer in the template and in the app. (This bullet deliberately does not quote the literal, for the same reason.) Two rules follow: an identifier that must stay equal across every generated project carries no project name at all. The build-slimming system property and the `appsetup` store directory are both named that way on purpose. And code asking "is this configured" should ask the service, not compare strings.
- **A full-screen UIKit presentation over the Compose host can wedge the navigation queue, and only a touch proves it.** `DelegatingRouter` drains queued commands under `observeWithLifecycle(STARTED)`, which is correct: applying a command to a controller whose host is genuinely gone loses the back stack. It rests on the host telling the truth about its visibility, and on iOS a full-screen presentation can leave that claim stuck below STARTED after the view is back. The app then takes taps and does nothing with them. Not a freeze: last frame holds, logging continues, shake-to-report still works, buttons just do nothing, and every bug report says "the app froze", which sends you to the main thread where nothing is wrong. Do not fix it by ungating the drain; that removes the protection in the ordinary case (every backgrounding, every interstitial) to fix the rare one. A press arriving while the host claims to be off screen is the contradiction, because a covered view is not touchable. `Modifier.navigationQueueWatchdog` at the Compose root detects exactly that and calls `NavigationRecovery.drainQueuedNavigation()`, which is deliberately a separate type from `Router` so feature code cannot reach it.
- **Editing a `@file:Import`ed Kotlin script does not invalidate the compiled-script cache.** `kotlin foo.main.kts` caches its compilation in `~/Library/Caches/main.kts.compiled.cache` keyed on the script it was handed, not on the files that script imports. Edit `scripts/lib/setup_store.main.kts`, re-run `scripts/setup_credentials.main.kts`, and you get the old library with no warning and no recompile pause. Same shape as the stale detekt worker classloader below: the tool runs, it is green, and it is running code you deleted. `rm -rf ~/Library/Caches/main.kts.compiled.cache` before you conclude an edit had no effect.
- **Every step in the Sentry upload chain reports success when it uploaded nothing, so the whole chain has to be checked by hand.** Three separate failures downstream, all green. (1) `sentry-cli debug-files upload` pointed at a directory with no symbols logs "Found 0 debug information files" and exits **zero**. It was aimed at gym's `OUTPUT_DIR`, which holds the dSYM *zip*; the `.dSYM` bundles are in the xcarchive, so no iOS crash that app ever received could be symbolicated. Resolve from `lane_context[SharedValues::XCODEBUILD_ARCHIVE]`, and `Shellwords.escape` it, because the archive name contains spaces and an unquoted path fails *identically* to this bug. (2) `autoUploadProguardMapping` keys off `SENTRY_AUTH_TOKEN` merely being **present**, so a job that omits the variable skips the upload silently rather than failing. `beta.yml` omitted it on Android, which meant beta never exercised the token. (3) On iOS, fastlane logs sentry-cli's error and carries on, so even an outright 401 leaves a green run. Together those hid a token rejected for nine days. Treat "nothing to upload" as fatal, keep that check outside any rescue, and validate a CI token against `GET /organizations/<org>/chunk-upload/` before setting it. Not against `/organizations/<org>/`: an `org:ci` token answers 403 there while uploading fine, so the friendlier endpoint rejects working tokens.
- **Do not gate anything that protects money or data on the release channel.** `beta.yml` stamps `RELEASE_CHANNEL_OVERRIDE: beta` and `release.yml` stamps `store`, and `Versioning.kt` bakes that into `BuildInfo.releaseChannel`. The value is correct when the binary is built and wrong forever after, because promoting a TestFlight build to the App Store is an ordinary action in App Store Connect that rewrites nothing: the binary that goes on sale is the one built for TestFlight, still saying `beta`. It is right at every moment you would think to check it, and promotion produces no artefact, no log line and no new binary, so the tag only becomes a lie in retrospect. One app shipped that way and found it by reading production events whose `build_type` was `"app store"` while `release_channel` said `"beta"`; it had invented an `isQaBuild` that was true for debug plus `beta` and used it to gate a QA menu reachable by deep link, leaving a live App Store build one link and one toggle from granting its paid unlock free. Gate on `BuildInfo.isDebug`, which cannot survive a release build at all, and let the channel do what it is good for, which is labelling telemetry. Two corollaries: a local build script that stamps `beta` for TestFlight will eventually produce a binary someone promotes, so `store` is the safer default with `beta` as the explicit opt-in; and a debug-only affordance reached by deep link needs its *route registration* gated, not just whatever UI normally opens it.
- **An empty result and a failed fetch are the same shape, and debug is the wrong level for either.** A remote fetch that parsed, succeeded, and still cannot produce the thing the screen exists to offer is an error, not a debug line, and debug is exactly the level a release build drops. One app's paywall rendered with no price and a buy button that errored on tap: nothing crashed, every `when` branch was handled, and no log survived the release severity filter. It was rejected by App Review for it and spent two weeks unable to reproduce, because the failure was in the reviewer's environment and produced no signal anywhere. Once the telemetry existed it took one read of Sentry: five events tagged "store returned no matching product" from a device reporting model `iPhone99,7` with a `VMAPPLE` kernel, running the exact build under review. The bug is never in the branch, it is in the severity of the branch, which no review catches and no test asserts. `SentryLogTree` already raises an event at error and above, so the whole fix is choosing the level deliberately. Report once per process: a store that is unhappy stays unhappy, and reporting on every foreground turns one broken build into thousands of identical events.
- **On the server, `withSpan` parents to the *current* OTel context.** Correct inside a request handler, wrong anywhere the current context outlives the unit of work. A WebSocket upgrade span stays current for the life of the socket, and a shared `Dispatchers.Default` scope leaves contexts on pool threads for unrelated work to inherit. Downstream this produced one trace id spanning hours and several users, permanently stuck at "root span not yet received". Root a new trace per unit of work. Full detail in the `withSpan` KDoc in `apps/server/.../plugins/Tracing.kt`.

## iOS Notes

- iOS framework compiled from `apps/compose`, embedded as `ComposeApp.xcframework`
- Swift types passed to Kotlin via `IosAppComponentFactory.create(...)`
- Reference `apps/compose/build/bin/iosSimulatorArm64/debugFramework/ComposeApp.framework/Headers/ComposeApp.h` for generated Swift interfaces
- **Use `@ObjCName("TypeName", exact = true)` on Kotlin types used from Swift** to give stable names that won't change when project is renamed:
  ```kotlin
  @file:OptIn(ExperimentalObjCName::class)
  import kotlin.experimental.ExperimentalObjCName
  import kotlin.native.ObjCName
  
  @ObjCName("MyType", exact = true)
  interface MyType { ... }
  ```
  Note: The `exact = true` parameter prevents module prefixes from being added. Without it, the Swift name would be `<ModuleName><ObjCName>` (e.g., `ComposeAppMyType`).

## Key Files

| Purpose | Path |
|---------|------|
| User model | `libraries/opson/src/.../User.kt` |
| SEAViewModel | `libraries/flowroutines/src/.../SEAViewModel.kt` |
| App DI | `apps/compose/src/.../AppComponent.kt` |
| iOS entry | `apps/ios/iosApp/iOSApp.swift` |
| Swift↔Kotlin patterns | `docs/swift-kotlin-communication-patterns.md` |

