#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# Sets SENTRY_AUTH_TOKEN on this repo, and proves the token works first.
#
# `setup_sentry.main.kts` does the one-time project setup: it creates the Sentry
# project, writes the DSN, sets SENTRY_ORG and SENTRY_PROJECT. This script does
# the one thing you come back for, which is fixing or rotating the CI token, and
# it is the half that has actually gone wrong.
#
# **It validates before it sets.** That is the entire point. A token that
# authenticates but cannot upload looks identical to a working one until a
# release build fails, and the failure is easy to miss: on iOS `fastlane` logs
# sentry-cli's error and carries on, so the run stays green. An app built from
# this template carried a rejected token for nine days that way, and it was
# first noticed when an Android release job died on an "Invalid org token (401)".
#
# The check is `GET /organizations/<org>/chunk-upload/`, which is the endpoint
# sentry-cli actually uploads mappings and dSYMs through, so a 200 here means
# the thing CI does will work. Do not be tempted by a friendlier-looking
# endpoint: an `org:ci` token answers 403 to `/organizations/<org>/` and
# `/projects/<org>/<project>/` while being perfectly good for uploads, so those
# would reject tokens that work.
#
# The token is not per app. One organization token covers every project in the
# org, so the first run saves it to the machine-local credential store the
# Kotlin setup scripts share and later runs in other repos find it there.
#
# Safe to re-run. Needs: gh (logged in), curl.
# ─────────────────────────────────────────────────────────────────────────────
set -euo pipefail

STORE_TOKEN_KEY="sentry.ciToken"
STORE_ORG_KEY="sentry.org"

bold() { printf "\033[1m%s\033[0m\n" "$1"; }
step() { printf "\n\033[1;36m==> %s\033[0m\n" "$1"; }
ok()   { printf "  \033[32m✓\033[0m %s\n" "$1"; }
warn() { printf "  \033[33m!\033[0m %s\n" "$1"; }

# ── The shared credential store ──────────────────────────────────────────────
# Same file `scripts/lib/setup_store.main.kts` reads and writes, found the same
# way: an explicit APPSETUP_DIR wins, otherwise the first conventional directory
# that already holds one, otherwise the default. Kept in step with that file by
# hand, because a bash script cannot import a Kotlin one.
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

# Permissions are set before the value is written, never after, so the token is
# not briefly world-readable.
store_write() {
  local key="$1" value="$2" directory temporary
  directory="$(dirname "$STORE")"
  mkdir -p "$directory" && chmod 700 "$directory"
  if [ ! -f "$STORE" ]; then
    : > "$STORE"
    printf '#Machine-local setup credentials, shared by every project you generate.\n' >> "$STORE"
  fi
  chmod 600 "$STORE"
  temporary="$(umask 077 && mktemp -t appsetup-store)"
  grep -v "^${key}=" "$STORE" > "$temporary" || true
  printf '%s=%s\n' "$key" "$value" >> "$temporary"
  cat "$temporary" > "$STORE"
  rm -f "$temporary"
}

# 200 means sentry-cli can upload. Anything else means it cannot, and the
# distinction between 401 and 403 is worth printing because they have different
# fixes: a bad or revoked token versus a token without org:ci.
token_status() {
  curl -s -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer $1" \
    "https://sentry.io/api/0/organizations/$2/chunk-upload/" 2>/dev/null || echo "000"
}

explain_status() {
  case "$1" in
    200) printf 'can upload\n' ;;
    401) printf 'rejected outright, so it is revoked, expired, or was mistyped\n' ;;
    403) printf 'authenticates but lacks the org:ci scope uploads need\n' ;;
    404) printf 'no such organization, so check the org slug rather than the token\n' ;;
    000) printf 'could not reach sentry.io at all\n' ;;
    *)   printf 'unexpected response\n' ;;
  esac
}

# ── Preflight ────────────────────────────────────────────────────────────────
step "Checking tools"
command -v gh >/dev/null 2>&1 || {
  echo "  ✗ gh is not installed. brew install gh" >&2; exit 1; }
gh auth status >/dev/null 2>&1 || {
  echo "  ✗ gh is not logged in. Run: gh auth login" >&2; exit 1; }
ok "gh is logged in"

REPO="$(gh repo view --json nameWithOwner -q .nameWithOwner)" || {
  echo "  ✗ No GitHub remote here. Push this repo first." >&2; exit 1; }
ok "this repo is $REPO"

# The org is a repo variable rather than a secret, so read it from there and
# fall back to the store. Getting this wrong produces a 404 that reads like a
# token problem.
ORG="$(gh variable get SENTRY_ORG --repo "$REPO" 2>/dev/null || true)"
[ -n "$ORG" ] || ORG="$(store_read "$STORE_ORG_KEY")"
if [ -z "$ORG" ]; then
  printf "  Sentry organization slug: "
  read -r ORG
fi
[ -n "$ORG" ] || { echo "  ✗ No organization slug. Nothing was set." >&2; exit 1; }
ok "organization is $ORG"

# ── Find a token that works ──────────────────────────────────────────────────
step "Token"

TOKEN="$(store_read "$STORE_TOKEN_KEY")"
if [ -n "$TOKEN" ]; then
  STATUS="$(token_status "$TOKEN" "$ORG")"
  if [ "$STATUS" = "200" ]; then
    ok "the token saved in $STORE $(explain_status "$STATUS")"
  else
    warn "the saved token: HTTP $STATUS, $(explain_status "$STATUS")"
    TOKEN=""
  fi
fi

if [ -z "$TOKEN" ]; then
  cat <<EOF

  Create an organization token:

    https://sentry.io/settings/$ORG/auth-tokens/

  Give it the scopes sentry-cli needs to upload debug files. The default
  org token scopes cover it. It starts with sntrys_.

  Not the user auth token. That one is for the setup script that creates
  projects, is stored separately, and answers 401 to uploads.

EOF
  stty -echo
  printf "  Paste the organization token (input hidden): "
  read -r TOKEN
  stty echo
  printf "\n"

  [ -n "$TOKEN" ] || { echo "  ✗ Nothing entered. Nothing was set." >&2; exit 1; }

  STATUS="$(token_status "$TOKEN" "$ORG")"
  if [ "$STATUS" != "200" ]; then
    echo "  ✗ That token cannot upload: HTTP $STATUS, $(explain_status "$STATUS")" >&2
    echo "    Nothing was set and nothing was saved, deliberately: setting a token" >&2
    echo "    that fails here means finding out from a failed release build." >&2
    unset TOKEN
    exit 1
  fi
  ok "token can upload to $ORG"

  store_write "$STORE_TOKEN_KEY" "$TOKEN"
  ok "saved it to $STORE, so the next repo does not ask"
fi

store_write "$STORE_ORG_KEY" "$ORG"

# ── Set it ───────────────────────────────────────────────────────────────────
step "Secret"

printf '%s' "$TOKEN" | gh secret set SENTRY_AUTH_TOKEN --repo "$REPO"
ok "set SENTRY_AUTH_TOKEN on $REPO"
unset TOKEN

PROJECT="$(gh variable get SENTRY_PROJECT --repo "$REPO" 2>/dev/null || true)"
if [ -n "$PROJECT" ]; then
  ok "SENTRY_PROJECT is $PROJECT"
else
  warn "SENTRY_PROJECT is not set on $REPO. Uploads need it; run setup_sentry.main.kts."
fi

cat <<EOF

$(bold "Done.")

The token was checked against the upload endpoint before it was set, so this
cannot leave you with a secret that fails later.

Other repos in the $ORG organization reuse this token. Run this script in each
of them and it will not ask again.

EOF
