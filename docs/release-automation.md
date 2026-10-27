# Release automation

Opson ships to the App Store and Play Store with no human clicks after one-time setup. This doc has two short sections for the two things most people ask, then reference material for when something breaks.

---

## Cutting a release (for humans)

> TL;DR: merge the "release" PR. That's it.

1. There is always (well: whenever there are new `fix:`/`feat:`/`perf:` commits on main) an open PR titled **`chore(main): release vX.Y.Z`**, opened automatically by the release-please bot. It contains the version bump + changelog.
2. **Merge that PR.** release-please creates the `vX.Y.Z` tag + GitHub Release.
3. release-please.yml then dispatches [release.yml](../.github/workflows/release.yml) for that tag (it can't rely on the tag-push trigger, GitHub's default `GITHUB_TOKEN` deliberately doesn't cascade workflow triggers). release.yml:
   - Android → Play Console production track, full rollout (internal for a first release)
   - iOS → the TestFlight external group named by `TESTFLIGHT_EXTERNAL_GROUP` in [release.yml](../.github/workflows/release.yml) → submitted to App Store review with Apple's built-in phased release
4. Apple review (1–3 days) and Play review (few hours) approve. Builds roll out automatically.

### What if the release PR doesn't exist?

No conventional-commit changes since the last release. Merge a `fix:` or `feat:` PR and the bot will open one within a minute.

### What if I want a specific version number?

Edit the release PR body to add `Release-As: 2.0.0` on its own line. The bot rewrites the PR with that version.

### What if I want to skip one store for this release?

Actions → **Release** → Run workflow → pick the tag, tick **skip_play_store** or **skip_app_store**.

### What if a release run failed halfway?

Actions → **Release** → Run workflow → pick the same tag. It re-runs idempotently (each store tolerates duplicate uploads of the same build).

### What if main is broken and I need a hotfix without shipping the broken code?

This is the one case the pipeline is not optimized for. Branch from the previous tag, cherry-pick the fix, tag manually (`git tag v1.2.4 && git push origin v1.2.4`), and `release.yml` will pick it up.

---

## How automated fixes land (what the bots do)

Sentry triage runs as a **Claude Code routine on the maintainer's machine**, not as a CI job. So it uses your normal Claude subscription and the Sentry MCP instead of a paid API key + curl. Schedule it weekly (or on demand) with the prompt at [scripts/prompts/sentry-triage.md](../scripts/prompts/sentry-triage.md). The prompt is the only thing you edit to change behavior.

Each run:

1. Pulls top 5 unresolved **production** Sentry issues from the last 7 days via the Sentry MCP. Debug/preview builds are filtered out.
2. For each, creates a `ai/sentry-<id>` branch, forms a hypothesis, writes the smallest plausible fix, adds a regression test if possible.
3. Opens a PR titled `fix: …` with labels `ai-autofix` and `sentry`.
4. Issues that can't be fixed from source (third-party SDK frames, user-environment noise) become tracking GitHub issues instead of PRs.

`auto-merge.yml` watches for the `ai-autofix` label and enables GitHub's native auto-merge on the PR. When CI is green, GitHub squash-merges it. The fix is now on main and the next release PR from release-please includes it.

**To block a specific PR from auto-merging**: remove the `ai-autofix` label or close the PR, GitHub's auto-merge cancels.

**To pause triage entirely**: disable the routine in Claude Code. No CI to touch.

### Post-release monitoring

There's no automated rollout guard. Apple's built-in phased release (7-day gradual rollout from 1% → 100%) gives you a safety window on iOS; Play ships to everyone at once, so halt there by hand. For detection, configure Sentry's native alerting (Sentry → Alerts → create a rule on *crash-free sessions* below 99% for the relevant project) and halt manually in App Store Connect / Play Console when needed.

---

## System map

```
PR   ──►  commitlint + CI                 Claude routine (local, weekly)
  │          │                                       │ reads Sentry MCP
  │          ▼ (green)                                ▼
  │      merge to main ◄──────── opens fix: PRs (label ai-autofix)
  │                                                  │
  │                                                  ▼
  │                                         auto-merge on green CI
  ▼
main ─── push ──► release-please (bot) maintains open "release vX.Y.Z" PR
                        │
                        ▼ merge
                 tag v*, GitHub Release
                        │
                        │ release-please.yml dispatches release.yml
                        │ (GITHUB_TOKEN doesn't cascade tag triggers)
                        ▼
                 release.yml ── Android AAB ──► Play production (full rollout)
                             ── iOS IPA ─────► TestFlight, the TESTFLIGHT_EXTERNAL_GROUP group
                             ── App Store ──► submit + phased rollout
                             ── Sentry ─────► create release + upload mappings

Post-release: Sentry's own alerting + App Store / Play's native phased
rollouts. Halt manually if crash-free rate tanks.
```

## Workflows at a glance

| Workflow | Fires on | What it does |
|---|---|---|
| [ci.yml](../.github/workflows/ci.yml) | PRs, push to main | Compile + tests. No uploads. Shipping happens via release.yml. Skipped on release-please's PR and its merge commit (bumps-only changes can't break the build). |
| [commitlint.yml](../.github/workflows/commitlint.yml) | PRs | Rejects non-conventional PR titles. |
| [release-please.yml](../.github/workflows/release-please.yml) | push to main | Maintains the release PR, creates tag + GH Release on merge. |
| [release.yml](../.github/workflows/release.yml) | dispatched by release-please.yml after tag creation; also `workflow_dispatch` with an explicit tag for re-runs; also fires on tag pushes made by a human | Full production release to both stores. |
| [auto-merge.yml](../.github/workflows/auto-merge.yml) | PR labeled `ai-autofix` | Enables GitHub auto-merge. |

