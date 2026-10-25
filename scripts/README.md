# Scripts

Utility scripts for this project.

> Template maintainers: init/verification tooling (`init_project.main.kts`,
> `verify_template.sh`) is documented in `docs/template-maintenance.md`.
> Those scripts are removed from generated projects.

## install_hooks.sh

Installs the repo's git hooks (`.githooks/`) into your local clone. Run once
after cloning, before your first commit:

```bash
./scripts/install_hooks.sh
```

## enable_ci.sh

Present only if CI was declined at project init. Installs the staged CI /
release automation (`.github/workflows/`, fastlane files, `legal/`,
release-please config) and then removes itself:

```bash
./scripts/enable_ci.sh
```

See SETUP.md for the GitHub secrets the pipeline needs.

## create_module.main.kts

Creates new KMP modules with proper structure and configuration.

- KMP source sets (`commonMain`, `androidMain`, `iosMain`) and the right
  convention plugin per module type
- Feature modules get a Screen + ViewModel starter; libraries get a basic
  class; the public/impl split is supported for libraries
- Updates `settings.gradle.kts` and `apps/compose/build.gradle.kts`

```bash
./scripts/create_module.main.kts                      # interactive
./scripts/create_module.main.kts feature messaging    # feature module
./scripts/create_module.main.kts library analytics    # library module
./scripts/create_module.main.kts library user:preferences  # sub-module
```

## dev/drive.py

Drives the app on a connected Android device or emulator, so UI work can be
verified without a human at the screen. Python 3 standard library plus `adb`:

```bash
./scripts/dev/drive.py launch [--fresh]   # start the app, wait for its first frame
./scripts/dev/drive.py text               # every label on screen
./scripts/dev/drive.py tap "Continue"     # tap the node whose label matches
./scripts/dev/drive.py shot /tmp/home.png # save a screenshot
./scripts/dev/drive.py --dry-run launch   # print the adb commands, run nothing
```

Nothing takes coordinates. It dumps the accessibility tree and taps by `text`
or `content-desc`, which survives a layout shift and a slow boot; a pixel
guess does not. The package comes from `applicationId` in
`versions.properties` (plus the debug suffix), and the launcher activity is
resolved on the device, so neither is written down anywhere to go stale.

`launch` uses `am start`, **not** `monkey -p <pkg> -c LAUNCHER 1`. Monkey's
trailing count is the number of random input events it fires after the app
starts, so the obvious incantation taps the first frame on every launch. The
script's docstring has the measured cost and is the reason not to "simplify"
it back.

## setup.main.kts

The one entry point. Walks every setup step in the order its dependencies
allow, offering each and skipping anything already done:

```bash
./scripts/setup.main.kts            # run the steps
./scripts/setup.main.kts --status   # where am I, changes nothing
```

Order is the reason this exists. Supabase has to exist before its values can be
pushed to Fly, and both GitHub-facing steps need a repo that does not exist at
the moment a project is generated. Running the pieces out of order does not
fail loudly. It half-configures things and leaves you to work out which half.

Every step below is also standalone and re-runnable, so "skip for now" is
always a safe answer.

## setup_credentials.main.kts

Fills in the machine-local credential store the other setup scripts read.

```bash
./scripts/setup_credentials.main.kts                   # walk through every value
./scripts/setup_credentials.main.kts --list            # show what is set, masked
./scripts/setup_credentials.main.kts --import <file>   # read an env-format file
./scripts/setup_credentials.main.kts --move-to <dir>   # relocate it
./scripts/setup_credentials.main.kts --clear           # forget everything
```

**Already keep a shared secrets folder?** If it has an env file naming values
after the CI secrets (`APPLE_TEAM_ID`, `ASC_KEY_ID`, `ANDROID_KEY_ALIAS`, …),
`--import` reads it straight in: the store keys use those same names on
purpose. Sourcing the file into your shell instead would look like it worked
and persist nothing: env wins at read time, so the editor skips anything
already set there.

`FILE_*` entries are understood too. Paths resolve relative to the env file, a
`*` in the filename is expanded (Apple bakes an unpredictable key id into the
`.p8`), and the folder they share becomes the signing folder. Nothing in those
files is read, only where they are.

The values are per person and per machine, not per project: your Sentry org,
your Fly deploy tokens, your Apple team. Fill them in once and every project
you generate afterwards stops asking. Nothing here is required. Every script
that cannot find a value still prompts for it, and every script reports which
values it found where, and what is still unset.

Precedence is environment variable → store → prompt, so CI (which already sets
these as environment variables) keeps working untouched and a one-off override
stays possible.

The path deliberately says nothing about this template: a path carrying the
project name would be rewritten per project by init, which is the opposite of
the point.

