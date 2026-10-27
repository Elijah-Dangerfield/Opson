# Architecture decisions

Append-only log. Add an entry whenever you make a non-trivial architectural call
(new module boundary, library choice, scope cut, schema shape). Each entry: date,
the decision, alternatives considered, and *why*. Newest first.

---

## 2026-10-27: Accounts are required; a reinstall starts signed out

**Decision:** onboarding goes Welcome, sign in, questions, mode, Today. Sign-in
is Apple on iOS and Google through the browser, as in Cards; email stays behind
`SignInMethods.EMAIL`. There is no guest path: `GuestAccountCreator`,
`createGuestSession` and anonymous sign-in are gone, and the healer
(`SessionHealer`) sends a stranded device to sign in instead of minting.

A new account goes on to the questions. An account that already exists goes
straight to Today: its mode is on the server, and running setup again would
write over it, which for someone who hides numbers is the worse mistake. The
split reads the server's one-shot `isNewAccount` (`AuthOutcomeClassifier`).

**Guests from older builds** keep their log. An onboarded guest is sent to the
sign-in step (`AppViewModel.signInRequired`), which links Apple or Google onto
the anonymous user (Supabase identity linking, as the account step did). If
that Apple ID or Google account already has an Opson account, the step says
so and offers to sign in to it, warning that the guest's log stays behind.

**A new install starts signed out.** iOS keeps Keychain items when the app is
deleted, so a reinstall read the last install's session; `OnboardingViewModel`
then took a leftover guest for a returning one and went to Today. That is why
the owner's fresh TestFlight install skipped onboarding (`release.onboardingSetup`
only adds steps and was not the cause). Now the first session load of an
install that has not finished onboarding (`FreshInstall`, a marker in the app's
own files) drops a signed-in session it finds in the Keychain. Signing in again
brings the account back. An anonymous one is kept, because a guest has no
credential to come back with and the sign-in step links onto it.

**Alternatives:** keep the Keychain session on reinstall and skip sign-in.
Rejected: deleting an app reads as signing out, the person may not be the same
one, and it is the behaviour the owner reported as a bug. Run setup again for
returning accounts. Rejected for the reason above.

## 2026-10-27: Grafana may see one person, through `support`, and never their log

**Decision:** a second read-only schema, `support` (V22), holds per-person
views for the owner's User detail and Users boards and the admin console's
user lookup: account id, email, display name, which builds each install ran,
counts per day, timestamps and Settings choices from an allowlist. `grafana_ro`
reads it as well as `analytics`. `analytics` keeps its rule: aggregates only,
no user id.

**Why:** the owner asked for Cards' user detail board as a CRM stand-in, and
support needs to answer "what is this person running and how do they use it".
The V9 promise that mattered was never "no user id" for its own sake; it was
that no panel can show one person's food log. That holds: no `support` column
carries a food name, amount, nutrient, weight, water, note, feeling, check-in
or onboarding answer, target or fast length, and `PostgresUserSupportTest`
fails if one appears. Emails reach the admin console and the User detail board,
which read the database; they never reach telemetry.

**Alternatives:** stamping `user_id` on client events in Loki. Rejected: it
only reaches builds from 0.2 on, and it would put the account id on every
event, where the install id is enough once `user_installs` maps one to the
other.

## 2026-10-27: One colour scheme, Slate; the palette setting is dropped

**Decision:** the four palettes (Pine, Espresso, Clay, Plum) are gone. Slate,
in light and dark, is the only scheme (design sync 4, `colors-slate.png`).
Appearance (System, Light, Dark) is the existing top-level `theme` key. The
`palette` key is no longer read or written: the field left `Settings`, and
decoding ignores it.

**Why:** the owner chose one scheme. Dropping the key, rather than keeping it
with one value, means no stored value can be wrong: a phone that had Espresso
reads Slate like everyone else. The server merges settings one level deep, so a
0.2 save leaves the key where it was, and a 0.1 phone on the same account keeps
the palette it picked (`AppearanceAcrossVersionsTest`).

**Alternatives:** map each palette to an appearance (Espresso to Dark, say).
Rejected: every palette had a light and a dark set, so a palette says nothing
about light or dark.

## 2026-10-27: Full numbers shows vitamins and minerals

**Decision:** Full numbers (and Sprint, which is "same as full, plus a banner")
shows micros. Every other preset keeps its level: Delayed, Blind, Nutrient,
Rhythm, Explorer, Mindful, Fuel and Coast all still hide them.

**Why:** the handoff ported the prototype's `FULL_VIS`, which made micros an
opt-in through Modes and numbers. Full's own line is "Every number visible, all
day", and the owner, on Full on a real phone, asked why no food had a
micronutrient breakdown when about 98% of catalog rows carry one. The opt-in
was a tidiness call with no safety reason behind it (nothing in the plans or
the `TODO(clinician)` notes mentions micros), and the modes that exist for
safety never showed micros in the first place.