Sentry triage is not a workflow. It runs as a Claude Code routine on the maintainer's machine. See [scripts/prompts/sentry-triage.md](../scripts/prompts/sentry-triage.md).

## Versioning

- **`versionName` / `MARKETING_VERSION`**: owned by release-please. Do not edit in feature PRs. `x-release-please-start-version` / `x-release-please-end` markers in [versions.properties](../versions.properties) and [Config.xcconfig](../apps/ios/Configuration/Config.xcconfig) tell the bot where to write.

  **Don't delete those markers, and don't add a file to `extra-files` without them.** release-please skips an unmarked `extra-files` entry *silently*. No warning, no error, exit 0. So the symptom is not a failed release. It is a green release that tags and changelogs a new version while the version files stay behind, and ships the new code labelled with the old version to both stores. Use the block form: in a properties file `#` only starts a comment at the start of a line, so a trailing `# x-release-please-version` ends up inside the value. An xcconfig comments with `//`, not `#`. Release-please matches the marker text wherever it appears on a line, but the file still has to parse.

  After changing anything about those files, verify by running a release-please dry run, or at minimum confirm the marker lines still bracket the value line and that `grep '^MARKETING_VERSION'` still returns it. `release.yml`, `beta.yml` and the Fastfile all read it that way.
- **`versionCode` / iOS build number**: auto-overridden in CI with `GITHUB_RUN_NUMBER` via env vars `VERSION_CODE_OVERRIDE` and `BUILD_NUMBER_OVERRIDE`. They bump monotonically without commits. (See [Versioning.kt](../build-logic/src/main/java/com/opson/util/Versioning.kt).)
- **Sentry release ID**: `opson@{version}+{build}` (e.g. `opson@0.2.0+42`). Created on both platforms in release.yml.

## Secrets and variables

Set under **Settings → Secrets and variables → Actions**. Secrets are encrypted, variables (`vars.*`) are plaintext.

### Only one of these is per-app

Worth knowing before you do this the second time: every secret below is a property of your Apple team, your Play developer account, or your Sentry org. They are identical for every app you will ever generate from this template. The only per-app Sentry value, the DSN, is not a secret at all. It lives in the committed `telemetry.properties`, written by `scripts/setup_sentry.main.kts`.

