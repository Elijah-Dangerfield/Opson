# Testing approach

How this codebase tests, layer by layer, and the conventions every new test
follows. The goal is a pyramid where each bug class is caught at exactly one
layer, the cheapest layer that can fail when it breaks.

## The layers

- **Library / feature unit tests** (`commonTest` in each module): pure logic,
  repositories over fakes, and SEAViewModel behaviour. `HomeViewModelTest` is
  the reference recipe for VM tests: extend `CoroutineTest` (from
  `:libraries:flowroutines:testing`), hand-roll a fake per repository
  dependency, drive actions, assert on `vm.state`.
- **Scenario harness** (`features/home/impl` `commonTest/harness/`): when a
  feature's tests keep re-wiring the same fakes, grow a tiny builder + verbs +
  `assertState {}` DSL next to them so tests read as user scenarios.
  `HomeScenario` / `HomeScenarioTest` demonstrate the shape. It's a pattern,
  not a framework, copy and adapt, don't generalize.
- **Composition tests** (`androidUnitTest` in a Compose module): a real
  composition, driven and asserted on the host JVM. Only for claims that are
  about composition itself: effects running and disposing, recomposition, what
  is on screen. See below.
- **Preview screenshots** (`:apps:screenshots`): every `@Preview` rendered to a
  PNG under Robolectric with Roborazzi. Catches what a screen *looks like*
  changing: record before, verify after. Not part of the plain unit-test sweep;
  run `./gradlew :apps:screenshots:verifyRoborazziDebug`. The setup, the screen
  id rule and where the baseline lives are in
  [design-sync.md](design-sync.md), because the same renders feed the design
  sync.
- **Leak tests** (`:apps:leaktest`): every surface each feature lists in
  `XLeakSurfaces.all`, rendered under Robolectric in every mode that does not
  show calories and scanned for a calorie amount or an "N left". Runs in the
  plain `testDebugUnitTest` sweep. A failure is a real leak: fix the screen.
  Plan: `docs/plans/screens/blind-leak-test.md`.
- **Server unit + route tests** (`:apps:server` `src/test`): plugins and
  routes through Ktor's `testApplication`, repositories over Testcontainers
  Postgres (`DatabaseTest` base class).
- **Full-stack server test** (`:apps:server` `FullStackMeTest`): the real DI
  graph over real Postgres through the same `installApp` seam production
  boots, with a `JwtVerification.Static` verifier swapped in. Proves component
  + auth + repository + route integrate; the client side is Ktor's test client.
- **End-to-end integration** (`:apps:integration`): the real *client* stack
  against the real server. See below.

## Composition tests

`FloatingWindowHostTest` (`:libraries:navigation`) is the worked example and the
first thing in this repo that ever asserted against a composition. It drives a
real `NavController` and the real `FloatingWindowHost` through push and pop, and
asserts that the popped entry was released and that the window's state was
scoped to its own back stack entry. Those are claims about effects running,
disposing, and the owners a composable sees, which no view-model test can reach.

The recipe, to copy:

```kotlin
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MyThingTest {
    @get:Rule val compose = createComposeRule()
    …
}
```

Where it lives and why: **`androidUnitTest`, not `commonTest`, and not a `jvm()`
target.** `commonTest` also compiles for iOS, where none of the harness
resolves. A `jvm()` target would be the Compose Multiplatform-idiomatic answer
(`runComposeUiTest` on desktop) but it cascades through every library that
already carries an `actual` (core, ui, networking, storage, telemetry), each
needing a third implementation written for nobody, plus a third value on the
two-case `Platform` enum that several exhaustive `when`s read. That is a lot of
shipped surface invented to serve tests, and it contradicts the stance
`:apps:integration` already took: reuse the Android variants on the host JVM.

Setup lives in `libraries/navigation/build.gradle.kts`. Two parts are
load-bearing and worth copying wholesale into the next module that wants this:
`testOptions.unitTests.isIncludeAndroidResources = true`, and the
`stageRobolectricJars` task. The second resolves Robolectric's Android framework
jar through Gradle and runs Robolectric offline against the staged copy, instead
of letting it fetch ~200MB from Maven into `~/.m2` at test time. Nothing caches
`~/.m2` on CI, and a network call inside a test is how a tier earns a reputation
for flaking.

It runs under `./gradlew testDebugUnitTest`, so CI's existing unit-test job
already covers it.

**What this layer is not for.** It is slow (a Robolectric sandbox per class) and
it is the easiest place in the codebase to write something that passes for the
wrong reason. Anything a view-model test or a pure-function test can answer
belongs there instead. Mutate the thing you think you are covering and watch the
test go red before you believe it: several branches in `FloatingWindowHost` turn
out to be unreachable from outside the host, and the test file names them rather
than implying coverage it does not have.

## The integration harness (`:apps:integration`)

An Android-library module whose tests run as **host-JVM Android unit tests**
(`./gradlew :apps:integration:testDebugUnitTest`), the same compilation path
the feature view models already use, so the harness can construct them
directly. `commonMain` is deliberately empty: nothing ships from this module,
and the iOS target must never try to link the JVM-only server.

What runs where in a harness test:

