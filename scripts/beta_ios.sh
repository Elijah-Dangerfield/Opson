#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Builds the iOS app and uploads it to TestFlight internal, from this machine.
#
#   ./scripts/beta_ios.sh
#
# The same lane `beta.yml` runs, without the hosted runner. That runner starts
# cold every time and spends ten minutes or more on the Kotlin/Native release
# link alone before it reaches the archive; your machine has those caches
# already. Use the workflow when the thing being tested *is* CI — the runner
# image, the secrets, the workflow file — and this the rest of the time.
#
# All this script does is find the values CI passes as secrets and variables,
# and then get out of the way. Fastlane does the work, and the Fastfile is
# already written for a local run: the keychain import is skipped when CI is
# unset, so the build signs with your own keychain rather than the stored .p12.
#
# Needs: Xcode, `bundle install` once in apps/ios, and the credential store
# filled in (`./scripts/setup_credentials.main.kts`).
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"

bold() { printf "\033[1m%s\033[0m\n" "$1"; }
ok()   { printf "  \033[32m✓\033[0m %s\n" "$1"; }
warn() { printf "  \033[33m!\033[0m %s\n" "$1"; }
die()  { printf "  \033[31m✗\033[0m %s\n" "$1" >&2; exit 1; }

# ── The shared credential store ──────────────────────────────────────────────
# Same file `scripts/lib/setup_store.main.kts` reads and writes, found the same
# way: an explicit APPSETUP_DIR wins outright, otherwise the first of the two
# conventional directories that already holds one. Kept in step with that file
# by hand, because a bash script cannot import a Kotlin one.
store_path() {
  if [ -n "${APPSETUP_DIR:-}" ]; then
    printf '%s\n' "$APPSETUP_DIR/credentials.properties"; return
  fi
  local default="${XDG_CONFIG_HOME:-$HOME/.config}/appsetup"
  local dir
  for dir in "$default" "$HOME/Documents/appsetup"; do
    if [ -f "$dir/credentials.properties" ]; then
      printf '%s\n' "$dir/credentials.properties"; return
    fi
  done
  printf '%s\n' "$default/credentials.properties"
}

STORE="$(store_path)"

# A java.util.Properties file, so `key=value` with no spaces around the `=`.
store_read() {
  [ -f "$STORE" ] || return 0
  awk -F= -v key="$1" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$STORE"
}

# Environment first, store second. Env-first is what the Kotlin setup scripts
# do, and it is what makes a one-off override possible without editing a file
# that every project reads.
from_store() {
  local var="$1" key="$2"
  if [ -n "${!var:-}" ]; then
    ok "$var (from the environment)"
    return
  fi
  local value
  value="$(store_read "$key")"
  [ -n "$value" ] || die "$var is not set and $key is not in $STORE. Run ./scripts/setup_credentials.main.kts"
  export "$var=$value"
  ok "$var (from the store)"
}

# Fastlane runs through bundler so the plugin versions match the ones CI
# resolves. Checked here rather than left to fail inside the lane, because the
# bundler error arrives after the credential work and reads like a fastlane
# problem.
if ! (cd apps/ios && bundle check >/dev/null 2>&1); then
  die "Gems are missing. Run once: (cd apps/ios && bundle install)"
fi

bold "Credentials"
from_store APPLE_TEAM_ID   apple.teamId
from_store ASC_KEY_ID      apple.ascKeyId
from_store ASC_ISSUER_ID   apple.ascIssuerId

# The Fastfile wants the .p8 base64-encoded in an env var, because that is the
# shape a CI secret has. The store holds a path to the file instead, so the
# encoding happens here. `base64 -i` is the BSD spelling; this script is macOS
# only by virtue of needing Xcode at all.
if [ -z "${ASC_KEY_P8_BASE64:-}" ]; then
  P8_PATH="$(store_read apple.ascKeyPath)"
  [ -n "$P8_PATH" ] || die "apple.ascKeyPath is not in $STORE. Run ./scripts/setup_credentials.main.kts"
  [ -f "$P8_PATH" ] || die "No .p8 at $P8_PATH (the store points at a file that is not there)"
  ASC_KEY_P8_BASE64="$(base64 -i "$P8_PATH" | tr -d '\n')"
  export ASC_KEY_P8_BASE64
  ok "ASC_KEY_P8_BASE64 (encoded from $P8_PATH)"
else
  ok "ASC_KEY_P8_BASE64 (from the environment)"
fi

# ── Sentry ───────────────────────────────────────────────────────────────────
# Optional, and the lane says so: `upload_sentry_release` returns early with no
# token. Skipping it means the build has no symbols on Sentry, so a crash from
# this build arrives unreadable. Fine for a build you are about to install and
# poke at by hand; not fine if you are chasing a crash.
#
# The org and project are repo *variables* rather than secrets, so `gh` can read
# them and there is nothing to type. The store holds the org as well; the
# project is per-app and lives only here.
printf "\n"; bold "Sentry"
if [ -z "${SENTRY_AUTH_TOKEN:-}" ]; then
  SENTRY_AUTH_TOKEN="$(store_read sentry.ciToken || true)"
  export SENTRY_AUTH_TOKEN
fi

if [ -n "${SENTRY_AUTH_TOKEN:-}" ]; then
  if [ -z "${SENTRY_ORG:-}" ]; then
    SENTRY_ORG="$(gh variable get SENTRY_ORG 2>/dev/null || store_read sentry.org || true)"
    export SENTRY_ORG
  fi
  if [ -z "${SENTRY_PROJECT:-}" ]; then
    SENTRY_PROJECT="$(gh variable get SENTRY_PROJECT 2>/dev/null || true)"
    export SENTRY_PROJECT
  fi
  if [ -n "${SENTRY_ORG:-}" ] && [ -n "${SENTRY_PROJECT:-}" ]; then
    ok "uploading dSYMs to $SENTRY_ORG/$SENTRY_PROJECT"
  else
    warn "no org or project, so the dSYM upload will be skipped"
    unset SENTRY_AUTH_TOKEN
  fi
else
  warn "no Sentry token, so this build's crashes will arrive unsymbolicated"
fi

# ── The build ────────────────────────────────────────────────────────────────
# `beta`, the same channel the workflow sets. It decides the environment tag on
# every event and, through `AdUnits.useTestUnits`, whether the build asks for
# live ad units or Google's test ones. A local build left on the `dev` default
# would report under a channel no store build ever uses.
export RELEASE_CHANNEL_OVERRIDE="${RELEASE_CHANNEL_OVERRIDE:-beta}"

# Deliberately not exported: CI. Setting it would send the Fastfile down the
# runner path — create a throwaway keychain, import a .p12 this machine does not
# have — and fail.

printf "\n"; bold "Building"
echo "  channel  $RELEASE_CHANNEL_OVERRIDE"
echo "  version  $(sed -n 's/^MARKETING_VERSION=//p' apps/ios/Configuration/Config.xcconfig)"
echo "  build    $(date +%Y%m%d%H%M) (timestamp, assigned by the lane)"
echo

cd apps/ios
exec bundle exec fastlane beta
