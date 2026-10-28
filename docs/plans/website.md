# Opson website and waitlist

Built like Downcard's site (`/Users/elijahdangerfield/Workspace/Cards/website`,
read-only reference): Astro, one file per route, a shared layout with nav and
footer, a theme in `global.css`, published to Cloudflare Pages by a workflow.

## Decisions

- **Where:** `website/` in this repo, Astro, deployed by
  `.github/workflows/pages.yml` to Cloudflare Pages (`wrangler pages deploy`).
- **Live at `https://www.opsontracker.com`, base `/`.** `astro.config.mjs`
  reads `SITE_URL` and `SITE_BASE` from the environment; the workflow passes
  the repo variables, and unset they fall back to the live site
  (`src/lib/site.ts`). Every internal link and asset goes through
  `import.meta.env.BASE_URL`, so a base path change needs no code.
- **Not live until the owner says.** The deploy job runs only when the repo
  variable `SITE_ENABLED` is `true`, so pushes don't fail before Pages is
  switched on.
- **Waitlist storage:** table `waitlist` in Supabase, written only by the
  server. Owner views and exports it from `apps/admin`. Grafana gets a daily
  count view, never emails.
- **Privacy and terms:** rendered from `legal/privacy.md` and `legal/terms.md`
  (imported at build time, not copied). The website's own wording (waitlist,
  no cookies or trackers on the site) lives in `website/` and is shown on the
  privacy page above the app policy. Editing `legal/` triggers the legal-sync
  PR to nightjarlabs.llc, so the site wording stays out of it.

## API contract (server agent builds, site agent calls)

`POST /v1/waitlist`, no auth, JSON:

```json
{ "email": "a@b.co", "source": "home", "company": "" }
```

- `email`: required, trimmed, max 254, a plausible address. Stored lower-cased.
- `source`: optional, `^[a-z0-9-]{1,32}$` (which page or campaign); anything
  else is stored as null.
- `company`: the honeypot. A real browser leaves it empty. If it has a value
  the server answers 204 and stores nothing.

Responses:

- `204` on success **and** when the email is already on the list (never
  reveal membership).
- `400 {"error":"invalid_email"}`
- `429 {"error":"rate_limited"}` past 5 per IP per 10 minutes, or 50 per IP a
  day.
- CORS: allowed origins from env `WAITLIST_ALLOWED_ORIGINS` (comma-separated;
  dev includes `http://localhost:4321`). Only this route gets that CORS
  policy.

The site reads the API base from `PUBLIC_API_URL` (repo variable at build;
default the prod server `https://opson-server-prod.fly.dev`).

## Waitlist privacy wording (site)

What we keep: the email, when it was added, and which page it came from. What
for: one email when Opson can be downloaded, and maybe a few product updates
before then, nothing else, never sold or shared. Getting removed: reply to any
email or write to the contact address, and the address is deleted.
