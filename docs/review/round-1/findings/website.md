# Findings: website (`website/src`)

All 18 files in the ledger were read (there is no `website/tests`; the tests
sit next to `src/lib`). `npm --prefix website test` (17 tests) and
`npm --prefix website run build` are green.

## 1. The honeypot looked like a company field to autofill (fixed)

- **Where:** `src/components/WaitlistForm.astro:43`: a hidden input named
  `company`, labelled "Company".
- **Rule:** brief, "waitlist form error and loading states" (a signup must not
  fail silently).
- **Failure:** Chrome fills organisation fields from a saved address even with
  `autocomplete="off"`, and it fills fields a stylesheet hides. A filled trap
  is a signup the server drops while the page says "You're on the list."
- **Fix:** `fix(website): the waitlist honeypot no longer looks like a company
  field`. The input is `hp_leave_empty`, labelled "Leave this empty", and still
  sent as `company`, so the server contract is unchanged. The explanation is a
  JSX comment, so it isn't shipped in the page bots read (checked in
  `dist/index.html`). No automated test: the site has no DOM harness.

## Not problems

- **`src/lib/waitlist.ts` and its tests.** 204 for both added and already
  listed, 400, 429, server errors, network failure and a timeout all map to a
  message; the `?c=` source follows the server's rule. The form disables its
  button while sending, says "Sending…" in a polite live region, marks the
  field `aria-invalid` and moves focus to it or to the status.
- **Copy claims.** Checked against the app: ten modes (Full, Blind and eight
  between), per-number overrides, Search, Describe it, Saved meals, Copy
  yesterday (meal or day), Ate no details, Trends with no streaks, Export
  everything (CSV) and Delete everything under More, Support. Nothing claims
  barcode, photo or voice.
- **Accessibility.** Skip link, one `h1` a page, labelled form fields, `alt`
  text that describes each screenshot, decorative images with empty `alt`,
  `aria-current` in the nav, no third-party scripts or fonts.

## Product notes

- **"Opson is with Apple for review."** (`index.astro:167`) goes stale the day
  0.1 is approved. Sure; it needs changing at release.
- **There's no Android mention.** The site reads as iPhone-only while Android
  ships to Play. Moderately sure people will ask.
