# Opson setup checklist

Action items after running `./scripts/init_project.main.kts`.

## Start here

```sh
./scripts/install_hooks.sh    # the build fails until you do this
./scripts/setup.main.kts      # walks everything below that can be automated
```

`setup.main.kts` runs the steps in the order their dependencies allow and skips
anything already done, so it is safe to re-run and safe to abandon halfway.
`./scripts/setup.main.kts --status` answers "where am I" without changing
anything.

What it can do for you, and what it can't:

| | |
| --- | --- |
| **One command** | Supabase project + auth config, Sentry project + DSN + CI vars, Fly apps + secrets + deploy tokens, every signing secret |
| **Once per machine** | `gh auth login`, `fly auth login`, and filling the [credential store](#credential-store-do-this-on-your-second-project) |
| **Always by hand** | Play Console app entry + service-account invite + data safety + content rating, App Store Connect app record + listing, promoting the first release in each store |

The rest of this page is the detail behind each of those, plus everything the
scripts deliberately don't touch. Read the section for a step when it fails or
when you want to know what it did.

**Hour 1: a running app**
- [ ] [Local dev](#local-dev): hooks + first build
- [ ] [Credential store](#credential-store-do-this-on-your-second-project): skip on your first project
- [ ] [Supabase auth](#supabase-auth-hour-1): project, providers, redirect URLs
- [ ] [Sentry](#sentry-one-script): one script, then crash reporting is on everywhere
- [ ] [Server deploy](#server-deploy-flyio): dev Fly app + secrets + `/_health`

**Day 1: pipelines + visibility**
- [ ] [GitHub secrets](#github-secrets-only-if-you-enabled-ci) (only if you enabled CI)
- [ ] [Repo settings](#repo-settings): Pages, Actions, branch protection, the `production` Environment
- [ ] [Day-1 verification](#day-1-verification): prove telemetry + deploys actually work

**Before shipping:**
- [ ] [Store listings](#store-listings): Play Console + App Store Connect
- [ ] [First release](#first-release): the manual-promotion gotcha
- [ ] [App icons](#app-icons)

---

## Local dev

```sh
./scripts/install_hooks.sh   # commit-msg hook for Conventional Commits
./gradlew build              # first sync + build
```

The Gradle build fails with an install-hooks message if you skip `install_hooks.sh`. That's intentional, release-please derives version bumps from commit history, so every commit must be in Conventional-Commits form (`feat:`, `fix:`, etc.).

To bypass in scripted contexts (not CI, `CI` env var is honored): `-Dopson.skipGitHooksCheck=true`.

---

## Credential store (do this on your second project)

```sh
./scripts/setup_credentials.main.kts
```

Most of what this checklist asks for belongs to your Sentry org, your Fly
account or your Apple team, and is identical for every app you generate. This
saves those answers once, on your machine, so the next project stops asking.
Everything here is optional: every setup script still prompts for whatever it
cannot find, and every script prints where each value came from before it
starts and what is still unset when it finishes.

On your first project there is nothing to reuse yet, so skip this and let the
scripts collect values as they go, each one offers to remember what you typed.

| | |
| --- | --- |
| Lives at | `~/.config/appsetup/credentials.properties` by default, mode 0600, outside every repo. `--move-to ~/Documents/appsetup` puts it somewhere iCloud syncs, which the scripts also search. A backup for what you cannot regenerate, a wider blast radius for what you can. |
| Precedence | environment variable → store → prompt (so CI is unaffected) |
| Holds | Sentry org + tokens, Supabase token and org slug, Fly org slug, Apple team / key IDs, signing passwords, and the *path* to your signing folder |
| Does not hold | the signing files themselves, or anything per project. The Sentry project slug derives from the `applicationId`, the Fly app name from the project name |
| New machine | run the script again there |

`--list` shows what is set (secrets masked); `--clear` forgets it all; `--import <file>` reads an env-format file, which is the quick path if you already keep a folder of shared release secrets. The store's keys are named after the CI secrets so most of them land untouched. The file
holds live deploy tokens in plain text, so if you copy it between machines,
treat the copy like the tokens and keep it out of anything that syncs.

---

## Sentry (one script)

The app ships with crash reporting off: `telemetry.properties` has a blank
`sentry.dsn`, nothing initialises, and nothing costs anything at runtime. Turn
it on once and it is on for every developer and every build type:

```sh
./scripts/setup_sentry.main.kts
```

It asks for one thing, a Sentry **user** auth token, then creates or adopts the
project, writes the DSN into `telemetry.properties`, sets the CI variables, and
finishes by sending a real event and waiting for it to arrive. It prints
whether that worked. If it says the event did not arrive, the setup is not done.
The whole point of the check is that a broken Sentry setup otherwise looks
identical to a working one.

Re-run it any time; every step is an upsert.

**Rotating or fixing just the CI token.** `./scripts/setup_sentry_ci.sh` sets
`SENTRY_AUTH_TOKEN` and nothing else, and it proves the token can upload before
it sets it. That check is the reason the script exists: a token that
authenticates but cannot upload looks exactly like a working one, and on iOS
fastlane logs sentry-cli's rejection and carries on, so the run stays green. One
app generated from this template carried a rejected token for nine days that
way. The full script above validates too, so reach for this one when you only
want to swap the token.

**Get the right token.** Sentry has two kinds and they are not interchangeable:

**The prefix is how you tell them apart.** `sntryu_` is personal, `sntrys_` is organization.

| Token | Where | Scopes | Use it for |
| --- | --- | --- | --- |
| **Personal** (`sntryu_`) | sentry.io, Account dropdown top left, then **Personal Tokens** ([direct link](https://sentry.io/settings/account/api/auth-tokens/)) | Selectable. Tick `project:read`, `project:write`, `org:read` | This script. Never written to the repo; saved to your [credential store](#credential-store-do-this-on-your-second-project) only if you say yes. |
| **Organization** (`sntrys_`) | **Settings > Developer Settings > Organization Tokens** | Fixed, not selectable | CI only (`SENTRY_AUTH_TOKEN`). Answers 403 to every read endpoint, so the script cannot use it. |

If the page offers you permission checkboxes you are on the personal one. If it
does not, you are on the organization one.

The script checks the prefix before it makes a request, so handing it an
organization token stops immediately with an explanation rather than a 403 that
looks like a broken or under-scoped token. It isn't broken; it's the wrong kind,
and no amount of re-issuing an organization token will help, because its
permissions cannot be changed.

Then **commit `telemetry.properties`.** The DSN belongs in git. It is a
write-only ingest endpoint that ships inside every store binary, anyone can
read it out of a published build in minutes, so it is not a secret, and any
scheme where each developer configures it locally means fresh clones silently
report nothing.

Dev versus prod needs no extra work. One project takes everything; the
`environment` tag is `{releaseChannel}-{platform}-{buildType}`, giving
`dev-android-debug` through `store-ios-release`.

---

## GitHub secrets (only if you enabled CI)

Set under **Settings → Secrets and variables → Actions**. All are required for `release.yml` to ship.

**Do this once, not once per app.** Every value below except `SENTRY_PROJECT` belongs to your Apple team, your Play developer account, or your Sentry org, and is identical for every project this template generates.

```sh
./scripts/setup_github_secrets.main.kts            # or --dry-run first
```

pushes all of them. Keep the certificate, the `.p8`, the upload keystore and the service-account JSON in **one private folder outside any repo**; the script asks for that folder once and the [credential store](#credential-store-do-this-on-your-second-project) remembers the path (never the files). The passwords beside them are account-wide, so the store holds those too. A new app then costs two values, not fifteen. See [release-automation.md → Secrets and variables](docs/release-automation.md#secrets-and-variables).

Anything it can't find is reported with what that specific gap costs, and everything else still goes up. So a partial folder is a useful run, not a failed one.

**Your Play service account can cover every app.** Invite it at the *account* level (Play Console → Users and permissions → Invite new user → grant permissions for the whole developer account rather than per-app) and future apps are covered automatically, so the JSON you save today keeps working for app number five. Verify after your next app appears: the service account should already be listed against it.

The one item that cannot be reissued is the Android upload keystore: once an app has shipped a build signed with it, losing it means asking Google to reset the upload key. Everything else on this page can be regenerated from a console in minutes.

### Android signing

| Secret | How to get it |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | `base64 -i upload-keystore.jks \| pbcopy` |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password |
| `ANDROID_KEY_ALIAS` | Key alias inside the keystore |
| `ANDROID_KEY_PASSWORD` | Key password |
| `PLAY_SERVICE_ACCOUNT_JSON` | Play Console → Setup → API access → create service account with *Release apps to testing tracks* + *Release apps to production*. Download the JSON. |

### Apple signing + App Store Connect

| Secret | How to get it |
| --- | --- |
| `APPLE_TEAM_ID` | Apple Developer → Membership → Team ID |
| `ASC_KEY_ID` | App Store Connect → Users and Access → Keys → Key ID |
| `ASC_ISSUER_ID` | Same page, Issuer ID (top of the Keys tab) |
| `ASC_KEY_P8_BASE64` | `base64 -i AuthKey_XXX.p8 \| pbcopy` |
| `APPLE_DIST_CERT_P12_BASE64` | Export your Apple Distribution cert from Keychain as .p12, then `base64 -i dist.p12 \| pbcopy` |
| `APPLE_DIST_CERT_PASSWORD` | Password you set when exporting the .p12 |
| `FASTLANE_APPLE_ID` *(optional)* | Apple ID email, for `fastlane deliver` |

### Sentry

| Secret | Notes |
| --- | --- |
| `SENTRY_AUTH_TOKEN` | Sentry → **Settings → Organization Tokens** → Create New Organization Token (`sntrys_…`). Used by `beta.yml`/`release.yml` to create releases + upload mappings/dSYMs. Organization tokens carry exactly one scope, `org:ci`, and it is not selectable: release creation, source map upload, code mappings. That is all CI needs. Do **not** go looking for `org:read` or `project:releases` to tick; those belong to personal tokens and are not offered here. A healthy org token also answers 403 to Sentry's read endpoints, so do not treat that as a broken token. |

`./scripts/setup_sentry.main.kts` sets this one for you, along with the two
variables below, under **Settings → Secrets and variables → Actions →
Variables** (not secrets):

| Var | Value |
| --- | --- |
| `SENTRY_ORG` | Your Sentry org slug |
| `SENTRY_PROJECT` | Your Sentry project slug |

There is no `SENTRY_DSN` secret. The DSN lives in the committed
`telemetry.properties`, see [Sentry](#sentry-one-script) for why. The
environment variable is still read first, so you can point one workflow
somewhere else, but you should not need to. `release.yml` fails before it
builds anything if neither is set: a store build with crash reporting off ships
blind, and that is not something to discover from a review rejection.

### Grafana Cloud telemetry (optional)

Used by `beta.yml` and `release.yml` to bake client app-event credentials into
store builds. Leave unset and the telemetry pipe stays dormant. The app builds
and runs fine.

`./scripts/setup_github_secrets.main.kts` pushes all three from your
[credential store](#credential-store-do-this-on-your-second-project), so this
is a fill-in-once set rather than a per-project one.

**These are account-wide because every project shares one Grafana stack.** Apps
are separated by the `service_name` label, not by having their own stack, so a
Loki query with no `service_name` matcher spans all of them. Quota and
retention are shared too. See `docs/practices/observability.md` for what that
costs and how to throttle one noisy app without touching the rest.

| Secret | Notes |
| --- | --- |
| `GRAFANA_OTLP_BASE_URL` | Grafana Cloud → OpenTelemetry → OTLP endpoint base URL |
| `GRAFANA_OTLP_INSTANCE_ID` | Same page, instance id (the numeric user) |
| `GRAFANA_LOGS_WRITE_TOKEN` | A Grafana Cloud access-policy token with logs:write. Grafana auto-revokes `glc_` tokens it finds in public repos, never commit one. |

### Server deploy (Fly.io)

```sh
./scripts/setup_fly.main.kts
```

creates both apps, points the fly configs at them, pushes the Supabase values
as Fly secrets, mints both tokens below into GitHub, and offers the first
deploy. Run `fly auth login` first. Creating apps is free, only the first
deploy starts a machine, and that step asks separately.

`server-deploy.yml` auto-deploys the dev server on pushes to `main` that touch
server paths; `server-deploy-prod.yml` queues a prod deploy behind a manual
approval. Requires the two Fly apps from `apps/server/DEPLOY.md`.

| Secret | Notes |
| --- | --- |
| `FLY_API_TOKEN_DEV` | `fly tokens create deploy -a <project>-server-dev --expiry 8760h` |
| `FLY_API_TOKEN_PROD` | `fly tokens create deploy -a <project>-server-prod --expiry 8760h` |
| `ADMIN_API_TOKEN_DEV` *(optional)* | The dev server's `ADMIN_API_TOKEN`, lets the deploy upload the per-version config manifest (see `apps/admin/README.md`). Skipped with a warning if unset. |
| `ADMIN_API_TOKEN_PROD` *(optional)* | Same, for prod. |

Also create the **`production` GitHub Environment** (Settings → Environments →
New environment → `production` → Required reviewers → add yourself).
`server-deploy-prod.yml` pauses on it until a human approves the run, without
the environment the prod deploy runs unguarded.

---

## Repo settings

- **Actions** → enable workflows.
- **Branch protection** on `main`:
  - Require PR.
  - Require status checks: `CI / Build + test`, `commitlint / Validate PR title`.
  - Require linear history (so release-please squash-merges cleanly).

---

## Store listings

Before `release.yml` can ship:

1. **Play Console** → Create app → fill out store listing, data-safety form, content rating, pricing/distribution. Create at least one internal track tester.
2. **App Store Connect** → My Apps → New App → pick the bundle ID that matches `apps/ios/fastlane/Appfile`. Fill out app info, pricing, privacy details.

   Two rejections arrive *after* the upload has already succeeded, which is what makes them worth knowing before you submit:

   - **ITMS-90129, name already taken.** Apple checks the binary's name for uniqueness at *delivery* time, not when you reserve it. The lever is `PRODUCT_NAME` in `apps/ios/Configuration/Config.xcconfig`, **not** `CFBundleName` in `Info.plist`, which `GENERATE_INFOPLIST_FILE` silently overrides. Setting it in the plist looks like it worked and reaches no build.
   - **ITMS-90683, missing purpose string.** Nothing to do today: the template touches no camera, microphone or location API, so it declares no `NS*UsageDescription` and this does not fire. It becomes yours the first time you add one, and the part that catches people is that delivery scans the binary for *API references* rather than call sites. Linking a framework is enough, so a dependency can trip this without you writing a line. `apps/ios/iosApp/Info.plist` carries the full rule in a comment, including why there is deliberately no camera key and how to find a reference you did not intend to add.
3. **TestFlight** external group: create a group named `External Testers` (or change `TESTFLIGHT_EXTERNAL_GROUP` in `release.yml`).
4. Privacy policy + terms of service URLs: written in [legal/](legal/) and published to `https://nightjarlabs.llc/<app-slug>/privacy` and `/terms`. Run `./scripts/setup_legal_sync.sh` once, merge the pull request it opens on the website repo, then paste those URLs into both store listings.

   **Read `legal/privacy.md` against your code first.** It ships claiming no ad or analytics SDKs and no account, because that is true of the template and is the first thing to stop being true of a real app. A live privacy policy that denies serving ads while the app serves them is a store problem and a trust problem at once.

---

## First release

> **!!! READ THIS BEFORE YOUR FIRST RELEASE !!!**
>
> Google Play and Apple both reject automated production uploads until a manually-promoted build exists. `release.yml` detects this and routes the **first** release to:
>
> - **Play internal track** (not production).
> - **TestFlight internal** (not external, not App Store submission).
>
> You must then, **once**:
>
> 1. **Play Console** → Internal testing → Promote release → Production. Fill out the production rollout form manually.
> 2. **App Store Connect** → TestFlight build → Submit for review manually.
>
> From release #2 onward, the pipeline uploads straight to Play production (10% staged rollout) and submits to the App Store with phased release.

The release-please PR shows a `!!! FIRST RELEASE !!!` banner the first time around so this is hard to miss.

---

## App icons

Drop your icons into:

- **iOS** → `apps/ios/iosApp/Assets.xcassets/AppIcon.appiconset/` (replace the placeholder set).
- **Android** → `apps/compose/src/androidMain/res/mipmap-*/` (replace `ic_launcher*.webp`). Three more pieces live outside those folders and are easy to leave as the placeholder: the background colour in `res/values/ic_launcher_background.xml`, the themed-icon layer in `res/drawable/ic_launcher_monochrome.xml`, and the adaptive icon itself in `res/mipmap-anydpi-v26/`. The monochrome layer is one flat colour by design — the launcher tints it, so a copy of your colour artwork comes out as a filled rectangle.
- **Shared** (used by Compose splash, about screens, etc.) → `libraries/resources/src/commonMain/composeResources/drawable/`.
- **Website** → `public/apps/<app-slug>.png` in the studio site repo, which is where the app's page and legal documents live.

---

## Networking

`:libraries:networking` ships a single configured `HttpClient` (plus an
authenticated variant) for every repo and data source to share.

**Set your base URL**, or deliberately don't. The shipped default leaves it
blank, which is a real configuration: an app with no API of its own still signs
in against Supabase and calls third parties by absolute URL. A *relative* path
with no base URL is rejected before it leaves the device, with a message naming
what to set. That is on purpose, Ktor would otherwise resolve it against
`http://localhost`, and the refused connection would trip the offline banner,
so an unconfigured project would present as offline on a device with full
signal.

The app's `NetworkConfig` is `OpsonNetworkConfig` in `apps/compose`, which
replaces `DefaultNetworkConfig`:

```kotlin
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, replaces = [DefaultNetworkConfig::class])
@Inject
class OpsonNetworkConfig : NetworkConfig {
    override val baseUrl: String = apiBaseUrl(BuildInfo.isDebug)
}
```

`apiBaseUrl` sends debug builds to `https://opson-server-dev.fly.dev` and
release builds to `https://opson-server-prod.fly.dev`. `SupabaseInfo` keys on
the same `BuildInfo.isDebug`, so the session and the server always belong to
the same environment.

**Authenticated calls.** Inject `NetworkClient` and use
`networkClient.authenticatedClient` for endpoints that need a Bearer token.
The token comes from your `AuthTokenProvider` binding (default is no-op).
401s trigger `refreshAccessToken()`.

**Wrap calls with `Catching { }`** at the call site. Ktor throws on non-2xx
and network errors; the rest of the codebase already uses this pattern.

**JSON config.** `NetworkJson` is strict in debug (unknown keys/missing
fields throw) and lenient in release (so a backend tweak can't crash users).

## Supabase auth (hour 1)

The app ships with Supabase auth and accounts required: Sign in with Apple
(iOS), Google through the browser, and email once `SignInMethods.EMAIL` is on,
with encrypted session storage and the `/v1/me` profile.

```sh
./scripts/setup_supabase.main.kts
```

does steps 1 to 4 below, creates the project, turns on anonymous sign-ins,
sets the redirect URLs, authorizes your bundle ID for Apple, and writes
`local.properties`. It needs a Supabase personal access token (`sbp_…`) from
<https://supabase.com/dashboard/account/tokens>, which the credential store
keeps for next time. It prints the generated database password **once**, save
it, the API will not hand it back.

The steps below are what it does, and what to do if you'd rather do it by hand
or something failed:

1. Create a Supabase project (free tier is fine). Note the project URL and
   the **publishable (anon) key** (Settings → API Keys).
2. Client config. Debug builds read the dev project from `local.properties`
   (or `SUPABASE_PROJECT_ID` / `SUPABASE_ANON_KEY` in the environment):
   ```
   supabase.projectId=<ref>
   supabase.anonKey=<publishable key>
   ```
   Release builds read the prod project from the committed
   `supabase.properties` (`supabase.prod.projectId` / `supabase.prod.anonKey`)
   and nothing else. `SupabaseInfo` picks between them by `BuildInfo.isDebug`,
   and `verifyReleaseEnvironment` fails a release build whose prod values are
   missing, malformed or the dev project.
3. Enable providers in the Supabase dashboard (Authentication → Providers).
   An account is required, so **Apple** (iOS) and **Google** (both
   platforms; Android has nothing else) must both work. **Email**
   (confirm-email on) only matters once `SignInMethods.EMAIL` is on. Turn on
   **Allow manual linking** (Authentication → Sign In / Providers): the
   sign-in step links Apple or Google onto guests from builds where accounts
   were optional. Leave **Anonymous sign-ins** on while any of those older
   builds is still installed; this build never creates a guest.

   **Google goes through the browser**, as in Cards, so the app holds no
   Google client ID. In Google Cloud, set up the OAuth consent screen (and
   publish it, or only listed test users can sign in), then create an OAuth
   client of type *Web application* with the authorized redirect URI
   `https://<ref>.supabase.co/auth/v1/callback`, one per Supabase project.
   Paste its client ID and secret into Supabase's Google provider.

   **Apple is set-and-forget here, and it is worth knowing why.** This app
   signs in with Apple *natively* on iOS: the system sheet returns an identity
   token, which goes to Supabase as `signInWith(IDToken)`. Supabase validates
   that against the **Authorized Client IDs** list, so the only thing to fill in
   is your iOS bundle ID. Leave the Services ID and Secret Key fields empty.
   Nothing expires and there is nothing to rotate.

   Those two fields belong to Apple's *browser* OAuth flow, which this template
   does not use. That flow needs a Services ID plus a client-secret JWT capped
   at six months, so turning it on signs you up for a recurring chore. You would
   only need it to offer Apple sign-in on Android. Apple's button is iOS-only
   here (`AndroidAppleSignInCoordinator` is a deliberate no-op).
4. Redirect URLs (Authentication → URL Configuration): add your custom
   scheme callbacks so browser OAuth and the verify-email link return to
   the app:
   ```
   <yourscheme>://login-callback
   <yourscheme>://auth/confirmed
   ```
   The scheme is your project's lowercase name (see the intent filter in
   `AndroidManifest.xml` / `CFBundleURLTypes` in `Info.plist`; both are already
   enabled).
5. Server env (see `apps/server/.env.example`): `SUPABASE_URL` for JWT
   verification, and `SUPABASE_SERVICE_ROLE_KEY` if you want in-app account
   deletion (`DELETE /v1/me`) and display-name mirroring. Treat the service
   role key as a root password, server secrets only, never the client.
6. Verify: launch the app → Get started → sign in with Apple or Google → a
   user appears in Supabase → Authentication → Users, and `GET /v1/me`
   (through the app) creates the profile row.

## Deep links

Compose NavHost handles the routing once URLs reach it. Per-route deep
links go on `screen<Route>(deepLinks = ...)`, use `routeDeepLink<T>()`,
never bare `navDeepLink` (iOS crashes on the missing base-route NavTypes).

The custom-scheme wiring is already enabled on both platforms (the auth
flows depend on it): the intent filter in
`apps/compose/src/androidMain/AndroidManifest.xml` and `CFBundleURLTypes`
in `apps/ios/iosApp/Info.plist`. For https App/Universal Links, add the
`assetlinks.json` / `Associated Domains` + AASA setup alongside.
`iOSApp.swift` forwards every `.onOpenURL` event to the Kotlin
`DeepLinkBridge`; `App.kt` routes OAuth callbacks to the auth layer and
everything else into the nav graph.

## In-app review

Inject `ReviewPrompter` and call `requestReview()` from a delighted-user
moment (e.g. after the user completes a meaningful task, or after N
sessions). The OS owns the throttling decision: both stores rate-limit how
often the dialog actually shows. Don't show your own UI before/after.

---

See `docs/release-automation.md` for the full pipeline runbook.

---

## Day-1 verification

Prove the observability + deploy story end to end while everything is fresh.
Each check has a definitive pass signal; if one fails, fix it now. These are
the tools you'll be debugging with later.

1. **Server is live.**
   ```sh
   curl https://<your-app>-server-dev.fly.dev/_health
   ```
   Expected: `{"ok":true}`.
2. **Client → server round trip.** Launch the app (device or simulator) and
   sign in with Apple or Google. Expected: a new user in Supabase →
   Authentication → Users with `is_anonymous = false`, and a row in the
   `profiles` table.
3. **Find your session in Loki** (if Grafana is wired). In Grafana → Explore →
   Loki, query your client logs by the app's service name and filter
   `session_id="<id>"`, grab the id from logcat or the Xcode console
   (`Session started`); More › Testing › QA menu has the user id. Expected: the `app.launched` event and your
   request logs, and the SAME `session_id` on the server's request logs.
4. **Trigger a test crash → Sentry.** Requires
   [`setup_sentry.main.kts`](#sentry-one-script) to have run and
   `telemetry.properties` to be committed, with a blank DSN the SDK never
   initialises and this check has nothing to find. Add a temporary `error()`
   behind a button in a debug build and tap it. Expected: the event in Sentry within a minute under environment
   `dev-<platform>-debug`, tagged with `session_id` and `commit_sha`. The setup
   script already proved ingest works, so if the script passed and this does
   not, the problem is in the app build, not in Sentry.
5. **Config round trip.** Open the admin console (`/admin` on the dev
   server, paste your `ADMIN_API_TOKEN`), flip `upgrade.maintenanceMessage`
   to a test string, foreground the app twice (refresh is throttled).
   Expected: the value changes in the QA config dashboard; the audit tab
   records your change.
