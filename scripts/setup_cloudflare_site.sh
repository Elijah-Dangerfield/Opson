#!/usr/bin/env bash
# Puts the marketing site (website/) live on Cloudflare Pages at your domain.
#
# Prompts for the Cloudflare API token (hidden), the account id, the domain and
# whether its DNS is already on Cloudflare. Then it:
#   1. checks the token,
#   2. creates the Pages project "opson" if it isn't there,
#   3. attaches the domain (and www) to the project,
#   4. stores CLOUDFLARE_API_TOKEN and CLOUDFLARE_ACCOUNT_ID as repo secrets,
#   5. sets SITE_URL, SITE_BASE=/ and SITE_ENABLED=true as repo variables,
#   6. allows the site's origin on opson-server-prod (WAITLIST_ALLOWED_ORIGINS),
#   7. runs the "Deploy website" workflow.
# Safe to re-run: every step checks before it writes.
#
# Usage: scripts/setup_cloudflare_site.sh
set -euo pipefail
cd "$(git rev-parse --show-toplevel)"

REPO=Elijah-Dangerfield/Opson
PROJECT=opson
API=https://api.cloudflare.com/client/v4

bold() { printf "\033[1m%s\033[0m\n" "$1"; }
ok()   { printf "  \033[32m✓\033[0m %s\n" "$1"; }
die()  { printf "  \033[31m✗\033[0m %s\n" "$1" >&2; exit 1; }

for tool in gh fly curl python3; do
  command -v "$tool" >/dev/null || die "$tool is not installed"
done

printf "Cloudflare API token (Pages edit; hidden): "
stty -echo; read -r CF_TOKEN; stty echo; printf "\n"
printf "Cloudflare account id: "; read -r CF_ACCOUNT
printf "Domain, without https or www (e.g. opson.app): "; read -r DOMAIN
printf "Is this domain's DNS already on Cloudflare, in the same account? [y/n]: "; read -r ON_CF

[ -n "$CF_TOKEN" ] || die "No token entered. Nothing changed."
[ -n "$CF_ACCOUNT" ] || die "No account id entered. Nothing changed."
DOMAIN="${DOMAIN#https://}"; DOMAIN="${DOMAIN#http://}"; DOMAIN="${DOMAIN#www.}"; DOMAIN="${DOMAIN%/}"
[[ "$DOMAIN" == *.* ]] || die "\"$DOMAIN\" doesn't look like a domain. Nothing changed."

# The token reaches curl on stdin, never on its command line where `ps` shows it.
cf() {
  local method="$1" path="$2" body="${3:-}"
  if [ -n "$body" ]; then
    printf 'header = "Authorization: Bearer %s"\n' "$CF_TOKEN" |
      curl -sS -K - -X "$method" -H "Content-Type: application/json" --data "$body" "$API$path"
  else
    printf 'header = "Authorization: Bearer %s"\n' "$CF_TOKEN" | curl -sS -K - -X "$method" "$API$path"
  fi
}
success() { python3 -I -c 'import json,sys; d=json.load(sys.stdin); sys.exit(0 if d.get("success") else 1)'; }
errors() { python3 -I -c 'import json,sys; d=json.load(sys.stdin); print("; ".join(e.get("message","") for e in d.get("errors",[])) or "unknown error")'; }

bold "Cloudflare"
cf GET /user/tokens/verify | success || die "Cloudflare rejected the token. Nothing changed."
ok "token works"

if cf GET "/accounts/$CF_ACCOUNT/pages/projects/$PROJECT" | success; then
  ok "Pages project \"$PROJECT\" exists"
else
  out=$(cf POST "/accounts/$CF_ACCOUNT/pages/projects" "{\"name\":\"$PROJECT\",\"production_branch\":\"main\"}")
  echo "$out" | success || die "Couldn't create the Pages project: $(echo "$out" | errors)"
  ok "created Pages project \"$PROJECT\" ($PROJECT.pages.dev)"
fi

if [[ "$ON_CF" =~ ^[Yy] ]]; then
  HOSTS=("$DOMAIN" "www.$DOMAIN")
  SITE_URL="https://$DOMAIN"
else
  HOSTS=("www.$DOMAIN")
  SITE_URL="https://www.$DOMAIN"
fi
existing=$(cf GET "/accounts/$CF_ACCOUNT/pages/projects/$PROJECT/domains")
for host in "${HOSTS[@]}"; do
  if echo "$existing" | python3 -I -c 'import json,sys; h=sys.argv[1]; d=json.load(sys.stdin); sys.exit(0 if any(x.get("name")==h for x in d.get("result",[])) else 1)' "$host"; then
    ok "$host is already on the project"
  else
    out=$(cf POST "/accounts/$CF_ACCOUNT/pages/projects/$PROJECT/domains" "{\"name\":\"$host\"}")
    echo "$out" | success || die "Couldn't add $host: $(echo "$out" | errors)"
    ok "added $host"
  fi
done

printf "\n"; bold "GitHub"
printf '%s' "$CF_TOKEN" | gh secret set CLOUDFLARE_API_TOKEN -R "$REPO" >/dev/null && ok "secret CLOUDFLARE_API_TOKEN"
printf '%s' "$CF_ACCOUNT" | gh secret set CLOUDFLARE_ACCOUNT_ID -R "$REPO" >/dev/null && ok "secret CLOUDFLARE_ACCOUNT_ID"
unset CF_TOKEN
gh variable set SITE_URL -R "$REPO" -b "$SITE_URL" >/dev/null && ok "SITE_URL=$SITE_URL"
gh variable set SITE_BASE -R "$REPO" -b "/" >/dev/null && ok "SITE_BASE=/"

printf "\n"; bold "Prod server"
ORIGINS="https://$DOMAIN,https://www.$DOMAIN"
fly secrets set "WAITLIST_ALLOWED_ORIGINS=$ORIGINS" -a opson-server-prod >/dev/null && ok "WAITLIST_ALLOWED_ORIGINS=$ORIGINS (the server restarts)"

printf "\n"; bold "Publish"
gh variable set SITE_ENABLED -R "$REPO" -b "true" >/dev/null && ok "SITE_ENABLED=true"
gh workflow run pages.yml -R "$REPO" --ref main >/dev/null && ok "started the \"Deploy website\" workflow"

printf "\n"
if [[ ! "$ON_CF" =~ ^[Yy] ]]; then
  bold "One step left, at Porkbun"
  echo "  Add a CNAME record: host \"www\", answer \"$PROJECT.pages.dev\"."
  echo "  Then add a URL forward from $DOMAIN to https://www.$DOMAIN."
  echo
fi
echo "  The site goes live at $SITE_URL once the workflow finishes (a few minutes)."
