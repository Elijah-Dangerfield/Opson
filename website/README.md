# Opson website

Marketing site and waitlist for Opson, built with [Astro](https://astro.build).
Decisions and the waitlist API contract are in
[`docs/plans/website.md`](../docs/plans/website.md).

## Commands

Run these inside `website/`.

| Command           | Action                                          |
| ----------------- | ----------------------------------------------- |
| `npm ci`          | Install dependencies                            |
| `npm run dev`     | Start the dev server at `localhost:4321/Opson/` |
| `npm test`        | Run the unit tests in `src/lib/` (`node --test`) |
| `npm run build`   | Build the site to `./dist/`                     |
| `npm run preview` | Serve the build locally                         |

`npm test` needs Node 23.6 or newer, which runs `.ts` files directly.

## Structure

- `src/pages/`: one file per route: `index` (home and waitlist), `privacy`,
  `terms`, `contact`, `404`
- `src/layouts/Layout.astro`: shared shell (meta tags, nav, footer)
- `src/components/`: `Nav`, `Footer`, `WaitlistForm`, `Screenshot`
- `src/lib/waitlist.ts`: the waitlist request and what each answer means
- `src/lib/paths.ts`: puts the base path in front of every link
- `src/content/site-privacy.md`: the website's own privacy wording
- `src/styles/global.css`: colors, type and shared rules
- `src/fonts/`: Atkinson Hyperlegible Next, subset to Latin (OFL)
- `public/`: the logo files (`favicon.svg`, the bowl without steam; `opson-mark.svg`, the
  mark on its white disc for the dark nav; `apple-touch-icon.png`; `og-image.png`, the
  link preview card) and `.nojekyll`. Sources are in `docs/design/brand/`

Some content comes from elsewhere in the repo at build time, so there is
nothing to keep in sync by hand:

- `/privacy` and `/terms` render `legal/privacy.md` and `legal/terms.md`. The
  privacy page puts `src/content/site-privacy.md` above the app policy. That
  wording stays in `website/` because editing `legal/` publishes to
  nightjarlabs.llc through the legal-sync workflow.
- The home page uses the App Store screenshots in
  `docs/app-store/screenshots/` and one design image,
  `docs/design/v3/support.png`, cropped to its export and delete buttons.
  Astro resizes them to WebP at 1x and 2x when it builds.

The layout borrows cronometer.com's shape: a dark band with the headline, the
form and a phone, then one colored block per feature. The colors are the
app's: Pine's dark green for the band, the first app icon's clay (`#A4512D`) as the
accent, and the four meal colors for the blocks. `global.css` lists the
contrast of every text pair. The site is light only.

## The waitlist

The form on the home page posts JSON to `${PUBLIC_API_URL}/v1/waitlist`:

```json
{ "email": "a@b.co", "source": "home", "company": "" }
```

- `source` is the `?c=` value from the page URL when it matches
  `^[a-z0-9-]{1,32}$`, otherwise the page name. So
  `…/Opson/?c=reddit-oct` tags signups from that link.
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
so that list needs the site's origin (`https://elijah-dangerfield.github.io`
today) and `http://localhost:4321` for local work.

## Going live

The deploy workflow is `.github/workflows/pages.yml`. It does nothing until
you switch it on:

1. In the repo, **Settings → Pages → Build and deployment → Source**: pick
   **GitHub Actions**.
2. In **Settings → Secrets and variables → Actions → Variables**, add:

   | Variable         | Value                                  |
   | ---------------- | -------------------------------------- |
   | `SITE_ENABLED`   | `true`                                 |
   | `SITE_URL`       | `https://elijah-dangerfield.github.io` |
   | `SITE_BASE`      | `/Opson`                               |
   | `PUBLIC_API_URL` | `https://opson-server-prod.fly.dev`    |

3. Make sure the server's `WAITLIST_ALLOWED_ORIGINS` includes
   `https://elijah-dangerfield.github.io`.
4. Run the **Deploy Pages** workflow from the Actions tab, or push a change
   under `website/`.

The site is then at <https://elijah-dangerfield.github.io/Opson/>.

## Moving to a custom domain

No code changes. Every link and asset goes through `import.meta.env.BASE_URL`.

1. Set `SITE_URL` to `https://<domain>` and `SITE_BASE` to `/`.
2. Add the domain under **Settings → Pages → Custom domain** and point DNS at
   GitHub Pages as it describes. GitHub keeps the domain in the Pages settings,
   so no `CNAME` file is needed when deploying with Actions.
3. Add `https://<domain>` to the server's `WAITLIST_ALLOWED_ORIGINS`.
4. Re-run **Deploy Pages**.

To check a base path locally before switching, build with it:

```sh
SITE_BASE=/ npm run build
SITE_BASE=/Opson npm run build
```