**Alternatives:** keep the opt-in and make the Nutrients page's "turn them on"
card louder. Rejected: it still breaks Full's promise, and the amount sheet
has no such card, so a Full user there sees nothing at all.

**What changes for people:** a Full or Sprint user sees All nutrients on the
amount sheet and vitamins and minerals on the Nutrients page. Anyone who
turned micros on by hand keeps an override that now matches the preset; anyone
who turned them off keeps them off.

---

## 2026-09-21: One Grafana stack for every project, separated by service_name

**Decision:** every app generated from this template sends to the same Grafana
Cloud stack. They are told apart by the `service_name` label, which
`GrafanaLogTree.SERVICE_NAME` and the server's `OTEL_SERVICE_NAME` both derive
from the project name, so the rename pass makes it per-project for free.
`deployment_environment` splits dev from prod inside that. The three
`GRAFANA_*` credentials are therefore account-wide and live in the machine
store alongside the Sentry org token.

**Why:** the separation already exists in the labels, so a stack per project
buys nothing you do not already have and costs a second thing to administer.
One stack means cross-project queries, one alerting setup, and one set of
credentials that `setup_github_secrets.main.kts` pushes without anyone typing
them. It is the same shape as Sentry: one org, per-app separation inside it.

**What it costs, and this is the real argument for the other choice:** quota is
shared, so a chatty app can eat the log allowance and blind the others.
Retention is per-stack, so you cannot keep one app's data longer. And one
`glc_` token sits in every repo's CI, so a revocation takes out all of them at
once. Grafana auto-revokes tokens it finds in public repos, which makes that
less hypothetical than it sounds.

**Why the quota risk is survivable:** the levers are already per-app and
remote. `telemetry.appEventsSampleRate` is stable-hashed per session, so a
sample rate is all-or-nothing per session rather than a partial trace, and
`telemetry.appEventsEnabled` is an instant kill switch. Either can be turned
down for one noisy app without touching the others or shipping a build.

