# Findings: website (`website/src`, `website/astro.config.mjs`)

Round two. All 20 ledger files were read, plus `src/content/site-privacy.md`
and `src/styles/global.css`, which sit in the slice but carry no reviewed
extension. There is no `website/tests/`; the tests sit next to `src/lib`. The
unslop-code scanner is clean on `src/` and `astro.config.mjs`. `npm test`
(20 tests, 17 before) and `npm run build` are green.

Round one's fix (the honeypot sent as `company` from an input named
`hp_leave_empty`) still holds.

## 1. A build without the repo variables shipped a site for the old GitHub Pages address (fixed)

- **Where:** `website/astro.config.mjs:8-9` (before the fix):
  `SITE_URL || 'https://elijah-dangerfield.github.io'` and
  `SITE_BASE || '/Opson'`.
- **Rule:** §9, one switch picks the environment and every value follows it;
  a wrong environment fails at build time, not on a visitor's screen.
- **Failure:** the site moved on 2026-10-27 to Cloudflare Pages at the root of
  `https://www.opsontracker.com` (release checklist, "Website go-live"). The
  workflow passes `vars.SITE_URL` and `vars.SITE_BASE`, and a missing variable
  arrives as an empty string, so the fallback applied. A build with
  `SITE_BASE` unset put every link, stylesheet, font and image under
  `/Opson/`, which is a 404 on Cloudflare, and every canonical and `og:url`
  named github.io. A plain local `npm run build` showed it:
  `<link rel="canonical" href="https://elijah-dangerfield.github.io/Opson/">`.
- **Fix:** `fix(website): a build without the repo variables targets
  www.opsontracker.com at /`. The values come from `siteConfig` in
  `src/lib/site.ts`, which falls back to the live site at `/`.
  `src/lib/site.test.ts` covers no variables, empty variables, and set ones.
  After the fix the local build's canonical is `https://www.opsontracker.com/`
  and the CSS is at `/_astro/…`. The `fs.allow` comment in the config also
  said the home page read `../docs/app-store/screenshots`; it reads
  `src/assets/screens`, and the comment now names the legal pages instead.

## 2. Two comments described the site as it was (fixed)

- **Where:** `src/lib/paths.ts:1-2` called `/Opson/` on GitHub Pages the
  base "today". `src/pages/index.astro:7-8` said the screenshots are
  "rendered from the app's own previews by scripts/design_sync.main.kts".
  That script never writes into `website/`; the shots are hand-copied
  Roborazzi renders with strips added, as `website/README.md` describes.
- **Rule:** §1, stale comments are bugs.
- **Failure:** someone refreshing the screenshots runs design sync and
  expects the site to change. It doesn't.
- **Fix:** `docs(website): comments say where the site and its screenshots
  come from now`.

## 3. The closing block promised one email, and the contact page named a row the app doesn't have (fixed)

- **Where:** `src/pages/index.astro:159` said "we'll send one note when Opson
  is in the App Store". The hero (`:29`) and `src/content/site-privacy.md:18`
  keep room for "a few updates before". `src/pages/contact.astro:23` said
  "More, then Support, then Delete everything"; the row is "Support and
  sharing" (`strings_settings.xml:31`).
- **Rule:** §1, code that reads true, applied to the page people read.
- **Failure:** sending a product update breaks a promise on the same page as
  the form. Someone looking for a row called Support finds a different label.
- **Fix:** `fix(website): the closing promise matches the privacy page, and
  Delete everything is under Support and sharing`. No test: copy only, and the
  site has no DOM harness.

## 4. `website/README.md` and `docs/plans/website.md` still describe GitHub Pages (deferred, cross-slice)

- **Where:** neither file is in this slice.
- **Rule:** §1 stale docs; §9 release values documented where people look.
- **Failure:** someone following "Going live" sets `SITE_BASE=/Opson` and
  breaks the site, which finding 1 no longer covers because a set variable
  wins.