- **In-process, real:** a Netty engine on an ephemeral port booted through the
  production `installApp(component, verification, …)` seam
  (`InProcessServer`), a real `ServerComponent` over a Testcontainers
  Postgres with the real Flyway migrations, and the real JWT auth plugin
  verifying HS256 tokens the test mints (`IntegrationAuth`, against the
  `JwtVerification.Static` seam).
- **In the client, real:** everything from the repositories down, wired by
  hand in `TestClient` the way the DI graph wires it: the diary repositories,
  the outbox and `DiarySyncer`, settings, fasting, the food catalog, photo
  estimates, remote config, `SupabaseAuthRepositoryImpl` with the user-change
  reset, `NetworkClientImpl` with the real headers, reachability and buses, and
  the app's own `AppDatabase` in memory under the bundled SQLite driver (its
  desktop native is unpacked by the module's build file). Requests travel real
  TCP, real serialization, real headers, real status codes.
- **Fake, on purpose:** exactly the seams a device owns (`DeviceSeams.kt`): the
  Supabase SDK (`StubSupabaseGateway`, holding a session whose JWT the server
  trusts), on-disk caches (an in-memory `CacheFactory` running the real store
  logic), the app lifecycle and the clock. On the server side only the outside
  services are stood in for (`FakeUpstreams`: Supabase admin, Anthropic,
  Open Food Facts), so a journey that reaches them stays on the machine.

`HarnessSmokeTest` pins the worked example: mint a JWT, boot the server, drive
the home VM, and assert the display name on screen came from the server's
`/v1/me` get-or-create, real client → real TCP → real server → real DB. The
journeys sit beside it by area (`onboarding/`, `sync/`, `fasting/`, `foods/`,
`account/`, `security/`, `waitlist/`, `errors/`, `config/`). New end-to-end
flows follow the same shape: use or add a surface on `TestClient`, a seed or
query helper on `InProcessServer` if the server side needs one, and await
state with `awaitState` / `awaitUntil`. Never fixed sleeps; step a `TestClock`
(the client's or `server.clock`) past a throttle or cache instead of waiting it
out. Every test makes its own users, barcodes and food names, because the
Postgres container is shared by the whole JVM. Docker down → the suite skips
(JUnit `Assume`), not fails.

**Fault injection:** `FaultProxy` sits between a client and the server. It can
take the network down (connections refused and cut, no HTTP status at all) or
answer chosen paths with a 5xx, a hang past the client's timeout, a body that
is not the contract, or a 502 after the server applied the request, which is
what a lost response looks like. Both sides stay real; only the wire between
them misbehaves.

## Which layer catches which bug (don't duplicate)

When you're tempted to assert the same thing at two layers, the lower one wins
and the higher one doesn't get written.

| Bug class | Owning layer | NOT here |
|---|---|---|
| Pure logic, data mapping, validation rules | library unit tests | never re-tested above |
| Action → state derivation in a VM | feature `commonTest` (VM unit / scenario) | not integration |
| Route status codes, error envelopes, auth challenge shapes | `:apps:server` route tests | not integration |
| SQL, migrations, repository contracts | `:apps:server` Testcontainers tests | not route tests |
| DI graph constructs against a live DB | `FullStackMeTest` | not per-repository tests |
| Effects running/disposing, recomposition, what is on screen | composition tests | not VM tests, which cannot see a composition |
| How a screen looks: layout, spacing, colour, type | preview screenshots | not composition tests, which assert on semantics rather than pixels |
| Client↔server contract drift (serialization, headers, auth handshake, real HTTP semantics) | `:apps:integration` | not unit tests with canned JSON |

Integration tests aren't a substitute for unit tests, they're slower and
harder to debug. Use them for the seam contract (real wire, real plumbing),
not for every rule the lower layers already own.

## Conventions

- **Hand-rolled fakes only.** No Mockito / MockK anywhere. Fakes go in
  `commonTest` next to their consumer, or in a shared `:libraries:x:testing`
  module if reused.
- **Dispatcher choice:** `UnconfinedTestDispatcher` (the `CoroutineTest`
  default) for most tests, continuations run eagerly, mirroring
  `viewModelScope` under `Main.immediate`. `StandardTestDispatcher` only for
  time-sensitive tests (timeouts, debounce, backoff) that need an explicit
  dispatch-then-advance cadence. Integration tests use a **real** Main
  dispatcher (`Dispatchers.setMain(Dispatchers.Default)`) because real sockets
  run on real threads.
- **`runCurrent()` not `advanceUntilIdle()`** when there's an unwanted
  future-scheduled task (e.g. a `withTimeout`), `advanceUntilIdle()` fires it.
- **`runCatching { … }` not `assertFailsWith { … }` around suspend bodies.**
  `assertFailsWith` has subtle suspend-context issues where an async-thrown
  exception reaches the test scope before the assertion runs. Call the suspend
  function inside `runCatching`, then assert on the captured result.
- **Every test file gets a top-level KDoc** explaining what's covered and
  what's intentionally NOT covered (and where that lives instead).
- **Integration tests belong in `:apps:integration`.** Don't inline a real
  server into a feature-module test.
- **No fixed sleeps.** Await a flow (`awaitState`), or poll with a timeout
  (`awaitUntil`) when the state has no push signal.