| Scope | Secrets |
|---|---|
| Account-wide, write once, reuse forever | `APPLE_TEAM_ID`, `APPLE_DIST_CERT_P12_BASE64`, `APPLE_DIST_CERT_PASSWORD`, `ASC_ISSUER_ID`, `ASC_KEY_ID`, `ASC_KEY_P8_BASE64`, `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, `PLAY_SERVICE_ACCOUNT_JSON`, `SENTRY_AUTH_TOKEN` |
| Per app, but not secrets | `SENTRY_ORG` / `SENTRY_PROJECT` repo variables, plus `sentry.dsn` in the committed `telemetry.properties`. All three set by `scripts/setup_sentry.main.kts` |

`SENTRY_AUTH_TOKEN` is the one on that list worth validating rather than pasting. It is set by `scripts/setup_sentry.main.kts`, and `scripts/setup_sentry_ci.sh` rotates it on its own; both check the token against `GET /organizations/<org>/chunk-upload/`, the endpoint sentry-cli uploads through, before setting the secret. Without that check a rejected token is invisible: on iOS fastlane logs sentry-cli's error and carries on, so the run stays green, and on Android the upload is skipped entirely unless the variable is set. Validate against the upload endpoint and nothing else. An `org:ci` token answers 403 to `/organizations/<org>/` and `/projects/<org>/<project>/` while being perfectly good for uploads, so a friendlier-looking endpoint rejects working tokens.

One Apple distribution certificate signs every app on the team. One App Store Connect API key covers the team. One Play service account covers the developer account, once you grant it access to each app. One upload keystore can sign all of them: with Play App Signing the upload key only proves *you* sent the build, and Google holds the key users actually verify.

So do not treat a new app as thirteen console visits. Keep the shared material in one private folder outside any repo, alongside a script that runs `gh secret set` for a named repo, and a new app costs one four-line file. GitHub does not offer account-level secrets for personal repos; a free organization does, with per-repository visibility, if you would rather have the platform hold them. Moving repos into an org rewrites their GitHub Pages URL. That no longer touches the privacy policy URL, which is on the studio domain, but it is still worth doing before you file anything derived from a repo URL.

The one that is genuinely dangerous to lose is the Android upload keystore. Everything else on the list can be reissued from a console in five minutes, including the Apple `.p8` that only downloads once. The keystore cannot: once an app has shipped a build signed with it, losing it means asking Google to reset the upload key before you can publish an update.

### Already set

- `APPLE_TEAM_ID`, `ASC_KEY_ID`, `ASC_ISSUER_ID`, `ASC_KEY_P8_BASE64`

### Needed for iOS releases (cert reuse)

| Name | Kind | Value |
|---|---|---|
| `APPLE_DIST_CERT_P12_BASE64` | secret | Base64 of your exported Apple Distribution .p12 (cert + private key). |
| `APPLE_DIST_CERT_PASSWORD` | secret | The password set when exporting the .p12. |

Without these, Xcode's automatic signing creates a new Apple Distribution cert on every CI run and eventually hits the team cap (~2–3 per type). With them, every run imports the same cert into a temp keychain and reuses it forever.

**One-time setup:**

1. On your Mac, open **Xcode → Settings → Accounts → [your team] → Manage Certificates** (or **Keychain Access**).
2. Find or create an "Apple Distribution: *your name* (TEAMID)" cert.
3. Right-click → **Export → Personal Information Exchange (.p12)** → set a password → save it (e.g. `~/apple-dist.p12`).
4. Base64 and stash:
   ```sh
   base64 -i ~/apple-dist.p12 | gh secret set APPLE_DIST_CERT_P12_BASE64
   gh secret set APPLE_DIST_CERT_PASSWORD    # paste the password you chose
   rm ~/apple-dist.p12                        # delete the local .p12
   ```

The cert is valid for ~1 year, when it expires, re-export and update the secret.

### Needed for Android production releases

| Name | Kind | Value |
|---|---|---|
| `ANDROID_KEYSTORE_BASE64` | secret | `base64 -i opson-upload.p12 \| pbcopy` |
| `ANDROID_KEYSTORE_PASSWORD` | secret | Set when keystore was created. |
| `ANDROID_KEY_ALIAS` | secret | e.g. `opson-release`. |
| `ANDROID_KEY_PASSWORD` | secret | For PKCS12: same as keystore password. |
| `PLAY_SERVICE_ACCOUNT_JSON` | secret | Full JSON body of the Play Console service account key (not base64). |

Generate the keystore once:

```sh
keytool -genkey -v -keystore opson-upload.p12 -storetype PKCS12 \
  -keyalg RSA -keysize 4096 -validity 10000 -alias opson-release
