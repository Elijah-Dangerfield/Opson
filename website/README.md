# Opson website

Marketing site and waitlist for Opson, built with [Astro](https://astro.build).
Decisions and the waitlist API contract are in
[`docs/plans/website.md`](../docs/plans/website.md).

## Commands

Run these inside `website/`.

| Command           | Action                                          |
| ----------------- | ----------------------------------------------- |
| `npm ci`          | Install dependencies                            |
| `npm run dev`     | Start the dev server at `localhost:4321/`       |
| `npm test`        | Run the unit tests in `src/lib/` (`node --test`) |
| `npm run build`   | Build the site to `./dist/`                     |
| `npm run preview` | Serve the build locally                         |

`npm test` needs Node 23.6 or newer, which runs `.ts` files directly.

## Structure

- `src/pages/`: one file per route: `index` (home and waitlist), `privacy`,
  `terms`, `health-data`, `credits`, `contact`, `recipe` (a shared recipe), `404`,
  and `.well-known/assetlinks.json` (Android App Links, built from a variable)
- `src/lib/recipe.ts`: what the recipe page asks the server and how it reads the answer
- `src/lib/appLinks.ts`: the two files that let a recipe link open the app
- `src/layouts/Layout.astro`: shared shell (meta tags, nav, footer)
- `src/components/`: `Nav`, `Footer`, `WaitlistForm`, `Screenshot`
- `src/lib/waitlist.ts`: the waitlist request and what each answer means
- `src/lib/paths.ts`: puts the base path in front of every link
- `src/content/site-privacy.md`: the website's own privacy wording
- `src/styles/global.css`: colors, type and shared rules
- `src/fonts/`: Atkinson Hyperlegible Next, subset to Latin (OFL)
- `public/_redirects` and `public/_headers`: Cloudflare Pages rules. `/r/<code>` is
  served by the recipe page; the Apple file is sent as JSON
- `public/.well-known/apple-app-site-association`: Apple's universal links file
  (`appLinks.test.ts` keeps it equal to `appleAppSiteAssociation()`)
- `public/`: the logo files (`favicon.svg`, the bowl without steam; `opson-mark.svg`, the
  mark on its white disc for the dark nav; `apple-touch-icon.png`; `og-image.png`, the
  link preview card) and `.nojekyll`. Sources are in `docs/design/brand/`

Some content comes from elsewhere in the repo at build time, so there is
nothing to keep in sync by hand:

- `/privacy` and `/terms` render `legal/privacy.md` and `legal/terms.md`. The
  privacy page puts `src/content/site-privacy.md` above the app policy. That
  wording stays in `website/` because editing `legal/` publishes to
  nightjarlabs.llc through the legal-sync workflow.

The phone shots in `src/assets/screens/` are copies, so they go stale when the
app changes. Each is a light-theme Compose preview rendered by
`./gradlew :apps:screenshots:recordRoborazziDebug` (390x844 pt at 3x, into
`apps/screenshots/build/outputs/roborazzi/`), with a 177 px strip added on top
in the image's top-left colour and a 102 px strip at the bottom in its
bottom-left colour, so the rounded frame doesn't cut into the app. The previews
they come from:

| Shot             | Preview render                               |
| ---------------- | -------------------------------------------- |
| `today-full`     | `today-previews__today-full`                 |
| `today-blind`    | `today-previews__today-no-numbers-light`     |
| `search`         | `search-preview__full-light`                 |
| `describe`       | `logger-previews__logger-describe`           |
| `targets-by-day` | `targets-screen__targets-by-day`             |
| `modes`          | `display-screen__display`                    |
| `trends`         | `trends-previews__trends`                    |
| `support`        | `support-screen__support`                    |

Astro resizes them to WebP at 1x and 2x when it builds.

The layout borrows cronometer.com's shape: a dark band with the headline, the
form and a phone, then one colored block per feature. The colors are the
app's (`Slate.kt`): its ink (`#18202E`) for the band, the orange accent
(`#A3500A`, and the lighter `#F7924A` for buttons on the band), the logo's teal
bowl as the second color, and the four meal colors for the blocks. `global.css`
lists the contrast of every text pair. The site is light only.

## The waitlist

The form on the home page posts JSON to `${PUBLIC_API_URL}/v1/waitlist`:

```json
{ "email": "a@b.co", "source": "home", "company": "" }
```

- `source` is the `?c=` value from the page URL when it matches
  `^[a-z0-9-]{1,32}$`, otherwise the page name. So
  `https://www.opsontracker.com/?c=reddit-oct` tags signups from that link.
- `company` is a honeypot. It's visually hidden and out of the tab order, so
  people leave it empty. The server answers 204 and stores nothing when it
  has a value.
- 204 shows "You're on the list.", whether the email is new or already there.
  400 asks the person to check the address, 429 asks them to wait a few
  minutes, and anything else (a network failure, a timeout, a 5xx) asks them
  to try again in a minute.

The request and the handling of each answer live in `src/lib/waitlist.ts`
and are tested in `src/lib/waitlist.test.ts`. The script in
`WaitlistForm.astro` only wires that to the page.

Without JavaScript the form is hidden and a note offers the contact address
instead, because the server only accepts JSON.

`PUBLIC_API_URL` defaults to `https://opson-server-prod.fly.dev`. The server
only answers browsers from origins listed in its `WAITLIST_ALLOWED_ORIGINS`,
so that list needs the site's origin (`https://www.opsontracker.com`) and
`http://localhost:4321` for local work.

## Shared recipes and the links that open the app

A recipe shared from the app (`release.recipes`, `docs/plans/recipes.md`) has the
link `https://www.opsontracker.com/r/<code>`, and its QR code carries that link.
Cloudflare serves `/r/*` from `src/pages/recipe.astro` (the `_redirects` rule keeps
the address), which reads the code, calls `GET ${PUBLIC_API_URL}/v1/recipes/public/<code>`
(open to any origin, no account) and shows the recipe, the line that a person made
it and Opson didn't check it, Open in Opson (`opson://recipe/<code>`) and links to
both stores. Without JavaScript it says to open the link on a phone with Opson.

### Universal links and App Links

With these in place, the link opens the app when it is installed and the page only
when it isn't. Until then everything still works: the link opens the page, and Open
in Opson opens the app.

- **iOS.** `public/.well-known/apple-app-site-association` claims `/r/*` for
  `MSMDV43SUS.com.opson.Opson`, and `_headers` serves it as JSON. The app's side is
  the Associated Domains entitlement (`applinks:www.opsontracker.com`,
  `applinks:opsontracker.com`). Only `iosAppDebug.entitlements` carries it today,
  because a release build signs with an App Store profile that has to include the
  capability, or the export fails. To turn it on for releases: in
  developer.apple.com, Certificates, Identifiers & Profiles, Identifiers,
  `com.opson.Opson`, tick Associated Domains and save; then copy the
  `com.apple.developer.associated-domains` key from `iosAppDebug.entitlements` into
  `iosApp.entitlements`. The next release's `get_provisioning_profile` makes a new
  profile with it.
- **Android.** The manifest already asks Android to verify `/r/` links on both
  hosts. Android checks `/.well-known/assetlinks.json`, which needs the SHA-256 of
  the certificate Google signs the app with: Play Console, the app, Test and release,
  App integrity, App signing, "App signing key certificate", SHA-256. Set it as the
  repo variable `ANDROID_CERT_SHA256` (several, comma-separated, if you also want a
  debug or upload key) and redeploy the site. Without it the file is `[]`.

```bash
printf "Play app signing SHA-256 (AA:BB:...): "; read -r FP
[ -n "$FP" ] && gh variable set ANDROID_CERT_SHA256 --body "$FP" && gh workflow run pages.yml; unset FP
```

Check them after a deploy: `curl -i https://www.opsontracker.com/.well-known/apple-app-site-association`
should answer 200 with `content-type: application/json`, and Google's
[Statement List tester](https://developers.google.com/digital-asset-links/tools/generator)
should say the link is verified for `com.opson`.

## Going live

The site is live at <https://www.opsontracker.com> on Cloudflare Pages.
`.github/workflows/pages.yml` builds it and runs `wrangler pages deploy` on
every push under `website/`, or from the Actions tab. It does nothing until
the repo variable `SITE_ENABLED` is `true`.

`scripts/setup_cloudflare_site.sh` sets what the workflow needs and adds the
site to the prod server's `WAITLIST_ALLOWED_ORIGINS`:

| Name                    | Kind     | Value                          |
| ----------------------- | -------- | ------------------------------ |
| `CLOUDFLARE_API_TOKEN`  | secret   | a token with Pages edit        |
| `CLOUDFLARE_ACCOUNT_ID` | secret   | the Cloudflare account         |
| `SITE_URL`              | variable | `https://www.opsontracker.com` |
| `SITE_BASE`             | variable | `/`                            |
| `SITE_ENABLED`          | variable | `true`                         |

`PUBLIC_API_URL` is optional and defaults to the prod server. With
`SITE_URL` or `SITE_BASE` unset, the build falls back to the live site and
`/` (`src/lib/site.ts`).