- **Change for the orchestrator:**
  - `website/README.md:14`: `localhost:4321/Opson/` becomes `localhost:4321/`.
  - `website/README.md:24`: the page list adds `health-data` and `credits`.
  - `website/README.md:82`: `…/Opson/?c=reddit-oct` becomes
    `https://www.opsontracker.com/?c=reddit-oct`.
  - `website/README.md:100`: the origin is `https://www.opsontracker.com`, not
    `https://elijah-dangerfield.github.io`.
  - `website/README.md:103-141` ("Going live" and "Moving to a custom
    domain"): replace both with the Cloudflare Pages setup. The site is live
    at `https://www.opsontracker.com` through `.github/workflows/pages.yml`
    (`wrangler pages deploy`). `scripts/setup_cloudflare_site.sh` sets the
    secrets `CLOUDFLARE_API_TOKEN` and `CLOUDFLARE_ACCOUNT_ID` and the
    variables `SITE_URL`, `SITE_BASE=/` and `SITE_ENABLED=true`. Unset
    `SITE_URL`/`SITE_BASE` fall back to the live site (`src/lib/site.ts`).
  - `docs/plans/website.md:8-16`: "deployed … to GitHub Pages" and "No domain
    yet … `/Opson`" become Cloudflare Pages at `https://www.opsontracker.com`,
    base `/`.

## Not problems

- **The waitlist contract.** `src/lib/waitlist.ts` matches
  `apps/server/.../routes/WaitlistRoutes.kt` and `domain/Waitlist.kt`:
  `POST /v1/waitlist`, the same source pattern, 204 for added, duplicate and
  honeypot, 400 and 429. The server's email rule is stricter than the
  browser's (`_a@b.co` passes `checkValidity` and gets a 400), and the form
  shows "doesn't look right" for it, which is correct.
- **`API_URL` falls back to prod.** That is right for a production build.
  `astro dev` reads `.env.development` (the dev server), and a local
  `astro preview` posting to prod is refused by prod's CORS list.
- **Footer year at build time.** The site redeploys on every push to
  `website/` or `legal/`, so the year can only lag until the next one.
- **Legal pages.** They render `legal/*.md`, the same files `legal-sync.yml`
  publishes to nightjarlabs.llc, so the two copies can't drift.

## Second review

A second reviewer read the branch's four commits against the merge base,
looking only for regressions they introduced, and flagged five things. Fates:

1. **A build with the repo variables might no longer target what they say
   (f6efd85a): not a regression.** Built four ways with `npx astro build`:
   no variables, prod (`https://www.opsontracker.com`, `/`), a preview host at
   `/Opson`, and at `/Opson/`. The first two give a canonical and `og:url` of
   `https://www.opsontracker.com/`, CSS, fonts and images under `/_astro/`,
   and no `github.io` anywhere. Both sub-path builds put every internal link,
   stylesheet, font `url()` and image under `/Opson/`, with the canonical,
   `og:image` and `og:url` on the preview host, and the nav still marks the
   current page. `astro.config.mjs` importing `src/lib/site.ts` loads in the
   build. The deploy workflow only builds for `main`, so there is no other
   target to check.
2. **`site.ts`'s header comment overstated the fallback (f6efd85a): real,
   fixed.** "Either way the build targets the live site" read as every build,
   but a set `SITE_URL` or `SITE_BASE` wins, which `site.test.ts` checks.
   Fixed in "docs(website): the site config comment says set variables win".
   No test: comment only.
3. **The new copy might not match the app or the privacy page (de52ecb3):
   not a regression.** The closing block no longer promises a single email,
   so it agrees with the hero's "maybe a few updates before" and with
   `site-privacy.md`. The contact page's path matches the app: the tab is
   "More" (`shell_tab_more`), the row "Support and sharing"
   (`settings_more_support`), and "Delete everything"
   (`settings_support_delete`) sits on `SupportScreen`. The built pages show
   both lines.
4. **The rewritten comments (3a73ba96, f6efd85a) might be wrong in turn: not
   a regression.** `index.astro` now points at `website/README.md`, which does
   describe the Roborazzi renders and their strips. The `fs.allow` comment
   names privacy, terms, health data and credits, and those are the four pages
   that import `../../../legal/*.md`; nothing else reads outside `website/`.
   `paths.ts` matches `paths.test.ts`.
5. **`docs/review/LEDGER.md` edited (78741741): not a regression.** Only the
   website section changed: two new files and the ticks. Running
   `python3 scripts/review_ledger.py` leaves the file unchanged, so the list
   matches what the generator produces.

After the fix: `npm test` 20 of 20, `npm run build` green.

## Product notes

- **The waitlist reads as iPhone-only.** "Want to know when it's out?" and
  "in the App Store" leave out Android, which ships to Play. Round one said
  the same and it still stands. Moderately sure people will ask.
- **Once 0.1 is public, the waitlist is the wrong call to action.** If people
  can already download the app, "Join the waitlist" in the nav and hero should
  become store links, and the form a mailing list for updates. Sure the copy
  needs to change at release; unsure whether that has already happened.
- **The bare domain 302s to www.** The checklist records a Porkbun URL
  forward, so the canonical (`www`) and the redirect agree. A 301 would let
  search engines carry the bare domain's links over. Fairly sure it's worth
  the switch.

## Deferred cross-slice items: fates (2026-10-28)

Applied on branch `worktree-agent-a89d6bb1fb957d17a` once every slice was on main.

- Finding 4: applied in 2f254417.