```

Keep the `.p12` in 1Password **and** on disk. Losing both = you can never update the Play Store listing. Ever.

### Needed for Sentry release tracking

| Name | Kind | Value |
|---|---|---|
| `SENTRY_AUTH_TOKEN` | secret | Personal auth token with `org:read project:read project:write project:releases`. |
| `SENTRY_ORG` | variable | Sentry org slug (from https://sentry.io/settings/). |
| `SENTRY_PROJECT` | variable | Sentry project slug. |

All three empty = Sentry release creation is skipped; the pipeline otherwise runs fine.

### Play service account: one-time setup

1. Google Cloud Console → new project "Opson CI".
2. APIs & Services → Library → **Google Play Android Developer API** → Enable.
3. IAM & Admin → Service Accounts → Create → name `opson-ci`, role none.
4. Click the service account → Keys → Add Key → JSON → save the downloaded file.
5. Play Console → Users and permissions → Invite new user → paste the service-account email → grant **Release manager** on the Opson app.
6. GitHub secrets: paste the **full JSON** (not base64) into `PLAY_SERVICE_ACCOUNT_JSON`.

### One-time App Store Connect setup

The pipeline ships only the binary + release notes (`skip_metadata: true`, `skip_screenshots: true`). The listing must be complete in App Store Connect before submission, or Apple rejects:

- [ ] App info: name, subtitle, category, privacy policy URL
- [ ] Pricing + availability
- [ ] Screenshots (≥ 6.5" iPhone set)
- [ ] Description, keywords, support URL
- [ ] Age rating + App Privacy declaration
- [ ] Review info (contact, demo account if relevant)
- [ ] External TestFlight group with invited testers, named to match `TESTFLIGHT_EXTERNAL_GROUP` in [release.yml](../.github/workflows/release.yml) (**External Testers** unless you changed it). The pipeline uploads to this group by name, and a mismatch is close to silent: the binary reaches App Store Connect, `pilot` cannot find the group, and the Fastfile logs a non-fatal warning and carries on to App Store submission with nobody on external TestFlight. Read the value from the workflow rather than from here.

### One-time Play Console setup

- [ ] Create the app entry for `com.opson`
- [ ] Complete Store listing (description, screenshots, feature graphic, icon)
- [ ] Content rating, target audience, data safety, category, contact
- [ ] **Ship the first production release manually from Play Console.** `r0adkll/upload-google-play` can't push to production until there's an approved prod release to update. Use `track: internal` in [release.yml](../.github/workflows/release.yml) for the first few releases if you prefer automation all the way down.

### Beta builds belong on your machine

`beta.yml` exists for the times CI itself is what you are testing: the runner image, the
secrets, the workflow file. For everything else run the same lane locally:

```bash
./scripts/beta_ios.sh
```

A hosted macOS runner starts from nothing every time. A beta run on 2026-09-28 spent over ten
minutes on the Kotlin/Native release link before it reached the archive, and forty-three
minutes in total; your machine has those caches already.

The script does not reimplement the lane. It finds the values the workflow passes as secrets
and variables, from the credential store the setup scripts write, encodes the App Store
Connect `.p8` the way a CI secret carries it, reads the Sentry org and project from the repo's
own variables, and hands off to fastlane. Anything already exported wins.

Two things it sets that are easy to get wrong by hand. `RELEASE_CHANNEL_OVERRIDE` is `beta`,
as the workflow sets it, so the build asks for test ad units rather than live ones. `CI` is
deliberately left unset, because that sends the Fastfile down the runner path and fails on a
`.p12` your machine does not have; with it unset the build signs from your own keychain, which
is what the Fastfile intends for a local run.

### One-time legal-sync setup

The privacy policy and terms of service live in [legal/](../legal/) as Markdown and are published to
`https://nightjarlabs.llc/<app-slug>/privacy` and `/terms` by
[legal-sync.yml](../.github/workflows/legal-sync.yml), which opens a pull request against the studio
site's repository whenever either file changes on `main`.