### Where it lives is a trade worth making on purpose

The default, `~/.config/appsetup/credentials.properties`, syncs nowhere. Live
deploy tokens stay on one machine. `--move-to` puts the store somewhere that
does sync, which on a Mac with Desktop & Documents sync means
`~/Documents/appsetup`. That backs up the credentials you cannot regenerate and
widens the blast radius for the ones you can. Both halves are real; the script
says both and does what it is told. Either way the file is `0600` and its
directory `0700`, so nothing else on the machine can read it.

`$APPSETUP_DIR` wins outright when it is set: naming a directory means that
directory, and falling through to another one because the named one is empty is
how a test run ends up pointed at a real store full of live credentials. With
it unset, the scripts search the default and then `~/Documents/appsetup`, and
use the first that actually holds a store. That
search is the whole reason the opt-in survives a machine change: the sync
brings the file down on the new laptop and the next run finds it with no setup.
Move it anywhere outside those three and you need `APPSETUP_DIR` in your shell
profile, which a new machine will not have, so the script warns when you pick
such a path.

## setup_sentry.main.kts

Turns crash reporting on. Run once, right after init:

```bash
./scripts/setup_sentry.main.kts
./scripts/setup_sentry.main.kts --non-interactive   # every value from env/store
```

Asks for a Sentry **user** auth token (`project:read`, `project:write`,
`org:read`; an organization `sntrys_…` token has only `org:ci` and 403s every
read endpoint), creates or adopts the project, writes the DSN into the
committed `telemetry.properties`, sets the CI variables and secret, then sends
a test event and waits for it to arrive before declaring success. Idempotent.

Commit `telemetry.properties` afterwards. A DSN is a write-only ingest
endpoint shipped inside every store binary, not a secret. Keeping it per
developer is what leaves fresh clones silently reporting nothing.

## setup_supabase.main.kts

Creates or adopts this project's Supabase project and configures the auth the
app actually ships:

```bash
./scripts/setup_supabase.main.kts
```

Turns on anonymous sign-ins (the guest flow does not work without it), sets the
site URL and redirect allow-list to this project's custom scheme, authorizes
your iOS bundle ID for native Sign in with Apple, writes
`supabase.projectId` / `url` / `anonKey` into `local.properties`, and fills the
Supabase half of `apps/server/.env`. Needs a Supabase personal access token
(`sbp_…`). The credential store holds it after the first time.

It prints the generated database password exactly once. That value is per
project, so it is deliberately not stored, and the Management API will not hand
it back.

Not covered: Google sign-in, which needs an OAuth client from the Google Cloud
console. The app runs fine without it.

## setup_fly.main.kts

Stands up the server's Fly apps and wires CI to deploy to them:

```bash
./scripts/setup_fly.main.kts
```

Creates the dev and prod apps, points `fly.toml` / `fly.prod.toml` at them,
pushes the Supabase values from `apps/server/.env` as Fly secrets, mints a
deploy token per app into `FLY_API_TOKEN_DEV` / `_PROD`, and offers the first
deploy, then curls `/_health` to prove it. Creating apps is free; the first
deploy starts a machine, so that step asks separately.

Deleted from projects generated with `--backend=no`.

## setup_github_secrets.main.kts

Pushes every release secret from one folder plus the credential store:

```bash
./scripts/setup_github_secrets.main.kts
./scripts/setup_github_secrets.main.kts --dry-run
```

Eleven signing values plus the three `GRAFANA_*` credentials, which are
account-wide because every project shares one Grafana stack (see
`docs/practices/observability.md`).

The binary material (upload keystore, Apple `.p12`, ASC `.p8`, Play
service-account JSON) lives in one folder outside every repo; the store
remembers *where* it is, never what is in it. The passwords beside them are
account-wide, so those the store does hold. Anything missing is reported with
what it costs, and everything else still goes up.

## lib/setup_store.main.kts

Not run directly. The shared half of the setup scripts: prompting, the
credential store, and the two summaries every setup run prints (where each
value came from, and what is still unset and what that costs). Import it with
`@file:Import("lib/setup_store.main.kts")`.

Adding a value the scripts should remember is one entry in `Keys` plus naming
it in the script's `plan(...)` call; `setup_credentials.main.kts` enumerates
`Keys.all`, so it shows up in the editor for free.

**Editing this file and seeing no change?** The `kotlin` CLI caches a compiled
script keyed on the script you handed it, not on what that script imports. So
an edit here does not invalidate the cache of anything that imports it. Clear
it with `rm -rf ~/Library/Caches/main.kts.compiled.cache`.

## cleanup.sh

Cleans build artifacts and caches:

```bash
./scripts/cleanup.sh
```