**When to split:** one app turns genuinely high-volume, or a project needs
different retention or access (a client's app, say). Moving is three secrets
and a rebuild rather than a migration, precisely because the separation lives
in the labels rather than in the stack.

## 2026-09-20: Provisioning is scripted per service, orchestrated by one entry point

**Decision:** each external service gets its own idempotent script
(`setup_supabase`, `setup_fly`, `setup_sentry`, `setup_github_secrets`,
`setup_credentials`), and `scripts/setup.main.kts` runs them in dependency
order, skipping what is done or not applicable. `init_project.main.kts` offers
to run the orchestrator at the end but never runs a service script directly.

**Why not one big script:** the steps have genuinely different prerequisites.
Supabase needs nothing but a token. Fly needs Supabase's output. Both
GitHub-facing steps need a repo that does not exist at the moment a project is
generated. A single script would have to encode all of that as internal
branching, and a failure halfway would leave no obvious re-entry point. Separate
scripts mean "re-run the one that failed" is always the answer.

**Why the orchestrator exists anyway:** the *order* is the part that is not
obvious, and getting it wrong does not fail loudly. It half-configures things
and leaves you to work out which half.

**Why init only offers it:** at the end of init there is no GitHub repo, so two
of the five steps cannot run. Running the chain inline would half-finish by
construction. The offer exists because the alternative, a wall of instructions
nobody reads, is how this template already shipped a blank Sentry DSN.

**What stays manual, permanently:** Play Console (app entry, service-account
invite, data safety, content rating) has no API for any of it. App Store Connect
listing copy and screenshots. The first release promotion in each store. These
are called out as their own group in init's closing output rather than mixed in
with the automatable steps, because a checklist that blurs "run this" with "go
fill in a web form" is one people stop reading.

## 2026-09-20: Apple sign-in is native-only, so there is no secret to rotate

**Decision:** Apple sign-in runs through the iOS system sheet
(`ASAuthorizationController` → identity token → Supabase
`signInWith(IDToken)`), and Supabase is configured with the bundle ID in
**Authorized Client IDs** and nothing else. The Services ID and client-secret
fields stay empty. `scripts/rotate_apple_sign_in_token.main.kts` is deleted,
along with the two store keys that fed it.

**Why:** the client secret belongs to Apple's browser OAuth flow, which nothing
here calls: the UI only ever passes `OAuthProvider.Google` to
`signInWithOAuth`, and `AndroidAppleSignInCoordinator` is a deliberate no-op.
Apple caps that secret at 180 days, so keeping the script meant every generated
app inherited a recurring six-month chore for a flow it does not use, plus a
SETUP checklist line telling people to do it. A maintenance obligation nobody
needs is worse than a missing feature: it gets done.

**What it costs:** offering Apple sign-in on Android later means the browser
flow, which means a Services ID, a client-secret JWT, and rotation before every
expiry. The script that minted that JWT is in this repo's history. It signed
an ES256 assertion from an Apple `.p8`, and the shape is the same one Supabase's
own docs describe. `toSupabaseProvider` carries a comment at the dead Apple
branch so the obligation is discovered at the code, not after shipping.

## 2026-09-20: Declining the backend at init is destructive, not staged

**Decision:** `init_project.main.kts` asks whether the project ships its own
backend. Answering no **deletes** `:apps:server`, `:apps:admin`,
`:apps:integration`, both Fly deploy workflows and the server/integration CI
jobs, and edits `settings.gradle.kts`, the root `build.gradle.kts` and the
three root docs. Nothing is staged for a later opt-in.

**Alternatives:** follow the CI precedent and stage the modules under
`template/` for a `scripts/enable_backend.sh`. **Why not:** the CI opt-out can
stage because installing CI afterwards is a pure file move: the staged files
have already been through the rename and substitution passes, so
`enable_ci.sh` just moves them. A backend is not a file move: it has to come
back into `settings.gradle.kts`, into `ci.yml` and into two deploy workflows.
A staged copy would be a folder of source with no switch, and one that quietly
rots because nothing builds it. Adding a backend later is a diff against the
template, which is where it stays maintained. The prompt says this out loud
rather than implying reversibility by analogy with the CI question.

**Also decided:** the prompt asks about *this repo shipping a server*, not
about network access. `:libraries:identity` (Supabase auth) and
`:libraries:networking` do not depend on `:apps:server`, so a client-only app
still signs in and calls third-party HTTP. "Does your app have a backend"
invites the wrong answer from anyone who reads it as "does your app use the
internet".

**Cost accepted:** every edit is anchored on exact text, so rewording the
affected passages breaks generation. It fails loudly rather than skipping the
edit, and `verify_template.sh` generates a client-only project on every run.

## 2026-09-20: One machine-local store for setup credentials

**Decision:** the setup scripts resolve credentials environment variable →
`~/.config/appsetup/credentials.properties` → prompt. The store holds only
values that are identical across every project you generate (Sentry org and
tokens, Fly deploy tokens, Apple team and key IDs, Supabase access token).
`scripts/setup_credentials.main.kts` populates it; `scripts/lib/setup_store.main.kts`
is the shared library.

**Why env first:** CI already exports these, so env-first means CI keeps
working untouched and a one-off override stays possible.

**Why the path says nothing about this template:** `init_project.main.kts`
rewrites the template's name in every text file it copies, so a path
containing it would be rewritten per project, and the entire point is that
the app you generate next month reads what you typed today. Same reasoning as
the `serverOnly` system property being project-agnostic.

**Why every script still prints two summaries:** the store will often be
absent (another laptop, a CI runner, a contributor who is not you), so it is
an accelerator and never a requirement. But a store that quietly fills some
values and leaves others blank reproduces the blank-Sentry-DSN failure across
every credential it touches. Blank is a supported value meaning "off", and off
looks exactly like working. So every run says where each value came from
before it starts, and what is still unset and what that costs when it
finishes. A non-interactive run with a missing required value dies naming the
value and how to supply it rather than proceeding with a blank.

**Not stored:** whether `gh` is authenticated. That is probed live. A cached
"yes" goes stale on token expiry, and a script that skips asking because of a
stale flag then fails somewhere less obvious, the exact silent
misconfiguration this design exists to prevent.

## 2026-06-21: Server mirrors client conventions

**Decision:** `:apps:server` reuses the client's stack, kotlin-inject + anvil DI
(`ServerScope`/`ServerComponent`), the `domain/` interface + `data/` impl split,
conventional commits, the version catalog. It's a plain JVM `application` module
(no convention plugin; those are KMP-only).

**Why:** one mental model across client and server. An agent (or human) moving
between them doesn't re-learn DI, error handling, or module layout. The cost was
accepted: the server can't use the KMP `:libraries:core` (`Catching`, logging)
because that module has no JVM target, so it keeps a couple of small local
equivalents rather than forcing a `jvm()` target onto every client library.

## 2026-06-21: Graceful degradation over required config

**Decision:** `DATABASE_URL`, `SUPABASE_URL`, `SENTRY_DSN`, and the OTLP endpoint
are all optional. With none set, the server boots and serves `/_health` +
`/v1/example`; DB-backed and authenticated routes simply aren't mounted, Sentry
no-ops, and OpenTelemetry exports to stdout.

**Alternatives:** require `DATABASE_URL` + `SUPABASE_URL` like the Cards origin
(fail-fast). **Why optional:** this is a template, "clone and run, see it boot"
beats a fail-fast error on first run. The fail-fast discipline still applies per
field via `Env.require` when a future field genuinely can't be defaulted.

## 2026-06-21: Auth is JWKS verification, never a shared secret

**Decision:** the server verifies Supabase JWTs against the project's public keys
(JWKS / ES256). The `JwtVerification` sealed seam has `Jwks` (prod) and `Static`
(tests mint HS256 tokens against a known verifier).

**Why:** no Supabase secret ever lives on the server, and auth. The highest-risk
surface, is fully testable offline (route tests + `FullStackMeTest` run the real
validate/challenge path with no network).

## 2026-06-21: `NoOpAuthTokenProvider` lives in the `:networking` api module

**Decision:** the default no-op `AuthTokenProvider` binding sits in
`:libraries:networking` (api), not `:impl`.

**Why:** the module-boundary rule forbids one `:impl` depending on another, but
`:libraries:identity:impl` must reference `NoOpAuthTokenProvider` to override it
with `@ContributesBinding(replaces = [NoOpAuthTokenProvider::class])`. Putting the
default binding next to the interface it defaults keeps the replacement
boundary-clean. (See also the `enforceModuleBoundaries` self-edge fix in
`build-logic`.)

## 2026-06-21: `serverOnly` build slimming

**Decision:** `-DserverOnly=true` makes `settings.gradle.kts` include only
`:apps:server`, so a Docker image build needs no Android/iOS toolchain. The
property name carries no project prefix on purpose, so the rename pass cannot
break the Dockerfile↔settings contract.

**Why:** this is a KMP monorepo; without slimming, a server image build would
configure every client module and need the Android SDK + Kotlin/Native. The
server has no client-library deps today, so the gate is a pure settings change;
if it gains one, add an always-included `include(...)` + a Dockerfile `COPY`.

## 2026-06-21: Flyway SQL is the schema source of truth

**Decision:** migrations under `resources/db/migration` define the schema; the
Exposed `Tables.kt` objects are read-side projections kept honest by
`DatabaseSchemaTest`. Repositories treat a unique-violation (SQLSTATE `23505`) as
the arbiter rather than pre-checking for races.

**Why:** one procedure for schema change (add the next `V##__name.sql`, never edit
an applied one), and idempotency that's correct under concurrency.

## 2026-09-09: Ports considered and rejected

**Decision:** three things a downstream app offered back are deliberately not in
this template. Recorded so they don't get re-proposed each time someone reads
that app's setup and notices the gap.

- **Macrobenchmark `FrameTimingMetric` in CI.** The right tool for catching jank
  regressions, and it needs a real device. Emulator frame timing on a shared CI
  runner varies more run-to-run than the regressions worth catching, so any
  threshold produces flaky red and gets disabled within a month. Only worth it
  for a project with a device farm. Real-user frame timing (`app.jank`) covers
  the same question continuously and is in the template instead.
- **The Grafana dashboards themselves.** The queries encode one app's event
  names. The *conventions* are portable and already carried; the boards are not.
- **The observability routine and its skills.** Genuinely useful, and shaped
  entirely around one project's dashboards, alert ids and inbox. Revisit only if
  a second app wants the same thing. That is the point at which the generic
  shape becomes visible.

**Why here rather than the port queue:** the queue is work waiting to happen, and
these are closed questions. Keeping them there made an empty queue impossible.

## 2026-09-14: Grafana dashboards stay out; the events registry comes in

**Decision:** this template ships no dashboard JSON and no dashboard contract
test. It does ship [telemetry-events.md](telemetry-events.md), a description of
what a generated project emits before anyone adds a feature.

**Why:** a downstream app re-proposed the dashboards with a sharper argument:
that more of its event surface is app-shaped than game-shaped, and that a
purchase-funnel board in particular is the one most likely to be wrong in a way
nobody notices. Both true. But its own recommended order starts with "decide
which events the template *guarantees* every generated app emits", and that is a
design decision nobody has made. A board querying an event a generated app might
not send reads zero forever and is indistinguishable from a real zero, which is
strictly worse than no board.

So the prerequisite landed and the boards did not. The registry is deliberately
worded as a description rather than a contract, because promoting it to a
guarantee is the decision still outstanding. Writing it surfaced one real bug
immediately: `conn.reconnecting` exists only in a test fixture, so any panel
built on it would have read zero forever.

The contract test that holds queries to emit sites is the genuinely portable
idea in that proposal, and it is worth writing the moment a generated project
has boards worth holding. Its traps are recorded at the bottom of the registry
so nobody re-derives them, in particular that a Gradle test task which does not
declare `inputs.files(...)` stays UP-TO-DATE and passes while checking nothing.