Run [`scripts/setup_legal_sync.sh`](../scripts/setup_legal_sync.sh) once to create the two secrets it
needs. Until then the workflow fails on every push that touches `legal/`.

**Why the text lives next to the code.** A privacy policy is a claim about what a specific binary
does. Keeping it in a marketing folder guarantees the day someone adds an SDK, ships it, and nobody
remembers that a sentence elsewhere just became false. Drop2048 hit that twice in one month, which
is what a process problem looks like rather than two mistakes. Next to the code, the diff that
changes what the app collects and the diff that corrects the claim land in the same review.

A pull request rather than a push, but nobody merges it: the site repo's `auto-merge-legal.yml`
merges any `legal-sync/*` branch once its Preview build passes, then dispatches the deploy. Editing
the Markdown here is the whole job.

That drops a review step on purpose. A PR nobody remembers to merge means the published policy
drifts behind the app, and that policy is the URL filed with Apple and Google, so stale is the worse
of the two failures. The build gate stays, which is what stops a malformed file from replacing a
good page.

## Runbook: something broke

| Symptom | First thing to check |
|---|---|
| No release PR appearing | No conventional-commit changes since last release, expected. Or check the latest run of `release-please.yml` in Actions. |
| commitlint blocking a PR | PR title isn't `type: lowercase subject`. Edit the title. |
| `release.yml` Android job: keystore missing | One of the `ANDROID_*` secrets is empty. |
| `release.yml` Android job: Play upload skipped with warning | `PLAY_SERVICE_ACCOUNT_JSON` is empty or malformed. |
| Apple rejects submission | Usually metadata or privacy-related. Fix in App Store Connect (the binary is already uploaded), resubmit from ASC, no rebuild. |
| Crash-free rate spikes after release | Halt phased rollout manually: App Store Connect → your app → Phased Release → **Pause Rollout**. Play Console → Production → **Halt rollout**. Ship a fix via normal flow; next release supersedes. |
| Tag created but release.yml didn't fire | release-please.yml failed at the dispatch step (check its run). Manual remediation: Actions → Release → Run workflow → enter the tag. If a human pushed the tag (not release-please), the push trigger would have fired it. If that's not the case, the `v*` prefix is probably wrong. |
| AI triage PR failed CI | Check the PR, if the fix is wrong, close it. `ai-autofix` PRs don't auto-merge without green CI. |
| `release.yml` attach-artifacts: a binary is missing from the Release | Cosmetic only. The store uploads happen in the platform jobs, which run first. A missing file means that platform's job failed and never published its artifact; fix that job. The step warns and passes rather than failing twice for one cause. |
| `release.yml` attach-artifacts: "Resource not accessible by integration" | Should not recur. This was `softprops/action-gh-release` PATCHing the release with the SHA `target_commitish` release-please sets, which GITHUB_TOKEN may not write; the step uses `gh release upload` now and touches no metadata. Don't reintroduce an action that updates the release, and read the comment on the step first. |

## Extending the pipeline

Small additions that keep the existing shape:

- **Weekly unattended release**: cron that merges the open release PR every Sunday unless labeled `hold-release`. ~10 lines.
- **`fastlane snapshot` screenshots**: once UI is stable, auto-generate screenshots from XCUITests instead of committing PNGs. Drops `skip_screenshots: true`.
- **Slack/Discord notifications**: webhook step at the end of `release.yml`.
- **Android dSYM/mapping to Sentry**: already wired in `release.yml` (the "Create Sentry release" step). Verify after the first Android release.
